package com.ithopeschool.projectbe.domain.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// setting_changes 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 기록이 섞이지 않습니다.
public interface SettingChangeRepository extends JpaRepository<SettingChange, Long> {

  // 내 설정 바꾼 기록을 바꾼 순서(번호 순서)대로 찾습니다.
  List<SettingChange> findByUserIdOrderById(Long userId);
}
