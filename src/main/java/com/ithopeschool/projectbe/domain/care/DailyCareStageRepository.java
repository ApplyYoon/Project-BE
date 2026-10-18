package com.ithopeschool.projectbe.domain.care;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// daily_care_stages 테이블의 창구입니다. 모든 메서드에 userId 조건이 붙어 있습니다.
public interface DailyCareStageRepository extends JpaRepository<DailyCareStage, Long> {

  // 그날의 내 단계. 아직 판정하지 않았으면 빈 Optional 입니다.
  Optional<DailyCareStage> findByUserIdAndStageDate(Long userId, LocalDate stageDate);

  // 그날보다 앞의 가장 최근 단계 (어제까지의 단계, 처음이면 빈 Optional)
  Optional<DailyCareStage> findFirstByUserIdAndStageDateBeforeOrderByStageDateDesc(
      Long userId, LocalDate stageDate);

  // 기간 안의 내 단계들, 날짜 순서대로
  List<DailyCareStage> findByUserIdAndStageDateBetweenOrderByStageDateAsc(
      Long userId, LocalDate from, LocalDate to);
}
