package com.ithopeschool.projectbe.domain.call;

import com.ithopeschool.projectbe.domain.call.dto.CallFinishRequest;
import com.ithopeschool.projectbe.domain.call.dto.CallFinishResponse;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 통화 기록을 마무리하는 로직입니다. (이슈 #29, docs/API.md "AI 통화 마무리")
//
// 순서
//   1. 로그인한 사람인지 확인한다                     (아니면 401)
//   2. 내 통화 기록인지 찾는다                        (없거나 남의 것이면 404)
//   3. 이미 마무리한 통화인지 본다                    (그렇다면 409)
//   4. 학생 말에 위기 표현이 있는지 단어 목록으로 본다 (있으면 내용 없이 "감지됨" 만 남기고 도움 안내)
//   5. 학생이 한마디도 안 했으면 GPT 를 부르지 않고 빈 기록으로 마무리한다
//   6. GPT 에게 자막을 정해진 칸으로 정리해 달라고 한다 (꺼져 있거나 실패하면 503, 기록은 그대로라 다시 시도할 수 있음)
//   7. 받은 칸을 검사한다                              (CallReview: 정해진 값이 아니면 그 칸은 비움)
//   8. GPT 도 위기 표현이라고 했으면 4번과 같이 한다   (단어 목록과 GPT 중 하나라도 걸리면 감지. 놓치는 쪽이 더 위험)
//   9. 요약·보인 생각 습관·약속을 저장하고 결론을 돌려준다
//
// ⚠️ 자막은 정리하는 데만 쓰고 저장하지 않습니다. 자막과 정리한 내용은 로그에 찍지 않습니다. (CLAUDE.md 4장)
@Service
@RequiredArgsConstructor
public class CallRecordService {

  private final CurrentUserProvider currentUserProvider;
  private final CallRecordRepository callRecordRepository;
  private final CallReviewClient callReviewClient;
  private final Clock clock;

  // @Transactional 을 붙이지 않은 이유: 중간에 GPT 를 최대 20초 기다립니다.
  // 그동안 DB 연결을 붙잡고 있지 않도록, 찾기와 저장을 따로 합니다. (repository 의 메서드는 각각 알아서 트랜잭션을 엽니다)
  public CallFinishResponse finish(Long callId, CallFinishRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    CallRecord record =
        callRecordRepository
            .findByIdAndUserId(callId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "통화 기록을 찾을 수 없어요"));
    if (record.isFinished()) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 마무리한 통화예요");
    }

    Instant now = clock.instant();
    List<String> studentLines = studentLinesOf(request.lines());

    // 4) 위기 표현 (단어 목록)
    if (CrisisText.hasCrisisText(studentLines)) {
      return finishWithCrisis(record, now);
    }

    // 5) 학생이 한마디도 하지 않은 통화
    if (studentLines.isEmpty()) {
      return finishWith(record, now, CallReview.empty());
    }

    // 6) GPT 에게 정리 부탁
    if (!callReviewClient.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI 정리가 아직 켜져 있지 않아요");
    }
    Optional<CallReview.Raw> raw =
        callReviewClient.review(
            CallReviewPrompt.instructions(),
            CallReviewPrompt.input(request.lines()),
            CallReviewPrompt.schema());
    if (raw.isEmpty()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "통화를 정리하지 못했어요. 잠시 후 다시 시도해 주세요");
    }

    // 7) 칸마다 검사
    CallReview review = CallReview.from(raw.get());

    // 8) 위기 표현 (GPT 판단)
    if (review.crisis()) {
      return finishWithCrisis(record, now);
    }

    // 9) 저장
    return finishWith(record, now, review);
  }

  // 정리한 값을 저장하고 결론을 돌려줍니다.
  private CallFinishResponse finishWith(CallRecord record, Instant now, CallReview review) {
    record.finish(
        now,
        review.summary(),
        review.foundBias(),
        review.foundEvidence(),
        review.actualQuestion(),
        review.missReason(),
        review.promiseIf(),
        review.promiseThen(),
        review.promiseKept());
    callRecordRepository.save(record);

    return new CallFinishResponse(
        String.valueOf(record.getId()),
        false,
        review.summary(),
        review.foundBias() == null
            ? null
            : CallFinishResponse.Bias.of(review.foundBias(), review.foundEvidence()),
        review.missReason() == null ? null : CallCard.missReasonCode(review.missReason()),
        review.promiseIf(),
        review.promiseThen(),
        review.promiseKept(),
        review.mood(),
        review.expectedMinutes());
  }

  // 위기 표현이 감지된 통화: 내용은 남기지 않고 "감지됨" 만 남깁니다. 다음 통화는 7일 동안 위로하는 통화가 됩니다. (CallService)
  private CallFinishResponse finishWithCrisis(CallRecord record, Instant now) {
    record.finishWithCrisis(now);
    callRecordRepository.save(record);
    return CallFinishResponse.help(String.valueOf(record.getId()));
  }

  // 학생(me)이 한 말만 꺼냅니다. 빈칸뿐인 줄은 뺍니다.
  private static List<String> studentLinesOf(List<CallFinishRequest.Line> lines) {
    List<String> studentLines = new ArrayList<>();
    for (CallFinishRequest.Line line : lines) {
      if ("me".equals(line.speaker()) && !line.text().isBlank()) {
        studentLines.add(line.text());
      }
    }
    return studentLines;
  }
}
