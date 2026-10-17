package com.ithopeschool.projectbe.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 진짜로 켜고(가짜 없이) 메시지 목록·읽음 표시를 불러 봅니다.
// 진짜로 가입한 두 사람(에이·비이)의 토큰으로 불러서, "다른 사용자의 메시지는 보이지도 바뀌지도 않는다" 를 확인합니다.
@SpringBootTest
@AutoConfigureMockMvc
class MessageFlowTest {

  private static final String PASSWORD = "messagePass2026";
  private static final String EMAIL_A = "message-flow-a@starflow.com";
  private static final String EMAIL_B = "message-flow-b@starflow.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private MessageService messageService;
  @Autowired private MessageRepository messageRepository;

  // 로그인해서 받은 토큰과, 그 사람의 사용자 번호
  private record Person(Long userId, String token) {}

  private Person a;
  private Person b;

  @BeforeEach
  void signUpTwoPeople() throws Exception {
    a = signUp(EMAIL_A);
    b = signUp(EMAIL_B);
  }

  // 테스트가 끝나면 두 사람을 탈퇴시켜 다른 테스트에 남지 않게 합니다. (탈퇴하면 메시지도 같이 지워집니다)
  @AfterEach
  void withdrawTwoPeople() throws Exception {
    for (Person person : List.of(a, b)) {
      mockMvc.perform(
          with(
              post("/api/v1/auth/withdraw"),
              person.token(),
              "{\"password\":\"" + PASSWORD + "\"}"));
    }
  }

