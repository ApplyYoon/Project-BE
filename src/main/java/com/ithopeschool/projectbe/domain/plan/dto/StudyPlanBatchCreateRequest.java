package com.ithopeschool.projectbe.domain.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// "계획 여러 개 한 번에 추가" 요청의 모양입니다. ("어제 계획 가져오기", "말로 계획 세우기" 가 씁니다)
//   { "plans": [ { 계획 추가와 같은 모양 }, { ... } ] }
// @Valid: 목록 안의 계획 하나하나도 계획 추가와 똑같이 검사합니다. 하나라도 틀리면 전부 저장하지 않습니다.
public record StudyPlanBatchCreateRequest(
    @NotEmpty(message = "추가할 계획을 하나 이상 보내 주세요")
        @Size(max = 50, message = "계획은 한 번에 50개까지 추가할 수 있어요")
        List<@Valid StudyPlanCreateRequest> plans) {}
