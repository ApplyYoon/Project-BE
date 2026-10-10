package com.ithopeschool.projectbe.domain.job;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// 작업의 결과를 "작업 이름 + 날짜" 한 칸에 저장해 두는 곳입니다. (연습용: 서버를 끄면 사라지는 메모리 저장소)
//
// 핵심은 save 가 "추가" 가 아니라 "덮어쓰기" 라는 점입니다.
//   같은 날 같은 작업을 두 번 돌려도 → 그 날짜 한 칸의 값이 같은 내용으로 다시 써질 뿐, 칸이 늘어나지 않습니다.
// 진짜 결과(매일 밤 요약표, 이슈 #34)는 DB 에 같은 규칙으로 저장합니다. (날짜가 같으면 덮어쓰기)
@Component
public class JobResultStore {

  // key: "작업이름/날짜"   value: 결과 글자
  // ConcurrentHashMap 은 여러 요청이 동시에 써도 깨지지 않는 Map 입니다.
  private final Map<String, String> results = new ConcurrentHashMap<>();

  // 그날 결과를 저장합니다. 이미 있으면 덮어씁니다.
  public void save(String jobName, LocalDate date, String result) {
    results.put(key(jobName, date), result);
  }

  // 그날 결과를 꺼냅니다. 없으면 빈 값(Optional.empty)입니다.
  public Optional<String> find(String jobName, LocalDate date) {
    return Optional.ofNullable(results.get(key(jobName, date)));
  }

  // 저장된 칸의 개수입니다. (두 번 돌려도 늘어나지 않는지 테스트에서 확인할 때 씁니다)
  public int size() {
    return results.size();
  }

  private String key(String jobName, LocalDate date) {
    return jobName + "/" + date;
  }
}
