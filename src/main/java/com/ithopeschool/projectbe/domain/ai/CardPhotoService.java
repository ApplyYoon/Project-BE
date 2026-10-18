package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.CardPhotoResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "시험 범위 사진으로 카드 만들기" 의 로직입니다. 시험 범위표 · 학습지 사진 한 장을 카드 초안 여러 장으로 바꿉니다.
// (2026-10-18, Project-App 요청 · 사용자 결정 "사진은 저장하지 않고 AI 가 읽고 버림")
// ⚠️ 아무것도 저장하지 않습니다. 사진은 메모리에서 OpenAI 로 보내고 끝이며, DB · 파일 · 로그 어디에도 남기지 않습니다.
//    학생이 미리보기를 확인한 뒤 FE 가 카드 저장 API(/api/v1/cards)로 저장합니다.
//
// 순서
//   1. 사진 모양을 봅니다. data URL(jpeg · png · webp)이 아니거나, base64 가 깨졌거나, 4MB 를 넘으면 400
//      시험 날짜가 기준 날짜보다 빠르면 400
//   2. 메모에 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   3. 맥락은 "말로 카드 만들기" 와 같습니다. (CardParseService.contextOf: 내 진행 중 목표 · 최근 카드 제목 · 한 칸 길이)
//   4. 사진과 함께 AI 를 부릅니다. 꺼짐 · 하루 한도 · 실패면 drafts 가 null 입니다. (한도는 작은 AI 기능 묶음 ASSIST)
//   5. AI 가 위기 표현이라고 하면 2번과 같이 하고, 시험 범위 사진이 아니라고 하면 drafts 가 null 입니다.
//   6. 카드마다 "말로 카드 만들기" 와 같은 검사를 합니다. 날짜는 시험 전날까지, 14장까지입니다. (CardPhoto)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 목표 · 카드 · 기록만 읽습니다.
@Service
@RequiredArgsConstructor
public class CardPhotoService {

  // 받는 사진 종류 (OpenAI 가 읽는 종류 중 휴대폰 사진에 쓰는 것)
  static final List<String> IMAGE_PREFIXES =
      List.of("data:image/jpeg;base64,", "data:image/png;base64,", "data:image/webp;base64,");
  // 사진 한 장의 최대 크기 (바이트)
  static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

  private final CurrentUserProvider currentUserProvider;
  private final CardParseService cardParseService;
  private final AiClient aiClient;

  // AI 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다. (CardParseService 와 같음)
  public CardPhotoResponse make(CardPhotoRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 사진 · 날짜 검사
    checkImage(request.image());
    if (request.examDate() != null && request.examDate().isBefore(request.date())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험 날짜는 기준 날짜보다 빠를 수 없어요");
    }

    // 2) 위기 표현 (단어 목록)
    if (request.note() != null && CrisisText.hasCrisisText(List.of(request.note()))) {
      return CardPhotoResponse.help();
    }

    // 3) 맥락
    CardParseService.Context context = cardParseService.contextOf(userId);

    // 4) 사진과 함께 AI
    Optional<CardPhoto.Answer> answer =
        aiClient.askWithImage(
            userId,
            GptUsageKind.ASSIST,
            CardPhoto.question(
                request.date(),
                request.examDate(),
                request.note(),
                context.goals(),
                context.recentTitles()),
            request.image(),
            CardPhoto.Answer.class);
    if (answer.isEmpty()) {
      return CardPhotoResponse.notMade();
    }

    // 5) 위기 표현 (AI 판단) · 시험 범위가 아닌 사진
    if (Boolean.TRUE.equals(answer.get().crisis())) {
      return CardPhotoResponse.help();
    }
    if (!Boolean.TRUE.equals(answer.get().examScope())) {
      return CardPhotoResponse.notMade();
    }

    // 6) 카드마다 검사
    List<CardParse.Draft> drafts =
        CardParse.from(
            answer.get().cardAnswer(),
            request.date(),
            context.myGoalIds(),
            context.block().minutes(),
            CardPhoto.maxDayOffset(request.date(), request.examDate()),
            CardPhoto.MAX_DRAFTS);
    return CardPhotoResponse.of(
        drafts, context.block().evidence(), CardPhoto.scopeOf(answer.get().scopeText()));
  }

  // 사진이 받는 모양인지 봅니다. 아니면 400 입니다. (사진 내용은 메시지에 넣지 않습니다)
  static void checkImage(String image) {
    String prefix = IMAGE_PREFIXES.stream().filter(image::startsWith).findFirst().orElse(null);
    if (prefix == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "사진은 jpeg · png · webp 의 data URL 로 보내 주세요");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(image.substring(prefix.length()));
    } catch (IllegalArgumentException e) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "사진을 읽을 수 없어요. 다시 찍어 주세요");
    }
    if (bytes.length == 0) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "사진을 읽을 수 없어요. 다시 찍어 주세요");
    }
    if (bytes.length > MAX_IMAGE_BYTES) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "사진이 너무 커요. 4MB 이하로 보내 주세요");
    }
  }
}
