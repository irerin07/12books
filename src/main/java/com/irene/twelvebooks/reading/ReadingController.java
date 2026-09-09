package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.reading.dto.ReadingCreateRequest;
import com.irene.twelvebooks.reading.dto.ReadingResponse;
import com.irene.twelvebooks.reading.dto.ReadingUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/readings")
public class ReadingController {

	private final ReadingService readingService;

	public ReadingController(ReadingService readingService) {
		this.readingService = readingService;
	}

	@PostMapping
	public ResponseEntity<ReadingResponse> add(@AuthUser Long userId,
			@Valid @RequestBody ReadingCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ReadingResponse.from(readingService.add(userId, request)));
	}

	@PatchMapping("/{id}")
	public ReadingResponse update(@AuthUser Long userId, @PathVariable Long id,
			@Valid @RequestBody ReadingUpdateRequest request) {
		return ReadingResponse.from(readingService.update(userId, id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> remove(@AuthUser Long userId, @PathVariable Long id) {
		readingService.remove(userId, id);
		return ResponseEntity.noContent().build();
	}
}
