package com.ithopeschool.projectbe.domain.exam;

import com.ithopeschool.projectbe.domain.safety.ExamPeriod;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 한 학생이 오늘 "시험 기간"(시험 직전 3일 ~ 직후 2일)인지, DB 에 등록한 시험(#16)으로 알려 줍니다. (이슈 #58)
// 시험 기간이면 안전 브레이크가 켜져서 관리 강도가 0(회복)이 됩니다. (SafetyBrake → CareLevel.of)
//
// 관리 강도를 정하는 쪽(#62)과 예약 작업이 "이 학생은 오늘 시험 기간인가" 를 볼 때 씁니다.
// 로그인과 상관없이, 받을 사람의 번호로 그 사람의 시험만 찾습니다. (다른 사람의 시험은 섞이지 않습니다)
@Component
@RequiredArgsConstructor
public class ExamPeriodChecker {

  private final ExamRepository examRepository;

  // 오늘(today, 새벽 4시 기준 날짜) 이 학생이 시험 기간이면 true
  @Transactional(readOnly = true)
  public boolean isExamPeriod(Long userId, LocalDate today) {
    return ExamPeriod.isNearExam(today, examDatesOf(userId));
  }

  // 오늘 시험 기간이면 낮춘 이유 문장(ExamPeriod.REASON), 아니면 null
  @Transactional(readOnly = true)
  public String reasonFor(Long userId, LocalDate today) {
    return ExamPeriod.reasonFor(today, examDatesOf(userId));
  }

  // 이 학생이 등록한 시험의 날짜들
  private List<LocalDate> examDatesOf(Long userId) {
    return examRepository.findByUserIdOrderByExamDateDescIdDesc(userId).stream()
        .map(Exam::getExamDate)
        .toList();
  }
}
