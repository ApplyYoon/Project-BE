package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.school.dto.SchoolClassesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 내 학교 반 목록의 입구입니다. 요청을 받아 Service 를 부르기만 합니다. (2026-10-23)
// 로그인이 필요합니다. 학교 번호는 가입 정보(accounts.school_code)에서 꺼냅니다. (SchoolTodayController 와 같음)
@Tag(name = "학교 학사일정")
@RestController
@RequestMapping("/api/v1/school/classes")
@RequiredArgsConstructor
public class SchoolClassController {

  private final SchoolTodayService schoolTodayService;

  @Operation(summary = "내 학교 한 학년의 반 번호 목록 (NEIS 학급정보 · 24시간마다 새로 받음 · 학교를 안 골랐으면 빈 목록 · 토큰 필요)")
  @GetMapping
  public SchoolClassesResponse classes(@RequestParam(required = false) Integer grade) {
    // 학년이 없거나 범위 밖이면 Service 가 400 으로 막습니다.
    // (required = true 로 두면 빠졌을 때 에러 처리기(GlobalExceptionHandler)가 500 으로 바꿔서 이렇게 했습니다)
    return schoolTodayService.getMyClasses(grade);
  }
}
