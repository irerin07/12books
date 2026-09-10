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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 외부 API를 부르는 코드에는 <b>그 API를 실제로 재본 기록</b>이 있어야 한다는 규약을 지키는 검사.
 *
 * <p>검색에 {@code MAX_PAGE = 50}이라는 상한이 있었다. 근거는 "카카오가 1~50만 받고 넘기면
 * 4xx"라는 주석 한 줄이었고, 공식 문서에도 그렇게 적혀 있어 아무도 다시 묻지 않았다.
 * 실제로 쳐 보니 <b>51쪽도 500쪽도 200이었다.</b> 그 상한 때문에 닿을 수 있는 결과의 절반을
 * 버렸고, "다음 쪽 있다"고 말해 놓고 그 쪽을 400으로 거절했다.
 *
 * <p><b>이 검사가 할 수 없는 일을 먼저 적는다.</b> 기록된 내용이 참인지는 판정할 수 없다.
 * 거짓을 적어 넣으면 통과한다. 이 검사가 막는 것은 <b>기록이 아예 없는 것</b>과
 * <b>재현 방법이 없는 것</b>이다 — 오늘 겪은 실패가 정확히 그 둘이었다.
 */
class ExternalApiContractTest {

	private static final Path SOURCES = Path.of("src/main/java");
	private static final Path CONTRACTS = Path.of("external-apis.md");

	/** 언제 잰 것인지. 값이 낡았는지 판단하려면 잰 날짜가 있어야 한다. */
	private static final Pattern MEASURED_ON = Pattern.compile("\\*\\*실측일:\\*\\*\\s*\\d{4}-\\d{2}-\\d{2}");

	/** 어떻게 다시 잴 것인지. 반년 뒤에 확인하려면 그때 쓴 명령이 필요하다. */
	private static final Pattern HOW_TO_REPRODUCE = Pattern.compile("\\*\\*재현:\\*\\*");

	@Test
	@DisplayName("외부 API 클라이언트마다 실측 기록이 있다")
	void everyExternalClientHasAMeasuredContract() {
		String contracts = read(CONTRACTS);
		List<String> violations = new ArrayList<>();

		for (Path client : externalApiClients()) {
			String name = client.getFileName().toString().replace(".java", "");
			String section = sectionOf(contracts, name);

			if (section == null) {
				violations.add("%s — external-apis.md에 `## %s` 절이 없다".formatted(name, name));
				continue;
			}
			if (!MEASURED_ON.matcher(section).find()) {
				violations.add("%s — 실측일이 없다 (`**실측일:** 2026-09-11` 형식)".formatted(name));
			}
			if (!HOW_TO_REPRODUCE.matcher(section).find()) {
				violations.add("%s — 재현 방법이 없다 (`**재현:**` 뒤에 실제로 친 명령)".formatted(name));
			}
		}

		assertThat(violations)
				.as("""
						외부 API의 동작에 기대는 코드에는 그 동작을 실제로 재본 기록이 있어야 한다.
						"문서에 그렇게 적혀 있다"는 근거가 아니다 — 문서와 실제가 다를 수 있고, 실제로 달랐다.
						external-apis.md에 `## <클래스명>` 절을 만들고 실측일·재현 명령·결과를 남긴다.""")
				.isEmpty();
	}

	@Test
	@DisplayName("기록에 재현 명령이 실제로 들어 있다")
	void reproductionStepsAreNotEmpty() {
		String contracts = read(CONTRACTS);
		List<String> violations = new ArrayList<>();

		for (Path client : externalApiClients()) {
			String name = client.getFileName().toString().replace(".java", "");
			String section = sectionOf(contracts, name);
			// 절이 아예 없는 경우는 위 테스트가 보고한다. 여기서 중복해서 세지 않는다.
			if (section != null && !section.contains("```")) {
				violations.add("%s — 재현 명령이 코드 블록으로 적혀 있지 않다".formatted(name));
			}
		}

		assertThat(violations)
				.as("재현은 붙여넣어 그대로 실행할 수 있어야 한다. 설명 문장만으로는 반년 뒤에 다시 재지 못한다.")
				.isEmpty();
	}

	/**
	 * 규칙은 파일 이름이다 — {@code *Client.java}. 외부를 부르는 것에 다른 이름을 붙이면 이 검사를
	 * 비켜 가지만, 그때는 규약을 모르고 지나친 것이 아니라 알고 피한 것이다. 훅과 마찬가지로
	 * 작정하고 피하는 사람까지 막지는 못한다.
	 */
	private static List<Path> externalApiClients() {
		try (Stream<Path> paths = Files.walk(SOURCES)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith("Client.java"))
					.toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException("소스를 읽지 못했습니다: " + SOURCES, e);
		}
	}

	/** {@code ## 이름}부터 다음 {@code ## }까지. 절이 없으면 null이다. */
	private static String sectionOf(String document, String heading) {
		int start = document.indexOf("## " + heading);
		if (start < 0) {
			return null;
		}
		int next = document.indexOf("\n## ", start + 1);
		return next < 0 ? document.substring(start) : document.substring(start, next);
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("""
					external-apis.md를 읽지 못했습니다. 외부 API를 부르는 코드가 있으면
					그 API를 재본 기록이 이 파일에 있어야 합니다.""", e);
		}
	}
}
