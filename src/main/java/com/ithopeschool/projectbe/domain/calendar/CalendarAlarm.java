package com.ithopeschool.projectbe.domain.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 구글 캘린더 일정 알림 테이블. 한 줄 = 학생의 구글 캘린더 일정 하나 (시작 10분 전에 푸시를 보낼 것). (2026-10-24)
// 사용자: "캘린더 시작 10분 전에 실제 앱처럼 알람 오게", "알림에 일정 제목 넣자"
//
// 서버는 구글에 직접 접속하지 않습니다. FE 가 학생의 브라우저에서 읽은 "앞으로 7일 일정" 을 올려 두면(PUT),
// 1분마다 도는 예약 작업(CalendarAlarmJob)이 시작 10분 전인 일정에 푸시를 보냅니다.
//
// ⚠️ 일정 제목은 알림 글에만 씁니다. 로그에 찍지 않고, GPT 에도 보내지 않습니다.
//    지난 일정은 예약 작업이 하루 뒤에 지우고, 학생이 캘린더 연결을 끊으면(DELETE) 바로 다 지웁니다.
@Entity
// 한 사람의 같은 일정은 한 줄만 둡니다. (올리기가 동시에 두 번 오면 같은 일정이 두 줄이 되어 알림이 두 번 가던 것을 막음)
// 이 규칙의 색인이 user_id 로 찾는 것도 맡고, 예약 작업은 "곧 시작하는 일정" 을 시작 시각 색인으로 찾습니다.
@Table(
    name = "calendar_alarms",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_calendar_alarms_user_id_event_key",
            columnNames = {"user_id", "event_key"}),
    indexes = @Index(name = "idx_calendar_alarms_starts_at", columnList = "starts_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarAlarm {

  // 구글 일정 id 의 최대 글자 수 (반복 일정은 '원래id_20261024T100000Z' 처럼 길어집니다)
  public static final int MAX_EVENT_KEY_LENGTH = 255;
  // 일정 제목의 최대 글자 수. 알림 한 줄에 다 보이지 않아서 FE 가 이만큼으로 잘라 보냅니다.
  public static final int MAX_TITLE_LENGTH = 100;

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 일정의 주인(users.id)
  @Column(nullable = false)
  private Long userId;

  // 구글 캘린더의 일정 id. 같은 일정을 다시 올렸을 때 같은 줄을 찾는 데 씁니다.
  @Column(nullable = false, length = MAX_EVENT_KEY_LENGTH)
  private String eventKey;

  // 일정 제목 (알림 글에 들어감)
  @Column(nullable = false, length = MAX_TITLE_LENGTH)
  private String title;

  // 일정이 시작하는 순간
  @Column(nullable = false)
  private Instant startsAt;

  // 알림을 처리한 시각. 보냈거나, 연락 금지 시간이라 건너뛰었으면 채워집니다. 비어 있으면 아직 안 보낸 것입니다.
  private Instant handledAt;

  public CalendarAlarm(Long userId, String eventKey, String title, Instant startsAt) {
    this.userId = userId;
    this.eventKey = eventKey;
    this.title = title;
    this.startsAt = startsAt;
  }

  // 같은 일정을 다시 받았을 때 고칩니다. 제목은 늘 바꾸고,
  // 시작 시각이 바뀌었으면(구글에서 일정을 옮김) 새 시각에 다시 알리도록 처리 표시를 지웁니다.
  public void update(String newTitle, Instant newStartsAt) {
    this.title = newTitle;
    if (!this.startsAt.equals(newStartsAt)) {
      this.startsAt = newStartsAt;
      this.handledAt = null;
    }
  }

  // 알림을 처리했다고 적습니다. 다음 실행에서 같은 일정을 또 보내지 않게 합니다.
  public void markHandled(Instant at) {
    this.handledAt = at;
  }
}
