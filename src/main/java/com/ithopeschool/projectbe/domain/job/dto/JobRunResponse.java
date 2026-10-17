package com.ithopeschool.projectbe.domain.job.dto;

// 작업을 불렀을 때의 응답 모양(DTO)입니다.
// job: 실행한 작업 이름 / status: DONE(잘 끝남), SKIPPED(이미 실행 중이라 건너뜀)
// date: 기준 날짜 'YYYY-MM-DD' (하루의 기준 새벽 4시) / ranAt: 실행한 순간(밀리초 숫자, docs/API.md 2-2)
// message: 사람이 읽을 한국어 설명
public record JobRunResponse(String job, String status, String date, long ranAt, String message) {}
