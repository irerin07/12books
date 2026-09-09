package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.reading.dto.GoalRequest;
import com.irene.twelvebooks.reading.dto.GoalResponse;
import com.irene.twelvebooks.reading.dto.LibraryItemResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LibraryController {

	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 50;

	private final LibraryService libraryService;

	public LibraryController(LibraryService libraryService) {
		this.libraryService = libraryService;
	}

	@GetMapping("/users/{handle}/library")
	public CursorPage<LibraryItemResponse> library(@PathVariable String handle,
			@RequestParam(required = false) ReadingStatus status,
			@RequestParam(required = false) @Min(2000) @Max(2100) Integer year,
			@RequestParam(required = false) @Min(2000) @Max(2100) Integer startedYear,
			@RequestParam(required = false) @Min(2000) @Max(2100) Integer finishedYear,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		LibraryFilter filter = new LibraryFilter(status, year, startedYear, finishedYear);
		return libraryService.library(handle, filter, cursor, clamp(size));
	}

	@PutMapping("/me/goals/{year}")
	public GoalResponse setGoal(@AuthUser Long userId,
			@PathVariable @Min(2000) @Max(2100) int year,
			@Valid @RequestBody GoalRequest request) {
		return libraryService.setGoal(userId, year, request.targetCount());
	}

	/** 크기는 컨트롤러에서 자른다. 클라이언트가 10000을 보내도 한 페이지는 50건이다. */
	private static int clamp(int size) {
		if (size < 1) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}
}
