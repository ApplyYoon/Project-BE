package com.ithopeschool.projectbe.domain.experiment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// experiments 테이블의 창구입니다. 모든 메서드에 userId 조건이 붙어 있습니다.
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

  // 내 실험 전부, 늦게 시작한 것부터 (같으면 나중에 만든 것부터)
  List<Experiment> findByUserIdOrderByStartDateDescIdDesc(Long userId);

  // 내 실험 중 그 상태인 것들 (진행 중인 실험 세기 등)
  List<Experiment> findByUserIdAndStatus(Long userId, ExperimentStatus status);
}
