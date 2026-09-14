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
			from User u where u.id in :ids
			""")
	List<ActorView> findActorViews(@Param("ids") List<Long> ids);

	Optional<User> findByEmail(String email);

	Optional<User> findByHandle(String handle);
}
