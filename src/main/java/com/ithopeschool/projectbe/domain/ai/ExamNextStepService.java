package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepResponse;
import com.ithopeschool.projectbe.domain.exam.Exam;
import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.exam.ExamScore;
import com.ithopeschool.projectbe.domain.exam.ExamScoreRepository;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.review.Review;
import com.ithopeschool.projectbe.domain.review.ReviewRepository;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 시험 회고 → 다음 카드에 넣을 한 가지의 로직입니다. (이슈 #237 표 10번)
// ⚠️ 아무것도 저장하지 않습니다. 학생이 고친 뒤 카드로 저장합니다.
//
// 순서
//   1. 내 시험인지, 그 시험의 회고가 있는지 봅니다. 없으면 404 입니다. (서버 DB 에 저장된 것만 씁니다)
//   2. 회고 답에 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   3. 과목 점수(예상 · 실제)를 읽고, 다음 카드에 넣을 과목을 코드가 고릅니다. (ExamNextStep.focusOf)
//   4. AI 가 회고 답을 보고 행동 한 가지를 씁니다. 꺼짐 · 하루 한도 · 실패 · 쓸 수 없는 답이면 nextStep 이 null 입니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 시험·회고·과목만 읽습니다.
// 회고 답과 AI 의 답은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class ExamNextStepService {

  private final CurrentUserProvider currentUserProvider;
  private final ExamRepository examRepository;
  private final ExamScoreRepository examScoreRepository;
  private final ReviewRepository reviewRepository;
  private final SubjectRepository subjectRepository;
  private final AiClient aiClient;

  public ExamNextStepResponse suggest(ExamNextStepRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Long examId = Long.valueOf(request.examId());

    // 1) 내 시험과 그 회고
    Exam exam =
        examRepository
            .findByIdAndUserId(examId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "시험을 찾을 수 없어요"));
    Review review =
        reviewRepository
            .findByUserIdAndExamId(userId, examId)
            .orElseThrow(
                () -> new ApiException(ErrorCode.NOT_FOUND, "이 시험의 회고가 아직 없어요. 회고를 먼저 써 주세요"));
    List<String> answers = List.of(review.getAnswer1(), review.getAnswer2(), review.getAnswer3());

    // 2) 위기 표현
    if (CrisisText.hasCrisisText(answers)) {
      return ExamNextStepResponse.help();
    }

    // 3) 과목 점수와 다음 카드 과목
    Map<Long, String> subjectNames = new HashMap<>();
    for (Subject subject : subjectRepository.findByUserIdOrderById(userId)) {
      subjectNames.put(subject.getId(), subject.getName());
    }
    List<ExamNextStep.SubjectScore> scores = new ArrayList<>();
    for (ExamScore score : examScoreRepository.findByUserIdAndExamIdOrderById(userId, examId)) {
      scores.add(
          new ExamNextStep.SubjectScore(
              score.getSubjectId(),
              subjectNames.getOrDefault(score.getSubjectId(), "(지운 과목)"),
              score.getExpectedScore(),
              score.getActualScore()));
    }
    ExamNextStep.SubjectScore focus = ExamNextStep.focusOf(scores);

    // 4) AI
    AiQuestion question =
        ExamNextStep.question(exam.getName(), exam.getExamDate(), scores, answers, focus);
    Optional<ExamNextStep.Answer> answer =
        aiClient.ask(userId, GptUsageKind.ASSIST, question, ExamNextStep.Answer.class);
    String text = answer.map(ExamNextStep::from).orElse(null);
    if (text == null) {
      return ExamNextStepResponse.of(null);
    }
    String subjectId = focus == null ? null : String.valueOf(focus.subjectId());
    return ExamNextStepResponse.of(new ExamNextStepResponse.NextStep(text, subjectId));
  }
}
