package com.ithopeschool.projectbe.domain.insight.dto;

import com.ithopeschool.projectbe.domain.insight.FirstHypothesis;
import com.ithopeschool.projectbe.domain.insight.FirstReportCountdown;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// GET /api/v1/insights/hypotheses 의 응답입니다. (이슈 #35)
// hypotheses: 설문 답으로 만든 가설 목록. 답이 없으면 빈 목록 []
// defaultSentence: 가설이 하나도 없을 때 대신 보여줄 문장. 가설이 있으면 null
// firstReport: 14일 차 첫 리포트까지의 카운트다운
public record HypothesesResponse(
    List<Hypothesis> hypotheses, String defaultSentence, FirstReport firstReport) {

  // 가설 하나
  // analysis: 2주 뒤 확인할 분석 이름 ("rhythm" 등) / analysisIssue: 그 분석의 이슈 번호 (37 ~ 40)
  public record Hypothesis(
      String answerType, String answer, String sentence, String analysis, int analysisIssue) {}

  // 첫 리포트 카운트다운
  // firstRecordDate: 첫 기록 날짜 'YYYY-MM-DD' (기록이 없으면 null)
  // totalDays: 첫 리포트까지 필요한 날 (14) / daysPassed: 지난 날 / daysLeft: 남은 날 / ready: 볼 수 있으면 true
  public record FirstReport(
      LocalDate firstRecordDate, int totalDays, int daysPassed, int daysLeft, boolean ready) {}

  public static HypothesesResponse of(
      List<FirstHypothesis.Hypothesis> hypotheses, FirstReportCountdown.Status countdown) {
    List<Hypothesis> items = new ArrayList<>();
    for (FirstHypothesis.Hypothesis hypothesis : hypotheses) {
      items.add(
          new Hypothesis(
              hypothesis.answerType(),
              hypothesis.answer(),
              hypothesis.sentence(),
              hypothesis.analysis().getValue(),
              hypothesis.analysis().getIssue()));
    }
    // 가설이 없을 때만 기본 문장을 담습니다. (이슈 #35 "답이 없으면 가설을 억지로 만들지 않는다")
    String defaultSentence = items.isEmpty() ? FirstHypothesis.DEFAULT_SENTENCE : null;
    FirstReport firstReport =
        new FirstReport(
            countdown.firstRecordDate(),
            FirstReportCountdown.TOTAL_DAYS,
            countdown.daysPassed(),
            countdown.daysLeft(),
            countdown.ready());
    return new HypothesesResponse(items, defaultSentence, firstReport);
  }
}
