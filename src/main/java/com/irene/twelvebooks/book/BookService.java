package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class BookService {

	private final BookRepository bookRepository;

	private final BookInserter bookInserter;

	private final BookSignature bookSignature;

	public BookService(BookRepository bookRepository, BookInserter bookInserter,
			BookSignature bookSignature) {
		this.bookRepository = bookRepository;
		this.bookInserter = bookInserter;
		this.bookSignature = bookSignature;
	}

	/**
	 * 이미 있는 책이면 그 행을 그대로 돌려준다. 같은 책을 여러 사람이 담아도 행이 하나여야
	 * 책별 감상평 목록이 갈라지지 않는다.
	 *
	 * <p>사전 조회와 insert 사이는 비어 있다 — 두 요청이 함께 조회를 통과할 수 있다.
	 * 락을 걸지 않고, 유니크 제약 위반을 "누가 먼저 넣었다"는 신호로 읽어 재조회한다.
	 *
	 * <p>이 메서드에 트랜잭션을 걸지 않는 이유는 재조회를 살리기 위해서다. insert 실패가
	 * 같은 트랜잭션 안에서 일어나면 그 트랜잭션은 재조회도 커밋도 할 수 없다
	 * ({@link BookInserter} 참고). 그래서 insert만 독립 트랜잭션으로 격리한다.
	 */
	public Book upsert(BookRegisterRequest request) {
		if (!bookSignature.matches(request)) {
			// 검색을 거치지 않았거나 값을 고쳐 보냈다. books는 공용이라 여기서 막지 않으면
			// 먼저 등록한 사람의 조작이 이후 모든 사용자에게 그대로 간다.
			throw new BusinessException(ErrorCode.BOOK_SIGNATURE_MISMATCH);
		}
		String isbn13 = normalize(request.isbn13());
		String sourceKey = isbn13 == null ? sourceKeyOf(request) : null;

		Optional<Book> existing = find(isbn13, sourceKey);
		if (existing.isPresent()) {
			return existing.get();
		}
		try {
			Book book = isbn13 == null
					? Book.withSourceKey(sourceKey, request.title(), request.authors(),
							request.publisher(), request.thumbnailUrl(), request.publishedAt())
					: Book.withIsbn13(isbn13, request.title(), request.authors(),
							request.publisher(), request.thumbnailUrl(), request.publishedAt());
			return bookInserter.insert(book);
		}
		catch (DataIntegrityViolationException e) {
			// 사전 조회를 함께 통과한 다른 요청이 먼저 넣었다. 그 행이 정답이다.
			return find(isbn13, sourceKey).orElseThrow(() -> e);
		}
	}

	@Transactional(readOnly = true)
	public Book getById(Long id) {
		return bookRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
	}

	private Optional<Book> find(String isbn13, String sourceKey) {
		return isbn13 == null ? bookRepository.findBySourceKey(sourceKey) : bookRepository.findByIsbn13(isbn13);
	}

	private static String normalize(String isbn13) {
		return isbn13 == null || isbn13.isBlank() ? null : isbn13.trim();
	}

	/**
	 * ISBN이 없는 책의 대체 유일 키. 카카오가 같은 책을 항상 같은 문자열로 주므로
	 * 제목·저자·출판사 조합이면 실질적으로 구분된다.
	 *
	 * <p>필드를 이을 때 {@link Canonical}을 쓴다 — 구분자로 단순히 이으면 제목에 그 구분자가
	 * 들어간 순간 필드 경계가 사라져 서로 다른 책이 한 행으로 합쳐진다.
	 */
	private static String sourceKeyOf(BookRegisterRequest request) {
		String seed = Canonical.join(Canonical.normalized(request.title()),
				Canonical.normalized(request.authors()),
				Canonical.normalized(request.publisher()));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(seed.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}
