package com.ithopeschool.projectbe.domain.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.school.NeisClient.NeisMajors;
import com.ithopeschool.projectbe.domain.school.NeisClient.NeisSchool;
import com.ithopeschool.projectbe.domain.school.dto.SchoolMajorsResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 학교 학과 Service 테스트. NEIS 는 가짜라 인터넷에 나가지 않습니다.
class SchoolMajorServiceTest {

  private static final String VOCATIONAL = "7530560";
  private static final String GENERAL = "7530075";
  private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");

  private final NeisClient neisClient = mock(NeisClient.class);
  private final Clock clock = mock(Clock.class);
  private final SchoolMajorService service = new SchoolMajorService(neisClient, clock);

  @BeforeEach
  void setUp() {
    given(clock.instant()).willReturn(NOW);
  }

  private void hasVocationalSchool() {
    given(neisClient.findSchool(VOCATIONAL))
        .willReturn(Optional.of(List.of(new NeisSchool("J10", "특성화고", "전문계"))));
    given(neisClient.fetchMajors("J10", VOCATIONAL))
        .willReturn(Optional.of(new NeisMajors(List.of("웹프로그래밍과", "해킹방어과"), true)));
  }

  @Test
  @DisplayName("특성화고는 학과 목록을 준다")
  void givesMajorsForVocationalSchool() {
    hasVocationalSchool();

    SchoolMajorsResponse response = service.getMajors(VOCATIONAL);

    assertThat(response.asksMajor()).isTrue();
    assertThat(response.schoolType()).isEqualTo("특성화고");
    assertThat(response.majors()).containsExactly("웹프로그래밍과", "해킹방어과");
    assertThat(response.complete()).isTrue();
  }

  @Test
  @DisplayName("일반고는 학과를 묻지 않고, 학과 목록을 부르지도 않는다")
  void skipsMajorsForGeneralSchool() {
    given(neisClient.findSchool(GENERAL))
        .willReturn(Optional.of(List.of(new NeisSchool("J10", "일반고", "일반계"))));

    SchoolMajorsResponse response = service.getMajors(GENERAL);

    assertThat(response.asksMajor()).isFalse();
    assertThat(response.majors()).isEmpty();
    verify(neisClient, never()).fetchMajors(anyString(), anyString());
  }

  @Test
  @DisplayName("24시간 안에 같은 학교를 다시 물으면 NEIS 를 부르지 않고, 24시간이 지나면 다시 받는다")
  void keepsAnswerFor24Hours() {
    hasVocationalSchool();

    service.getMajors(VOCATIONAL);
    service.getMajors(VOCATIONAL);
    verify(neisClient, times(1)).findSchool(VOCATIONAL);

    given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(25)));
    service.getMajors(VOCATIONAL);
    verify(neisClient, times(2)).findSchool(VOCATIONAL);
  }

  @Test
  @DisplayName("학교 번호가 숫자 7자리가 아니면 400 이고 NEIS 를 부르지 않는다")
  void rejectsWrongSchoolCode() {
    for (String wrong : List.of("", "123", "75305600", "abc1234")) {
      assertThatThrownBy(() -> service.getMajors(wrong))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
    verify(neisClient, never()).findSchool(anyString());
  }

  @Test
  @DisplayName("그런 번호의 학교가 없으면 404")
  void notFoundWhenNoSchool() {
    given(neisClient.findSchool("9999999")).willReturn(Optional.of(List.of()));

    assertThatThrownBy(() -> service.getMajors("9999999"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  @DisplayName("NEIS 가 멈추면 503 이고, 받아 두지 않아서 다음에 다시 받아 본다")
  void unavailableWhenNeisDown() {
    given(neisClient.findSchool(VOCATIONAL)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMajors(VOCATIONAL))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));

    hasVocationalSchool();
    assertThat(service.getMajors(VOCATIONAL).majors()).containsExactly("웹프로그래밍과", "해킹방어과");
  }
}
