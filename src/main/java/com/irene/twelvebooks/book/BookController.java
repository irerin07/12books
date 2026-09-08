package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.book.dto.BookResponse;
import com.irene.twelvebooks.book.dto.BookSearchResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

	/** 카카오 검색이 받는 page의 상한. 넘겨봐야 카카오가 4xx를 준다. */
	private static final int MAX_PAGE = 50;

	private final BookService bookService;
	private final KakaoBookClient kakaoBookClient;
	private final BookSignature bookSignature;

	public BookController(BookService bookService, KakaoBookClient kakaoBookClient,
			BookSignature bookSignature) {
		this.bookService = bookService;
		this.kakaoBookClient = kakaoBookClient;
		this.bookSignature = bookSignature;
	}

	/**
	 * 카카오 프록시. 결과를 저장하지 않는다 — 담는 순간에만 내부에 확정된다.
	 *
	 * <p>page를 여기서 막지 않으면 잘못 보낸 값이 카카오까지 가서 4xx가 되고, 그것이 다시
	 * E001/502로 번역되어 <b>클라이언트 실수가 외부 장애로 둔갑</b>한다.
	 */
	@GetMapping("/search")
	public List<BookSearchResult> search(@RequestParam("q") @NotBlank String query,
			@RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(MAX_PAGE) int page) {
		return kakaoBookClient.search(query, page).stream().map(bookSignature::signed).toList();
	}

	@PostMapping
	public ResponseEntity<BookResponse> register(@Valid @RequestBody BookRegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(BookResponse.from(bookService.upsert(request)));
	}

	@GetMapping("/{id}")
	public BookResponse read(@PathVariable Long id) {
		return BookResponse.from(bookService.getById(id));
	}
}
