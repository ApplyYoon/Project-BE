package com.ithopeschool.projectbe.domain.gpt;

import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// GPT 비용 지키기 (이슈 #33). GPT 를 부르기 전에 "오늘 더 불러도 되는지" 보고, 부른 것을 기록합니다.
// 음성 AI 는 분당 과금이라, 버그 하나나 장난 요청으로 한 달 예산이 사라질 수 있습니다.
//
// 한도는 모두 설정(환경변수)에서 받습니다. (application.yml 의 app.ai)
//   하루 통화 횟수         AI_CALL_DAILY_LIMIT_PER_USER     (처음 값 3번)
//   하루 통화 시간         AI_CALL_DAILY_MINUTES_PER_USER   (처음 값 15분)
//   통화 한 번 최대 길이   AI_CALL_MAX_SECONDS              (처음 값 300초 = 5분)
//   하루 글 호출 횟수      AI_DAILY_LIMIT_PER_USER          (처음 값 20번, 통화 요약 + 코치 + 계획 초안을 합쳐서)
//   하루 작은 AI 기능 횟수 AI_ASSIST_DAILY_LIMIT_PER_USER   (처음 값 40번, 체크리스트 추천 등을 합쳐서. 이슈 #237)
//
// "하루" 는 새벽 4시(한국 시간)에 바뀝니다. (DayRule)
// 아직 끝나지 않은 통화(FE 가 마무리를 부르지 않음)는 최대 길이만큼 썼다고 봅니다. 비용은 넉넉하게 잡는 쪽이 안전하기 때문입니다.
//
// 비용은 "대략" 입니다. 요금은 달러라서 대략 환산한 원 값을 설정으로 받습니다. 요금이 바뀌면 설정만 고치면 됩니다.
@Component
public class GptUsageGuard {

  // OpenAI 가 음성 통화(Realtime) 하나를 끊는 최대 길이(초). 60분입니다. (docs/DEPLOY.md)
  static final int OPENAI_MAX_SESSION_SECONDS = 60 * 60;

  // 글 호출 한도(하루 20번)를 함께 쓰는 종류입니다.
  static final List<GptUsageKind> TEXT_KINDS =
      List.of(GptUsageKind.CALL_REVIEW, GptUsageKind.COACH, GptUsageKind.PLAN_PARSE);
  // 작은 AI 기능(입력 칸을 AI 가 먼저 채우기, 이슈 #236·#237)의 종류입니다. 글 호출과 한도를 따로 셉니다. (하루 40번)
  //   카드를 만들 때마다 부르는 기능이라 자주 쓰입니다. 같이 세면 코치·통화 요약이 한도에 막힙니다.
  static final List<GptUsageKind> ASSIST_KINDS =
      List.of(GptUsageKind.CHECKLIST, GptUsageKind.ASSIST);

  private final GptUsageRepository gptUsageRepository;
  private final Clock clock;
  private final int callDailyLimit;
  private final int callDailyMinutes;
  private final int maxCallSeconds;
  private final int textDailyLimit;
  private final int assistDailyLimit;
  private final int costWonPerCallMinute;
  private final int costWonPerTextCall;

  public GptUsageGuard(
      GptUsageRepository gptUsageRepository,
      Clock clock,
      @Value("${app.ai.call-daily-limit-per-user:3}") int callDailyLimit,
      @Value("${app.ai.call-daily-minutes-per-user:15}") int callDailyMinutes,
      @Value("${app.ai.call-max-seconds:300}") int maxCallSeconds,
      @Value("${app.ai.daily-limit-per-user:20}") int textDailyLimit,
      @Value("${app.ai.assist-daily-limit-per-user:40}") int assistDailyLimit,
      @Value("${app.ai.cost-won-per-call-minute:80}") int costWonPerCallMinute,
      @Value("${app.ai.cost-won-per-text-call:1}") int costWonPerTextCall) {
    this.gptUsageRepository = gptUsageRepository;
    this.clock = clock;
    this.callDailyLimit = callDailyLimit;
    this.callDailyMinutes = callDailyMinutes;
    this.maxCallSeconds = maxCallSeconds;
    this.textDailyLimit = textDailyLimit;
    this.assistDailyLimit = assistDailyLimit;
    this.costWonPerCallMinute = costWonPerCallMinute;
    this.costWonPerTextCall = costWonPerTextCall;
  }

