package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// "내 정보 고치기" 요청의 모양입니다. 고칠 수 있는 값을 한꺼번에 보냅니다. (일부만 보내는 방식이 아닙니다)
// 이름·닉네임 규칙은 가입할 때(SignupRequest)와 같습니다. 이메일은 여기서 바꿀 수 없습니다.
public record ProfileUpdateRequest(
    // 실명. 선택입니다. 안 보내면 저장된 실명이 비워집니다. (이 요청은 일부가 아니라 전부를 보내는 방식이라서요. 이슈 #71)
    @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하로 적어 주세요") String name,
    @NotNull(message = "닉네임을 입력해 주세요")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 적어 주세요")
        String nickname,
    // 고른 고등학교의 번호와 이름. 학교를 지우려면 둘 다 null 로 보냅니다.
    @Size(max = 20, message = "학교 번호가 너무 길어요") String schoolCode,
    @Size(max = 100, message = "학교 이름이 너무 길어요") String schoolName,
    // [선택 동의] 일기·감정 기록을 분석에 쓰기. 켜고 끌 수 있습니다.
    @NotNull(message = "일기 분석 동의 여부를 보내 주세요") Boolean diaryAnalysis) {

  // 값이 들어오는 순간에 앞뒤 공백을 지웁니다. 그래서 위의 검사는 공백을 지운 값으로 돌아갑니다.
  public ProfileUpdateRequest {
    // 실명은 선택이라, 공백뿐인 글자는 "안 보냄" 으로 봅니다.
    name = name == null || name.isBlank() ? null : name.trim();
    nickname = nickname == null ? null : nickname.trim();
  }
}
