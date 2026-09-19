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
 * 사람이 나오는 목록 쿼리는 차단을 걸러야 한다는 규약을 지키는 검사.
 *
 * <p>차단은 화면 기능이 아니라 도메인 정책이라 목록을 만드는 쿼리 전부에 조건이 붙는다
 * (plan.md L2-2). <b>하나라도 빠뜨리면 "차단했는데 팔로워 목록에선 보인다"가 된다.</b>
 * 소프트 삭제 때 {@code deleted_at is null}을 전 쿼리에 손으로 달면서 같은 위험을 겪었고
 * 그때는 하네스를 만들지 못했다 — 계획서가 "여기서는 만든다"고 적어 둔 자리다.
 *
 * <p><b>사람 눈으로 막을 수 없는 종류의 실수다.</b> 조건을 빠뜨린 쿼리는 잘 도는 것처럼
 * 보이고, 차단한 사람이 그 목록을 열어 보기 전까지 아무도 모른다.
 *
 * <h2>이 검사가 못 하는 것</h2>
 *
 * 조건이 <b>있는지</b>만 본다. 그 조건이 올바른 방향을 보는지, 올바른 컬럼을 비교하는지는
 * 판정하지 못한다 — {@code ExternalApiContractTest}가 기록의 존재만 보고 내용의 참을
 * 리뷰에 넘기는 것과 같은 자리다. 실제 동작은 {@code BlockVisibilityTest}가 지킨다.
 *
 * <h2>면제</h2>
 *
 * 정말 걸러서는 안 되는 목록이 있다 — 차단 목록 자체, 사람이 아니라 책이 나오는 목록,
 * 운영자용 목록. 그 자리에는 <b>사유와 함께</b> 표식을 남긴다.
 *
 * <pre>
 * // allow-no-block-filter: 차단 목록 자체다. 거르면 항상 빈 목록이 된다
 * </pre>
 *
 * 사유 없는 표식은 검사가 다시 잡는다 — 침묵시키는 용도로 쓰이면 규약이 무의미해진다.
 * 이 구멍은 처음부터 뚫어 둔 것이 아니라 <b>실제 예외 셋이 빌드를 빨갛게 만든 뒤에</b>
 * 만들었다. 가정만으로 미리 뚫으면 그게 곧 빠져나갈 길이 된다.
 */
class BlockConventionTest {

	private static final Path SOURCES = Path.of("src/main/java");

	/**
	 * 사람이 나오는 목록 쿼리. 이름으로 고른다 — 모든 {@code @Query}를 대상으로 삼으면
	 * 권한 검사나 단건 잠금 조회까지 걸려 면제 목록만 길어진다.
	 */
	private static final Pattern LIST_QUERY = Pattern.compile(
			"(find\\w*Page|countFollowers|countFollowings|countUnread)\\s*\\(");

	/** 조건이 붙었다는 표식. 상수로 빼서 쓰는 경우도 통과시킨다. */
	private static final Pattern BLOCK_FILTER = Pattern.compile("from Block bl|BLOCKED|NOT_BLOCKED");

	/**
	 * 면제 표식과 사유. 사유가 10자 미만이면 표식으로 치지 않는다 —
	 * {@code PrimaryKeyConventionTest}와 같은 방식이다.
	 *
	 * <p>표식 뒤 공백을 {@code \s*}가 아니라 {@code [ \\t]*}로 잡는 이유는 {@code \s}가
	 * 줄바꿈까지 먹기 때문이다. 그러면 사유 없는 표식 <b>다음 줄의 코드</b>가 사유로 읽힌다.
	 */
	private static final Pattern ESCAPE_HATCH =
			Pattern.compile("allow-no-block-filter[ \\t]*:[ \\t]*(\\S[^\\r\\n]{9,})");

