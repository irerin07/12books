package com.irene.twelvebooks.convention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 복합 PK를 쓰지 않는다는 규약({@code CLAUDE.md} 코드 규약, {@code plan.md} T5)을 지키는 검사.
 *
 * <p>규약을 문서에만 적어 두면 다음에 관계 테이블을 만드는 사람이 교과서적 기본값인 복합 PK로
 * 돌아간다. 실제로 {@code follows}가 그랬고, 두 가지가 조용히 깨졌다 — 커서 페이징이 단조 증가
 * 키를 잃었고, 식별자를 직접 넣는 바람에 JPA {@code save()}가 insert 대신 merge로 나가
 * <b>유니크 제약이 발동할 기회조차 없었다</b>(중복 팔로우가 409 대신 204로 성공했다).
 *
 * <p>이 검사는 {@code build}에 얹혀 있고 {@code build}는 머지 필수 체크다. 훅과 달리 우회할
 * 경로가 없다.
 *
 * <p><b>정말 필요하면 빠져나갈 수 있다.</b> 해당 줄 앞에 사유와 함께 표식을 남긴다:
 *
 * <pre>
 * -- allow-composite-pk: 왜 여기서는 복합 PK여야 하는지
 * primary key (a_id, b_id)
 * </pre>
 *
 * 사유 없는 표식은 검사가 다시 잡는다 — 침묵시키는 용도로 쓰이면 규약이 무의미해진다.
 */
class PrimaryKeyConventionTest {

	private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
	private static final Path SOURCES = Path.of("src/main/java");

	/** 표식과 사유. 사유가 10자 미만이면 표식으로 치지 않는다. */
	private static final Pattern ESCAPE_HATCH = Pattern.compile("allow-composite-pk\\s*:\\s*(\\S.{9,})");

	private static final Pattern PRIMARY_KEY = Pattern.compile("primary\\s+key\\s*\\(([^)]*)\\)",
			Pattern.CASE_INSENSITIVE);

	/** {@code @IdClass}는 {@code @Id} 뒤가 단어 문자라 경계가 없어 여기 걸리지 않는다. */
	private static final Pattern ID_ANNOTATION = Pattern.compile("@Id\\b");

	@Test
	@DisplayName("마이그레이션에 복합 PK가 없다")
	void migrationsDeclareSingleColumnPrimaryKeys() {
		List<String> violations = new ArrayList<>();

		for (Path file : filesUnder(MIGRATIONS, ".sql")) {
			String sql = read(file);
			Matcher matcher = PRIMARY_KEY.matcher(sql);
			while (matcher.find()) {
				if (!matcher.group(1).contains(",")) {
					continue;
				}
				if (excused(sql, matcher.start())) {
					continue;
				}
				violations.add("%s — primary key (%s)".formatted(file.getFileName(), matcher.group(1).trim()));
			}
		}

		assertThat(violations)
				.as("""
						복합 PK를 쓰지 않는다 (CLAUDE.md 코드 규약).
						대리 키 `id bigint auto_increment`를 두고 유일성은 unique 제약이 맡는다.
						정말 필요하면 바로 윗줄에 사유를 남긴다: -- allow-composite-pk: <이유>""")
				.isEmpty();
	}

	@Test
	@DisplayName("엔티티에 복합 식별자가 없다")
	void entitiesDeclareSingleIdentifiers() {
		List<String> violations = new ArrayList<>();

		for (Path file : filesUnder(SOURCES, ".java")) {
			String source = read(file);
			if (!source.contains("@Entity")) {
				continue;
			}
			if (source.contains("@IdClass") && !excused(source, source.indexOf("@IdClass"))) {
				violations.add(file.getFileName() + " — @IdClass");
			}
			if (source.contains("@EmbeddedId") && !excused(source, source.indexOf("@EmbeddedId"))) {
				violations.add(file.getFileName() + " — @EmbeddedId");
			}

			long ids = ID_ANNOTATION.matcher(source).results().count();
			if (ids > 1 && !excused(source, source.length())) {
				violations.add("%s — @Id %d개".formatted(file.getFileName(), ids));
			}
		}

		assertThat(violations)
				.as("""
						복합 PK를 쓰지 않는다 (CLAUDE.md 코드 규약).
						식별자를 직접 넣으면 save()가 insert가 아니라 merge로 나가
						유니크 제약이 발동하지 못한다 — 중복 요청이 409 대신 조용히 성공한다.
						정말 필요하면 사유를 남긴다: // allow-composite-pk: <이유>""")
				.isEmpty();
	}

	@Test
	@DisplayName("사유 없는 표식은 면제로 쳐주지 않는다")
	void escapeHatchDemandsAReason() {
		assertThat(ESCAPE_HATCH.matcher("-- allow-composite-pk:").find()).isFalse();
		assertThat(ESCAPE_HATCH.matcher("-- allow-composite-pk: 필요해서").find()).isFalse();
		assertThat(ESCAPE_HATCH.matcher("-- allow-composite-pk: 외부 시스템이 이 키로 조회한다").find()).isTrue();
	}

	/** 표식은 걸린 지점 <b>앞쪽</b>에만 유효하다. 파일 어딘가에 있다고 전부 면제되지 않는다. */
	private static boolean excused(String content, int position) {
		return ESCAPE_HATCH.matcher(content.substring(0, position)).find();
	}

	private static List<Path> filesUnder(Path root, String suffix) {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(suffix))
					.toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException("검사할 파일을 읽지 못했습니다: " + root, e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("파일을 읽지 못했습니다: " + file, e);
		}
	}
}
