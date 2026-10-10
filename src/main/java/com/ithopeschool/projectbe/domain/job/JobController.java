package com.ithopeschool.projectbe.domain.job;

import com.ithopeschool.projectbe.domain.job.dto.JobErrorResponse;
import com.ithopeschool.projectbe.domain.job.dto.JobRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 예약 작업을 부르는 API 입니다. 스케줄러(cron-job.org)가 정해진 시각에 이 주소를 부릅니다.
//   POST /api/v1/jobs/practice      헤더 X-Job-Key: <비밀 키>
//
// 비밀 키 확인은 여기서 하지 않고 JobKeyInterceptor 가 /api/v1/jobs/** 전체에 대해 먼저 합니다.
// 그래서 이 안에 새 주소를 추가해도 키 확인을 깜빡할 수 없습니다.
@Tag(name = "예약 작업")
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

  private final JobRunner jobRunner;

  @Operation(
      summary = "예약 작업 한 번 실행 (비밀 키 필요)",
      description = "같은 작업을 두 번 불러도 결과가 망가지지 않습니다. 이미 실행 중이면 SKIPPED 로 답합니다.")
  // Swagger 에서 Try it out 을 할 때 헤더 칸이 보이게 합니다. (로컬 키는 application-local.yml 에 있습니다)
  @Parameter(
      name = JobKeyInterceptor.KEY_HEADER,
      in = ParameterIn.HEADER,
      required = true,
      description = "비밀 키. 틀리거나 없으면 401")
  @PostMapping("/{name}")
  public ResponseEntity<Object> run(@PathVariable String name) {
    return jobRunner
        .run(name)
        .<ResponseEntity<Object>>map(this::toResponse)
        .orElseGet(
            () ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(JobErrorResponse.of("NOT_FOUND", "그런 작업이 없어요")));
  }

  // 실행 결과를 HTTP 응답으로 바꿉니다.
  private ResponseEntity<Object> toResponse(JobRunResult result) {
    return switch (result.status()) {
      case DONE -> ok(result, "작업을 실행했어요");
      case SKIPPED -> ok(result, "같은 작업이 이미 실행 중이라 건너뛰었어요");
      // 실패는 500 으로 알려야 스케줄러(cron-job.org)가 "실패" 로 알아채고 이메일을 보냅니다.
      case FAILED ->
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(JobErrorResponse.of("INTERNAL_ERROR", "작업이 실패했어요"));
    };
  }

  private ResponseEntity<Object> ok(JobRunResult result, String message) {
    return ResponseEntity.ok(
        new JobRunResponse(
            result.job(),
            result.status().name(),
            result.date().toString(),
            result.ranAt().toEpochMilli(),
            message));
  }
}
