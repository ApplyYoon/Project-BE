package com.ithopeschool.projectbe.domain.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.ai.dto.ChecklistResponse;
import com.ithopeschool.projectbe.domain.ai.dto.ExamNextStepResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FailCauseResponse;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepRequest;
import com.ithopeschool.projectbe.domain.ai.dto.FirstStepResponse;
import com.ithopeschool.projectbe.domain.ai.dto.QuickCardResponse;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardRequest;
import com.ithopeschool.projectbe.domain.ai.dto.RoutineCardResponse;
import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextResponse;
import com.ithopeschool.projectbe.domain.ai.dto.TextLabelResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 모양을 확인합니다. 진짜 로직(…Service)은 모두 가짜입니다.
@WebMvcTest(AiController.class)
class AiControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChecklistService checklistService;
  @MockitoBean private FirstStepService firstStepService;
  @MockitoBean private FailCauseService failCauseService;
  @MockitoBean private ExamNextStepService examNextStepService;
  @MockitoBean private TextLabelService textLabelService;
  @MockitoBean private RoutineCardService routineCardService;
  @MockitoBean private QuickCardService quickCardService;
  @MockitoBean private SettingsFromTextService settingsFromTextService;

  private ResultActions checklist(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/checklist").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(String body, String message) throws Exception {
    checklist(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(checklistService);
  }

  @Test
  @DisplayName("보내면 200 과 항목 목록이다 (저장하지 않으므로 201 이 아니다)")
  void suggests() throws Exception {
    given(checklistService.suggest(any()))
        .willReturn(ChecklistResponse.of(List.of("42~45쪽 문제 풀기", "틀린 문제 표시하기", "채점하기")));

    checklist("{\"title\":\"수학 문제집\",\"goal\":\"6월 모의고사 수학 2등급\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"items\":[\"42~45쪽 문제 풀기\",\"틀린 문제 표시하기\",\"채점하기\"]}",
                    true));
  }

  @Test
  @DisplayName("AI 가 못 만들었으면 에러가 아니라 200 과 items: null 이다")
  void noItemsIsNull() throws Exception {
    given(checklistService.suggest(any())).willReturn(ChecklistResponse.of(null));

    checklist("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"items\":null}", true));
  }

  @Test
  @DisplayName("위기 표현이면 200 과 showHelp: true, items: null 이다")
  void crisisShowsHelp() throws Exception {
    given(checklistService.suggest(any())).willReturn(ChecklistResponse.help());

    checklist("{\"title\":\"그냥 사라지고 싶어\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":true,\"items\":null}", true));
  }

  @Test
  @DisplayName("제목이 비었거나 200자를 넘거나, 목표가 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void rejectsBadRequests() throws Exception {
    expectRejected("{\"title\":\"\"}", "카드 제목을 적어 주세요");
    expectRejected("{\"title\":\"   \"}", "카드 제목을 적어 주세요");
    expectRejected("{}", "카드 제목을 적어 주세요");
    expectRejected("{\"title\":\"" + "가".repeat(201) + "\"}", "카드 제목은 200자 이하로 적어 주세요");
    expectRejected(
        "{\"title\":\"수학\",\"goal\":\"" + "가".repeat(101) + "\"}", "목표는 100자 이하로 적어 주세요");
  }

  @Test
  @DisplayName("로그인하지 않았으면 401 이다 (서비스가 던진 그대로)")
  void loginRequired() throws Exception {
    given(checklistService.suggest(any()))
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    checklist("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  // ---- 첫 준비 행동 ----

  private ResultActions firstStep(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/first-step").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectFirstStepRejected(String body, String message) throws Exception {
    firstStep(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(firstStepService);
  }

  @Test
  @DisplayName("첫 준비 행동: 보내면 200 과 행동 하나이고, 단계를 안 보내면 0 으로 받는다")
  void firstStepSuggests() throws Exception {
    given(firstStepService.suggest(eq(new FirstStepRequest("42~45쪽 문제 풀기", "수학 문제집", 0, null))))
        .willReturn(FirstStepResponse.of("수학 문제집 42쪽 펴기"));

    firstStep("{\"item\":\" 42~45쪽 문제 풀기 \",\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"step\":\"수학 문제집 42쪽 펴기\"}", true));
  }

  @Test
  @DisplayName("첫 준비 행동: AI 가 못 만들면 200 과 step: null, 위기 표현이면 showHelp: true 다")
  void firstStepNullAndHelp() throws Exception {
    given(firstStepService.suggest(any())).willReturn(FirstStepResponse.of(null));
    firstStep("{\"item\":\"단어 외우기\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"step\":null}", true));

    given(firstStepService.suggest(any())).willReturn(FirstStepResponse.help());
    firstStep("{\"item\":\"죽고 싶다\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":true,\"step\":null}", true));
  }

  @Test
  @DisplayName("첫 준비 행동: 첫 항목이 비었거나 100자를 넘거나, 단계가 0 ~ 2 밖이거나, 앞의 행동이 40자를 넘으면 400 이다")
  void firstStepRejectsBadRequests() throws Exception {
    expectFirstStepRejected("{\"item\":\" \"}", "체크리스트 첫 항목을 보내 주세요");
    expectFirstStepRejected("{}", "체크리스트 첫 항목을 보내 주세요");
    expectFirstStepRejected("{\"item\":\"" + "가".repeat(101) + "\"}", "첫 항목은 100자 이하로 보내 주세요");
    expectFirstStepRejected("{\"item\":\"단어\",\"level\":3}", "단계는 0 ~ 2 사이로 보내 주세요");
    expectFirstStepRejected("{\"item\":\"단어\",\"level\":-1}", "단계는 0 ~ 2 사이로 보내 주세요");
    expectFirstStepRejected(
        "{\"item\":\"단어\",\"level\":1,\"previousStep\":\"" + "가".repeat(41) + "\"}",
        "앞의 행동은 40자 이하로 보내 주세요");
    expectFirstStepRejected(
        "{\"item\":\"단어\",\"title\":\"" + "가".repeat(201) + "\"}", "카드 제목은 200자 이하로 적어 주세요");
  }

  // ---- 예상 실패 원인 선지 ----

  private ResultActions failCauseOptions(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/fail-cause-options")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  @DisplayName("예상 실패 원인 선지: 보내면 200 과 선지 3개 (reason · text · inner), 못 만들면 options: null")
  void failCauseOptionsSuggests() throws Exception {
    given(failCauseService.suggest(any()))
        .willReturn(
            FailCauseResponse.of(
                List.of(
                    new FailCauseResponse.Option("tired", "학원 갔다 와서 졸림", false),
                    new FailCauseResponse.Option("event", "친구 연락에 나가기", false),
                    new FailCauseResponse.Option("phone", "문제 막힐 때 폰부터 집기", true))));
    failCauseOptions("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"options\":["
                        + "{\"reason\":\"tired\",\"text\":\"학원 갔다 와서 졸림\",\"inner\":false},"
                        + "{\"reason\":\"event\",\"text\":\"친구 연락에 나가기\",\"inner\":false},"
                        + "{\"reason\":\"phone\",\"text\":\"문제 막힐 때 폰부터 집기\",\"inner\":true}]}",
                    true));

    given(failCauseService.suggest(any())).willReturn(FailCauseResponse.of(null));
    failCauseOptions("{\"title\":\"수학 문제집\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"options\":null}", true));
  }

  @Test
  @DisplayName("예상 실패 원인 선지: 제목이 비었거나 200자를 넘으면 400 이고 서비스를 부르지 않는다")
  void failCauseOptionsRejectsBadRequests() throws Exception {
    failCauseOptions("{\"title\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("카드 제목을 적어 주세요"));
    failCauseOptions("{\"title\":\"" + "가".repeat(201) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("카드 제목은 200자 이하로 적어 주세요"));
    verifyNoInteractions(failCauseService);
  }

  // ---- 시험 회고 → 다음 카드에 넣을 한 가지 ----

  private ResultActions examNextStep(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/exam-next-step").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("시험 회고 → 다음 한 가지: 200 과 행동·과목, 못 만들면 nextStep: null, 회고가 없으면 404")
  void examNextStepSuggests() throws Exception {
    given(examNextStepService.suggest(any()))
        .willReturn(
            ExamNextStepResponse.of(new ExamNextStepResponse.NextStep("수학 기출 1회분 시간 재고 풀기", "3")));
    examNextStep("{\"examId\":\"9\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"nextStep\":{\"text\":\"수학 기출 1회분 시간 재고 풀기\",\"subjectId\":\"3\"}}",
                    true));

    given(examNextStepService.suggest(any())).willReturn(ExamNextStepResponse.of(null));
    examNextStep("{\"examId\":\"9\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"nextStep\":null}", true));

    given(examNextStepService.suggest(any()))
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "이 시험의 회고가 아직 없어요. 회고를 먼저 써 주세요"));
    examNextStep("{\"examId\":\"9\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("이 시험의 회고가 아직 없어요. 회고를 먼저 써 주세요"));
  }

  @Test
  @DisplayName("시험 회고 → 다음 한 가지: 시험 번호가 없거나 숫자가 아니면 400 이고 서비스를 부르지 않는다")
  void examNextStepRejectsBadRequests() throws Exception {
    examNextStep("{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("시험 번호를 보내 주세요"));
    examNextStep("{\"examId\":\"abc\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("시험 번호가 올바르지 않아요"));
    verifyNoInteractions(examNextStepService);
  }

  // ---- 글에 이름표 붙이기 ----

  private ResultActions classifyText(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/classify-text").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("글에 이름표 붙이기: 200 과 이름표, 못 붙이면 label: null")
  void classifyTextLabels() throws Exception {
    given(textLabelService.label(any())).willReturn(TextLabelResponse.of("outer"));
    classifyText("{\"kind\":\"reason\",\"text\":\"동생이 떠들었다\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"label\":\"outer\"}", true));

    given(textLabelService.label(any())).willReturn(TextLabelResponse.of(null));
    classifyText("{\"kind\":\"memo\",\"text\":\"문자 보내기\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"label\":null}", true));
  }

  @Test
  @DisplayName("글에 이름표 붙이기: 종류가 reason · memo 가 아니거나, 글이 비었거나 100자를 넘으면 400 이다")
  void classifyTextRejectsBadRequests() throws Exception {
    classifyText("{\"text\":\"문자\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("글의 종류를 보내 주세요"));
    classifyText("{\"kind\":\"diary\",\"text\":\"문자\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("글의 종류는 reason 또는 memo 로 보내 주세요"));
    classifyText("{\"kind\":\"memo\",\"text\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("분류할 글을 보내 주세요"));
    classifyText("{\"kind\":\"memo\",\"text\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("글은 100자 이하로 보내 주세요"));
    verifyNoInteractions(textLabelService);
  }

  // ---- 루틴 카드 제안 (이슈 #239) ----

  private ResultActions routineCards(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/routine-cards").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRoutineCardsRejected(String body, String message) throws Exception {
    routineCards(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
    verifyNoInteractions(routineCardService);
  }

  @Test
  @DisplayName("루틴 카드: 보내면 200 과 카드 목록이고, dDay · totalAmount · amountUnit 이 서비스까지 간다")
  void routineCardsSuggests() throws Exception {
    given(routineCardService.suggest(any()))
        .willReturn(
            new RoutineCardResponse(
                false,
                List.of(
                    new RoutineCardResponse.Suggestion(
                        "영단어 30쪽",
                        List.of(1, 2, 3, 4, 5),
                        "19:00",
                        50,
                        List.of("폰을 다른 방에 두고 오기")))));

    routineCards(
            "{\"goal\":\" 영단어장 1회독 \",\"obstacle\":\"\",\"plan\":\"폰을 다른 방에 두기\","
                + "\"dDay\":\"2026-10-26\",\"totalAmount\":300,\"amountUnit\":\"쪽\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"cards\":[{\"title\":\"영단어 30쪽\",\"repeatDays\":[1,2,3,4,5],"
                        + "\"startTime\":\"19:00\",\"durationMinutes\":50,\"checklist\":[\"폰을 다른 방에 두고 오기\"]}]}",
                    true));
    // 앞뒤 공백은 지우고, 빈 방해물은 없는 것(null)으로 받습니다.
    verify(routineCardService)
        .suggest(
            new RoutineCardRequest(
                "영단어장 1회독", null, "폰을 다른 방에 두기", LocalDate.of(2026, 10, 26), 300, "쪽"));
  }

  @Test
  @DisplayName("루틴 카드: AI 가 못 만들었으면 200 과 cards: null, 위기 표현이면 showHelp: true")
  void routineCardsNullAndHelp() throws Exception {
    given(routineCardService.suggest(any())).willReturn(new RoutineCardResponse(false, null));
    routineCards("{\"goal\":\"수학 3등급\",\"plan\":\"문제집부터 펴기\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"cards\":null}", true));

    given(routineCardService.suggest(any())).willReturn(RoutineCardResponse.help());
    routineCards("{\"goal\":\"수학 3등급\",\"plan\":\"그냥 사라지고 싶어\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":true,\"cards\":null}", true));
  }

  @Test
  @DisplayName("루틴 카드: 목표·대처가 비었거나 길거나, 분량·단위가 범위를 벗어나면 400 이고 서비스를 부르지 않는다")
  void routineCardsRejectsBadRequests() throws Exception {
    expectRoutineCardsRejected("{\"plan\":\"대처\"}", "목표를 적어 주세요");
    expectRoutineCardsRejected("{\"goal\":\"목표\",\"plan\":\"  \"}", "대처를 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"" + "가".repeat(101) + "\",\"plan\":\"대처\"}", "목표는 100자 이하로 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"obstacle\":\"" + "가".repeat(101) + "\",\"plan\":\"대처\"}",
        "방해물은 100자 이하로 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"plan\":\"" + "가".repeat(101) + "\"}", "대처는 100자 이하로 적어 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"plan\":\"대처\",\"totalAmount\":0}", "전체 분량은 1 ~ 100000 사이로 보내 주세요");
    expectRoutineCardsRejected(
        "{\"goal\":\"목표\",\"plan\":\"대처\",\"amountUnit\":\"가나다라마바\"}", "분량 단위는 5자 이하로 보내 주세요");
  }

  // ---- 즉석 카드 ----

  private ResultActions quickCard(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/quick-card").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("즉석 카드: 200 과 제목·항목, 못 만들면 card: null")
  void quickCardSuggests() throws Exception {
    given(quickCardService.suggest(any()))
        .willReturn(
            QuickCardResponse.of(
                new QuickCardResponse.Card("수학 문제집", List.of("42쪽 펴기", "5문제 풀기"))));
    quickCard("{\"text\":\"수학 하자\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"card\":{\"title\":\"수학 문제집\",\"items\":[\"42쪽 펴기\",\"5문제 풀기\"]}}",
                    true));

    given(quickCardService.suggest(any())).willReturn(QuickCardResponse.of(null));
    quickCard("{\"text\":\"수학 하자\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"card\":null}", true));
  }

  @Test
  @DisplayName("즉석 카드: 한 줄이 비었거나 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void quickCardRejectsBadRequests() throws Exception {
    quickCard("{\"text\":\" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("한 줄을 적어 주세요"));
    quickCard("{\"text\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("한 줄은 100자 이하로 적어 주세요"));
    verifyNoInteractions(quickCardService);
  }

  // ---- 말로 설정 바꾸기 ----

  private ResultActions settingsFromText(String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/ai/settings-from-text")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  @DisplayName("말로 설정 바꾸기: 200 과 변경안(바뀌는 칸만), 못 만들면 proposal: null")
  void settingsFromTextSuggests() throws Exception {
    given(settingsFromTextService.suggest(any()))
        .willReturn(
            SettingsFromTextResponse.of(
                new SettingsFromTextResponse.Proposal(
                    null, null, null, "09:00", "연락 금지 끝을 09:00 으로 바꿔요")));
    settingsFromText("{\"text\":\"아침엔 알림 꺼 줘\"}")
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"showHelp\":false,\"proposal\":{\"callTime\":null,\"dailyCount\":null,"
                        + "\"quietStart\":null,\"quietEnd\":\"09:00\",\"summary\":\"연락 금지 끝을 09:00 으로 바꿔요\"}}",
                    true));

    given(settingsFromTextService.suggest(any())).willReturn(SettingsFromTextResponse.of(null));
    settingsFromText("{\"text\":\"수위 5로\"}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"showHelp\":false,\"proposal\":null}", true));
  }

  @Test
  @DisplayName("말로 설정 바꾸기: 부탁이 비었거나 100자를 넘으면 400 이고 서비스를 부르지 않는다")
  void settingsFromTextRejectsBadRequests() throws Exception {
    settingsFromText("{\"text\":\"\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("바꾸고 싶은 것을 적어 주세요"));
    settingsFromText("{\"text\":\"" + "가".repeat(101) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("부탁은 100자 이하로 적어 주세요"));
    verifyNoInteractions(settingsFromTextService);
  }
}