  // 지금 통화를 하나 더 걸면, 그 통화를 최대 몇 초까지 해도 되는지 돌려줍니다. 0 이면 오늘은 더 걸 수 없습니다.
  //   오늘 3번 다 걸었음                      →  0
  //   오늘 13분 썼음 (한도 15분)               →  120  (남은 2분만)
  //   오늘 5분 썼음                            →  300  (한 번 최대 길이 5분)
  @Transactional(readOnly = true)
  public int allowedCallSeconds(Long userId) {
    List<GptUsage> todayCalls =
        gptUsageRepository.findByUserIdAndKindAndCalledAtGreaterThanEqual(
            userId, GptUsageKind.CALL, startOfToday());
    if (todayCalls.size() >= callDailyLimit) {
      return 0;
    }

    int usedSeconds = 0;
    for (GptUsage call : todayCalls) {
      // 아직 끝나지 않은 통화는 최대 길이만큼 쓴 것으로 셉니다.
      usedSeconds += call.isCallFinished() ? call.getCallSeconds() : maxCallSeconds;
    }
    int remainingSeconds = callDailyMinutes * 60 - usedSeconds;
    return Math.max(0, Math.min(maxCallSeconds, remainingSeconds));
  }

  // 통화를 시작했다고 기록합니다. (통화 시간과 비용은 끝날 때 채웁니다)
  @Transactional
  public void recordCallStart(Long userId, Long callRecordId) {
    gptUsageRepository.save(GptUsage.callStarted(userId, clock.instant(), callRecordId));
  }

  // 통화가 끝났을 때 통화 시간과 비용을 채웁니다. 이미 채웠으면(마무리를 다시 부름) 그대로 둡니다.
  // 통화 시간은 시작 ~ 끝입니다.
  //   ⚠️ 5분(AI_CALL_MAX_SECONDS)으로 자르지 않습니다. 통화를 끊는 것은 FE 라서, 브라우저를 고쳐 쓰면 더 길게 통화할 수 있습니다.
  //      그런 통화를 5분으로 적으면 실제 비용이 숨고 하루 한도도 안 지켜집니다. (docs/DEPLOY.md)
  //      대신 OpenAI 가 통화를 끊는 최대 길이(60분)로만 자릅니다. 마무리를 몇 시간 뒤에 불러도 그 이상은 통화했을 수 없어서입니다.
  // 비용은 분당 비용 × 통화한 분이고, 원 단위로 올림합니다.
  @Transactional
  public void recordCallFinish(
      Long userId, Long callRecordId, Instant startedAt, Instant finishedAt) {
    gptUsageRepository
        .findByCallRecordIdAndUserId(callRecordId, userId)
        .filter(usage -> !usage.isCallFinished())
        .ifPresent(
            usage -> {
              long seconds = Duration.between(startedAt, finishedAt).toSeconds();
              int callSeconds = (int) Math.max(0, Math.min(OPENAI_MAX_SESSION_SECONDS, seconds));
              int costWon = (int) Math.ceil(callSeconds * costWonPerCallMinute / 60.0);
              usage.finishCall(callSeconds, costWon);
            });
  }

  // 글을 받는 GPT(통화 요약·코치·작은 AI 기능)를 한 번 더 불러도 되는지 보고, 되면 기록합니다. 한도를 넘었으면 false 입니다.
  // 한도는 두 묶음으로 따로 셉니다.
  //   통화 요약 · 코치 · 계획 초안 (TEXT_KINDS)   →  합쳐서 하루 20번
  //   작은 AI 기능 (ASSIST_KINDS)                  →  합쳐서 하루 40번
  @Transactional
  public boolean tryUseText(Long userId, GptUsageKind kind) {
    if (kind == GptUsageKind.CALL) {
      throw new IllegalArgumentException("통화는 allowedCallSeconds 와 recordCallStart 를 씁니다");
    }
    boolean assist = ASSIST_KINDS.contains(kind);
    List<GptUsageKind> sameLimitKinds = assist ? ASSIST_KINDS : TEXT_KINDS;
    int dailyLimit = assist ? assistDailyLimit : textDailyLimit;
    long usedToday =
        gptUsageRepository.countByUserIdAndKindInAndCalledAtGreaterThanEqual(
            userId, sameLimitKinds, startOfToday());
    if (usedToday >= dailyLimit) {
      return false;
    }
    gptUsageRepository.save(GptUsage.textCall(userId, kind, clock.instant(), costWonPerTextCall));
    return true;
  }

  // 오늘이 시작된 순간 (한국 시간 새벽 4시)
  private Instant startOfToday() {
    return DayRule.startOf(DayRule.dateOf(clock.instant()));
  }
}
