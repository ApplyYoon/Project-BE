package com.ithopeschool.projectbe.domain.auth.dto;

import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.user.User;

// "내 정보" 의 응답 모양입니다. 로그인한 본인에게만 돌려줍니다.
//   { "email": "tmax@starflow.com", "name": "김티맥", "nickname": "티맥이",
//     "schoolCode": "7530560", "schoolName": "한국디지털미디어고등학교", "diaryAnalysis": false }
// 비밀번호(해시 포함)는 어떤 경우에도 담지 않습니다.
public record ProfileResponse(
    String email,
    // 실명
    String name,
    // 화면에 보이는 별명
    String nickname,
    // 고른 고등학교의 번호와 이름. 고르지 않았으면 둘 다 null
    String schoolCode,
    String schoolName,
    // [선택 동의] 일기·감정 기록을 분석에 써도 되는지
    boolean diaryAnalysis) {

  public static ProfileResponse of(User user, Account account) {
    return new ProfileResponse(
        account.getEmail(),
        account.getName(),
        user.getNickname(),
        account.getSchoolCode(),
        account.getSchoolName(),
        account.isDiaryAnalysisAgreed());
  }
}
