package com.ithopeschool.projectbe.domain.plan;

import com.ithopeschool.projectbe.domain.gpt.GptUsageGuard;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.plan.dto.PlanParseRequest;
import com.ithopeschool.projectbe.domain.plan.dto.PlanParseResponse;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "말로 계획 세우기" 의 로직입니다. 문장 하나를 계획 초안 목록으로 바꿉니다. (이슈 #86, docs/API.md "말로 계획 세우기")
// ⚠️ 아무것도 저장하지 않습니다. 저장은 학생이 미리보기를 확인한 뒤 FE 가 계획 추가 API(#12)로 합니다.
//
// 순서
//   1. 위기 표현(단어 목록)이 있으면 GPT 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   2. GPT 가 꺼져 있으면 503
//   3. GPT 를 부른다고 기록합니다. (#33. 하루 한도는 2026-10-24 에 없앴습니다)
//   4. 내 과목 목록(번호·이름)을 같이 주고 GPT 를 부릅니다. 실패하거나 시간이 넘으면 빈 목록이 아니라 503
//   5. GPT 가 위기 표현이라고 하면 1번과 같이 합니다. (단어 목록과 GPT 중 하나라도 걸리면 감지)
//   6. 줄마다 검사해서(PlanDraft.from) 쓸 수 있는 줄만 돌려줍니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 과목만 GPT 에게 줍니다.
// 학생이 쓴 문장과 GPT 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class PlanParseService {

  private final CurrentUserProvider currentUserProvider;
  private final SubjectRepository subjectRepository;
  private final PlanParseClient planParseClient;
  private final GptUsageGuard gptUsageGuard;

  // GPT 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다.
  // (호출 기록은 GptUsageGuard 가 따로 저장합니다)
  public PlanParseResponse parse(PlanParseRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 위기 표현 (단어 목록)
    if (CrisisText.hasCrisisText(List.of(request.text()))) {
      return PlanParseResponse.help();
    }

    // 2) GPT 가 꺼져 있음 (내 컴퓨터에서는 항상 여기서 멈춥니다)
    if (!planParseClient.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 계획 만들기가 아직 켜져 있지 않아요");
    }

    // 3) 부른 것을 기록
    gptUsageGuard.recordText(userId, GptUsageKind.PLAN_PARSE);

    // 4) GPT 에게 내 과목 목록과 문장을 줍니다.
    List<Subject> subjects = subjectRepository.findByUserIdOrderById(userId);
    Optional<PlanDraft.Raw> raw =
        planParseClient.parse(
            PlanParsePrompt.instructions(),
            PlanParsePrompt.input(subjects, request.text()),
            PlanParsePrompt.schema());
    if (raw.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "계획을 만들지 못했어요. 잠시 후 다시 시도해 주세요");
    }

    // 5) 위기 표현 (GPT 판단)
    if (Boolean.TRUE.equals(raw.get().crisis())) {
      return PlanParseResponse.help();
    }

    // 6) 줄마다 검사. 내 과목 번호만 남깁니다.
    Set<String> mySubjectIds = new HashSet<>();
    for (Subject subject : subjects) {
      mySubjectIds.add(String.valueOf(subject.getId()));
    }
    return PlanParseResponse.of(request.date(), PlanDraft.from(raw.get(), mySubjectIds));
  }
}
