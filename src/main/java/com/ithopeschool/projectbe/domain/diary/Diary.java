package com.ithopeschool.projectbe.domain.diary;

import com.ithopeschool.projectbe.domain.care.MissReason;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 일기 테이블. 한 줄 = 학생이 쓴 일기 하나. 같은 날에 여러 개를 써도 됩니다. (이슈 #60, 기획서 v2 2장 「선택 수집」)
//
// ⚠️ 원문(content)은 본인만 봅니다. 나중에 학부모 공유가 생겨도 원문은 빼고 보냅니다.
//    내용은 로그에 찍지 않습니다. 저장 전에 위기 표현 감지(#56)를 거칩니다.
//
// GPT 가 원문에서 뽑은 것은 한 일기에 여러 개라서 딸린 테이블(@ElementCollection)에 담습니다.
//   diary_emotions : (diary_id, emotion)  감정 — 체크인의 감정 태그(EmotionTag)와 같은 값
//   diary_causes   : (diary_id, cause)    실패 원인 — 체크인의 못 채운 이유(MissReason)와 같은 값
//   diary_keywords : (diary_id, keyword)  반복해서 나오는 짧은 낱말
// 딸린 테이블은 일기에 붙어 있어서, 일기를 지우면 JPA 가 뽑은 데이터도 같이 지웁니다.
@Entity
// 목록은 늘 "내 일기 · 기간" 으로 찾아서 (user_id, diary_date) 색인을 둡니다.
@Table(
    name = "diaries",
    indexes = @Index(name = "idx_diaries_user_id_diary_date", columnList = "user_id, diary_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

  // 원문은 이 글자 수까지 받습니다. (DB 칸 길이와 요청 검사가 같은 값을 씁니다)
  public static final int MAX_CONTENT_LENGTH = 2000;
  // 키워드 하나의 최대 글자 수
  public static final int MAX_KEYWORD_LENGTH = 20;

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 일기의 주인(users.id). 원문을 본인만 보게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 어느 날의 일기인지. 쓴 시각을 하루 기준(새벽 4시, DayRule)으로 바꾼 날짜입니다.
  @Column(nullable = false)
  private LocalDate diaryDate;

  // 학생이 쓴 일기 원문 (2000자까지). 본인에게만 보여 줍니다.
  @Column(nullable = false, length = MAX_CONTENT_LENGTH)
  private String content;

  // 일기를 저장한 시각
  @Column(nullable = false)
  private Instant writtenAt;

  // AI 분석을 마친 시각. 분석하지 않았으면(동의 안 함 · 위기 표현 · AI 꺼짐 · 실패) 비어 있습니다.
  // 비어 있으면 아래 감정·원인·키워드가 비어 있어도 "없음" 이 아니라 "모름" 입니다.
  private Instant analyzedAt;

  // GPT 가 뽑은 감정들. EAGER: 일기를 꺼낼 때 늘 같이 보여 주기 때문에 한 번에 읽어 옵니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "diary_emotions", joinColumns = @JoinColumn(name = "diary_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "emotion", length = 20, nullable = false)
  private Set<EmotionTag> emotions = EnumSet.noneOf(EmotionTag.class);

  // GPT 가 뽑은 실패 원인들. 체크인 원인과 같은 값이라 실패 원인 분석(#39)에 같이 셀 수 있습니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "diary_causes", joinColumns = @JoinColumn(name = "diary_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "cause", length = 20, nullable = false)
  private Set<MissReason> causes = EnumSet.noneOf(MissReason.class);

  // GPT 가 뽑은 반복 키워드들 (하나에 20자까지)
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "diary_keywords", joinColumns = @JoinColumn(name = "diary_id"))
  @Column(name = "keyword", length = MAX_KEYWORD_LENGTH, nullable = false)
  private Set<String> keywords = new LinkedHashSet<>();

  public Diary(Long userId, LocalDate diaryDate, String content, Instant writtenAt) {
    this.userId = userId;
    this.diaryDate = diaryDate;
    this.content = content;
    this.writtenAt = writtenAt;
  }

  // AI 분석 결과를 담습니다. 받은 값으로 전부 바꾸고 분석한 시각을 남깁니다.
  public void recordAnalysis(
      Set<EmotionTag> newEmotions,
      Set<MissReason> newCauses,
      Set<String> newKeywords,
      Instant analyzedAt) {
    this.emotions.clear();
    this.emotions.addAll(newEmotions);
    this.causes.clear();
    this.causes.addAll(newCauses);
    this.keywords.clear();
    this.keywords.addAll(newKeywords);
    this.analyzedAt = analyzedAt;
  }
}
