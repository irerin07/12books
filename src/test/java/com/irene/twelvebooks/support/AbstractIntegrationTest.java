package com.irene.twelvebooks.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Statement;
import java.util.List;

/**
 * 통합 테스트의 공통 기반. 실제 MySQL·Redis 컨테이너를 띄우고 Flyway 마이그레이션을 그대로 태운다.
 * 컨테이너는 static 필드라 JVM 하나 안의 모든 통합 테스트가 재사용한다.
 */
@SpringBootTest(properties = {
		// 비밀값은 운영에서 환경변수로만 주입한다. 테스트는 고정 더미 값으로 바인딩만 확인한다.
		"twelvebooks.jwt.secret=test-secret-key-for-integration-tests-0123456789",
		"twelvebooks.kakao.rest-api-key=test-kakao-rest-api-key",
		"twelvebooks.book.signature-secret=test-book-signature-secret-0123456789"
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

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * 테스트마다 모든 테이블을 비운다.
	 *
	 * <p>클래스마다 자기가 쓰는 리포지토리만 지우면 테이블이 늘 때마다 깨진다 — 남의 테스트가
	 * 남긴 자식 행 때문에 {@code users} 삭제가 FK에 걸리는 식이라, 실행 순서에 따라 통과와
	 * 실패가 갈리는 불안정한 테스트가 된다.
	 *
	 * <p>목록을 information_schema에서 읽으므로 Phase가 늘어 테이블이 생겨도 여기는 그대로다.
	 * FK 검사를 잠깐 끄는 이유는 삭제 순서를 몰라도 되게 하기 위해서다. truncate라
	 * auto_increment도 함께 되돌아가 테스트가 id에 기대도 흔들리지 않는다.
	 *
	 * <p>상위 클래스의 {@code @BeforeEach}가 하위 것보다 먼저 실행되므로, 각 테스트의 준비
	 * 코드는 빈 DB에서 시작한다.
	 */
	@BeforeEach
	void cleanDatabase() {
		List<String> tables = jdbcTemplate.queryForList("""
				select table_name from information_schema.tables
				where table_schema = database() and table_name <> 'flyway_schema_history'
				""", String.class);

		for (String table : tables) {
			// 우리 스키마에서 읽은 이름이지만 SQL에 이어 붙이므로 형태를 확인하고 쓴다.
			if (!table.matches("[A-Za-z0-9_]+")) {
				throw new IllegalStateException("예상치 못한 테이블 이름: " + table);
			}
		}

		// foreign_key_checks는 커넥션 단위 설정이다. JdbcTemplate 호출마다 풀에서 커넥션을
		// 새로 받으면 "끈 커넥션"과 "truncate하는 커넥션"이 달라져 FK가 그대로 살아 있을 수
		// 있다. 하나의 커넥션 안에서 끄고-비우고-켠다.
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("set foreign_key_checks = 0");
				try {
					for (String table : tables) {
						statement.execute("truncate table `" + table + "`");
					}
				}
				finally {
					statement.execute("set foreign_key_checks = 1");
				}
			}
			return null;
		});
	}
}
