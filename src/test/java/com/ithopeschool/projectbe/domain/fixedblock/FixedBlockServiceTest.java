package com.ithopeschool.projectbe.domain.fixedblock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockCreateRequest;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockResponse;
import com.ithopeschool.projectbe.domain.fixedblock.dto.FixedBlockUpdateRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB 를 가짜로 바꿔서 검사 규칙과 "내 것만" 을 확인합니다.
class FixedBlockServiceTest {

  private static final Long ME = 7L;
  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final FixedBlockRepository repository = mock(FixedBlockRepository.class);
  private final FixedBlockService service =
      new FixedBlockService(currentUserProvider, repository, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    // 저장하면 DB 가 번호 3 을 붙여 준 것처럼 돌려줍니다.
    given(repository.save(any(FixedBlock.class)))
        .willAnswer(
            invocation -> {
              FixedBlock block = invocation.getArgument(0);
              ReflectionTestUtils.setField(block, "id", 3L);
              return block;
            });
  }

  // 내 학원 일정 (화 · 목 18:00~21:00, 번호 5)
  private FixedBlock myAcademy() {
    FixedBlock block =
        new FixedBlock(ME, "수학 학원", List.of(2, 4), LocalTime.of(18, 0), LocalTime.of(21, 0), NOW);
    ReflectionTestUtils.setField(block, "id", 5L);
    given(repository.findByIdAndUserId(5L, ME)).willReturn(Optional.of(block));
    return block;
  }

  private static void assertBadRequest(Runnable action, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  // ---- 만들기 ----

  @Test
  @DisplayName("만들면 내 번호 · 보낸 값 · 지금 시각으로 저장하고, 요일은 작은 숫자부터 돌려준다")
  void create() {
    FixedBlockResponse response =
        service.create(new FixedBlockCreateRequest("학교", List.of(5, 1, 2, 3, 4), "08:00", "16:30"));

    ArgumentCaptor<FixedBlock> captor = ArgumentCaptor.forClass(FixedBlock.class);
    verify(repository).save(captor.capture());
    FixedBlock saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(ME);
    assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(8, 0));
    assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(16, 30));
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);

    assertThat(response)
        .isEqualTo(
            new FixedBlockResponse(
                "3", "학교", List.of(1, 2, 3, 4, 5), "08:00", "16:30", NOW.toEpochMilli()));
  }

  @Test
  @DisplayName("자정을 넘는 23:00~01:00 도 만들 수 있다 (하루는 새벽 4시에 시작)")
  void createOvernight() {
    FixedBlockResponse response =
        service.create(new FixedBlockCreateRequest("야간 자습", List.of(6), "23:00", "01:00"));

    assertThat(response.endTime()).isEqualTo("01:00");
  }

  @Test
  @DisplayName("끝 시각이 시작보다 앞이거나 같으면 400 이고 저장하지 않는다")
  void endBeforeStart() {
    assertBadRequest(
        () -> service.create(new FixedBlockCreateRequest("학원", List.of(2), "18:00", "17:00")),
        "끝 시각은 시작보다 뒤여야 해요");
    assertBadRequest(
        () -> service.create(new FixedBlockCreateRequest("학원", List.of(2), "18:00", "18:00")),
        "끝 시각은 시작보다 뒤여야 해요");
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("같은 요일을 두 번 보내면 400")
  void repeatedDay() {
    assertBadRequest(
        () -> service.create(new FixedBlockCreateRequest("학원", List.of(2, 2), "18:00", "21:00")),
        "같은 요일을 두 번 보냈어요");
  }

  // ---- 목록 ----

  @Test
  @DisplayName("목록은 내 일정만 만든 순서로 돌려준다")
  void getBlocks() {
    FixedBlock academy = myAcademy();
    given(repository.findByUserIdOrderById(ME)).willReturn(List.of(academy));

    assertThat(service.getBlocks())
        .extracting(FixedBlockResponse::id, FixedBlockResponse::title)
        .containsExactly(tuple("5", "수학 학원"));
  }

  // ---- 바꾸기 ----

  @Test
  @DisplayName("보낸 것만 바꾸고 나머지는 그대로 둔다")
  void updateOnlySent() {
    FixedBlock academy = myAcademy();

    FixedBlockResponse response =
        service.update(5L, new FixedBlockUpdateRequest(null, List.of(4, 2, 6), null, "22:00"));

    assertThat(response.title()).isEqualTo("수학 학원");
    assertThat(response.repeatDays()).containsExactly(2, 4, 6);
    assertThat(response.startTime()).isEqualTo("18:00");
    assertThat(response.endTime()).isEqualTo("22:00");
    assertThat(academy.getEndTime()).isEqualTo(LocalTime.of(22, 0));
  }

  @Test
  @DisplayName("끝 시각만 바꿔도 지금 시작 시각과 비교해서, 시작보다 앞이면 400 이고 아무것도 안 바뀐다")
  void updateChecksMergedTimes() {
    FixedBlock academy = myAcademy();

    assertBadRequest(
        () -> service.update(5L, new FixedBlockUpdateRequest("영어 학원", null, null, "17:00")),
        "끝 시각은 시작보다 뒤여야 해요");
    assertThat(academy.getTitle()).isEqualTo("수학 학원");
    assertThat(academy.getEndTime()).isEqualTo(LocalTime.of(21, 0));
  }

  @Test
  @DisplayName("아무것도 보내지 않으면 400")
  void updateNothing() {
    myAcademy();

    assertBadRequest(
        () -> service.update(5L, new FixedBlockUpdateRequest(null, null, null, null)),
        "바꿀 이름 · 요일 · 시각을 보내 주세요");
  }

  @Test
  @DisplayName("없는 번호이거나 남의 일정이면 바꾸기 · 지우기 모두 404")
  void notFound() {
    given(repository.findByIdAndUserId(9L, ME)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.update(9L, new FixedBlockUpdateRequest("학원", null, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage("고정 일정을 찾을 수 없어요")
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThatThrownBy(() -> service.delete(9L))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(repository, never()).delete(any());
  }

  // ---- 지우기 ----

  @Test
  @DisplayName("내 일정은 지운다")
  void delete() {
    FixedBlock academy = myAcademy();

    service.delete(5L);

    verify(repository).delete(academy);
  }
}
