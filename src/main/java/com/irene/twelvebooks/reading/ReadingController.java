package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.reading.dto.ReadingCreateRequest;
import com.irene.twelvebooks.reading.dto.ReadingResponse;
import com.irene.twelvebooks.reading.dto.ReadingUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReadingController {

	private final ReadingService readingService;

	public ReadingController(ReadingService readingService) {
		this.readingService = readingService;
	}

	@PostMapping("/readings")
	public ResponseEntity<ReadingResponse> add(@AuthUser Long userId,
			@Valid @RequestBody ReadingCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ReadingResponse.from(readingService.add(userId, request)));
	}

	@PatchMapping("/readings/{id}")
	public ReadingResponse update(@AuthUser Long userId, @PathVariable Long id,
			@Valid @RequestBody ReadingUpdateRequest request) {
		return ReadingResponse.from(readingService.update(userId, id, request));
	}

	@DeleteMapping("/readings/{id}")
	public ResponseEntity<Void> remove(@AuthUser Long userId, @PathVariable Long id) {
		readingService.remove(userId, id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 이 책에 대한 내 기록. 담기 버튼을 그리기 전에 부른다.
	 *
	 * <p>서재에 있으면 그 기록이고({@code inBookshelf: true}), 없으면 <b>가장 최근 지난
	 * 독서</b>다. 후자면 화면이 "200쪽까지 읽으셨어요. 이어서 읽을까요?"를 물어 {@code resume}을
	 * 정할 수 있다 — 묻지 않고 담으면 서버가 {@code R003}으로 되돌려보낸다.
	 *
	 * <p>{@code readings}가 아니라 책 아래 경로인 것은 질문이 "이 책에 대한"이기 때문이다.
	 * 화면은 그 시점에 기록 id를 모른다.
	 */
	@GetMapping("/books/{bookId}/reading")
	public ReadingResponse myReading(@AuthUser Long userId, @PathVariable Long bookId) {
		return ReadingResponse.from(readingService.readingOf(userId, bookId));
	}
}