	@Test
	@DisplayName("사람이 나오는 목록 쿼리는 전부 차단을 거른다")
	void listQueriesFilterBlocks() {
		List<String> violations = new ArrayList<>();

		for (Path file : repositoriesUnder(SOURCES)) {
			String source = read(file);
			if (!LIST_QUERY.matcher(source).find()) {
				continue;
			}
			if (ESCAPE_HATCH.matcher(source).find()) {
				continue;
			}
			if (!BLOCK_FILTER.matcher(source).find()) {
				violations.add(file.getFileName() + " — " + namesIn(source));
			}
		}

		assertThat(violations)
				.as("""
						사람이 나오는 목록 쿼리는 차단을 걸러야 한다 (plan.md L2-2).

						  and not exists (select 1 from Block bl
						    where (bl.blockerId = :viewerId and bl.blockedId = <상대>)
						       or (bl.blockerId = <상대> and bl.blockedId = :viewerId))

						행은 한 방향(누가 눌렀나)이지만 보이지 않는 것은 양방향이라 둘 다 본다.
						여러 쿼리가 함께 쓰는 자리가 있으면 상수로 빼서 한 번만 적는다 —
						목록마다 손으로 달면 언젠가 하나를 빠뜨린다.

						정말 걸러서는 안 되는 목록이면 사유를 남긴다:
						  // allow-no-block-filter: <왜 이 목록은 차단을 걸러서는 안 되는지>""")
				.isEmpty();
	}

	@Test
	@DisplayName("사유 없는 면제 표식은 면제로 쳐주지 않는다")
	void escapeHatchDemandsAReason() {
		assertThat(ESCAPE_HATCH.matcher("// allow-no-block-filter:").find()).isFalse();
		assertThat(ESCAPE_HATCH.matcher("// allow-no-block-filter: 필요해서").find()).isFalse();
		assertThat(ESCAPE_HATCH.matcher("// allow-no-block-filter: 차단 목록 자체라 거르면 빈다").find()).isTrue();
	}

	@Test
	@DisplayName("사유는 표식과 같은 줄에서만 읽는다 — 다음 줄 코드를 사유로 착각하지 않는다")
	void escapeHatchDoesNotReadTheNextLineAsAReason() {
		assertThat(ESCAPE_HATCH.matcher("""
				// allow-no-block-filter:
				List<BlockItemResponse> findBlockedPage(Long id);""").find()).isFalse();
	}

	/**
	 * handle로 사람을 집는 엔드포인트는 차단을 확인해야 한다.
	 *
	 * <p>목록 안의 사람만 거르고 프로필을 열어 두면 반쪽이 된다 — 글은 안 보이는데 팔로워 수는
	 * 보이고, 링크를 타면 그 사람의 목록이 그대로 열린다.
	 *
	 * <p>{@code BlockController}만 예외다. 차단을 풀려면 상대를 집을 수 있어야 한다.
	 */
	@Test
	@DisplayName("handle로 사람을 집는 엔드포인트는 차단을 확인한다")
	void handleScopedEndpointsCheckBlocks() {
		List<String> violations = new ArrayList<>();

		for (Path file : filesUnder(SOURCES, "Controller.java")) {
			String source = read(file);
			if (file.getFileName().toString().equals("BlockController.java")) {
				continue;
			}
			if (!source.contains("/users/{handle}")) {
				continue;
			}
			if (!source.contains("blockGuard.requireVisible")) {
				violations.add(file.getFileName().toString());
			}
		}

		assertThat(violations)
				.as("""
						handle로 사람을 집는 경로는 blockGuard.requireVisible을 지나야 한다.
						차단된 사이에서는 상대가 없는 사람이고, 404로 답한다 —
						403이면 "차단당했다"가 드러난다.""")
				.isEmpty();
	}

	/** 표식이 없는 파일에서 무엇이 걸렸는지 보여 준다. 실패 메시지가 곧 할 일 목록이 된다. */
	private static String namesIn(String source) {
		Matcher matcher = LIST_QUERY.matcher(source);
		List<String> names = new ArrayList<>();
		while (matcher.find()) {
			if (!names.contains(matcher.group(1))) {
				names.add(matcher.group(1));
			}
		}
		return String.join(", ", names);
	}

	private static List<Path> repositoriesUnder(Path root) {
		return filesUnder(root, "Repository.java");
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
