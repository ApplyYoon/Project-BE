package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.LongPlanOutline;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanRequest;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanResponse;
import com.ithopeschool.projectbe.domain.ai.dto.LongPlanReviseRequest;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "긴 계획(30일)" 의 로직입니다. 만들기와 고치기가 있습니다. (2026-10-18)
// 사용자: "내가 적으면 얘가 30일치를 준다든지, 교과서나 이런 정보를 받아서"
// ⚠️ 아무것도 저장하지 않습니다. 사진도 AI 가 읽고 버립니다. 학생이 [이대로 할게] 를 누르면 FE 가 카드로 저장합니다.
//
// 만들기 순서
//   1. 날짜 · 사진 검사 (틀리면 400)
//   2. 목표 · 준비하는 것의 이름에 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp (#56)
//   3. AI 가 할 일 목록을 씁니다. 사진이 있으면 사진과 함께. (꺼짐 · 하루 한도 · 실패면 plan 이 null, 한도는 ASSIST)
//   4. AI 가 위기 표현이라고 하면 showHelp, 공부 자료가 아닌 사진이라고 하면 plan 이 null
//   5. 할 일을 검사하고(LongPlan.topicsOf), 코드가 날짜별 카드로 나눕니다. (LongPlanScheduler)
// 고치기 순서
//   1. 이미 지난 계획 · 30일보다 먼 계획이면 400
//   2. 요청 · 목표 · 할 일 제목에 위기 표현이 있으면 showHelp
//   3. AI 가 바꿀 칸만 채웁니다. 검사해서 뼈대에 넣고(LongPlan.revised), 코드가 다시 나눕니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 한도는 사람마다 셉니다.
// AI 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다. (DB 를 쓰지 않습니다)
@Service
@RequiredArgsConstructor
public class LongPlanService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;
  private final Clock clock;

  // 새 계획 만들기
  public LongPlanResponse make(LongPlanRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());

    // 1) 날짜 · 사진
    LocalDate endDate = request.endDate();
    if (!endDate.isAfter(today) || endDate.isAfter(today.plusDays(LongPlan.MAX_DAYS))) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험 · 마감 날짜는 내일부터 30일 안으로 보내 주세요");
    }
    LocalDate startDate = request.startDate() == null ? today : request.startDate();
    if (startDate.isBefore(today) || !startDate.isBefore(endDate)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 오늘부터 시험 · 마감 전날까지예요");
    }
    if (LongPlanScheduler.studyDaysOf(startDate, endDate, request.restDays()).isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "공부할 날이 없어요. 쉬는 요일을 줄여 주세요");
    }
    if (request.image() != null) {
      CardPhotoService.checkImage(request.image());
    }
    int dailyMinutes =
        request.dailyMinutes() == null
            ? LongPlanRequest.DEFAULT_DAILY_MINUTES
            : request.dailyMinutes();
    String startTime =
        request.startTime() == null ? LongPlanRequest.DEFAULT_START_TIME : request.startTime();

    // 2) 위기 표현 (단어 목록)
    List<String> texts = new ArrayList<>();
    texts.add(request.goal());
    if (request.focus() != null && request.focus().name() != null) {
      texts.add(request.focus().name());
    }
    if (CrisisText.hasCrisisText(texts)) {
      return LongPlanResponse.help();
    }

    // 3) AI — 사진이 있으면 사진과 함께, 없으면 답이 길 수 있어서 오래 기다리는 쪽으로
    AiQuestion question =
        LongPlan.question(
            request.goal(),
            startDate,
            endDate,
            dailyMinutes,
            request.restDays(),
            request.focus(),
            request.image() != null);
    Optional<LongPlan.Answer> answer =
        request.image() != null
            ? aiClient.askWithImage(
                userId, GptUsageKind.ASSIST, question, request.image(), LongPlan.Answer.class)
            : aiClient.askLong(userId, GptUsageKind.ASSIST, question, LongPlan.Answer.class);
    if (answer.isEmpty()) {
      return LongPlanResponse.notMade();
    }

    // 4) 위기 표현 (AI 판단) · 공부 자료가 아닌 사진
    if (Boolean.TRUE.equals(answer.get().crisis())) {
      return LongPlanResponse.help();
    }
    if (request.image() != null && Boolean.TRUE.equals(answer.get().notStudy())) {
      return LongPlanResponse.notMade();
    }

    // 5) 할 일 검사 → 날짜별로 나누기
    List<LongPlanOutline.Topic> topics = LongPlan.topicsOf(answer.get().topics());
    if (topics.isEmpty()) {
      return LongPlanResponse.notMade();
    }
    LongPlanOutline plan =
        new LongPlanOutline(
            request.goal(),
            startDate,
            endDate,
            dailyMinutes,
            startTime,
            request.restDays(),
            topics);
    return scheduled(LongPlan.replyOf(answer.get().reply(), LongPlan.DEFAULT_REPLY), plan);
  }

  // 지금 계획을 학생의 요청대로 고치기
  public LongPlanResponse revise(LongPlanReviseRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    LocalDate today = DayRule.dateOf(clock.instant());
    LongPlanOutline current = request.plan();

    // 1) 날짜
    if (!current.endDate().isAfter(today) || !current.endDate().isAfter(current.startDate())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "이미 지난 계획이에요. 새로 만들어 주세요");
    }
    if (current.endDate().isAfter(today.plusDays(LongPlan.MAX_DAYS))) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험 · 마감 날짜는 30일 안이어야 해요");
    }

    // 2) 위기 표현 — 학생이 쓴 요청 · 목표와 할 일 제목
    List<String> texts = new ArrayList<>();
    texts.add(request.request());
    texts.add(current.goal());
    for (LongPlanOutline.Topic topic : current.topics()) {
      texts.add(topic.title());
    }
    if (CrisisText.hasCrisisText(texts)) {
      return LongPlanResponse.help();
    }

    // 3) AI → 바꾼 칸 검사 → 다시 나누기
    Optional<LongPlan.ReviseAnswer> answer =
        aiClient.askLong(
            userId,
            GptUsageKind.ASSIST,
            LongPlan.reviseQuestion(current, request.request(), today),
            LongPlan.ReviseAnswer.class);
    if (answer.isEmpty()) {
      return LongPlanResponse.notMade();
    }
    LongPlanOutline revised = LongPlan.revised(current, answer.get(), today);
    return scheduled(
        LongPlan.replyOf(answer.get().reply(), LongPlan.DEFAULT_REVISE_REPLY), revised);
  }

  // 뼈대를 날짜별 카드로 나눠 응답을 만듭니다.
  private static LongPlanResponse scheduled(String reply, LongPlanOutline plan) {
    LongPlanScheduler.Result result = LongPlanScheduler.schedule(plan);
    return LongPlanResponse.of(reply, plan, result.cards(), result.overflow());
  }
}
