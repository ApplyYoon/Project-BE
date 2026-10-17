package com.ithopeschool.projectbe.domain.fixedblock;

import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockCreateRequest;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockResponse;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockUpdateRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 고정 일정(학교 · 학원) 목록 · 만들기 · 바꾸기 · 지우기 로직입니다. (이슈 #223, docs/API.md "고정 일정")
// 서버에 두는 이유: AI 하루 계획이 "학교 · 학원 시간을 뺀 빈 시간" 에 카드를 넣으려면 서버가 이 시간을 알아야 합니다.
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 모든 조회 · 수정에 "내 userId" 조건이 붙어서 남의 일정은 보이지도, 바뀌지도 않습니다. (남의 번호는 404)
//
// 카드를 저장할 때의 수면 보호 · 10분 버퍼 검사(이슈 #223 의 나머지)는 카드 저장(#254)이 develop 에 들어온 뒤에 더합니다.
@Service
@RequiredArgsConstructor
public class FixedBlockService {

  private final CurrentUserProvider currentUserProvider;
  private final FixedBlockRepository fixedBlockRepository;
  private final Clock clock;

  // 내 고정 일정 목록 (만든 순서). 하나도 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<FixedBlockResponse> getBlocks() {
    Long userId = currentUserProvider.getCurrentUserId();
    return fixedBlockRepository.findByUserIdOrderById(userId).stream()
        .map(FixedBlockResponse::from)
        .toList();
  }

  // 고정 일정 만들기
  @Transactional
  public FixedBlockResponse create(FixedBlockCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    checkNoRepeatedDay(request.repeatDays());
    LocalTime startTime = LocalTime.parse(request.startTime());
    LocalTime endTime = LocalTime.parse(request.endTime());
    checkEndAfterStart(startTime, endTime);

    FixedBlock block =
        new FixedBlock(
            userId, request.title(), request.repeatDays(), startTime, endTime, Instant.now(clock));
    return FixedBlockResponse.from(fixedBlockRepository.save(block));
  }

  // 고정 일정 바꾸기: 보낸 것만 바꿉니다. 검사를 모두 마친 뒤에 바꿔서, 하나라도 틀리면 아무것도 바뀌지 않습니다.
  @Transactional
  public FixedBlockResponse update(Long id, FixedBlockUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    FixedBlock block = findMyBlock(userId, id);

    if (request.title() == null
        && request.repeatDays() == null
        && request.startTime() == null
        && request.endTime() == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 이름 · 요일 · 시각을 보내 주세요");
    }

    // 1) 검사: 하나만 바꿔도 "바뀐 뒤의 시작 · 끝" 으로 봅니다. (예: 끝만 17:00 으로 바꾸면 시작 18:00 과 비교)
    if (request.repeatDays() != null) {
      checkNoRepeatedDay(request.repeatDays());
    }
    LocalTime startTime =
        request.startTime() == null ? block.getStartTime() : LocalTime.parse(request.startTime());
    LocalTime endTime =
        request.endTime() == null ? block.getEndTime() : LocalTime.parse(request.endTime());
    checkEndAfterStart(startTime, endTime);

    // 2) 바꾸기. 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    if (request.title() != null) {
      block.changeTitle(request.title());
    }
    if (request.repeatDays() != null) {
      block.changeRepeatDays(request.repeatDays());
    }
    block.changeTime(startTime, endTime);
    return FixedBlockResponse.from(block);
  }

  // 고정 일정 지우기. 카드나 기록이 이 일정을 가리키지 않아서 그냥 지웁니다.
  @Transactional
  public void delete(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    fixedBlockRepository.delete(findMyBlock(userId, id));
  }

  // 내 고정 일정 중에서 번호로 찾습니다. 없는 번호이거나 남의 일정이면 똑같이 404 입니다.
  private FixedBlock findMyBlock(Long userId, Long id) {
    return fixedBlockRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "고정 일정을 찾을 수 없어요"));
  }

  // 같은 요일을 두 번 보내면 400 입니다. ([1, 1, 3] 처럼)
  private void checkNoRepeatedDay(List<Integer> days) {
    if (new HashSet<>(days).size() != days.size()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 요일을 두 번 보냈어요");
    }
  }

  // 끝 시각은 시작보다 뒤여야 합니다. 하루는 새벽 4시에 시작해서 23:00 ~ 01:00 은 됩니다. (FE 와 같은 문장)
  private void checkEndAfterStart(LocalTime startTime, LocalTime endTime) {
    if (!FixedBlock.isEndAfterStart(startTime, endTime)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "끝 시각은 시작보다 뒤여야 해요");
    }
  }
}
