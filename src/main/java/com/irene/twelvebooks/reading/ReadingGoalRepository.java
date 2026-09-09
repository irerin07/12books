package com.irene.twelvebooks.reading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReadingGoalRepository extends JpaRepository<ReadingGoal, Long> {

	Optional<ReadingGoal> findByUserIdAndYear(Long userId, int year);

	/**
	 * 그 해의 목표를 세우거나 고친다. 한 문장이라 경합이 없다.
	 *
	 * <p>"조회해서 없으면 만든다"로 쓰면 같은 사용자·연도에 두 요청이 동시에 들어올 때 둘 다
	 * "없음"을 보고, 하나가 유니크 제약에 걸려 500이 된다. PUT은 본래 멱등이므로 DB에게
	 * 그대로 시키는 편이 단순하고 정확하다.
	 *
	 * <p>{@code created_at}·{@code updated_at}을 직접 채우는 것은 네이티브 쿼리가 JPA 감사를
	 * 거치지 않기 때문이다.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			insert into reading_goals (user_id, year, target_count, created_at, updated_at)
			values (:userId, :year, :targetCount, now(6), now(6)) as new
			on duplicate key update target_count = new.target_count, updated_at = now(6)
			""", nativeQuery = true)
	void upsert(@Param("userId") Long userId, @Param("year") int year,
			@Param("targetCount") int targetCount);
}
