package com.ithopeschool.projectbe.domain.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB · 위기 감지 · AI 를 가짜로 바꿔서 저장 순서와 검사 로직만 확인합니다.
class DiaryServiceTest {

  private static final Long USER_ID = 7L;
  // 지금은 2026-10-24 오후 6시(한국 시간)
  private static final Instant NOW = Instant.parse("2026-10-24T09:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-24");
  private static final String CONTENT = "오늘은 폰을 보다가 수학을 못 했다.";

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final DiaryRepository diaryRepository = mock(DiaryRepository.class);
  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final SafetyService safetyService = mock(SafetyService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final DiaryService service =
      new DiaryService(
          currentUserProvider,
          diaryRepository,
          accountRepository,
          safetyService,
          aiClient,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(diaryRepository.save(any()))
        .willAnswer(
            invocation -> {
              Diary diary = invocation.getArgument(0);
              ReflectionTestUtils.setField(diary, "id", 3L);
              return diary;
            });
  }

  // 일기 분석 동의 여부만 정한 가짜 계정
  private void agreed(boolean agreed) {
    Account account = mock(Account.class);
    given(account.isDiaryAnalysisAgreed()).willReturn(agreed);
    given(accountRepository.findByUserId(USER_ID)).willReturn(Optional.of(account));
  }

  @Test
  @DisplayName("동의했으면 저장 뒤 AI 가 뽑은 감정·원인·키워드를 담는다. 정해진 값이 아닌 것은 버린다")
  void createsAndAnalyzes() {
    agreed(true);
    given(aiClient.ask(eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(DiaryAnalysis.Answer.class)))
        .willReturn(
            Optional.of(
                new DiaryAnalysis.Answer(
                    List.of("TIRED", "HAPPY"),
                    List.of("phone", "laziness"),
                    List.of(" 수학 ", "", "폰", "아주아주아주아주아주아주아주아주긴키워드입니다", "늦잠", "학원", "숙제", "시험"))));

    DiaryResponse response = service.create(new DiaryCreateRequest("  " + CONTENT + "  ", 2));

    assertThat(response.id()).isEqualTo("3");
    assertThat(response.date()).isEqualTo(TODAY);
    assertThat(response.content()).isEqualTo(CONTENT);
    assertThat(response.mood()).isEqualTo(2);
    assertThat(response.writtenAt()).isEqualTo(NOW);
    assertThat(response.analyzed()).isTrue();
    assertThat(response.emotions()).containsExactly("TIRED");
    // 체크인 원인과 같은 값(FE 글자)만 남습니다.
    assertThat(response.causes()).containsExactly("phone");
    // 빈 낱말 · 20자 넘는 낱말은 버리고 5개까지
    assertThat(response.keywords()).containsExactly("수학", "폰", "늦잠", "학원", "숙제");
    assertThat(response.showHelp()).isFalse();
    assertThat(response.helpLines()).isEmpty();
    verify(safetyService).check(USER_ID, List.of(CONTENT));
    verify(safetyService, never()).activate(any());
  }

  @Test
  @DisplayName("분석에 동의하지 않았으면 AI 를 부르지 않고 저장만 한다 (analyzed: false)")
  void noConsentNoAnalysis() {
    agreed(false);

    DiaryResponse response = service.create(new DiaryCreateRequest(CONTENT, null));

    assertThat(response.analyzed()).isFalse();
    assertThat(response.emotions()).isEmpty();
    verifyNoInteractions(aiClient);
    verify(diaryRepository).save(any());
  }

  @Test
  @DisplayName("AI 가 꺼졌거나 실패하면(빈 값) 분석 없이 저장만 된다")
  void aiFailedStillSaves() {
    agreed(true);
    given(aiClient.ask(any(), any(), any(), eq(DiaryAnalysis.Answer.class)))
        .willReturn(Optional.empty());

    DiaryResponse response = service.create(new DiaryCreateRequest(CONTENT, null));

    assertThat(response.analyzed()).isFalse();
    verify(diaryRepository).save(any());
  }

  @Test
  @DisplayName("위기 표현이면 보호 조치를 켜고 도움 안내를 돌려준다. 일기는 저장하되 AI 분석은 하지 않는다")
  void crisisShowsHelpAndSkipsAi() {
    agreed(true);
    given(safetyService.check(eq(USER_ID), any())).willReturn(true);

    DiaryResponse response = service.create(new DiaryCreateRequest("다 그만두고 사라지고 싶어", null));

    assertThat(response.showHelp()).isTrue();
    assertThat(response.helpLines()).isNotEmpty();
    assertThat(response.helpMessage()).isEqualTo(SafetyService.HELP_MESSAGE);
    assertThat(response.analyzed()).isFalse();
    verify(safetyService).activate(USER_ID);
    verify(diaryRepository).save(any());
    verifyNoInteractions(aiClient);
  }

  @Test
  @DisplayName("목록은 내 번호와 기간으로 찾는다. 시작이 끝보다 늦거나 31일을 넘으면 400")
  void listsWithinRange() {
    LocalDate from = TODAY.minusDays(30);
    given(diaryRepository.findByUserIdAndDiaryDateBetweenOrderByWrittenAtDesc(USER_ID, from, TODAY))
        .willReturn(List.of(new Diary(USER_ID, TODAY, CONTENT, 2, NOW)));

    assertThat(service.getDiaries(from, TODAY)).hasSize(1);

    assertThatThrownBy(() -> service.getDiaries(TODAY, TODAY.minusDays(1)))
        .isInstanceOf(ApiException.class)
        .hasMessage("시작 날짜는 끝 날짜보다 늦을 수 없어요");
    assertThatThrownBy(() -> service.getDiaries(TODAY.minusDays(31), TODAY))
        .isInstanceOf(ApiException.class)
        .hasMessage("한 번에 31일까지만 조회할 수 있어요");
  }

  @Test
  @DisplayName("지우기는 내 일기만 찾아서 지우고, 없거나 남의 일기면 404")
  void deletesOnlyMine() {
    Diary mine = new Diary(USER_ID, TODAY, CONTENT, 2, NOW);
    given(diaryRepository.findByIdAndUserId(3L, USER_ID)).willReturn(Optional.of(mine));
    given(diaryRepository.findByIdAndUserId(99L, USER_ID)).willReturn(Optional.empty());

    service.delete(3L);
    verify(diaryRepository).delete(mine);

    assertThatThrownBy(() -> service.delete(99L))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
  }
}
