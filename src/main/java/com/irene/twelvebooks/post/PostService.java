package com.irene.twelvebooks.post;

import com.irene.twelvebooks.book.Book;
import com.irene.twelvebooks.book.BookRepository;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.post.dto.PostCreateRequest;
import com.irene.twelvebooks.post.dto.PostResponse;
import com.irene.twelvebooks.reading.Reading;
import com.irene.twelvebooks.reading.ReadingLinker;
import com.irene.twelvebooks.user.User;
import com.irene.twelvebooks.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostService {

	private static final Logger log = LoggerFactory.getLogger(PostService.class);

	private final PostRepository postRepository;
	private final BookRepository bookRepository;
	private final UserRepository userRepository;
	private final ReadingLinker readingLinker;

	public PostService(PostRepository postRepository, BookRepository bookRepository,
			UserRepository userRepository, ReadingLinker readingLinker) {
		this.postRepository = postRepository;
		this.bookRepository = bookRepository;
		this.userRepository = userRepository;
		this.readingLinker = readingLinker;
	}

	/**
	 * 감상평을 쓴다. 서재에 없는 책이면 {@code READING}으로 만들어 연결한다 —
	 * "책 담기를 잊어도 글은 써진다"(spec.md §1.4)가 코드로 지켜지는 지점.
	 *
	 * <p>연결과 저장은 한 트랜잭션이고, 연결한 기록은 커밋까지 잠가 둔다. 그 사이에 같은 기록이
	 * 서재에서 빠지면 아직 저장되지 않은 글이 사라진 id로 insert되어 외래 키에 걸린다 —
	 * {@code on delete set null}은 이미 저장된 글만 지킨다. {@link ReadingLinker#holdForWrite} 참고.
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

		Reading reading = readingLinker.linkOrCreate(authorId, book.getId());
		// 이미 빠진 뒤라면 연결 없이 쓴다. 글을 막을 이유가 아니고, 삭제 이후의 정상 상태와 같다.
		Long readingId = readingLinker.holdForWrite(reading.getId()).orElse(null);

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
		return PostResponse.of(postRepository.save(post), author, book);
	}

	@Transactional(readOnly = true)
	public PostResponse read(Long postId) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
		User author = userRepository.findById(post.getAuthorId())
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		Book book = bookRepository.findById(post.getBookId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
		return PostResponse.of(post, author, book);
	}

	/**
	 * 삭제는 작성자 본인만. 남의 글에 404가 아니라 403을 주는 것은 서재와 같은 이유다 —
	 * 감상평은 공개 글이라 존재 자체가 비밀이 아니다.
	 */
	@Transactional
	public void remove(Long userId, Long postId) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
		if (!post.writtenBy(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		postRepository.delete(post);
	}

	@Transactional(readOnly = true)
	public CursorPage<PostResponse> byBook(Long bookId, Long cursor, int size) {
		if (!bookRepository.existsById(bookId)) {
			// 빈 목록으로 답하면 "글이 아직 없는 책"과 "없는 책"이 구분되지 않는다.
			throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
		}
		return assemble(postRepository.findBookPage(bookId, cursor, PageRequest.ofSize(size + 1)), size);
	}

	@Transactional(readOnly = true)
	public CursorPage<PostResponse> explore(Long cursor, int size) {
		return assemble(postRepository.findExplorePage(cursor, PageRequest.ofSize(size + 1)), size);
	}

	/**
	 * 페이지의 글들을 응답으로 바꾼다.
	 *
	 * <p>작성자와 책은 <b>페이지 전체를 모아 한 번씩</b> 조회한다. 글마다 따로 읽으면 페이지
	 * 크기만큼 쿼리가 늘어나고, 그 비용은 Phase 5의 피드에서 그대로 커진다. 이 방식은 한 페이지가
	 * 20건이든 50건이든 쿼리가 세 번이다.
	 */
	private CursorPage<PostResponse> assemble(List<Post> rows, int size) {
		CursorPage<Post> page = CursorPage.of(rows, size, Post::getId);

		Map<Long, User> authors = userRepository.findAllById(
						page.items().stream().map(Post::getAuthorId).distinct().toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		Map<Long, Book> books = bookRepository.findAllById(
						page.items().stream().map(Post::getBookId).distinct().toList()).stream()
				.collect(Collectors.toMap(Book::getId, Function.identity()));

		return new CursorPage<>(
				page.items().stream()
						.map(post -> PostResponse.of(post, authors.get(post.getAuthorId()),
								books.get(post.getBookId())))
						.toList(),
				page.nextCursor(), page.hasNext());
	}
}
