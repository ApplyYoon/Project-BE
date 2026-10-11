package com.ithopeschool.projectbe.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// "비밀번호 바꾸기" 요청의 모양입니다.
// 새 비밀번호의 규칙은 가입할 때(SignupRequest)와 같습니다. 지금 비밀번호가 맞는지는 AccountService 가 확인합니다.
public record PasswordChangeRequest(
    @NotBlank(message = "지금 비밀번호를 입력해 주세요") String currentPassword,
    @NotNull(message = "새 비밀번호를 입력해 주세요")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 적어 주세요")
        @Pattern(regexp = ".*[a-zA-Z].*", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요")
        @Pattern(regexp = ".*[0-9].*", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요")
        String newPassword) {}
