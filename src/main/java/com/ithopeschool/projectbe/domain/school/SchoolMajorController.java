package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.school.dto.SchoolMajorsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 학교 번호로 학교 종류와 학과 목록을 알려 주는 입구입니다. 요청을 받아 Service 를 부르기만 합니다. (2026-10-19)
// 로그인이 필요 없습니다. 가입 화면(아직 계정이 없음)에서 "무슨 과야?" 를 물을 때 부르기 때문입니다.
@Tag(name = "학교 학과")
@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolMajorController {

  private final SchoolMajorService schoolMajorService;

  @Operation(
      summary =
          "학교 종류 + 학과 목록 (특성화고 · 마이스터고만 학과를 줌 · 로그인 필요 없음 · 학교마다 24시간 받아 둠 · 키가 없으면 complete: false 일 수 있음)")
  @GetMapping("/{schoolCode}/majors")
  public SchoolMajorsResponse majors(@PathVariable String schoolCode) {
    return schoolMajorService.getMajors(schoolCode);
  }
}
