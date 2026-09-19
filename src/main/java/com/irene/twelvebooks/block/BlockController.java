package com.irene.twelvebooks.block;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.block.dto.BlockItemResponse;
import com.irene.twelvebooks.common.ratelimit.RateLimit;
import com.irene.twelvebooks.common.support.CursorPage;
import com.irene.twelvebooks.common.support.PageSize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 차단. 팔로우와 같은 모양이다 — 생성·삭제 모두 204이고, 관계는 "있다/없다"가 전부다.
 *
 * <p>목록이 {@code /me/blocks}인 것은 <b>남의 차단 목록은 볼 수 없기 때문</b>이다. 팔로워
 * 목록이 {@code /users/{handle}/followers}인 것과 다른 이유가 여기 있다.
 */
@RestController
@RequestMapping("/api/v1")
public class BlockController {

	private final BlockService blockService;

	public BlockController(BlockService blockService) {
		this.blockService = blockService;
	}

	@RateLimit(name = "block", limit = 60, windowSeconds = 60, scope = RateLimit.Scope.USER)
	@PostMapping("/users/{handle}/block")
	public ResponseEntity<Void> block(@AuthUser Long userId, @PathVariable String handle) {
		blockService.block(userId, handle);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/users/{handle}/block")
	public ResponseEntity<Void> unblock(@AuthUser Long userId, @PathVariable String handle) {
		blockService.unblock(userId, handle);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me/blocks")
	public CursorPage<BlockItemResponse> blocked(@AuthUser Long userId,
			@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return blockService.blocked(userId, cursor, PageSize.clamp(size));
	}
}
