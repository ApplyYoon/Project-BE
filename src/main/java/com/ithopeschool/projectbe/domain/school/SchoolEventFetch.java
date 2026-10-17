package com.ithopeschool.projectbe.domain.school;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 학교마다 NEIS 학사일정을 마지막으로 받은 때. 한 줄 = 한 학교. (이슈 #282)
// 받은 지 오래됐는지 보고 NEIS 를 다시 부를지 정합니다. (SchoolEventService)
@Entity
@Table(name = "school_event_fetches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolEventFetch {

  // NEIS 학교 번호
  @Id
  @Column(length = 20)
  private String schoolCode;

  // 교육청 코드 (예: 'J10'). 학사일정을 부를 때 학교 번호와 같이 필요해서 한 번 찾은 뒤 저장해 둡니다.
  @Column(nullable = false, length = 10)
  private String officeCode;

  // 마지막으로 NEIS 에서 잘 받은 시각
  @Column(nullable = false)
  private Instant fetchedAt;

  public SchoolEventFetch(String schoolCode, String officeCode, Instant fetchedAt) {
    this.schoolCode = schoolCode;
    this.officeCode = officeCode;
    this.fetchedAt = fetchedAt;
  }

  // 새로 받았을 때 시각을 고칩니다.
  public void markFetched(Instant fetchedAt) {
    this.fetchedAt = fetchedAt;
  }
}
