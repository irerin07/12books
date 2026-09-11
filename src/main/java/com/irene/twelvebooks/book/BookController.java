package com.irene.twelvebooks.book;

import com.irene.twelvebooks.book.dto.BookRegisterRequest;
import com.irene.twelvebooks.book.dto.BookResponse;
import com.irene.twelvebooks.book.dto.BookSearchPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
public class BookController {

	/**
	 * 검색어 길이 상한. 사람이 치는 검색어는 이보다 훨씬 짧다. 상한이 없으면 긴 문자열이
	 * 그대로 외부 URI와 오류 로그로 흘러 들어간다.
	 */
	private static final int MAX_QUERY_LENGTH = 100;

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
	 * <p><b>쪽번호에 상한을 두지 않는다.</b> 멈추는 판단은 응답의 {@code hasNext}가 한다.
	 * 상한을 두면 우리가 "다음 쪽 있다"고 말해 놓고 그 다음 쪽 요청을 400으로 거절하는 모순이
	 * 생긴다 — 실제로 50으로 잘라 두는 동안 그랬다.
	 *
	 * <p>원래 상한의 근거는 "카카오가 1~50만 받고 넘기면 4xx"였는데 <b>사실이 아니다.</b>
	 * 문서에는 1~50으로 적혀 있지만, 실측하면 51·100·500쪽 모두 200에 문서를 주고
	 * {@code is_end}만 도달 한계(pageable_count ÷ size)에서 참으로 바뀐다. 상한이 있는 동안
	 * 우리는 닿을 수 있는 결과의 절반을 스스로 버리고 있었다.
	 *
	 * <p>{@code @Min(1)}은 남긴다. 0쪽이나 음수쪽은 뜻이 없는 입력이고, 카카오가 그것을 어떻게
	 * 다루는지에 기대고 싶지 않다.
	 *
	 * <p>{@code target}으로 제목·저자를 나눠 찾을 수 있다. 안 보내면 좁히지 않는다.
	 */
	@GetMapping("/search")
	public BookSearchPage search(@RequestParam("q") @NotBlank @Size(max = MAX_QUERY_LENGTH) String query,
			@RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
			@RequestParam(name = "target", defaultValue = "ALL") BookSearchTarget target) {
		// 앞뒤 공백은 검색 의도가 아니다. 그대로 넘기면 외부로 나가는 URI만 길어진다.
		BookSearchPage found = kakaoBookClient.search(query.strip(), page, target);
		// 서명은 여기서 붙인다. 카카오 응답을 옮기는 일과 출처를 보증하는 일은 다르다.
		return found.withItems(found.items().stream().map(bookSignature::signed).toList());
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
