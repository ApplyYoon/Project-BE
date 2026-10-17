package com.ithopeschool.projectbe.domain.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// focus_lock_apps 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 앱이 섞이지 않습니다.
public interface FocusLockAppRepository extends JpaRepository<FocusLockApp, Long> {

  // 내 잠금 대상 앱을 넣은 순서(번호 순서)대로 찾습니다. 없으면 빈 목록입니다.
  List<FocusLockApp> findByUserIdOrderById(Long userId);

  // 내 잠금 대상 앱을 모두 지웁니다. (목록을 통째로 바꿀 때 먼저 지우고 새로 넣습니다)
  //
  // 왜 deleteByUserId 대신 @Query 로 직접 쓰나요?
  // 이름만으로 만든 삭제는 "나중에 한꺼번에" 지워서, 같은 앱을 다시 넣으면 지우기 전에 넣으려다
  // "같은 앱은 한 번만" 규칙에 걸릴 수 있습니다. 이 쿼리는 부르는 즉시 DB 에서 지웁니다.
  // 사용자 번호는 SQL 글자에 붙이지 않고 파라미터(:userId)로 넘깁니다.
  @Modifying
  @Query("delete from FocusLockApp a where a.userId = :userId")
  int deleteAllOfUser(@Param("userId") Long userId);
}
