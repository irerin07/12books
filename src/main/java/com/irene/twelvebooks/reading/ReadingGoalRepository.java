package com.irene.twelvebooks.reading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingGoalRepository extends JpaRepository<ReadingGoal, Long> {

	Optional<ReadingGoal> findByUserIdAndYear(Long userId, int year);
}
