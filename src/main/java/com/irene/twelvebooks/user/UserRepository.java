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
	 * <p>조건을 쿼리에 두는 이유는 부르는 곳이 많아서다 — 로그인·가입 중복 확인·비밀번호
	 * 재설정이 전부 이 메서드를 지난다. 호출부마다 확인하게 하면 한 곳을 빠뜨리는 날이 오고,
	 * 그 한 곳이 "탈퇴했는데 로그인된다"가 된다.
	 */
	@Query("select u from User u where u.email = :email and u.deletedAt is null")
	Optional<User> findByEmail(@Param("email") String email);

	@Query("select u from User u where u.handle = :handle and u.deletedAt is null")
	Optional<User> findByHandle(@Param("handle") String handle);
}
