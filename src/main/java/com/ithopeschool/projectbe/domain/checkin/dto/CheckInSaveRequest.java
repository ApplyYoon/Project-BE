package com.ithopeschool.projectbe.domain.checkin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// "체크인 저장" 요청의 모양입니다. 날짜는 주소에 들어 있습니다. (PUT /api/v1/checkins/2026-10-17)
//   { "source": "manual", "condition": 4, "mood": 3,
//     "missReason": "etc", "missReasonNote": "동아리 발표", "expectedMinutes": 120,
//     "promiseIf": "폰을 보고 싶으면", "promiseThen": "물 한 잔 마신다",
//     "suggestionKept": true, "emotions": ["STUCK", "TIRED"] }
// - source 만 꼭 있어야 하고, 나머지는 모두 보내지 않아도(null) 됩니다.
// - source · missReason · emotions 가 정해진 값인지, '기타' 글과 약속 두 칸이 맞게 왔는지는 Service 에서 검사합니다.
// - emotions(하루 첫 태그의 감정, 이슈 #220)는 여러 개를 보낼 수 있고, 같은 것을 두 번 보내면 하나로 봅니다.
//   다른 칸과 달리, 아예 안 보내면(null) 그날의 감정을 그대로 둡니다. 비우려면 빈 배열 [] 을 보냅니다.
public record CheckInSaveRequest(
    @NotNull(message = "어디서 한 체크인인지(source) 보내 주세요") String source,
    @Min(value = 1, message = "컨디션은 1 ~ 5 중에서 골라 주세요")
        @Max(value = 5, message = "컨디션은 1 ~ 5 중에서 골라 주세요")
        Integer condition,
    @Min(value = 1, message = "기분은 1 ~ 5 중에서 골라 주세요")
        @Max(value = 5, message = "기분은 1 ~ 5 중에서 골라 주세요")
        Integer mood,
    String missReason,
    @Size(max = 50, message = "기타 이유는 50자까지 적을 수 있어요") String missReasonNote,
    // 하루는 1440분이라 그보다 길게 공부할 수는 없습니다.
    @Min(value = 0, message = "예상 순공시간은 0분 이상이어야 해요")
        @Max(value = 1440, message = "예상 순공시간은 1440분(24시간) 이하여야 해요")
        Integer expectedMinutes,
    @Size(max = 100, message = "약속은 100자까지 적을 수 있어요") String promiseIf,
    @Size(max = 100, message = "약속은 100자까지 적을 수 있어요") String promiseThen,
    Boolean suggestionKept,
    @Size(max = 6, message = "감정 태그는 6개까지 고를 수 있어요") List<String> emotions) {}
