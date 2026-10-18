package com.ithopeschool.projectbe.domain.school.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 내 학교 홈페이지의 최근 가정통신문 · 공지사항 응답입니다. (2026-10-18)
//   { "school": { "code": "7530075", "name": "분당고등학교" },
//     "fetchedAt": 1792310400000,
//     "notices": [ { "title": "2학기 2차 지필평가 시험 범위 안내", "date": "2026-10-15",
//                    "kind": "가정통신문",
//                    "url": "https://…/na/ntt/selectNttInfo.do?mi=20164&bbsId=15687&nttSn=1962073"
// } ] }
// - school 이 null 이면 가입 때 학교를 안 고른 것입니다. notices 는 빈 목록입니다.
// - fetchedAt: 홈페이지에서 마지막으로 잘 받은 시각(밀리초). 못 받았으면 null (홈페이지 모양이 달라 못 읽는 학교도 null)
// - notices: 최근 30일 글, 날짜가 최근인 순서. 게시판마다 10개까지. 작성자 · 본문 · 첨부는 없습니다.
// - url: 글을 여는 학교 홈페이지 주소. 만들 수 없으면 null
public record SchoolNoticesResponse(School school, Long fetchedAt, List<Notice> notices) {

  public record School(String code, String name) {}

  public record Notice(String title, LocalDate date, String kind, String url) {}

  public static SchoolNoticesResponse noSchool() {
    return new SchoolNoticesResponse(null, null, List.of());
  }

  public static SchoolNoticesResponse of(
      String code, String name, Instant fetchedAt, List<Notice> notices) {
    return new SchoolNoticesResponse(
        new School(code, name), fetchedAt == null ? null : fetchedAt.toEpochMilli(), notices);
  }
}
