package com.irene.twelvebooks.book;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param signatureSecret 검색 결과 서명에 쓰는 HMAC 키. 이 값을 아는 쪽만 등록 가능한
 *                        메타데이터를 만들 수 있다. JWT 키와 분리해 용도별로 나눈다.
 */
@ConfigurationProperties(prefix = "twelvebooks.book")
public record BookProperties(String signatureSecret) {
}
