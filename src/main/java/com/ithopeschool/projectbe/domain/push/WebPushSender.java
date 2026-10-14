package com.ithopeschool.projectbe.domain.push;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 웹 푸시(앱을 닫아도 오는 알림)를 실제로 보내는 곳입니다. (이슈 #32)
//
// ■ 어떻게 보내나
//   1. 알림 내용(제목·글·누르면 열 주소)을 JSON 글자로 만든다
//   2. 라이브러리(web-push)가 구독의 열쇠로 내용을 잠그고, VAPID 서명을 붙인 요청을 만든다   (preparePost)
//   3. 그 요청을 자바 기본 HttpClient 로 브라우저 회사의 주소(endpoint)에 보낸다
//   암호화(RFC 8291)와 서명(VAPID)은 직접 짜면 틀리기 쉬워서 라이브러리에 맡기고, 보내는 일은 기다리는 시간을
//   우리가 정하려고 직접 합니다.
//
// ■ 배포 서버에서만 켜집니다
//   VAPID 키는 application-prod.yml 에만 자리가 있습니다. (환경변수 VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY)
//   내 컴퓨터에서는 isEnabled() 가 false 라서 보내지 않습니다.
//
// ■ 결과
//   SENT    보냄 (2xx)
//   GONE    그 구독은 끝났음 (404 · 410). 학생이 알림을 끄거나 앱을 지운 것이라, 부르는 쪽이 구독을 지웁니다.
//   FAILED  그 밖의 실패 (시간 초과, 5xx 등). 다음에 다시 보내 볼 수 있어서 구독은 그대로 둡니다.
//   실패해도 에러를 던지지 않습니다.
//
// ⚠️ 키, 알림 내용, 구독 주소(endpoint)는 로그에 찍지 않습니다.
@Component
public class WebPushSender {

  private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

  // 라이브러리가 쓰는 암호 계산기를 자바에 한 번 등록합니다. (여러 번 등록해도 괜찮습니다)
  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  public enum Result {
    SENT,
    GONE,
    FAILED
  }

  // 알림 하나의 내용. title: 제목 / body: 글 / url: 누르면 열 앱 안의 주소 (예: "/records")
  public record Payload(String title, String body, String url) {}

  private final String publicKey;
  // null 이면 꺼진 상태입니다. (키가 없거나 키 모양이 틀림)
  private final PushService pushService;
  private final HttpClient httpClient;

  @Autowired
  public WebPushSender(
      @Value("${app.push.vapid-public-key:}") String publicKey,
      @Value("${app.push.vapid-private-key:}") String privateKey,
      @Value("${app.push.subject:}") String subject) {
    this(
        publicKey,
        privateKey,
        subject,
        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  // 테스트에서 가짜 HttpClient 를 끼워 넣을 때 쓰는 생성자입니다.
  WebPushSender(String publicKey, String privateKey, String subject, HttpClient httpClient) {
    this.publicKey = publicKey == null ? "" : publicKey.trim();
    this.pushService = createPushService(this.publicKey, privateKey, subject);
    this.httpClient = httpClient;
    if (pushService != null) {
      log.info("[푸시] 웹 푸시를 보낼 수 있어요. (VAPID 키가 설정돼 있습니다)");
    } else {
      log.info("[푸시] 웹 푸시가 꺼져 있어요. 배포 서버에 VAPID_PUBLIC_KEY 와 VAPID_PRIVATE_KEY 가 있어야 켜집니다.");
    }
  }

  // 키가 둘 다 있고 모양이 맞으면 라이브러리의 보내기 도구를 만듭니다. 아니면 null (꺼짐)
  private static PushService createPushService(
      String publicKey, String privateKey, String subject) {
    if (publicKey.isEmpty() || privateKey == null || privateKey.isBlank()) {
      return null;
    }
    try {
      return new PushService(publicKey, privateKey.trim(), subject);
    } catch (GeneralSecurityException | RuntimeException e) {
      // 키 값은 찍지 않고 "모양이 틀렸다" 는 것만 알립니다.
      log.warn("[푸시] VAPID 키의 모양이 올바르지 않아서 웹 푸시를 끕니다: {}", e.getClass().getSimpleName());
      return null;
    }
  }

  // 웹 푸시를 보낼 수 있는 서버인지
  public boolean isEnabled() {
    return pushService != null;
  }

  // 브라우저가 구독할 때 쓰는 공개 키. 꺼져 있으면 빈 글자입니다. (공개 키는 비밀값이 아닙니다)
  public String publicKey() {
    return isEnabled() ? publicKey : "";
  }

  // 구독 하나로 알림 하나를 보냅니다.
  public Result send(PushSubscription subscription, Payload payload) {
    if (!isEnabled()) {
      return Result.FAILED;
    }
    try {
      HttpPost post = preparePost(subscription, payload);
      HttpResponse<Void> response =
          httpClient.send(toJdkRequest(post), HttpResponse.BodyHandlers.discarding());
      return resultOf(response.statusCode());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.FAILED;
    } catch (IOException | GeneralSecurityException | JoseException | RuntimeException e) {
      // 열쇠 모양이 틀린 구독, 연결 실패, 시간 초과가 모두 여기로 옵니다. 내용·주소가 담겨 있을 수 있어서 종류만 찍습니다.
      log.warn("[푸시] 알림을 보내지 못했어요: {}", e.getClass().getSimpleName());
      return Result.FAILED;
    }
  }

  // 라이브러리가 내용을 잠그고 서명을 붙인 요청을 만듭니다. (테스트에서 모양을 확인하려고 따로 둡니다)
  HttpPost preparePost(PushSubscription subscription, Payload payload)
      throws GeneralSecurityException, IOException, JoseException {
    Notification notification =
        new Notification(
            subscription.getEndpoint(),
            subscription.getP256dh(),
            subscription.getAuth(),
            JSON.writeValueAsString(payload));
    // aes128gcm: 지금 브라우저들이 쓰는 표준 잠금 방식 (RFC 8291)
    return pushService.preparePost(notification, Encoding.AES128GCM);
  }

  // 라이브러리가 만든 요청(Apache HttpPost)을 자바 기본 HttpClient 의 요청으로 옮깁니다. 기다리는 시간을 정합니다.
  private static HttpRequest toJdkRequest(HttpPost post) throws IOException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(post.getURI()).timeout(REQUEST_TIMEOUT);
    for (Header header : post.getAllHeaders()) {
      // Content-Length 는 HttpClient 가 알아서 붙여서 옮기지 않습니다. (직접 붙이면 에러가 납니다)
      if (!header.getName().equalsIgnoreCase("Content-Length")) {
        builder.header(header.getName(), header.getValue());
      }
    }
    byte[] body =
        post.getEntity() == null ? new byte[0] : post.getEntity().getContent().readAllBytes();
    return builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
  }

  // 상태 번호를 결과로 바꿉니다.   201 → SENT / 410 → GONE / 500 → FAILED
  static Result resultOf(int status) {
    if (status >= 200 && status < 300) {
      return Result.SENT;
    }
    if (status == 404 || status == 410) {
      return Result.GONE;
    }
    return Result.FAILED;
  }
}
