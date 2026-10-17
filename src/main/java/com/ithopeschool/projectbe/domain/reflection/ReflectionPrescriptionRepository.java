package com.ithopeschool.projectbe.domain.reflection;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// reflection_prescriptions 테이블의 창구입니다. userId 로만 찾아서, 다른 사람의 처방이 섞이지 않습니다.
public interface ReflectionPrescriptionRepository
    extends JpaRepository<ReflectionPrescription, Long> {

  // 내 가장 최근 처방 (번호가 가장 큰 줄). 받은 적이 없으면 빈 Optional 입니다.
  Optional<ReflectionPrescription> findFirstByUserIdOrderByIdDesc(Long userId);
}
