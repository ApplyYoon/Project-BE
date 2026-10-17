package com.ithopeschool.projectbe.domain.review;

import com.ithopeschool.projectbe.domain.exam.ExamRepository;
import com.ithopeschool.projectbe.domain.review.dto.ReviewResponse;
import com.ithopeschool.projectbe.domain.review.dto.ReviewSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 회고(시험 후 3문항 · 주간 회고)를 저장하는 로직입니다. (이슈 #17, docs/API.md "회고")
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고,
// 내 번호로만 찾아서 다른 사람의 회고는 건드리지 않습니다. 회고 내용은 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class ReviewService {

  private final CurrentUserProvider currentUserProvider;
  private final ReviewRepository reviewRepository;
  // 시험 후 회고를 붙일 시험이 "내 시험" 인지 확인할 때 씁니다.
  private final ExamRepository examRepository;
  // 저장한 시각을 알 때 씁니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 회고를 저장합니다. 같은 시험·같은 주에 이미 쓴 게 있으면 새 답으로 덮어쓰고, 없으면 새로 만듭니다.
  @Transactional
  public ReviewResponse save(ReviewSaveRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    ReviewType type = parseType(request.type());
    List<String> answers = request.answers();
    Instant now = clock.instant();

    Long examId = null;
    Optional<Review> found;
    if (type == ReviewType.EXAM) {
      examId = checkExamReview(userId, request);
      found = reviewRepository.findByUserIdAndExamId(userId, examId);
    } else {
      checkWeeklyReview(request);
      found = reviewRepository.findByUserIdAndWeekStart(userId, request.weekStart());
    }

    Review review;
    if (found.isPresent()) {
      // 같은 시험·같은 주에 다시 썼으면 덮어씁니다. @Transactional 이라 바뀐 값은 끝날 때 DB 에 저장됩니다.
      review = found.get();
      review.overwrite(answers.get(0), answers.get(1), answers.get(2), now);
    } else {
      review =
          reviewRepository.save(
              new Review(
                  userId,
                  type,
                  examId,
                  request.weekStart(),
                  answers.get(0),
                  answers.get(1),
                  answers.get(2),
                  now));
    }
    return ReviewResponse.from(review);
  }

  // 시험 후 회고: 내 시험이어야 하고, 세 질문 모두 답해야 합니다. 검사를 통과하면 시험 번호를 돌려줍니다.
  private Long checkExamReview(Long userId, ReviewSaveRequest request) {
    if (request.examId() == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "어느 시험의 회고인지 examId 를 보내 주세요");
    }
    if (request.weekStart() != null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험 후 회고에는 weekStart 를 보내지 않아요");
    }
    for (String answer : request.answers()) {
      if (answer.isEmpty()) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "시험 후 회고는 세 질문에 모두 답해 주세요");
      }
    }
    Long examId = Long.valueOf(request.examId());
    // 없는 시험과 남의 시험은 구별하지 않고 똑같이 404 입니다. (시험 API 와 같은 방식)
    if (examRepository.findByIdAndUserId(examId, userId).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "시험을 찾을 수 없어요");
    }
    return examId;
  }

  // 주간 회고: 그 주의 월요일 날짜가 있어야 하고, 적어도 한 질문에는 답해야 합니다. (나머지는 건너뛰어도 됩니다)
  private void checkWeeklyReview(ReviewSaveRequest request) {
    if (request.weekStart() == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "어느 주의 회고인지 weekStart 를 보내 주세요");
    }
    if (request.examId() != null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "주간 회고에는 examId 를 보내지 않아요");
    }
    // 한 주는 월요일부터 일요일까지입니다. (FE 의 WeeklyReview.weekStart 와 같은 기준)
    if (request.weekStart().getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "weekStart 는 그 주의 월요일 날짜로 보내 주세요");
    }
    boolean allEmpty = request.answers().stream().allMatch(String::isEmpty);
    if (allEmpty) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "적어도 한 질문에는 답해 주세요");
    }
  }

  // FE 가 보낸 글자("exam")를 종류로 바꿉니다. 두 가지가 아니면 400 입니다.
  private ReviewType parseType(String value) {
    ReviewType type = ReviewType.fromValue(value);
    if (type == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "회고 종류는 exam, weekly 중에서 골라 주세요");
    }
    return type;
  }
}
