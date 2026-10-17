package com.ithopeschool.projectbe.domain.job;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// "문지기"입니다. /api/v1/jobs/** 요청이 Controller 에 닿기 전에 비밀 키를 먼저 확인합니다. (JobWebConfig 에서 연결)
// 예약 작업 주소는 인터넷에 열려 있어서, 키가 없으면 아무나 불러 작업을 마구 돌릴 수 있기 때문입니다.
//
//   요청 헤더 X-Job-Key 의 값  ==  서버 설정 app.job.secret-key (환경변수 JOB_SECRET_KEY)  →  통과
//   그 밖의 모든 경우(헤더 없음, 틀림, 서버에 키가 설정 안 됨)                              →  401 로 거절
//
// 키가 설정되지 않은 서버는 "모두 허용" 이 아니라 "모두 거절" 합니다. 설정을 깜빡해도 문이 열리지 않게 하려는 것입니다.
@Component
public class JobKeyInterceptor implements HandlerInterceptor {

  // 스케줄러가 비밀 키를 담아 보내는 헤더 이름입니다.
  public static final String KEY_HEADER = "X-Job-Key";

  private static final Logger log = LoggerFactory.getLogger(JobKeyInterceptor.class);

  // 거절할 때 돌려줄 본문입니다. docs/API.md 3장의 에러 모양이고, 글자가 고정이라 내용을 만들어 붙일 일이 없습니다.
  private static final String UNAUTHORIZED_BODY =
      "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"작업 키가 없거나 맞지 않아요\"}}";

  // 진짜 키는 들고 있지 않고 "해시(섞어서 알아볼 수 없게 만든 값)" 만 들고 있습니다. 설정이 비어 있으면 null 입니다.
  private final byte[] expectedHash;

  public JobKeyInterceptor(@Value("${app.job.secret-key:}") String secretKey) {
    if (secretKey == null || secretKey.isBlank()) {
      this.expectedHash = null;
      // 값은 찍지 않고 "비어 있다" 는 사실만 알립니다.
      log.warn("JOB_SECRET_KEY 가 비어 있어서 예약 작업 요청을 전부 거절합니다. 배포 서버에 키를 설정하세요.");
    } else {
      this.expectedHash = sha256(secretKey);
    }
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (isValidKey(request.getHeader(KEY_HEADER))) {
      return true;
    }

    // 어떤 키였는지, 어느 주소였는지는 로그에 남기지 않습니다. (남이 넣은 글자가 로그를 어지럽히거나 비밀이 새지 않게)
    log.warn("[작업] 비밀 키가 없거나 맞지 않아 요청을 거절했어요.");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(UNAUTHORIZED_BODY);
    // false 를 돌려주면 Controller 로 넘어가지 않고 여기서 끝납니다.
    return false;
  }

  private boolean isValidKey(String givenKey) {
    if (expectedHash == null || givenKey == null) {
      return false;
    }
    // 그냥 equals 로 비교하면 "앞에서부터 몇 글자까지 맞았는지" 가 응답 시간에 드러나 키를 한 글자씩 알아낼 수 있습니다.
    // MessageDigest.isEqual 은 어디가 틀려도 같은 시간이 걸리고, 해시끼리 비교하면 길이도 항상 같아서 더 안전합니다.
    return MessageDigest.isEqual(sha256(givenKey), expectedHash);
  }

  private static byte[] sha256(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 은 자바에 늘 들어 있어서 여기에 올 일이 없습니다.
      throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
    }
  }
}
