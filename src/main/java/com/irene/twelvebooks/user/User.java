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
	 *
	 * <p>동시 수정은 <strong>last-write-wins</strong>다. dirty checking이 전체 UPDATE를 날리므로
	 * 두 기기가 각각 다른 필드를 동시에 고치면 나중 쓰기가 먼저 것을 되돌린다. 같은 사용자의
	 * 드문 상황이고 프로필은 갱신 유실을 감수할 수 있다고 보아 방어하지 않는다.
	 * 자세한 판단과 도입 순서는 plan.md "의도적으로 하지 않는 것"에 있다.
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
