package com.ithopeschool.projectbe.domain.reflection.dto;

import com.ithopeschool.projectbe.domain.reflection.ReflectionPrescription;
import com.ithopeschool.projectbe.domain.reflection.ReflectionRule;
import java.time.LocalDate;
import java.util.List;

// 지금 내 성찰일기 처방을 FE 에 알려 주는 모양입니다. 처방이 없으면 prescription 이 null 입니다.
//   { "date": "2026-10-17",
//     "prescription": { "id": "3", "startDate": "2026-10-17", "endDate": "2026-10-21", "days": 5,
//                       "missReason": "unwilling", "reasonDays": 3,
//                       "reason": "최근 일주일 동안 '하기 싫음' 때문에 못 채운 날이 3일 있었어. 5일 동안 짧게 돌아보자.",
//                       "questions": ["...", "...", "..."] } }
// - prescription 안의 id · startDate · days · reason · questions 는
//   FE 의 ReflectionPrescription 타입과 같은 이름입니다.
// - id 는 다른 API 와 같이 글자로, missReason 은 FE 글자("tooMuch")로 보냅니다.
// - 누구의 처방인지(userId)는 담지 않습니다.
public record ReflectionCurrentResponse(LocalDate date, Prescription prescription) {

  // date: 서버가 "오늘" 로 본 날짜 (새벽 4시 기준)
  // prescription: 지금 처방. 없으면 null
  public record Prescription(
      String id,
      LocalDate startDate,
      LocalDate endDate,
      int days,
      String missReason,
      int reasonDays,
      String reason,
      List<String> questions) {}

  // 처방이 없을 때
  public static ReflectionCurrentResponse none(LocalDate today) {
    return new ReflectionCurrentResponse(today, null);
  }

  // 처방이 있을 때
  public static ReflectionCurrentResponse of(LocalDate today, ReflectionPrescription prescription) {
    return new ReflectionCurrentResponse(
        today,
        new Prescription(
            String.valueOf(prescription.getId()),
            prescription.getStartDate(),
            prescription.getEndDate(),
            prescription.getDays(),
            prescription.getMissReason().toValue(),
            prescription.getReasonDays(),
            ReflectionRule.reasonSentence(
                prescription.getMissReason(), prescription.getReasonDays(), prescription.getDays()),
            prescription.getQuestions()));
  }
}
