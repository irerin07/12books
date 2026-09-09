package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.reading.dto.GoalResponse;
import com.irene.twelvebooks.reading.dto.LibraryItemResponse;
import org.springframework.stereotype.Service;

@Service
public class LibraryService {

	public CursorPage<LibraryItemResponse> library(String handle, LibraryFilter filter, Long cursor, int size) {
		throw new UnsupportedOperationException("아직 구현되지 않았습니다");
	}

	public GoalResponse setGoal(Long userId, int year, int targetCount) {
		throw new UnsupportedOperationException("아직 구현되지 않았습니다");
	}
}
