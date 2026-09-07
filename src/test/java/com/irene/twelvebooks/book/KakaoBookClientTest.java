package com.irene.twelvebooks.book;

import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오는 스텁한다. 테스트가 네트워크나 API 키에 의존하면 CI에서 깨진다.
 */
class KakaoBookClientTest {

	private static final String BASE_URL = "https://dapi.kakao.com";
	private static final String API_KEY = "test-kakao-key";

	private static final String RESPONSE = """
			{
			  "documents": [
			    {
			      "title": "코드 컴플리트",
			      "authors": ["스티브 맥코넬"],
			      "publisher": "위키북스",
			      "isbn": "8960777331 9788960777330",
			      "thumbnail": "https://example.com/cover.jpg",
			      "datetime": "2017-05-10T00:00:00.000+09:00"
			    }
			  ],
			  "meta": { "total_count": 1, "pageable_count": 1, "is_end": true }
			}
			""";

	private KakaoBookClient client;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new KakaoBookClient(builder.build(), new KakaoProperties(API_KEY, BASE_URL));
	}

	@Test
	@DisplayName("검색 결과를 내부 표현으로 바꿔 돌려준다")
	void searchesBooks() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/v3/search/book")))
				// 한글은 URI에서 퍼센트 인코딩되므로 디코드해서 본다
				.andExpect(request -> assertThat(
						URLDecoder.decode(request.getURI().getQuery(), StandardCharsets.UTF_8))
						.contains("query=코드").contains("page=1"))
				.andExpect(header("Authorization", "KakaoAK " + API_KEY))
				.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

		var results = client.search("코드", 1);

		assertThat(results).hasSize(1).first().satisfies(book -> {
			assertThat(book.title()).isEqualTo("코드 컴플리트");
			// 카카오의 authors는 배열이지만 정렬·검색 요구가 없어 콤마로 합쳐 다룬다
			assertThat(book.authors()).isEqualTo("스티브 맥코넬");
			assertThat(book.publisher()).isEqualTo("위키북스");
			assertThat(book.thumbnailUrl()).isEqualTo("https://example.com/cover.jpg");
			// isbn 필드는 "ISBN10 ISBN13" 형태다. 13자리만 쓴다.
			assertThat(book.isbn13()).isEqualTo("9788960777330");
			assertThat(book.publishedAt()).isEqualTo(java.time.LocalDate.of(2017, 5, 10));
		});

		server.verify();
	}

	@Test
	@DisplayName("저자가 여럿이면 콤마로 합친다")
	void joinsMultipleAuthors() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[{"title":"공저","authors":["김","이"],"publisher":"출판사",
						"isbn":"","thumbnail":"","datetime":"2020-01-02T00:00:00.000+09:00"}],
						"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.search("공저", 1).getFirst().authors()).isEqualTo("김, 이");
	}

	@Test
	@DisplayName("ISBN13이 없으면 비어 있다 — sourceKey로 대체된다")
	void handlesMissingIsbn13() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[{"title":"ISBN 없음","authors":["저자"],"publisher":"출판사",
						"isbn":"8960777331","thumbnail":"","datetime":"2020-01-02T00:00:00.000+09:00"}],
						"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.search("없음", 1).getFirst().isbn13()).isNull();
	}

	@Test
	@DisplayName("카카오가 5xx를 주면 502로 바꾼다 — 원인을 그대로 흘리지 않는다")
	void translatesServerErrorTo502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.search("코드", 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
	}

	@Test
	@DisplayName("키가 거부당해도(401) 502다 — 클라이언트 잘못이 아니다")
	void translatesUnauthorizedTo502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
						.withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> client.search("코드", 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
	}
}
