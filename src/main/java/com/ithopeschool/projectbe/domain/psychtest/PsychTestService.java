package com.ithopeschool.projectbe.domain.psychtest;

import com.ithopeschool.projectbe.domain.psychtest.dto.PsychTestResponse;
import com.ithopeschool.projectbe.domain.psychtest.dto.PsychTestSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 온보딩 심리검사 결과를 저장하고 꺼내는 로직입니다. (이슈 #219, docs/API.md "심리검사")
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 결과만 봅니다.
@Service
@RequiredArgsConstructor
public class PsychTestService {

  private final CurrentUserProvider currentUserProvider;
  private final PsychTestRepository psychTestRepository;
  private final Clock clock;

  // 결과를 새 줄로 저장합니다. 다시 하면 덮어쓰지 않고 쌓입니다.
  @Transactional
  public PsychTestResponse save(PsychTestSaveRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Map<PsychItem, Integer> answers = checkAnswers(request.answers());
    PsychTest saved = psychTestRepository.save(new PsychTest(userId, clock.instant(), answers));
    return PsychTestResponse.from(saved);
  }

  // 내 가장 최근 결과. 아직 안 했으면 404 입니다.
  @Transactional(readOnly = true)
  public PsychTestResponse getLatest() {
    Long userId = currentUserProvider.getCurrentUserId();
    return psychTestRepository
        .findFirstByUserIdOrderByIdDesc(userId)
        .map(PsychTestResponse::from)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "아직 심리검사를 하지 않았어요"));
  }

  // 답을 확인하고 문항 코드로 바꿉니다. 틀린 것이 있으면 400 이고, 무엇이 틀렸는지 알려 줍니다.
  //   1. 모르는 코드가 있으면 안 됩니다. (S1 ~ S5 … C1 ~ C5 만)
  //   2. 답은 1 ~ 7 의 정수입니다.
  //   3. 문장 35개에 모두 답했거나, 옛 14문항 검사(척도마다 앞의 두 문장)에 모두 답했어야 합니다.
  //      옛 검사를 이 기기에만 저장해 둔 학생의 결과도 서버에 쌓으려고 둘 다 받습니다. 그 밖에 일부만 답한 것은 받지 않습니다.
  private static Map<PsychItem, Integer> checkAnswers(Map<String, Integer> raw) {
    Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
    for (Map.Entry<String, Integer> entry : raw.entrySet()) {
      PsychItem item = PsychItem.fromCode(entry.getKey());
      if (item == null) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "모르는 문항이 있어요: " + entry.getKey());
      }
      Integer answer = entry.getValue();
      if (answer == null || answer < 1 || answer > 7) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "답은 1 ~ 7 사이로 보내 주세요");
      }
      answers.put(item, answer);
    }
    if (!isFullTest(answers.keySet()) && !isOldTest(answers.keySet())) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "문장 " + PsychItem.values().length + "개(옛 검사는 " + OLD_TEST_SIZE + "개)에 모두 답해 주세요");
    }
    return answers;
  }

  // 옛 14문항 검사의 문장 수
  static final int OLD_TEST_SIZE = 14;

  // 문장 35개에 모두 답했는지
  private static boolean isFullTest(Set<PsychItem> answered) {
    return answered.size() == PsychItem.values().length;
  }

  // 옛 14문항 검사의 문장에만, 그리고 그 14개에 모두 답했는지
  private static boolean isOldTest(Set<PsychItem> answered) {
    return answered.size() == OLD_TEST_SIZE && answered.stream().allMatch(PsychItem::isInOldTest);
  }
}
