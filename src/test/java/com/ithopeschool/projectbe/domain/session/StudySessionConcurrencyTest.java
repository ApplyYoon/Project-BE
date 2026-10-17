package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// "같은 기록 저장이 동시에 여러 번 와도 하나만 저장되는지" 를 진짜 DB(H2)로 확인합니다. (이슈 #13 "겹치는 기록은 거절")
// 정지 버튼을 두 번 누르거나, 인터넷이 끊겨 FE 가 다시 보내면 같은 요청이 거의 동시에 도착합니다.
//
// 여러 일꾼(스레드)이 출발 신호(CountDownLatch)를 기다렸다가 한꺼번에 저장을 부릅니다.
// 지금 요청한 사람은 토큰 대신 가짜 CurrentUserProvider 로 정합니다. (스레드에는 요청 정보가 없어서)
@SpringBootTest
class StudySessionConcurrencyTest {

  private static final int REQUESTS = 4;

  @Autowired private StudySessionService studySessionService;
  @Autowired private StudySessionRepository studySessionRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private UserRepository userRepository;

  @MockitoBean private CurrentUserProvider currentUserProvider;

  private Long userId;
  private Long subjectId;

  @BeforeEach
  void createUserAndSubject() {
    userId = userRepository.save(new User("동시저장")).getId();
    subjectId = subjectRepository.save(new Subject(userId, "수학", SubjectColor.BLUE)).getId();
    given(currentUserProvider.getCurrentUserId()).willReturn(userId);
  }

  @AfterEach
  void deleteCreatedRows() {
    // 이 테스트가 만든 줄만 지웁니다. (다른 테스트의 데이터는 건드리지 않음)
    studySessionRepository.deleteAll(
        studySessionRepository.findAll().stream()
            .filter(session -> session.getUserId().equals(userId))
            .toList());
    subjectRepository.deleteById(subjectId);
    userRepository.deleteById(userId);
  }

  @Test
  @DisplayName("똑같은 기록 저장이 4번 동시에 와도 1번만 저장되고, 나머지 3번은 409 다")
  void sameRequestAtOnce() throws Exception {
    // 10분짜리 기록 (1시간 전에 시작)
    long start = Instant.now().minusSeconds(3600).toEpochMilli();
    StudySessionCreateRequest request =
        new StudySessionCreateRequest(
            String.valueOf(subjectId), start, start + 600_000, 0, null, null, null, null, null);

    CountDownLatch go = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(REQUESTS);
    List<Future<String>> results = new ArrayList<>();
    for (int i = 0; i < REQUESTS; i++) {
      results.add(
          workers.submit(
              () -> {
                go.await();
                try {
                  studySessionService.create(request);
                  return "saved";
                } catch (ApiException e) {
                  return e.getErrorCode().name();
                }
              }));
    }
    go.countDown(); // 출발!

    List<String> answers = new ArrayList<>();
    for (Future<String> result : results) {
      answers.add(result.get());
    }
    workers.shutdown();

    assertThat(answers).containsExactlyInAnyOrder("saved", "CONFLICT", "CONFLICT", "CONFLICT");
    assertThat(studySessionRepository.findAll())
        .filteredOn(session -> session.getUserId().equals(userId))
        .hasSize(1);
  }
}
