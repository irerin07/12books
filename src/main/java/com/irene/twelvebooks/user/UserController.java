package com.irene.twelvebooks.user;

import com.irene.twelvebooks.auth.AuthUser;
import com.irene.twelvebooks.user.dto.ProfileResponse;
import com.irene.twelvebooks.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/users/{handle}")
	public ProfileResponse profile(@PathVariable String handle) {
		return ProfileResponse.from(userService.getByHandle(handle));
	}

	@PatchMapping("/me")
	public ProfileResponse updateMe(@AuthUser Long userId, @Valid @RequestBody UpdateProfileRequest request) {
		return ProfileResponse.from(userService.updateProfile(userId, request));
	}
}
