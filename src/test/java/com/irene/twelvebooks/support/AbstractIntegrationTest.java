package com.irene.twelvebooks.support;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트의 공통 기반. 실제 MySQL·Redis 컨테이너를 띄우고 Flyway 마이그레이션을 그대로 태운다.
 * 컨테이너는 static 필드라 JVM 하나 안의 모든 통합 테스트가 재사용한다.
 */
@SpringBootTest(properties = {
		// 비밀값은 운영에서 환경변수로만 주입한다. 테스트는 고정 더미 값으로 바인딩만 확인한다.
		"twelvebooks.jwt.secret=test-secret-key-for-integration-tests-0123456789",
		"twelvebooks.kakao.rest-api-key=test-kakao-rest-api-key"
})
@AutoConfigureMockMvc
@SuppressWarnings("resource")
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
			.withDatabaseName("twelvebooks")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	static {
		MYSQL.start();
		REDIS.start();
	}
}
