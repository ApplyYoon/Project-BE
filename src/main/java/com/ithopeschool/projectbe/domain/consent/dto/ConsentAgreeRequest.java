package com.ithopeschool.projectbe.domain.consent.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// "다시 동의하기" 요청의 모양입니다. 동의 문구가 바뀌어서 최신 문구에 다시 동의할 때 보냅니다. (이슈 #71)
//   { "terms": true, "privacy": true, "over14": true, "version": "v1" }
//
// 가입할 때의 동의(SignupRequest.Consents)와 같은 규칙입니다. 필수 칸을 false 로 보내거나 아예 안 보내면 400 입니다.
// (안 보내면 값을 읽을 수 없어서 기본 문장이 나옵니다) 일기·감정 분석 동의(선택)는 여기서 받지 않고 내 정보 고치기에서 바꿉니다.
// 이 요청도 만 14세 확인을 받습니다. (이 확인을 받기 전에 가입한 계정이 여기서 처음 확인하게 됩니다)
public record ConsentAgreeRequest(
    @AssertTrue(message = "서비스 이용약관에 동의해 주세요") boolean terms,
    @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요") boolean privacy,
    @AssertTrue(message = "만 14세 이상만 이용할 수 있어요") boolean over14,
    @NotBlank(message = "동의 문구 버전(version)을 보내 주세요") @Size(max = 20, message = "동의 문구 버전이 너무 길어요")
        String version) {}
