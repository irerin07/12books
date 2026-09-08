package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookSearchResult;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 카카오 책 검색 프록시.
 *
 * <p>어떤 실패든 {@link ErrorCode#EXTERNAL_API_ERROR}(502)로 바꾼다. 카카오의 상태 코드를
 * 그대로 흘리면 키가 거부당한 401이 클라이언트에게 "당신의 인증이 잘못됐다"로 읽힌다.
 * 원인은 로그에만 남긴다.
 */
@Component
public class KakaoBookClient {

	private static final Logger log = LoggerFactory.getLogger(KakaoBookClient.class);

	private static final String SEARCH_PATH = "/v3/search/book";
	private static final String AUTHORIZATION_PREFIX = "KakaoAK ";
	private static final int ISBN13_LENGTH = 13;

	/** 카카오가 저자를 주지 않는 책이 있다. 등록 DTO는 저자를 요구하므로 여기서 표준값으로 맞춘다. */
	private static final String UNKNOWN_AUTHOR = "작자 미상";

	private final RestClient restClient;
	private final KakaoProperties properties;

	/**
	 * 카카오를 동시에 부를 수 있는 요청 수의 상한.
	 *
	 * <p>타임아웃만으로는 장애가 격리되지 않는다. 카카오가 읽기 타임아웃 직전까지 끄는 상태가
	 * 되면 검색 요청 하나하나가 톰캣 스레드를 3초씩 붙잡고, 트래픽이 조금만 몰려도 스레드 풀이
	 * 말라 로그인·책 조회까지 함께 멈춘다. 상한을 넘은 요청은 <b>기다리지 않고</b> 즉시 실패시켜
	 * 검색의 장애가 검색 안에서 끝나게 한다.
	 */
	private final Semaphore permits;

	private final Counter rejected;

	/** 거부 로그를 남기는 최소 간격. */
	private static final Duration REJECTION_LOG_INTERVAL = Duration.ofSeconds(10);

	private final AtomicLong lastRejectionLoggedAt = new AtomicLong(0);

	public KakaoBookClient(RestClient restClient, KakaoProperties properties, MeterRegistry meterRegistry) {
		this.restClient = restClient;
		this.properties = properties;
		this.permits = new Semaphore(properties.maxConcurrentCalls());
		this.rejected = meterRegistry.counter("kakao.search.rejected");
	}

	public List<BookSearchResult> search(String query, int page) {
		if (!permits.tryAcquire()) {
			rejected.increment();
			logRejectionSparingly();
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
		}
		try {
			return doSearch(query, page);
		}
		finally {
			permits.release();
		}
	}

	/**
	 * 거부는 카운터로 세고 로그는 드물게 남긴다. 상한 초과마다 WARN을 찍으면 카카오가 느려진
	 * 바로 그 순간 로그가 폭주해, 장애 대응에 쓸 로그가 묻히고 로깅 자체가 병목이 된다.
	 * 얼마나 거부됐는지는 {@code kakao.search.rejected} 카운터가 정확히 알고 있다.
	 */
	private void logRejectionSparingly() {
		long now = System.nanoTime();
		long last = lastRejectionLoggedAt.get();
		if (now - last >= REJECTION_LOG_INTERVAL.toNanos() && lastRejectionLoggedAt.compareAndSet(last, now)) {
			log.warn("카카오 동시 호출 상한({}) 초과 — 대기하지 않고 실패시킨다. 누적 거부 {}건",
					properties.maxConcurrentCalls(), (long) rejected.count());
		}
	}

	private List<BookSearchResult> doSearch(String query, int page) {
		try {
			KakaoSearchResponse response = restClient.get()
					.uri(properties.baseUrl() + SEARCH_PATH, uri -> uri
							.queryParam("query", query)
							.queryParam("page", page)
							.build())
					.header("Authorization", AUTHORIZATION_PREFIX + properties.restApiKey())
					.retrieve()
					.body(KakaoSearchResponse.class);

			if (response == null || response.documents() == null) {
				// 빈 결과가 아니라 계약이 깨진 것이다. 빈 목록으로 숨기면 카카오 장애가
				// "검색 결과 없음"으로 보여 원인을 영영 못 찾는다.
				log.error("카카오 응답에 documents가 없습니다: queryLength={}, page={}", query.length(), page);
				throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
			}
			return response.documents().stream().map(KakaoBookClient::toResult).toList();
		}
		catch (RestClientException | DateTimeParseException e) {
			// 변환 실패도 카카오 쪽 문제다. 그대로 두면 500 + 스택트레이스가 되어
			// "카카오 실패는 E001/502"라는 Phase 2의 계약이 깨진다.
			// 검색어 원문은 남기지 않는다. 사용자가 친 값이 그대로 로그에 들어가면 개행 한 줄로
			// 로그 형태가 깨지고, 로그를 읽는 쪽에서 남의 검색어를 보게 된다.
			log.error("카카오 책 검색 실패: queryLength={}, page={}", query.length(), page, e);
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
		}
	}

	private static BookSearchResult toResult(KakaoDocument document) {
		if (document == null || document.title() == null || document.title().isBlank()) {
			// 제목은 not null 컬럼이다. 여기서 걸러내지 않으면 검색은 통과하고 등록에서
			// 터지거나, null document가 그대로 NPE가 되어 500으로 새어 나간다.
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
		}
		return new BookSearchResult(
				isbn13Of(document.isbn()),
				document.title(),
				authorsOf(document.authors()),
				document.publisher(),
				document.thumbnail(),
				publishedDateOf(document.datetime()),
				// 서명은 컨트롤러에서 붙인다. 카카오 응답을 옮기는 일과 출처를 보증하는 일은 다르다.
				null);
	}

	/**
	 * 배열이지만 정렬·검색 요구가 없어 콤마로 합쳐 다룬다.
	 *
	 * <p>원소에 null이 섞여 올 수 있다. 그대로 {@code String.join}에 넘기면 저자 이름이
	 * 문자열 {@code "null"}로 저장된다.
	 */
	private static String authorsOf(List<String> authors) {
		if (authors == null) {
			return UNKNOWN_AUTHOR;
		}
		String joined = authors.stream()
				.filter(each -> each != null && !each.isBlank())
				.map(String::trim)
				.collect(java.util.stream.Collectors.joining(", "));
		return joined.isBlank() ? UNKNOWN_AUTHOR : joined;
	}

	/**
	 * 카카오의 isbn은 "ISBN10 ISBN13" 형태다. 둘 다 없을 수도, 하나만 있을 수도 있다.
	 *
	 * <p>구분자 패턴의 백슬래시는 두 번 적어야 한다. 한 번만 적으면 Java 15부터 {@code \s}가
	 * <b>공백 문자 하나</b>를 뜻하는 문자열 이스케이프로 먼저 해석되어, 컴파일은 되지만 패턴이
	 * " +"가 되고 탭 같은 다른 여백에서는 나뉘지 않는다.
	 */
	private static String isbn13Of(String isbn) {
		if (isbn == null || isbn.isBlank()) {
			return null;
		}
		for (String candidate : isbn.trim().split("\\s+")) {
			if (candidate.length() == ISBN13_LENGTH) {
				return candidate;
			}
		}
		return null;
	}

	private static LocalDate publishedDateOf(String datetime) {
		if (datetime == null || datetime.isBlank()) {
			return null;
		}
		return OffsetDateTime.parse(datetime).toLocalDate();
	}

	private record KakaoSearchResponse(List<KakaoDocument> documents) {
	}

	private record KakaoDocument(String title, List<String> authors, String publisher, String isbn,
			String thumbnail, String datetime) {
	}
}
