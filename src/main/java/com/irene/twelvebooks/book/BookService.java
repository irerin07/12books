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

	public BookService(BookRepository bookRepository, BookInserter bookInserter) {
		this.bookRepository = bookRepository;
		this.bookInserter = bookInserter;
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
		String isbn13 = normalize(request.isbn13());
		String sourceKey = isbn13 == null ? sourceKeyOf(request) : null;

		Optional<Book> existing = find(isbn13, sourceKey);
		if (existing.isPresent()) {
			return existing.get();
		}
		try {
			return bookInserter.insert(Book.builder()
					.isbn13(isbn13)
					.sourceKey(sourceKey)
					.title(request.title())
					.authors(request.authors())
					.publisher(request.publisher())
					.thumbnailUrl(request.thumbnailUrl())
					.publishedAt(request.publishedAt())
					.build());
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
	 */
	private static String sourceKeyOf(BookRegisterRequest request) {
		String seed = "%s|%s|%s".formatted(request.title(), request.authors(),
				request.publisher() == null ? "" : request.publisher());
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(seed.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}
