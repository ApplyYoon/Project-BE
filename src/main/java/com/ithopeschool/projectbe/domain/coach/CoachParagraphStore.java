package com.ithopeschool.projectbe.domain.coach;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// 한 번 받은 AI 문단을 기억해 두는 곳입니다. (이슈 #87 "한 주에 한 번만 만들고, 저장한 문단을 다시 쓴다")
// 같은 사람이 같은 기간을 "같은 숫자" 로 다시 물으면, GPT 를 또 부르지 않고 기억해 둔 문단을 돌려줍니다.
// GPT 는 부를 때마다 돈이 나가고, 같은 숫자를 주면 비슷한 글이 다시 나올 뿐이기 때문입니다.
//
//   월요일에 지난주 리포트를 열었다          →  GPT 에게 묻고 문단을 기억
//   화요일에 지난주 리포트를 다시 열었다      →  숫자가 그대로라서 기억해 둔 문단 (GPT 를 부르지 않음)
//   이번 주 리포트를 수요일·목요일에 열었다   →  그 사이 기록이 늘어 숫자가 달라졌으면 새로 묻고 바꿔 기억
//
// 숫자가 달라지면 새로 묻는 이유: 옛 문단의 숫자가 화면의 "근거 숫자" 와 달라지면 안 되기 때문입니다.
// 지난 주들은 숫자가 더 바뀌지 않아서, 한 주에 한 번만 만들게 됩니다.
//
// ⚠️ 문단은 서버의 메모리에만 있습니다. 서버가 꺼졌다 켜지면 모두 사라지고, 그러면 한 번씩 다시 묻습니다.
//    DB 에 남기는 일은 호출 기록을 남기는 이슈 #33 에서 같이 합니다. (CoachDailyLimit 과 같은 사정입니다)
// ⚠️ 기억해 둔 숫자와 문단은 로그에 찍지 않습니다.
@Component
public class CoachParagraphStore {

  // 기억해 두는 문단의 최대 개수입니다. 이만큼 차면 전부 비우고 다시 모읍니다. (메모리가 끝없이 늘지 않게)
  static final int MAX_SIZE = 1000;

  // 기억해 둔 문단 하나입니다.
  // signature: 그때 받은 숫자와 한도를 글자로 이어 붙인 것. 다음에 온 것과 같은지 비교하는 데 씁니다.
  // paragraph: AI 가 쓴 문단
  private record Saved(String signature, String paragraph) {}

  // "누가 · 어떤 종류 · 어느 기간" → 기억해 둔 문단
  private final Map<String, Saved> savedByKey = new HashMap<>();

  // 기억해 둔 문단이 "이 숫자·이 한도" 로 만든 것이면 돌려줍니다. 없거나 숫자가 달라졌으면 비어 있습니다.
  // synchronized: 여러 요청이 동시에 와도 한 번에 하나씩만 들어옵니다. (CoachDailyLimit 과 같은 이유)
  //
  // periodKey: 어느 기간인지. 주간은 그 주의 월요일('2026-10-05'), 기간을 받지 않는 어제 한 줄·월간은 빈 글자
  // signature: 이번에 받은 숫자와 한도를 글자로 이어 붙인 것
  public synchronized Optional<String> find(
      Long userId, CoachKind kind, String periodKey, String signature) {
    Saved saved = savedByKey.get(keyOf(userId, kind, periodKey));
    if (saved == null || !saved.signature().equals(signature)) {
      return Optional.empty();
    }
    return Optional.of(saved.paragraph());
  }

  // 문단을 기억합니다. 같은 사람·종류·기간의 옛 문단이 있으면 새것으로 바꿉니다.
  public synchronized void save(
      Long userId, CoachKind kind, String periodKey, String signature, String paragraph) {
    String key = keyOf(userId, kind, periodKey);
    if (!savedByKey.containsKey(key) && savedByKey.size() >= MAX_SIZE) {
      savedByKey.clear();
    }
    savedByKey.put(key, new Saved(signature, paragraph));
  }

  //   (7번 사용자, WEEKLY, "2026-10-05")  →  "7|WEEKLY|2026-10-05"
  private static String keyOf(Long userId, CoachKind kind, String periodKey) {
    return userId + "|" + kind + "|" + periodKey;
  }
}
