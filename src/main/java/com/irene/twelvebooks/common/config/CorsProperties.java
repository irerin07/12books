package com.irene.twelvebooks.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 브라우저에서 이 API를 부를 수 있는 오리진.
 *
 * <p>목록을 코드에 박지 않는 이유는 프런트 주소가 배포 환경마다 다르고, 미리보기 배포는
 * 주소가 계속 바뀌기 때문이다. 박아 두면 프런트가 새 주소를 쓸 때마다 서버를 다시 배포해야
 * 한다.
 *
 * <p><b>비어 있으면 CORS를 아예 열지 않는다.</b> "설정을 깜빡했다"가 "아무나 부를 수 있다"로
 * 이어지면 안 된다 — 개발 기본값을 넣어 두면 그 값이 그대로 배포로 따라간다.
 *
 * @param allowedOrigins 정확한 오리진 목록. 와일드카드를 쓰지 않는다 — 자격증명을 동반하는
 *                       요청에는 브라우저가 {@code *}를 거부하고, 그 조합은 refresh 쿠키를
 *                       쓰는 우리 흐름에서 조용히 깨진다.
 */
@ConfigurationProperties(prefix = "twelvebooks.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}
}
