package com.ithopeschool.projectbe.domain.job;

import java.time.Instant;
import java.time.LocalDate;

// 작업이 실행될 때 실행기가 건네주는 정보입니다.
// date: 이 실행이 "몇 월 며칠" 의 일인지. 하루의 기준(새벽 4시, 한국 시간)을 따릅니다. (DayRule)
// startedAt: 실행을 시작한 순간
public record JobContext(LocalDate date, Instant startedAt) {}
