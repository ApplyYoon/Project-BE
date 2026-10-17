package com.ithopeschool.projectbe.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 잠금 대상 앱 테이블. 한 줄 = 한 사용자가 고른 잠금 대상 앱 하나. (이슈 #22)
// 같은 사용자 안에서는 같은 앱을 두 번 넣을 수 없습니다. (이름이 같아도 사용자가 다르면 괜찮습니다)
@Entity
@Table(
    name = "focus_lock_apps",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_focus_lock_apps_user_id_app_name",
            columnNames = {"user_id", "app_name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FocusLockApp {

  // 앱 이름의 최대 글자 수
  public static final int MAX_APP_NAME_LENGTH = 30;

  // 한 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 앱을 고른 사람(users.id)
  @Column(nullable = false)
  private Long userId;

  // 잠글 앱 이름. 예: "유튜브"
  @Column(nullable = false, length = MAX_APP_NAME_LENGTH)
  private String appName;

  public FocusLockApp(Long userId, String appName) {
    this.userId = userId;
    this.appName = appName;
  }
}
