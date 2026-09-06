package com.irene.twelvebooks.user;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false, unique = true, length = 20)
	private String handle;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(length = 200)
	private String bio;

	@Column(name = "avatar_url", length = 500)
	private String avatarUrl;

	protected User() {
	}

	private User(String email, String passwordHash, String handle, String displayName) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.handle = handle;
		this.displayName = displayName;
	}

	public static User create(String email, String passwordHash, String handle, String displayName) {
		return new User(email, passwordHash, handle, displayName);
	}

	/**
	 * 프로필 수정. null인 필드는 "보내지 않았다"는 뜻이므로 건드리지 않는다.
	 * handle과 email은 여기서 바꿀 수 없다 — 식별자와 자격증명은 프로필 수정의 대상이 아니다.
	 */
	public void updateProfile(String displayName, String bio, String avatarUrl) {
		if (displayName != null) {
			this.displayName = displayName;
		}
		if (bio != null) {
			this.bio = bio;
		}
		if (avatarUrl != null) {
			this.avatarUrl = avatarUrl;
		}
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getHandle() {
		return handle;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getBio() {
		return bio;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}
}
