package com.ithopeschool.projectbe.domain.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// "구글로 시작하기" 에서 FE 가 보낸 구글 신분증(ID 토큰)이 진짜인지 구글에 물어보는 곳입니다. (이슈 #19)
//
// ■ 흐름
//   FE 의 [Google로 시작하기] → 구글이 FE 에게 ID 토큰(credential)을 줌 → FE 가 우리 서버로 보냄
//   → 이 클래스가 구글의 tokeninfo 주소에 그 토큰을 보내 "진짜인지, 누구 것인지" 를 받아 옴
//
// ■ 확인하는 것 (하나라도 틀리면 빈 값)
//   1. 구글이 "진짜이고 아직 안 끝난 토큰" 이라고 답했다 (가짜이거나 끝난 토큰이면 구글이 400 을 줍니다)
//   2. aud(이 토큰을 받을 앱)가 우리 클라이언트 ID 다 — 다른 사이트용 토큰을 가져와 쓰는 것을 막습니다
//   3. iss(토큰을 만든 곳)가 구글이다
//   4. 이메일이 구글이 확인한 이메일이다 (email_verified)
//
// ■ 켜는 법: 서버 환경변수 GOOGLE_CLIENT_ID (FE 의 VITE_GOOGLE_CLIENT_ID 와 같은 값. 공개돼도 되는 값입니다)
//   비어 있으면 꺼진 상태라 구글 로그인 API 가 503 을 돌려줍니다. client secret 은 쓰지 않습니다.
//
// ■ 알아 둘 것: tokeninfo 는 구글이 "확인용" 으로 열어 둔 주소라, 요청이 아주 많으면 느려질 수 있습니다.
//   사용자가 많아지면 구글의 공개 키로 서버에서 직접 확인하는 방식으로 바꿉니다. (지금 규모에서는 충분합니다)
//
// ⚠️ ID 토큰과 이메일은 로그에 찍지 않습니다.
@Component
public class GoogleIdTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

  static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";
  // 구글이 만든 토큰의 iss 는 이 둘 중 하나입니다. (구글 문서 "Verify the Google ID token")
  private static final Set<String> GOOGLE_ISSUERS =
      Set.of("accounts.google.com", "https://accounts.google.com");
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private final String clientId;
  private final RestClient restClient;

  // Spring 이 쓰는 생성자입니다.
  @Autowired
  public GoogleIdTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
    this(clientId, RestClient.builder().requestFactory(requestFactoryWithTimeouts()));
  }

  // 테스트에서 가짜 구글 서버를 끼워 넣을 때 쓰는 생성자입니다. (GoogleIdTokenVerifierTest)
  GoogleIdTokenVerifier(String clientId, RestClient.Builder builder) {
    this.clientId = clientId == null ? "" : clientId.trim();
    this.restClient = builder.build();
    if (isEnabled()) {
      log.info("[구글 로그인] 켜져 있어요. (GOOGLE_CLIENT_ID 가 설정돼 있습니다)");
    } else {
      log.info("[구글 로그인] 꺼져 있어요. 서버에 GOOGLE_CLIENT_ID 가 있어야 켜집니다.");
    }
  }

  // 구글 로그인을 쓸 수 있는 서버인지 알려줍니다.
  public boolean isEnabled() {
    return !clientId.isEmpty();
  }

  // ID 토큰을 확인하고, 진짜면 그 사람의 구글 정보를 돌려줍니다. 가짜·끝남·꺼짐이면 빈 값입니다.
  public Optional<GoogleProfile> verify(String idToken) {
    if (!isEnabled() || idToken == null || idToken.isBlank()) {
      return Optional.empty();
    }
    try {
      // {idToken} 자리에 값을 넣으면 RestClient 가 주소에 맞게 바꿔(인코딩) 넣어 줍니다.
      TokenInfo info =
          restClient
              .get()
              .uri(TOKENINFO_URL + "?id_token={idToken}", idToken.trim())
              .retrieve()
              .body(TokenInfo.class);
      return Optional.ofNullable(info).flatMap(this::toProfile);
    } catch (RestClientException e) {
      // 가짜·끝난 토큰(구글이 400), 시간 초과, 연결 실패가 모두 여기로 옵니다. 종류만 찍습니다.
      log.info("[구글 로그인] 토큰을 확인하지 못했어요: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 구글의 답에서 위의 2 ~ 4 를 확인합니다.
  private Optional<GoogleProfile> toProfile(TokenInfo info) {
    if (!clientId.equals(info.aud())) {
      log.warn("[구글 로그인] 다른 앱을 위한 토큰이 왔어요. 받지 않았습니다.");
      return Optional.empty();
    }
    if (!GOOGLE_ISSUERS.contains(info.iss())) {
      return Optional.empty();
    }
    if (!"true".equalsIgnoreCase(info.emailVerified())) {
      return Optional.empty();
    }
    if (isBlank(info.sub()) || isBlank(info.email())) {
      return Optional.empty();
    }
    // 실명은 수집하지 않습니다. 예전 호출자를 위한 name 자리는 빈 글자만 둡니다.
    String name = "";
    return Optional.of(new GoogleProfile(info.sub(), info.email().trim(), name));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static JdkClientHttpRequestFactory requestFactoryWithTimeouts() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  // 확인이 끝난 구글 정보
  //   subject : 구글이 정해 준 이 사람의 고유 번호(sub). 이메일은 바뀔 수 있어서 계정은 이 번호로 찾습니다.
  //   email   : 구글이 확인한 이메일
  //   name    : 호환용 빈 글자 (실명을 읽지 않음)
  public record GoogleProfile(String subject, String email, String name) {}

  // 구글 tokeninfo 응답에서 우리가 읽는 부분만 적었습니다. 나머지 필드는 무시합니다.
  //   { "aud": "…apps.googleusercontent.com", "iss": "https://accounts.google.com", "sub": "1101…",
  //     "email": "tmax@gmail.com", "email_verified": "true", "name": "김티맥", "exp": "1760…" }
  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenInfo(
      String aud,
      String iss,
      String sub,
      String email,
      // 구글은 "true" 처럼 글자로 줍니다.
      @JsonProperty("email_verified") String emailVerified) {}
}
