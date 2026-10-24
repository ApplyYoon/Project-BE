package com.ithopeschool.projectbe.domain.diary;

import com.ithopeschool.projectbe.domain.ai.AiClient;
import com.ithopeschool.projectbe.domain.auth.Account;
import com.ithopeschool.projectbe.domain.auth.AccountRepository;
import com.ithopeschool.projectbe.domain.diary.dto.DiaryCreateRequest;
import com.ithopeschool.projectbe.domain.diary.dto.DiaryResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.SafetyService;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 일기를 저장하고 꺼내고 지우는 로직입니다. (이슈 #60)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고,
// 내 번호로만 찾아서 다른 사람의 일기 원문은 볼 수도 지울 수도 없습니다. (다른 사람의 일기 번호는 404)
// ⚠️ 일기 원문과 AI 의 답은 로그에 찍지 않습니다.
//
// 저장 순서
//   1. 위기 표현 감지(#56, SafetyService). 걸리면 보호 조치를 켜고 도움 안내(showHelp)를 같이 돌려줍니다.
//      학생이 쓴 글은 잃어버리지 않게 일기는 그대로 저장하고, AI 분석만 하지 않습니다.
//   2. 일기 저장.
//   3. 일기 분석에 동의(가입 때의 선택 동의 diaryAnalysisAgreed)했을 때만 AI 가 감정·원인·키워드를 뽑습니다.
//      AI 꺼짐 · 하루 한도 · 실패면 분석 없이 저장만 됩니다. (analyzed: false)
@Service
@RequiredArgsConstructor
public class DiaryService {

  // 기간 조회가 받아 주는 가장 긴 기간(일)입니다. 시작일과 끝일을 모두 센 날 수입니다. (체크인 조회와 같음)
  static final int MAX_QUERY_DAYS = 31;

  private final CurrentUserProvider currentUserProvider;
  private final DiaryRepository diaryRepository;
  private final AccountRepository accountRepository;
  private final SafetyService safetyService;
  private final AiClient aiClient;
  private final Clock clock;

  @Transactional
  public DiaryResponse create(DiaryCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    String content = request.content().strip();

    // 1) 위기 표현 감지. 어떤 기능보다 먼저입니다.
    boolean showHelp = safetyService.check(userId, List.of(content));
    if (showHelp) {
      safetyService.activate(userId);
    }

    // 2) 저장
    Instant now = clock.instant();
    Diary diary = diaryRepository.save(new Diary(userId, DayRule.dateOf(now), content, now));

    // 3) AI 분석 (위기 표현이 없고, 분석에 동의한 사람만)
    if (!showHelp && agreedToAnalysis(userId)) {
      Optional<DiaryAnalysis.Answer> answer =
          aiClient.ask(
              userId,
              GptUsageKind.ASSIST,
              DiaryAnalysis.question(content),
              DiaryAnalysis.Answer.class);
      answer.ifPresent(
          raw -> {
            DiaryAnalysis.Result result = DiaryAnalysis.from(raw);
            // @Transactional 이라 바뀐 값은 끝날 때 DB 에 저장됩니다.
            diary.recordAnalysis(
                result.emotions(), result.causes(), result.keywords(), clock.instant());
          });
    }
    return DiaryResponse.from(diary, showHelp);
  }

  // 기간 안의 내 일기 목록. from ~ to 는 둘 다 포함하는 날짜이고, 최근에 쓴 것부터입니다. 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<DiaryResponse> getDiaries(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();

    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_QUERY_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_QUERY_DAYS + "일까지만 조회할 수 있어요");
    }

    return diaryRepository
        .findByUserIdAndDiaryDateBetweenOrderByWrittenAtDesc(userId, from, to)
        .stream()
        .map(DiaryResponse::from)
        .toList();
  }

  // 내 일기 하나를 지웁니다. 뽑은 감정·원인·키워드도 같이 지워집니다. 되돌릴 수 없습니다.
  // 없는 번호이거나 다른 사람의 일기면 404 입니다.
  @Transactional
  public void delete(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    Diary diary =
        diaryRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "일기를 찾을 수 없어요"));
    diaryRepository.delete(diary);
  }

  // 일기 분석에 동의했는지. 계정 정보가 없으면 동의하지 않은 것으로 봅니다.
  private boolean agreedToAnalysis(Long userId) {
    return accountRepository.findByUserId(userId).map(Account::isDiaryAnalysisAgreed).orElse(false);
  }
}
