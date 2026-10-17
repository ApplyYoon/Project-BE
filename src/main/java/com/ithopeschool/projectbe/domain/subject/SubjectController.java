package com.ithopeschool.projectbe.domain.subject;

import com.ithopeschool.projectbe.domain.subject.dto.SubjectCreateRequest;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectResponse;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "과목" API 의 입구입니다. 네 주소 모두 로그인이 필요합니다. (docs/API.md "과목")
@Tag(name = "과목")
@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {

  private final SubjectService subjectService;

  @Operation(summary = "내 과목 목록 (만든 순서)")
  @GetMapping
  public List<SubjectResponse> getSubjects() {
    return subjectService.getSubjects();
  }

  @Operation(summary = "과목 추가 (이름 30자까지 · 색 red/orange/green/blue/purple · 같은 이름은 409)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SubjectResponse create(@Valid @RequestBody SubjectCreateRequest request) {
    return subjectService.create(request);
  }

  @Operation(summary = "과목 이름·색 바꾸기 (바꿀 것만 보냄)")
  @PatchMapping("/{id}")
  public SubjectResponse update(
      @PathVariable Long id, @Valid @RequestBody SubjectUpdateRequest request) {
    return subjectService.update(id, request);
  }

  @Operation(summary = "과목 삭제 (계획이나 공부 기록이 있는 과목은 지울 수 없음 · 409)")
  @DeleteMapping("/{id}")
  // 돌려줄 내용이 없어서 204 로 답합니다. (docs/API.md 4장)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    subjectService.delete(id);
  }
}
