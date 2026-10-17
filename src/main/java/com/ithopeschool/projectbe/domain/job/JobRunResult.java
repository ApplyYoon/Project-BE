package com.ithopeschool.projectbe.domain.job;

import java.time.Instant;
import java.time.LocalDate;

// 실행기가 Controller 에게 돌려주는 "이번 실행은 이랬다" 는 기록입니다.
public record JobRunResult(String job, JobRunStatus status, LocalDate date, Instant ranAt) {}
