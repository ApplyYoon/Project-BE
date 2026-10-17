package com.ithopeschool.projectbe.domain.onboarding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.auth.dto.UserResponse;
import com.ithopeschool.projectbe.domain.onboarding.dto.InitialPlanResponse;
import com.ithopeschool.projectbe.domain.onboarding.dto.OnboardingResponse;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Controller 만 띄워서 요청 모양 검사와 응답 상태 번호를 확인합니다. 진짜 로직(Service)은 가짜입니다.
@WebMvcTest(OnboardingController.class)
class OnboardingControllerTest {

  // 꼭 있어야 하는 네 칸 (뒤에 칸을 더 붙일 수 있게 닫는 괄호는 빼 둠)
  private static final String REQUIRED =
      "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OnboardingService onboardingService;
  @MockitoBean private InitialPlanService initialPlanService;
  @MockitoBean private OnboardingCompletionService onboardingCompletionService;

  private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
    return mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private void expectRejected(MockHttpServletRequestBuilder builder, String body, String message)
      throws Exception {
    send(builder, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.message").value(message));
  }

  private static OnboardingResponse sample() {
    return new OnboardingResponse(
        "4",
        1790000000000L,
        "고2",
        "수능",
        LocalDate.of(2027, 11, 18),
        null,
        null,
        "07:00",
        null,
        null,
        null,
        null,
        List.of(),
        List.of("weekend"),
        "night",
        null,
        null,
        180,
        null,
        null,
        new OnboardingResponse.SelfAwareness("morning", null, null, 180));
  }

  @Test
  @DisplayName("저장(PUT)·일부 고치기(PATCH)·조회(GET)는 200 이고, 시각은 밀리초 숫자 · 빈 답은 null 로 나간다")
  void okResponses() throws Exception {
    given(onboardingService.save(any())).willReturn(sample());
    given(onboardingService.update(any())).willReturn(sample());
    given(onboardingService.get()).willReturn(sample());

    send(put("/api/v1/onboarding"), REQUIRED + "}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("4"))
        .andExpect(jsonPath("$.answeredAt").value(1790000000000L))
        .andExpect(jsonPath("$.targetExamDate").value("2027-11-18"))
        // 값이 없는 칸도 빼지 않고 null 로 보냅니다. (docs/API.md 4장)
        .andExpect(jsonPath("$.targetUniversity").isEmpty())
        .andExpect(jsonPath("$.firstSelfAwareness.chronotype").value("morning"));
    send(patch("/api/v1/onboarding"), "{\"chronotype\":\"night\"}").andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/onboarding")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("PUT 에서 꼭 있어야 하는 네 개 중 하나라도 빠지거나 비면 400 이고 서비스를 부르지 않는다")
  void putRequiresFour() throws Exception {
    expectRejected(
        put("/api/v1/onboarding"),
        "{\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180}",
        "학년을 골라 주세요");
    expectRejected(
        put("/api/v1/onboarding"),
        "{\"grade\":\"고2\",\"targetExam\":\"   \",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180}",
        "목표 시험을 입력해 주세요");
    expectRejected(
        put("/api/v1/onboarding"),
        "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"dailyAvailableMinutes\":180}",
        "시험 날짜를 보내 주세요");
    expectRejected(
        put("/api/v1/onboarding"),
        "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\"}",
        "하루 공부 가능 시간을 골라 주세요");
    verifyNoInteractions(onboardingService);
  }

  @Test
  @DisplayName("글자 수 · 시각 모양 · 과목 번호 · 등급·점수·자신감 범위 · 목록 길이가 틀리면 400")
  void rejectsBadShapes() throws Exception {
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"motivationReason\":\"" + "가".repeat(51) + "\"}",
        "공부하는 이유는 50자 이하로 적어 주세요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"targetUniversity\":\"" + "가".repeat(31) + "\"}",
        "목표 대학은 30자 이하로 적어 주세요");
    // 24:00 · 7:00 · 07시 같은 모양은 받지 않습니다.
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"sleepTime\":\"24:00\"}",
        "취침 시각은 23:30 처럼 보내 주세요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"wakeUpTime\":\"7:00\"}",
        "기상 시각은 07:00 처럼 보내 주세요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"abc\",\"grade\":2,\"confidence\":4}]}",
        "과목 번호가 올바르지 않아요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":10,\"confidence\":4}]}",
        "등급은 1 ~ 9 예요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"3\",\"score\":101,\"confidence\":4}]}",
        "점수는 0 ~ 100 이에요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":2,\"confidence\":6}]}",
        "자신감은 1 ~ 5 예요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":2}]}",
        "자신감(confidence)을 골라 주세요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED + ",\"subjectLevels\":[null]}",
        "subjectLevels 에 빈 값이 있어요");
    expectRejected(
        put("/api/v1/onboarding"),
        REQUIRED
            + ",\"pastFailureCauses\":[\"weekend\",\"phone\",\"afterExam\",\"lateAcademy\",\"tooMuchPlan\",\"weekend\"]}",
        "계획이 무너진 때는 5개까지 고를 수 있어요");
    verifyNoInteractions(onboardingService);
  }

  @Test
  @DisplayName("날짜 모양이 틀리거나(2027/11/18) 숫자 자리에 글자가 오면 400")
  void rejectsBrokenJson() throws Exception {
    send(
            put("/api/v1/onboarding"),
            "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027/11/18\",\"dailyAvailableMinutes\":180}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    send(put("/api/v1/onboarding"), REQUIRED + ",\"maxFocusMinutes\":\"오십분\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(onboardingService);
  }

  @Test
  @DisplayName("숫자 칸에 소수(180.5 · 3.7)나 따옴표 숫자(\"180\")를 보내면 잘라서 받지 않고 400 이다")
  void rejectsNonWholeNumbers() throws Exception {
    // 처음 만들었을 때는 3.7 이 3 으로, 180.5 가 180 으로 말없이 저장됐습니다. 다시 생기지 않게 지킵니다.
    String[] bodies = {
      "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180.5}",
      "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":180.0}",
      "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\",\"dailyAvailableMinutes\":\"180\"}",
      REQUIRED + ",\"maxFocusMinutes\":50.9}",
      REQUIRED + ",\"maxFocusMinutes\":99999999999}",
      REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":2.5,\"confidence\":3}]}",
      REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"3\",\"score\":85.5,\"confidence\":3}]}",
      REQUIRED + ",\"subjectLevels\":[{\"subjectId\":\"3\",\"grade\":2,\"confidence\":3.7}]}"
    };
    for (String body : bodies) {
      send(put("/api/v1/onboarding"), body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    send(patch("/api/v1/onboarding"), "{\"maxFocusMinutes\":50.5}")
        .andExpect(status().isBadRequest());
    send(post("/api/v1/onboarding/initial-plan"), "{\"dailyMinutes\":180.5,\"daysPerWeek\":5}")
        .andExpect(status().isBadRequest());
    send(put("/api/v1/onboarding/initial-plan"), "{\"dailyMinutes\":180,\"daysPerWeek\":5.0}")
        .andExpect(status().isBadRequest());
    verifyNoInteractions(onboardingService, initialPlanService);
  }

  @Test
  @DisplayName("숫자 칸에 null 을 보내면 '안 보냄' 이라 정수 검사를 거치지 않는다 (선택 항목은 비워도 됨)")
  void nullNumbersAreFine() throws Exception {
    given(onboardingService.save(any())).willReturn(sample());

    send(put("/api/v1/onboarding"), REQUIRED + ",\"maxFocusMinutes\":null}")
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("PATCH 에서 학년·목표 시험을 빈 글자로 보내면 400 (꼭 있어야 하는 답은 지울 수 없음)")
  void patchCannotEraseRequired() throws Exception {
    expectRejected(patch("/api/v1/onboarding"), "{\"grade\":\"\"}", "학년을 골라 주세요");
    expectRejected(
        patch("/api/v1/onboarding"), "{\"targetExam\":\"  \"}", "목표 시험은 1 ~ 30자로 적어 주세요");
    verifyNoInteractions(onboardingService);
  }

  @Test
  @DisplayName("설문을 아직 안 했으면 조회 404 (서비스가 던진 에러)")
  void notFoundFromService() throws Exception {
    given(onboardingService.get())
        .willThrow(new ApiException(ErrorCode.NOT_FOUND, "아직 온보딩 설문을 하지 않았어요"));

    mockMvc
        .perform(get("/api/v1/onboarding"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.message").value("아직 온보딩 설문을 하지 않았어요"));
  }

  @Test
  @DisplayName("처음 계획: 세우기 201 · 다시 세우기 200 · 조회 200, 값이 빠지면 400, 두 번 세우면 409")
  void initialPlan() throws Exception {
    InitialPlanResponse plan = new InitialPlanResponse(LocalDate.of(2026, 10, 17), 180, 5);
    given(initialPlanService.create(any())).willReturn(plan);
    given(initialPlanService.remake(any())).willReturn(plan);
    given(initialPlanService.get()).willReturn(plan);

    send(post("/api/v1/onboarding/initial-plan"), "{\"dailyMinutes\":180,\"daysPerWeek\":5}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdDate").value("2026-10-17"))
        .andExpect(jsonPath("$.dailyMinutes").value(180))
        .andExpect(jsonPath("$.daysPerWeek").value(5));
    send(put("/api/v1/onboarding/initial-plan"), "{\"dailyMinutes\":60,\"daysPerWeek\":3}")
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/onboarding/initial-plan")).andExpect(status().isOk());

    expectRejected(
        post("/api/v1/onboarding/initial-plan"),
        "{\"daysPerWeek\":5}",
        "하루 목표 시간(dailyMinutes)을 골라 주세요");

    given(initialPlanService.create(any()))
        .willThrow(new ApiException(ErrorCode.CONFLICT, "처음 계획은 이미 세웠어요. 바꾸려면 다시 세우기를 해 주세요"));
    send(post("/api/v1/onboarding/initial-plan"), "{\"dailyMinutes\":180,\"daysPerWeek\":5}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  @DisplayName("온보딩 마침 표시: 본문 없이 POST 하면 200 과 onboarded 가 true 인 내 정보를 돌려준다")
  void completesOnboarding() throws Exception {
    given(onboardingCompletionService.complete()).willReturn(new UserResponse("1", "티맥이", true));

    mockMvc
        .perform(post("/api/v1/onboarding/completion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.name").value("티맥이"))
        .andExpect(jsonPath("$.onboarded").value(true));
  }
}
