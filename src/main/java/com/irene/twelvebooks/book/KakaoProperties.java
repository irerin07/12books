package com.irene.twelvebooks.book;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "twelvebooks.kakao")
public record KakaoProperties(String restApiKey, String baseUrl) {
}
