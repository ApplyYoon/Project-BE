package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

// "탈퇴" 요청의 모양입니다. 되돌릴 수 없는 일이라, 본인이 맞는지 비밀번호를 한 번 더 받습니다.
public record WithdrawRequest(@NotBlank(message = "비밀번호를 입력해 주세요") String password) {}
