package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookSearchResult;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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

	private final RestClient restClient;
	private final KakaoProperties properties;

	public KakaoBookClient(RestClient restClient, KakaoProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public List<BookSearchResult> search(String query, int page) {
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
				return List.of();
			}
			return response.documents().stream().map(KakaoBookClient::toResult).toList();
		}
		catch (RestClientException e) {
			log.error("카카오 책 검색 실패: query={}, page={}", query, page, e);
			throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
		}
	}

	private static BookSearchResult toResult(KakaoDocument document) {
		return new BookSearchResult(
				isbn13Of(document.isbn()),
				document.title(),
				// 배열이지만 정렬·검색 요구가 없어 콤마로 합쳐 다룬다
				document.authors() == null ? "" : String.join(", ", document.authors()),
				document.publisher(),
				document.thumbnail(),
				publishedDateOf(document.datetime()));
	}

	/** 카카오의 isbn은 "ISBN10 ISBN13" 형태다. 둘 다 없을 수도, 하나만 있을 수도 있다. */
	private static String isbn13Of(String isbn) {
		if (isbn == null || isbn.isBlank()) {
			return null;
		}
		for (String candidate : isbn.trim().split("\s+")) {
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
