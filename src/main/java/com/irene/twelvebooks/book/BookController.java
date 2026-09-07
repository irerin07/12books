package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.book.dto.BookResponse;
import com.irene.twelvebooks.book.dto.BookSearchResult;
import jakarta.validation.Valid;
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

	private final BookService bookService;
	private final KakaoBookClient kakaoBookClient;

	public BookController(BookService bookService, KakaoBookClient kakaoBookClient) {
		this.bookService = bookService;
		this.kakaoBookClient = kakaoBookClient;
	}

	/** 카카오 프록시. 결과를 저장하지 않는다 — 담는 순간에만 내부에 확정된다. */
	@GetMapping("/search")
	public List<BookSearchResult> search(@RequestParam("q") @NotBlank String query,
			@RequestParam(name = "page", defaultValue = "1") int page) {
		return kakaoBookClient.search(query, page);
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