  private Person signUp(String email) throws Exception {
    String signupBody =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "티맥이",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
        }
        """
            .formatted(email, PASSWORD);
    String signedUp =
        mockMvc
            .perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(signupBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String idText = JsonPath.read(signedUp, "$.id");
    Long userId = Long.valueOf(idText);

    String loggedIn =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Person(userId, JsonPath.read(loggedIn, "$.token"));
  }

  // 이 토큰을 Authorization 헤더에 붙이고, 본문이 있으면 JSON 으로 담습니다.
  private static MockHttpServletRequestBuilder with(
      MockHttpServletRequestBuilder builder, String token, String body) {
    builder.header("Authorization", "Bearer " + token);
    return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private ResultActions list(Person person, String query) throws Exception {
    return mockMvc.perform(with(get("/api/v1/messages" + query), person.token(), null));
  }

  private ResultActions markRead(Person person, Long messageId) throws Exception {
    return mockMvc.perform(
        with(patch("/api/v1/messages/" + messageId + "/read"), person.token(), null));
  }

  // 그 사람에게 메시지 count 개를 차례로 보냅니다(서버 안에서 저장). 보낸 메시지를 보낸 순서대로 돌려줍니다.
  private List<Message> send(Person person, int count) {
    MessageKind[] kinds = MessageKind.values();
    List<Message> sent = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      sent.add(messageService.save(person.userId(), kinds[i % kinds.length], "메시지 " + (i + 1)));
    }
    return sent;
  }

  private List<String> idsOf(List<Message> messages) {
    return messages.stream().map(message -> String.valueOf(message.getId())).toList();
  }

  @Test
  @DisplayName("로그인하지 않고 부르면 목록도 읽음 표시도 401")
  void requiresLogin() throws Exception {
    Message message = send(a, 1).get(0);

    mockMvc
        .perform(get("/api/v1/messages"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    mockMvc
        .perform(patch("/api/v1/messages/" + message.getId() + "/read"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    mockMvc
        .perform(get("/api/v1/messages").header("Authorization", "Bearer wrong-token-123"))
        .andExpect(status().isUnauthorized());

    // 토큰이 없어서 막힌 읽음 표시는 DB 에도 반영되지 않았습니다.
    assertThat(messageRepository.findById(message.getId()).orElseThrow().isRead()).isFalse();
  }

  @Test
  @DisplayName("메시지가 없는 사람의 목록은 404 가 아니라 빈 배열")
  void emptyListForNewPerson() throws Exception {
    list(a, "").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("목록은 최신순 20개씩이고, 모양은 id(글자)·kind·text·sentAt(숫자)·read·readAt(null) 이다")
  void listIsNewestFirstTwentyAtATime() throws Exception {
    List<Message> sent = send(a, 25);

    String body =
        list(a, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(20))
            .andExpect(jsonPath("$[0].id").value(String.valueOf(sent.get(24).getId())))
            .andExpect(jsonPath("$[0].text").value("메시지 25"))
            .andExpect(
                jsonPath("$[0].kind")
                    .value("carrot")) // 종류 6가지를 돌려 가며 보내서 25번째(24 % 6 = 0)는 첫 번째 종류
            .andExpect(jsonPath("$[0].sentAt").isNumber())
            .andExpect(jsonPath("$[0].read").value(false))
            .andExpect(jsonPath("$[0].readAt").value(nullValue()))
            .andExpect(jsonPath("$[19].text").value("메시지 6"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // 모든 줄이 정해 둔 여섯 가지 종류 중 하나입니다.
    List<String> kinds = JsonPath.read(body, "$[*].kind");
    assertThat(kinds).isSubsetOf("carrot", "nudge", "stick", "event", "recovery", "call");
    assertThat(kinds).containsAll(List.of("carrot", "nudge", "stick", "event", "recovery", "call"));
  }

  @Test
  @DisplayName("before 로 더 오래된 5개를 이어서 받고, after 로 새로 온 것만 받는다")
  void pagingWithBeforeAndAfter() throws Exception {
    List<Message> sent = send(a, 25);
    Long firstPageLast = sent.get(5).getId(); // 첫 쪽의 마지막(가장 오래된) = 6번째

    // 더 오래된 것: 1~5번째 (최신순이라 5번째가 먼저)
    list(a, "?before=" + firstPageLast)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].text").value("메시지 5"))
        .andExpect(jsonPath("$[4].text").value("메시지 1"));

    // 새로 온 것: 23번째 뒤에 온 24·25번째 (최신순)
    list(a, "?after=" + sent.get(22).getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].text").value("메시지 25"))
        .andExpect(jsonPath("$[1].text").value("메시지 24"));

    // 가장 새 메시지보다 뒤에는 아무것도 없습니다.
    list(a, "?after=" + sent.get(24).getId()).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("다른 사용자의 메시지는 목록에 보이지 않는다 (에이·비이가 각자 자기 것만 본다)")
  void otherPeoplesMessagesAreNotListed() throws Exception {
    List<Message> sentToA = send(a, 3);
    List<Message> sentToB = send(b, 2);

    String bodyOfA = list(a, "").andReturn().getResponse().getContentAsString();
    String bodyOfB = list(b, "").andReturn().getResponse().getContentAsString();

    List<String> idsOfA = JsonPath.read(bodyOfA, "$[*].id");
    List<String> idsOfB = JsonPath.read(bodyOfB, "$[*].id");
    assertThat(idsOfA).containsExactlyInAnyOrderElementsOf(idsOf(sentToA));
    assertThat(idsOfB).containsExactlyInAnyOrderElementsOf(idsOf(sentToB));

    // 다른 사람의 번호를 after 로 써도 비이의 목록에 에이의 메시지가 섞이지 않습니다.
    String bodyWithRange =
        list(b, "?after=" + sentToA.get(0).getId()).andReturn().getResponse().getContentAsString();
    List<String> idsInRange = JsonPath.read(bodyWithRange, "$[*].id");
    assertThat(idsInRange).containsExactlyInAnyOrderElementsOf(idsOf(sentToB));
  }

  @Test
  @DisplayName("읽음 표시: 읽은 시각이 저장되고, 목록에도 읽음으로 나오고, 또 눌러도 처음 읽은 시각이 그대로다")
  void markReadFlow() throws Exception {
    List<Message> sent = send(a, 2);
    Long target = sent.get(1).getId();

    String firstBody =
        markRead(a, target)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(String.valueOf(target)))
            .andExpect(jsonPath("$.read").value(true))
            .andExpect(jsonPath("$.readAt").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long firstReadAt = ((Number) JsonPath.read(firstBody, "$.readAt")).longValue();
    // DB 에 읽은 시각이 저장됐습니다.
    assertThat(messageRepository.findById(target).orElseThrow().getReadAt()).isNotNull();

    // 목록: 읽은 것은 read true, 안 읽은 것은 false
    list(a, "")
        .andExpect(jsonPath("$[0].read").value(true))
        .andExpect(jsonPath("$[0].readAt").value(firstReadAt))
        .andExpect(jsonPath("$[1].read").value(false));

    // 또 눌러도 에러가 아니고 처음 읽은 시각이 그대로입니다.
    markRead(a, target)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").value(firstReadAt));
  }

  @Test
  @DisplayName("다른 사용자의 메시지는 읽음 표시를 해도 404 이고, 그 메시지는 그대로 안 읽은 상태다")
  void cannotMarkOthersMessageRead() throws Exception {
    Message others = send(b, 1).get(0);

    markRead(a, others.getId())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("메시지를 찾을 수 없어요"));

    assertThat(messageRepository.findById(others.getId()).orElseThrow().isRead()).isFalse();
    // 주인은 정상적으로 읽음 표시를 할 수 있습니다.
    markRead(b, others.getId()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("없는 번호나 숫자가 아닌 번호는 404 와 400")
  void missingAndBadIds() throws Exception {
    markRead(a, 999_999L)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mockMvc
        .perform(with(patch("/api/v1/messages/abc/read"), a.token(), null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    list(a, "?after=abc")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("탈퇴하면 그 사람의 메시지도 모두 지워지고, 다른 사람의 메시지는 남는다")
  void withdrawDeletesMessages() throws Exception {
    send(a, 3);
    Message ofB = send(b, 1).get(0);

    mockMvc
        .perform(
            with(post("/api/v1/auth/withdraw"), a.token(), "{\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isNoContent());

    assertThat(
            messageRepository.findByUserIdAndIdGreaterThanAndIdLessThanOrderByIdDesc(
                a.userId(), 0L, Long.MAX_VALUE, PageRequest.of(0, 100)))
        .isEmpty();
    assertThat(messageRepository.findById(ofB.getId())).isPresent();
  }

  @Test
  @DisplayName("내 컴퓨터의 테스트 토큰으로도 불러지고, 메시지가 없으면 빈 배열이다")
  void localTestToken() throws Exception {
    mockMvc
        .perform(get("/api/v1/messages").header("Authorization", "Bearer local-test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
