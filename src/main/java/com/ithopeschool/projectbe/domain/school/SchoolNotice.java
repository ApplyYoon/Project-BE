package com.ithopeschool.projectbe.domain.school;

import java.time.LocalDate;

// 학교 홈페이지 게시판의 글 하나입니다. 제목 · 날짜 · 게시판 종류 · 글 주소만 담습니다. (SchoolHomepageCrawler)
// 작성자 · 본문 · 첨부 파일은 가져오지 않습니다.
//   kind: "가정통신문" 또는 "공지사항"
//   url : 글을 여는 주소. 만들 수 없으면 null 입니다.
public record SchoolNotice(String title, LocalDate date, String kind, String url) {}
