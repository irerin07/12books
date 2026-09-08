package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.book.dto.BookSearchResult;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;

/**
 * 검색 결과에 서명을 붙이고, 등록 요청이 그 서명을 그대로 가져왔는지 확인한다.
 *
 * <p>{@code books}는 공용 테이블이다. 등록 본문을 그대로 믿으면 인증된 사용자 누구나
 * 실제 ISBN에 지어낸 제목·저자·썸네일을 붙여 먼저 등록할 수 있고, unique 제약 때문에
 * 이후 그 책을 담는 모든 사용자가 오염된 행을 받는다. 서명은 "이 조합은 우리 검색이 준
 * 것"이라는 증거이므로, 서버는 등록 시 카카오를 다시 부르지 않고도 출처를 확인할 수 있다.
 */
@Component
public class BookSignature {

	private static final String ALGORITHM = "HmacSHA256";

	/** 키나 직렬화 방식을 바꿀 때 옛 서명과 구분하기 위한 접두사. */
	private static final String VERSION = "v1";

	/**
	 * HMAC-SHA256의 블록에 맞춘 최소 키 길이. 이 키가 뚫리면 등록 경로의 방어선이 통째로
	 * 사라지므로, 짧은 키로 조용히 기동하느니 부팅에서 죽는 편이 낫다.
	 */
	private static final int MINIMUM_SECRET_BYTES = 32;

	private final SecretKeySpec key;

	BookSignature(BookProperties properties) {
		byte[] secret = properties.signatureSecret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MINIMUM_SECRET_BYTES) {
			// 값 자체는 남기지 않는다. 길이만으로 무엇이 잘못됐는지 충분히 알 수 있다.
			throw new IllegalStateException(
					"twelvebooks.book.signature-secret은 UTF-8 기준 %d바이트 이상이어야 합니다 (현재 %d바이트)"
							.formatted(MINIMUM_SECRET_BYTES, secret.length));
		}
		this.key = new SecretKeySpec(secret, ALGORITHM);
	}

	public BookSearchResult signed(BookSearchResult result) {
		return result.withSignature(sign(result.isbn13(), result.title(), result.authors(),
				result.publisher(), result.thumbnailUrl(), result.publishedAt()));
	}

	public boolean matches(BookRegisterRequest request) {
		if (request.signature() == null) {
			return false;
		}
		String expected = sign(request.isbn13(), request.title(), request.authors(),
				request.publisher(), request.thumbnailUrl(), request.publishedAt());
		// 길이·내용이 같은지 상수 시간에 본다. 앞자리부터 비교하면 걸린 시간이 정답을 흘린다.
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				request.signature().getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String isbn13, String title, String authors, String publisher,
			String thumbnailUrl, LocalDate publishedAt) {
		String payload = Canonical.join(isbn13, title, authors, publisher, thumbnailUrl,
				publishedAt == null ? null : publishedAt.toString());
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return VERSION + "." + Base64.getUrlEncoder().withoutPadding()
					.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HMAC-SHA256을 쓸 수 없습니다", e);
		}
	}
}
