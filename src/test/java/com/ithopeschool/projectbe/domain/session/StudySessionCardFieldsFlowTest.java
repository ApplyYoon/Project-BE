package com.ithopeschool.projectbe.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.CardRepository;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.care.Stage4Service;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlanRepository;
import com.ithopeschool.projectbe.domain.session.dto.SessionEndReportRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionMemoRequest;
import com.ithopeschool.projectbe.domain.session.dto.SessionPauseRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionCreateRequest;
import com.ithopeschool.projectbe.domain.session.dto.StudySessionResponse;
import com.ithopeschool.projectbe.domain.subject.SubjectRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 진짜 DB(H2)에 카드 세션을 저장하고 다시 꺼내서, 딸린 테이블 네 개(일시정지 · 체크한 항목 · 잘 된 이유 · 메모)까지
// 빠짐없이 돌아오는지 확인합니다. (이슈 #224) 옛 기록(카드 칸이 빈 줄)이 그대로 읽히는지도 봅니다.
@DataJpaTest
class StudySessionCardFieldsFlowTest {

  private static final Long ME = 1L;
  private static final long MINUTE = 60_000;

  @Autowired private StudySessionRepository studySessionRepository;
  @Autowired private StudyPlanRepository studyPlanRepository;
  @Autowired private SubjectRepository subjectRepository;
  @Autowired private CardRepository cardRepository;
  @Autowired private TestEntityManager em;

  private StudySessionService service;
  private Long cardId;

  // 한국 시간 2026년 10월 12일(월) 19:00 = 기록의 시작
  private static final Instant START =
      ZonedDateTime.of(2026, 10, 12, 19, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();

  @BeforeEach
  void setUp() {
    CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    service =
        new StudySessionService(
            currentUserProvider,
            studySessionRepository,
            subjectRepository,
            studyPlanRepository,
            cardRepository,
            mock(Stage4Service.class),
            Clock.fixed(START.plusSeconds(6 * 3600), ZoneOffset.UTC));
    // lockUser 가 users 의 줄을 잠그므로 1번 사용자가 있어야 합니다.
    em.getEntityManager()
        .createNativeQuery(
            "insert into users (id, nickname, created_at) values (1, '나', current_timestamp)")
        .executeUpdate();
    cardId =
        em.persistAndFlush(
                new Card(
                    ME,
                    "수학 문제집",
                    LocalDate.of(2026, 10, 12),
                    60,
                    List.of(new ChecklistItem("i1", "42쪽"), new ChecklistItem("i2", "43쪽")),
                    START))
            .getId();
  }

  private long at(int minutes) {
    return START.toEpochMilli() + minutes * MINUTE;
  }

  @Test
  @DisplayName("카드 세션을 저장하고 기간 목록으로 다시 꺼내면 카드 칸이 모두 그대로다")
  void savedCardSessionComesBack() {
    service.create(
        new StudySessionCreateRequest(
            "",
            at(0),
            at(60),
            0,
            null,
            null,
            60,
            null,
            "study",
            String.valueOf(cardId),
            "offline",
            List.of(
                new SessionPauseRequest(at(20), at(25), "toilet", null),
                new SessionPauseRequest(at(40), at(50), "other", "택배")),
            false,
            new SessionEndReportRequest(
                Map.of("i1", at(30), "i2", at(55)),
                null,
                "none",
                new SessionEndReportRequest.Proof("https://example.com/note", null),
                List.of("goodPlan")),
            List.of(new SessionMemoRequest(at(10), "엄마한테 문자")),
            null,
            null));
    em.flush();
    em.clear();

    List<StudySessionResponse> sessions =
        service.getSessions(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));

    assertThat(sessions).hasSize(1);
    StudySessionResponse session = sessions.get(0);
    assertThat(session.cardId()).isEqualTo(String.valueOf(cardId));
    assertThat(session.subjectId()).isEmpty();
    assertThat(session.mode()).isEqualTo("offline");
    assertThat(session.pauseCount()).isEqualTo(2);
    assertThat(session.pausedMs()).isEqualTo(15 * MINUTE);
    assertThat(session.pauses())
        .containsExactly(
            new StudySessionResponse.PauseResponse(at(20), at(25), "toilet", null),
            new StudySessionResponse.PauseResponse(at(40), at(50), "other", "택배"));
    assertThat(session.endedEarly()).isFalse();
    assertThat(session.endReport().checked()).isEqualTo(Map.of("i1", at(30), "i2", at(55)));
    assertThat(session.endReport().withWhat()).isEqualTo("none");
    assertThat(session.endReport().proof().link()).isEqualTo("https://example.com/note");
    assertThat(session.endReport().goodReasons()).containsExactly("goodPlan");
    assertThat(session.memos())
        .containsExactly(new StudySessionResponse.MemoResponse(at(10), "엄마한테 문자"));
  }

  @Test
  @DisplayName("옛 기록(일시정지 횟수만 있고 카드 칸이 빔)은 mode normal · pauses null(모름) · endReport null 로 읽힌다")
  void oldSessionStillReads() {
    em.persistAndFlush(
        new StudySession(
            ME,
            3L,
            Instant.ofEpochMilli(at(0)),
            Instant.ofEpochMilli(at(50)),
            2,
            300_000,
            null,
            null,
            null,
            PlanKind.STUDY));
    em.clear();

    StudySessionResponse session =
        service.getSessions(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)).get(0);

    assertThat(session.pauseCount()).isEqualTo(2);
    assertThat(session.pauses()).isNull();
    assertThat(session.mode()).isEqualTo("normal");
    assertThat(session.endedEarly()).isFalse();
    assertThat(session.endReport()).isNull();
    assertThat(session.memos()).isNull();
    assertThat(session.cardId()).isNull();
  }
}
