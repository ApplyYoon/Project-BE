package com.ithopeschool.projectbe.domain.school;

import com.ithopeschool.projectbe.domain.school.NeisClient.NeisMajors;
import com.ithopeschool.projectbe.domain.school.NeisClient.NeisSchool;
import com.ithopeschool.projectbe.domain.school.dto.SchoolMajorsResponse;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 학교 번호 → 학교 종류 + 학과 목록을 알려 주는 로직입니다. (2026-10-19)
// 사용자: "특성화고면 내가 무슨 과인지, 일반고면 무슨 상황인지 알아야 뉴스나 공지나 학교생활을 더 도와줄 거 아냐"
//   - 특성화고 · 마이스터고는 NEIS 의 학과 목록을 줍니다. 그 밖의 학교는 학과를 묻지 않습니다. (FE 가 계열을 묻습니다)
//   - 로그인이 필요 없습니다. 가입 중(아직 계정이 없음)에 부르기 때문입니다. NEIS 의 공개 자료라 괜찮습니다.
//   - 누구나 부를 수 있어서 NEIS 를 자주 부르지 않게, 잘 받은 학교는 서버 메모리에 24시간 둡니다.
//     (DB 에 저장하지 않습니다 — 테이블 설계는 학생이 합니다)
@Service
@RequiredArgsConstructor
public class SchoolMajorService {

  // 받아 둔 목록을 이만큼 씁니다. 학과는 거의 안 바뀝니다.
  static final Duration KEEP_FOR = Duration.ofHours(24);
  // NEIS 의 학교 번호는 숫자 7자리입니다. (예: '7530560')
  static final Pattern SCHOOL_CODE = Pattern.compile("\\d{7}");

  private final NeisClient neisClient;
  private final Clock clock;

  // 받아 둔 응답. 열쇠는 학교 번호
  private final Map<String, Saved> saved = new ConcurrentHashMap<>();

  public SchoolMajorsResponse getMajors(String schoolCode) {
    if (schoolCode == null || !SCHOOL_CODE.matcher(schoolCode).matches()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "학교 번호는 숫자 7자리예요");
    }
    Instant now = clock.instant();
    // 메모리가 계속 늘지 않게 오래된 것은 지웁니다.
    saved.values().removeIf(s -> s.savedAt().isBefore(now.minus(KEEP_FOR)));
    Saved already = saved.get(schoolCode);
    if (already != null) {
      return already.response();
    }

    List<NeisSchool> schools = neisClient.findSchool(schoolCode).orElseThrow(this::neisDown);
    if (schools.isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "그런 번호의 학교가 없어요");
    }
    NeisSchool school = schools.get(0);
    SchoolMajorsResponse response;
    if (school.asksMajor()) {
      NeisMajors majors =
          neisClient.fetchMajors(school.officeCode(), schoolCode).orElseThrow(this::neisDown);
      response =
          SchoolMajorsResponse.withMajors(
              schoolCode, school.schoolType(), majors.names(), majors.complete());
    } else {
      response = SchoolMajorsResponse.withoutMajors(schoolCode, school.schoolType());
    }
    saved.put(schoolCode, new Saved(now, response));
    return response;
  }

  // NEIS 가 멈췄을 때. 받아 두지 않아서 다음에 부르면 다시 받아 봅니다.
  private ApiException neisDown() {
    return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "학교 정보를 지금 받을 수 없어요. 잠시 뒤 다시 해 주세요");
  }

  private record Saved(Instant savedAt, SchoolMajorsResponse response) {}
}
