package com.ithopeschool.projectbe.domain.school;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 학교 학사일정 한 줄 (NEIS 에서 받아 둔 것). 한 줄 = 한 학교의 하루 행사 하나. (이슈 #282)
// 학생마다가 아니라 학교마다 저장해서, 같은 학교 학생들이 같이 씁니다. (개인정보 없음)
// 같은 학교 · 같은 날 · 같은 이름의 행사는 하나뿐입니다. 새로 받으면 그 기간의 줄을 지우고 다시 넣습니다.
@Entity
@Table(
    name = "school_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_school_events_school_code_event_date_name",
            columnNames = {"school_code", "event_date", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // NEIS 학교 번호 (accounts.school_code 와 같은 값)
  @Column(nullable = false, length = 20)
  private String schoolCode;

  // 행사 날짜
  @Column(nullable = false)
  private LocalDate eventDate;

  // 행사 이름 (예: '2학기 2차 정기시험')
  @Column(nullable = false, length = 100)
  private String name;

  // 행사 내용 (예: '2학기 2차 정기시험(3학년 졸업시험)'). 없으면 비어 있음
  @Column(length = 200)
  private String content;

  // 해당 학년들 (예: '1,2' · 비어 있으면 학년 구분 없음)
  @Column(nullable = false, length = 10)
  private String grades;

  public SchoolEvent(
      String schoolCode, LocalDate eventDate, String name, String content, String grades) {
    this.schoolCode = schoolCode;
    this.eventDate = eventDate;
    this.name = name;
    this.content = content;
    this.grades = grades;
  }
}
