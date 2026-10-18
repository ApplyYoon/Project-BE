package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.school.dto.SchoolNoticesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 내 학교 홈페이지 가정통신문 · 공지사항의 입구입니다. 요청을 받아 Service 를 부르기만 합니다. (2026-10-18)
// 로그인이 필요합니다. 학교 번호는 가입 정보(accounts.school_code)에서 꺼내서, 다른 학교를 물을 수 없습니다.
@Tag(name = "학교 공지")
@RestController
@RequestMapping("/api/v1/school/notices")
@RequiredArgsConstructor
public class SchoolNoticeController {

  private final SchoolNoticeService schoolNoticeService;

  @Operation(
      summary =
          "내 학교 홈페이지 최근 가정통신문 · 공지사항 (제목 · 날짜 · 주소만, 최근 30일 · 하루 1번 받아 둠 · 학교를 안 골랐으면 school: null · 토큰 필요)")
  @GetMapping
  public SchoolNoticesResponse notices() {
    return schoolNoticeService.getMyNotices();
  }
}
