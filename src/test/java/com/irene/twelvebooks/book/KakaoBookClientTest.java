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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
	private MeterRegistry registry;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		registry = new SimpleMeterRegistry();
		client = new KakaoBookClient(builder.build(), new KakaoProperties(API_KEY, BASE_URL, 8), registry);
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
	@DisplayName("documents가 없는 응답은 빈 결과가 아니라 502다 — 계약이 깨진 것이다")
	void translatesMalformedBodyTo502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.search("코드", 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
	}

	@Test
	@DisplayName("datetime을 읽지 못해도 500이 아니라 502다")
	void translatesUnparsableDatetimeTo502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[{"title":"제목","authors":["저자"],"publisher":"출판사",
						"isbn":"","thumbnail":"","datetime":"어제"}],"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.search("코드", 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
	}

	@Test
	@DisplayName("저자가 없는 결과도 등록 가능한 형태로 나온다 — 빈 문자열은 등록에서 거부된다")
	void fillsMissingAuthors() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[{"title":"저자 없음","authors":[],"publisher":"출판사",
						"isbn":"","thumbnail":"","datetime":"2020-01-02T00:00:00.000+09:00"}],
						"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.search("없음", 1).getFirst().authors()).isEqualTo("작자 미상");
	}

	@Test
	@DisplayName("documents에 null이 섞여 있으면 NPE로 죽지 않고 502다")
	void translatesNullDocumentTo502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[null],"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.search("코드", 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
	}

	@Test
	@DisplayName("제목 없는 문서는 502다 — title은 not null 컬럼이라 등록에서 터진다")
	void translatesTitlelessDocumentTo502() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[{"title":null,"authors":["저자"],"publisher":"출판사",
						"isbn":"","thumbnail":"","datetime":""}],"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.search("코드", 1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
	}

	@Test
	@DisplayName("저자 배열의 null·빈 원소는 버린다 — \"null\"이 저자로 저장되지 않는다")
	void dropsBlankAuthorElements() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[{"title":"제목","authors":["김",null,"  "],"publisher":"출판사",
						"isbn":"","thumbnail":"","datetime":""}],"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.search("코드", 1).getFirst().authors()).isEqualTo("김");
	}

	@Test
	@DisplayName("저자가 전부 비어 있으면 작자 미상이다")
	void fallsBackWhenEveryAuthorIsBlank() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
				.andRespond(withSuccess("""
						{"documents":[{"title":"제목","authors":[null,""],"publisher":"출판사",
						"isbn":"","thumbnail":"","datetime":""}],"meta":{"is_end":true}}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.search("코드", 1).getFirst().authors()).isEqualTo("작자 미상");
	}

	@Test
	@DisplayName("동시 호출 상한을 넘으면 기다리지 않고 즉시 502다 — 느린 검색이 톰캣 스레드를 다 먹지 않는다")
	void failsFastWhenConcurrencyLimitExceeded() throws Exception {
		int limit = 2;
		KakaoBookClient limited = new KakaoBookClient(RestClient.builder()
				.requestFactory(new BlockingRequestFactory()).build(),
				new KakaoProperties(API_KEY, BASE_URL, limit), registry);

		CountDownLatch inFlight = new CountDownLatch(limit);
		ExecutorService pool = Executors.newFixedThreadPool(limit);
		try {
			for (int i = 0; i < limit; i++) {
				pool.submit(() -> {
					inFlight.countDown();
					return limited.search("코드", 1);
				});
			}
			assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();
			Thread.sleep(200); // 두 요청이 상한을 채울 때까지

			// 핵심은 502가 아니라 "기다리지 않는다"는 것이다. 상한이 없으면 이 호출도
			// 앞의 둘처럼 묶여 있다가 타임아웃으로 502가 되므로, 걸린 시간을 함께 본다.
			long startedAt = System.nanoTime();
			assertThatThrownBy(() -> limited.search("코드", 1))
					.isInstanceOf(BusinessException.class)
					.extracting(e -> ((BusinessException) e).getErrorCode())
					.isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
			assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));

			// 거부는 로그가 아니라 카운터로 센다. 상한 초과마다 WARN을 남기면 장애 중
			// 로그가 폭주해 그 자체가 병목이 된다.
			assertThat(registry.counter("kakao.search.rejected").count()).isEqualTo(1.0);
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** 응답을 주지 않고 붙잡고 있는 카카오. 스레드가 묶이는 상황을 만든다. */
	private static class BlockingRequestFactory implements ClientHttpRequestFactory {

		@Override
		public ClientHttpRequest createRequest(java.net.URI uri, HttpMethod httpMethod) {
			return new AbstractClientHttpRequest() {

				@Override
				public HttpMethod getMethod() {
					return httpMethod;
				}

				@Override
				public java.net.URI getURI() {
					return uri;
				}

				@Override
				protected OutputStream getBodyInternal(HttpHeaders headers) {
					return OutputStream.nullOutputStream();
				}

				@Override
				protected ClientHttpResponse executeInternal(HttpHeaders headers) {
					try {
						Thread.sleep(Duration.ofSeconds(30));
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					throw new ResourceAccessException("중단됨");
				}
			};
		}
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
