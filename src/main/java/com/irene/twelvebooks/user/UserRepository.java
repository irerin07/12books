package com.irene.twelvebooks.user;

import com.irene.twelvebooks.notification.ActorView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * 목록에 곁들일 사람들의 <b>표시용 값만</b> 읽는다.
	 *
	 * <p>엔티티를 읽으면 {@code passwordHash}까지 따라온다. 화면에 이름과 사진만 필요한데
	 * 목록을 열 때마다 스무 명의 해시를 메모리로 올릴 이유가 없다.
	 */
	@Query("""
			select new com.irene.twelvebooks.notification.ActorView(
				u.id, u.handle, u.displayName, u.avatarUrl)
			from User u where u.id in :ids and u.deletedAt is null
			""")
	List<ActorView> findActorViews(@Param("ids") List<Long> ids);

	/**
	 * 살아 있는 계정만 찾는다. 탈퇴한 행은 남아 있지만 <b>없는 것과 같이</b> 답한다.
	 *
	 * <p>생성 컬럼으로 찾는다. {@code email = ? and deleted_at is null}로 쓰면 그 조건을
	 * 받쳐 줄 인덱스가 없어 보관 중인 탈퇴 계정이 쌓일수록 느려진다 — 유니크 제약이 걸린
	 * {@code active_email}을 그대로 타면 인덱스 하나로 끝난다.
	 *
	 * <p>조건을 쿼리에 두는 이유는 부르는 곳이 많아서다 — 로그인·가입 중복 확인·비밀번호
	 * 재설정이 전부 이 메서드를 지난다. 호출부마다 확인하게 하면 한 곳을 빠뜨리는 날이 오고,
	 * 그 한 곳이 "탈퇴했는데 로그인된다"가 된다.
	 */
	@Query("select u from User u where u.activeEmail = :email")
	Optional<User> findByEmail(@Param("email") String email);

	@Query("select u from User u where u.activeHandle = :handle")
	Optional<User> findByHandle(@Param("handle") String handle);
}
