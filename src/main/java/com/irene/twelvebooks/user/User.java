package com.irene.twelvebooks.user;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code @DynamicUpdate}인 이유가 있다.
 *
 * <p>기본 동작은 <b>엔티티 전체</b>를 UPDATE하는 것이라, 프로필 수정이 사용자를 읽은 뒤
 * 비밀번호가 재설정되면 뒤늦게 나가는 UPDATE가 <b>옛 해시를 다시 저장한다.</b> 재설정이
 * 조용히 무효가 되고, 재설정하는 이유가 보통 탈취라는 것을 생각하면 가장 곤란한 자리다.
 *
 * <p>바뀐 컬럼만 실으면 프로필 수정은 이름·소개·사진만, 비밀번호 변경은 해시만 건드린다.
 * 같은 문제를 {@code role}에서는 {@code updatable = false}로 막았지만 비밀번호는 응용이
 * 바꿔야 하므로 그 방법을 쓸 수 없다.
 *
 * <p>대신 UPDATE 문이 매번 달라져 캐시되지 않는다. 사용자 행 갱신은 드물어 감수할 만하다.
 */
@Entity
@Table(name = "users")
@DynamicUpdate
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

	/**
	 * 권한. 토큰이 아니라 <b>이 행</b>이 진실이다.
	 *
	 * <p>역할을 토큰에 실으면 권한을 뺏어도 그 사람의 토큰이 만료될 때까지 관리자로 남는다.
	 * 뺏는 이유를 생각하면 그 시차가 가장 곤란한 순간에 열려 있는 셈이다.
	 *
	 * <p>{@code updatable = false}인 이유가 따로 있다. 프로필 수정은 dirty checking으로
	 * <b>전체 UPDATE</b>를 날리는데, 거기에 이 값이 실리면 수정 요청이 엔티티를 읽은 뒤
	 * 운영자가 권한을 회수했을 때 <b>회수가 취소된다</b> — 옛 값이 그대로 다시 저장된다.
	 * 역할을 바꾸는 경로는 SQL뿐이므로(CLAUDE.md) JPA는 이 컬럼을 읽기만 한다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private UserRole role;

	protected User() {
	}

	private User(String email, String passwordHash, String handle, String displayName) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.handle = handle;
		this.displayName = displayName;
		this.role = UserRole.USER;
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

	/**
	 * 비밀번호를 갈아 끼운다. 해시는 바깥에서 만든다 — 엔티티가 인코더를 알면 도메인이
	 * 보안 구현에 묶인다.
	 */
	public void changePassword(String newPasswordHash) {
		if (newPasswordHash == null || newPasswordHash.isBlank()) {
			throw new IllegalArgumentException("비밀번호 해시는 비어 있을 수 없습니다");
		}
		this.passwordHash = newPasswordHash;
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

	public UserRole getRole() {
		return role;
	}
}
