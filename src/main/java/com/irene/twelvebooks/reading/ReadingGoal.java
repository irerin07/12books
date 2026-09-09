package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 연간 독서 목표. 설정하지 않은 해는 <b>행이 없다</b> — 가입 시 미리 만들지 않는다.
 *
 * <p>쓰기는 {@link ReadingGoalRepository#upsert}가 원자적으로 처리한다. "조회해서 없으면 만든다"는
 * 두 요청이 함께 "없음"을 보고 하나가 유니크 제약에 걸리는 경로가 있어서다. 그래서 이 클래스는
 * 사실상 읽기 모델이고, 값의 범위는 요청 DTO와 스키마의 CHECK가 지킨다.
 */
@Entity
@Table(name = "reading_goals")
public class ReadingGoal extends BaseTimeEntity {

	/**
	 * 목표를 세우지 않은 해에 적용하는 값(spec.md F8).
	 *
	 * <p>이 기본값을 실제로 쓰는 것은 <b>조회</b>이고, 그 조회는 Phase 8의 프로필 통계에서
	 * 생긴다. Phase 3은 설정만 한다.
	 */
	public static final int DEFAULT_TARGET_COUNT = 12;

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
