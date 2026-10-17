package com.ithopeschool.projectbe.domain.checkin;

import com.ithopeschool.projectbe.domain.care.MissReason;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 체크인 테이블. 한 줄 = 한 사람의 하루치 체크인 하나. (이슈 #24)
// 같은 날을 다시 보내면 새 줄을 만들지 않고 그 줄을 덮어씁니다.
// 그래서 한 사람에게 같은 날짜의 줄은 하나뿐입니다. (user_id, check_in_date) 가 겹치지 않게 막아 두었습니다.
//
// 누구 · 날짜 · source 는 서버가 정해서 항상 있습니다.
// 나머지는 학생이 "답하는" 칸이라 모두 비어 있어도 됩니다. 비어 있는 칸은 "0" 이 아니라 "모름" 입니다.
//
// 글(기타 원인, 약속)이 들어오는 곳이라 위기 표현 감지(#56)와 암호화(#68) 대상입니다. 내용은 로그에 찍지 않습니다.
//
// 하루 첫 태그의 감정(여러 개)은 한 줄에 여러 개라서 딸린 테이블(@ElementCollection)에 담습니다. (이슈 #220)
//   checkin_emotions : (checkin_id, emotion)  고른 감정마다 한 줄
@Entity
@Table(
    name = "checkins",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_checkins_user_id_check_in_date",
            columnNames = {"user_id", "check_in_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckIn {

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 체크인의 주인(users.id). 다른 사람의 데이터가 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어느 날의 체크인인지. (date 는 DB 예약어라 checkInDate 라고 지었습니다)
  @Column(nullable = false)
  private LocalDate checkInDate;

  // 어디서 만든 체크인인지 (직접 입력 / 통화 / 1탭)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CheckInSource source;

  // 오늘 컨디션 (1 ~ 5). 안 골랐으면 비어 있습니다.
  private Integer condition;

  // 오늘 기분 (1 ~ 5, 1 이 가장 처짐). 안 골랐으면 비어 있습니다.
  private Integer mood;

  // 어제 계획을 못 채운 이유. 못 채운 날에만 고르고, 안 골랐으면 비어 있습니다.
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private MissReason missReason;

  // 이유를 '기타' 로 골랐을 때 적은 한 줄 (50자까지). '기타' 가 아니면 항상 비어 있습니다.
  @Column(length = 50)
  private String missReasonNote;

  // 오늘 예상 순공시간(분). 안 적었으면 비어 있습니다.
  private Integer expectedMinutes;

  // 오늘의 약속 앞부분 "만약 ~하면" (100자까지). 약속을 안 정했으면 비어 있습니다.
  @Column(length = 100)
  private String promiseIf;

  // 오늘의 약속 뒷부분 "~한다" (100자까지). 약속을 안 정했으면 비어 있습니다.
  @Column(length = 100)
  private String promiseThen;

  // 개선안을 실천했는지 (예 / 아니오). 안 골랐으면 비어 있습니다.
  private Boolean suggestionKept;

  // 하루 첫 태그에서 고른 감정들 (막막함 · 불안함 · 피곤함 · 짜증남 · 평온함 · 의욕 있음). 안 골랐으면 비어 있습니다.
  // EAGER: 체크인을 꺼낼 때 늘 같이 보여 주기 때문에 한 번에 읽어 옵니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "checkin_emotions", joinColumns = @JoinColumn(name = "checkin_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "emotion", length = 20, nullable = false)
  private Set<EmotionTag> emotions = EnumSet.noneOf(EmotionTag.class);

  public CheckIn(
      Long userId,
      LocalDate checkInDate,
      CheckInSource source,
      Integer condition,
      Integer mood,
      MissReason missReason,
      String missReasonNote,
      Integer expectedMinutes,
      String promiseIf,
      String promiseThen,
      Boolean suggestionKept) {
    this.userId = userId;
    this.checkInDate = checkInDate;
    overwrite(
        source,
        condition,
        mood,
        missReason,
        missReasonNote,
        expectedMinutes,
        promiseIf,
        promiseThen,
        suggestionKept);
  }

  // 같은 날을 다시 보냈을 때, 새로 받은 값으로 전부 바꿉니다.
  // 이번에 안 보낸 칸은 비워집니다. 예전 값을 남기지 않고 "마지막에 보낸 그대로" 가 그날의 체크인입니다.
  public void overwrite(
      CheckInSource source,
      Integer condition,
      Integer mood,
      MissReason missReason,
      String missReasonNote,
      Integer expectedMinutes,
      String promiseIf,
      String promiseThen,
      Boolean suggestionKept) {
    this.source = source;
    this.condition = condition;
    this.mood = mood;
    this.missReason = missReason;
    this.missReasonNote = missReasonNote;
    this.expectedMinutes = expectedMinutes;
    this.promiseIf = promiseIf;
    this.promiseThen = promiseThen;
    this.suggestionKept = suggestionKept;
  }

  // 고른 감정을 받은 것으로 전부 바꿉니다. 빈 목록이면 감정이 비워집니다. (덮어쓰기와 같은 "마지막에 보낸 그대로")
  // AI 통화가 만드는 체크인(CallCheckIn)은 감정을 묻지 않아서 이 메서드를 부르지 않습니다.
  public void replaceEmotions(Set<EmotionTag> newEmotions) {
    this.emotions.clear();
    this.emotions.addAll(newEmotions);
  }
}
