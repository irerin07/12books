package com.irene.twelvebooks.post;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.post.dto.PostCreateRequest;
import com.irene.twelvebooks.post.dto.PostResponse;
import com.irene.twelvebooks.reading.ReadingLinker;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PostService {

	private static final Logger log = LoggerFactory.getLogger(PostService.class);

	private final PostRepository postRepository;
	private final BookRepository bookRepository;
	private final UserRepository userRepository;
	private final ReadingLinker readingLinker;
	private final PostLikeService postLikeService;
	private final Clock clock;

	public PostService(PostRepository postRepository, BookRepository bookRepository,
			UserRepository userRepository, ReadingLinker readingLinker,
			PostLikeService postLikeService, Clock clock) {
		this.postRepository = postRepository;
		this.bookRepository = bookRepository;
		this.userRepository = userRepository;
		this.readingLinker = readingLinker;
		this.postLikeService = postLikeService;
		this.clock = clock;
	}

	/**
	 * 감상평을 쓴다. 서재에 없는 책이면 {@code READING}으로 만들어 연결한다 —
	 * "책 담기를 잊어도 글은 써진다"(spec.md §1.4)가 코드로 지켜지는 지점.
	 *
	 * <p>연결과 저장은 한 트랜잭션이고, 연결한 기록은 커밋까지 잠가 둔다. 그 사이에 같은 기록이
	 * 서재에서 빠지면 아직 저장되지 않은 글이 사라진 id로 insert되어 외래 키에 걸린다 —
	 * {@code on delete set null}은 이미 저장된 글만 지킨다. {@link ReadingLinker#linkForWrite} 참고.
	 *
	 * <p>{@link ReadingLinker}의 insert는 독립 트랜잭션이라 여기에 트랜잭션이 있어도
	 * 유니크 제약 위반 뒤 재조회가 살아 있다 — 실패가 안쪽 트랜잭션과 함께 끝나기 때문이다.
	 */
	@Transactional
	public PostResponse write(Long authorId, PostCreateRequest request) {
		Book book = bookRepository.findById(request.bookId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
		User author = userRepository.findById(authorId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// 빈 값이면 붙일 기록이 없다는 뜻이다(그 사이 서재에서 빠졌다). 글을 막을 이유가 아니고,
		// 연결 없는 글은 삭제 이후의 정상 상태와 같다.
		Long readingId = readingLinker.linkForWrite(authorId, book.getId()).orElse(null);

		Post post;
		try {
			post = Post.write(authorId, book.getId(), readingId, request.content(),
					request.fromPage(), request.toPage(), request.spoilerOrDefault());
		}
		catch (IllegalArgumentException e) {
			// DTO 검증이 이미 같은 규칙을 보지만, 엔티티도 스스로를 지킨다. 여기 걸렸다면
			// 클라이언트 입력 문제이므로 500이 아니라 400이다. 사유는 로그에만 남긴다.
			log.debug("감상평이 불변식에 걸렸습니다: authorId={}", authorId, e);
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		// 방금 만든 글이라 좋아요가 있을 수 없다. 확인하러 가는 것은 답을 아는 질문이다.
		return PostResponse.of(postRepository.save(post), author, book, false);
	}

	@Transactional(readOnly = true)
	public PostResponse read(Long viewerId, Long postId) {
		Post post = postRepository.findLive(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
		User author = userRepository.findById(post.getAuthorId())
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		Book book = bookRepository.findById(post.getBookId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
		return PostResponse.of(post, author, book,
				!postLikeService.likedAmong(viewerId, List.of(postId)).isEmpty());
	}

	/**
	 * 삭제는 작성자 본인만. 남의 글에 404가 아니라 403을 주는 것은 서재와 같은 이유다 —
	 * 감상평은 공개 글이라 존재 자체가 비밀이 아니다.
	 */
	@Transactional
	public void remove(Long userId, Long postId) {
		Post post = postRepository.findLive(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
		if (!post.writtenBy(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		// 행은 남기고 플래그만 세운다. 달려 있던 좋아요·댓글도 함께 남는데, 글이 안 보이면
		// 거기에 닿는 경로가 전부 404라 화면에는 사라진 것과 같다. 지운 뒤에 "무엇이 있었나"를
		// 물을 수 있어야 해서 그 둘도 지우지 않는다.
		postRepository.softDelete(postId, LocalDateTime.now(clock));
	}

	@Transactional(readOnly = true)
	public CursorPage<PostResponse> byBook(Long viewerId, Long bookId, Long cursor, int size) {
		if (!bookRepository.existsById(bookId)) {
			// 빈 목록으로 답하면 "글이 아직 없는 책"과 "없는 책"이 구분되지 않는다.
			throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
		}
		return assemble(viewerId,
				postRepository.findBookPage(bookId, cursor, PageRequest.ofSize(size + 1)), size);
	}

	/**
	 * 한 사람이 쓴 감상평. 프로필의 글 목록이자 "내 글만 보기"다.
	 *
	 * <p>{@code /posts/me}를 따로 두지 않은 것은 둘이 같은 질문이기 때문이다 — 내 handle로
	 * 부르면 내 글이다. 경로를 나누면 같은 조회가 둘이 되고 한쪽만 고쳐지는 날이 온다.
	 */
	@Transactional(readOnly = true)
	public CursorPage<PostResponse> byAuthor(Long viewerId, String handle, Long cursor, int size) {
		Long authorId = userRepository.findByHandle(handle)
				// 빈 목록으로 답하면 "아직 안 쓴 사람"과 "없는 사람"이 구분되지 않는다.
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
				.getId();
		return assemble(viewerId,
				postRepository.findAuthorPage(authorId, cursor, PageRequest.ofSize(size + 1)), size);
	}

	/**
	 * 홈. <b>내 글과 내가 팔로우하는 사람의 글을 뺀</b> 최신순이다 — 아직 팔로우하지 않은
	 * 사람들을 만나는 자리.
	 *
	 * <p>팔로잉을 빼므로 {@link #timeline}과 서로 겹치지 않는다. 화면은 둘을 원하는 비율로
	 * 이어 붙이면 되고, 같은 글이 두 번 나올 일이 없다.
	 */
	@Transactional(readOnly = true)
	public CursorPage<PostResponse> home(Long viewerId, List<Long> followeeIds, Long cursor, int size) {
		List<Long> excluded = Stream.concat(Stream.of(viewerId), followeeIds.stream()).distinct().toList();
		return assemble(viewerId,
				postRepository.findHomePage(excluded, cursor, PageRequest.ofSize(size + 1)), size);
	}

	@Transactional(readOnly = true)
	public CursorPage<PostResponse> timeline(Long viewerId, List<Long> followeeIds, Long cursor, int size) {
		if (followeeIds.isEmpty()) {
			return new CursorPage<>(List.of(), null, false);
		}
		return assemble(viewerId,
				postRepository.findTimelinePage(followeeIds, cursor, PageRequest.ofSize(size + 1)), size);
	}

	/**
	 * 페이지의 글들을 응답으로 바꾼다.
	 *
	 * <p>작성자와 책, 그리고 <b>내가 누른 좋아요</b>는 페이지 전체를 모아 한 번씩 조회한다.
	 * 글마다 따로 읽으면 페이지 크기만큼 쿼리가 늘어나고, 그 비용은 피드에서 그대로 커진다.
	 * 이 방식은 한 페이지가 20건이든 50건이든 쿼리 수가 같다.
	 */
	private CursorPage<PostResponse> assemble(Long viewerId, List<Post> rows, int size) {
		CursorPage<Post> page = CursorPage.of(rows, size, Post::getId);

		Map<Long, User> authors = userRepository.findAllById(
						page.items().stream().map(Post::getAuthorId).distinct().toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		Map<Long, Book> books = bookRepository.findAllById(
						page.items().stream().map(Post::getBookId).distinct().toList()).stream()
				.collect(Collectors.toMap(Book::getId, Function.identity()));

		Set<Long> liked = postLikeService.likedAmong(viewerId,
				page.items().stream().map(Post::getId).toList());

		return new CursorPage<>(
				page.items().stream()
						.map(post -> PostResponse.of(post, authors.get(post.getAuthorId()),
								books.get(post.getBookId()), liked.contains(post.getId())))
						.toList(),
				page.nextCursor(), page.hasNext());
	}
}
