package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 연간 독서 목표. 설정하지 않은 해는 <b>행이 없다</b> — 가입 시 미리 만들지 않고,
 * 조회 계층이 없으면 기본 12권으로 간주한다. 아무것도 안 한 사용자의 빈 행이 쌓이지 않는다.
 */
@Entity
@Table(name = "reading_goals")
public class ReadingGoal extends BaseTimeEntity {

	/** 목표를 세우지 않은 해에 적용하는 값. spec.md F8. */
	public static final int DEFAULT_TARGET_COUNT = 12;

	private static final int MIN_TARGET = 1;
	private static final int MAX_TARGET = 1000;
	private static final int MIN_YEAR = 2000;
	private static final int MAX_YEAR = 2100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false)
	private int year;

	@Column(name = "target_count", nullable = false)
	private int targetCount;

	protected ReadingGoal() {
	}

	private ReadingGoal(Long userId, int year, int targetCount) {
		require(year >= MIN_YEAR && year <= MAX_YEAR, "연도는 %d~%d입니다".formatted(MIN_YEAR, MAX_YEAR));
		this.userId = userId;
		this.year = year;
		updateTargetCount(targetCount);
	}

	public static ReadingGoal of(Long userId, int year, int targetCount) {
		return new ReadingGoal(userId, year, targetCount);
	}

	public void updateTargetCount(int targetCount) {
		require(targetCount >= MIN_TARGET && targetCount <= MAX_TARGET,
				"목표 권수는 %d~%d입니다".formatted(MIN_TARGET, MAX_TARGET));
		this.targetCount = targetCount;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public int getYear() {
		return year;
	}

	public int getTargetCount() {
		return targetCount;
	}
}
