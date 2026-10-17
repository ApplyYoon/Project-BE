package com.ithopeschool.projectbe.domain.settings;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// care_settings 테이블의 창구입니다. 사용자 한 명에 한 줄이라 userId 로 찾습니다.
public interface CareSettingRepository extends JpaRepository<CareSetting, Long> {

  // 내 설정을 찾습니다. 아직 한 번도 바꾼 적이 없으면 빈 Optional 입니다. (그때는 처음 값을 씁니다)
  Optional<CareSetting> findByUserId(Long userId);
}
