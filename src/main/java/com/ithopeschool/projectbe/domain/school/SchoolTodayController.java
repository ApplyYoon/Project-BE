package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.school.dto.SchoolTodayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 오늘 학교 정보의 입구입니다. 요청을 받아 Service 를 부르기만 합니다.
// 로그인이 필요합니다. 학교 번호는 가입 정보(accounts.school_code)에서 꺼내서, 다른 학교를 물을 수 없습니다.
@Tag(name = "학교 학사일정")
@RestController
@RequestMapping("/api/v1/school/today")
@RequiredArgsConstructor
public class SchoolTodayController {

  private final SchoolTodayService schoolTodayService;

  @Operation(
      summary =
          "오늘 학교 정보 (NEIS 급식 오늘·내일 + 30일 안의 학사일정 + grade·classNo 를 주면 오늘 우리 반 시간표 · 6시간마다 새로 받음 · 학교를 안 골랐으면 school: null · 토큰 필요)")
  @GetMapping
  public SchoolTodayResponse today(
      @RequestParam(required = false) Integer grade,
      @RequestParam(required = false) Integer classNo) {
    // 반은 서버에 저장하지 않고 FE 가 요청마다 보냅니다. 범위 확인은 Service 가 합니다.
    return schoolTodayService.getMyToday(grade, classNo);
  }
}
