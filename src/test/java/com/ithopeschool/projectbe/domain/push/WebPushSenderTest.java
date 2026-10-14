package com.ithopeschool.projectbe.domain.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import nl.martijndwars.webpush.Utils;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 진짜 암호화로 확인하는 테스트입니다. 테스트 안에서 VAPID 키와 "가짜 브라우저" 의 열쇠를 직접 만들고,
// 컴퓨터 안에 작은 가짜 푸시 서버(HttpServer)를 띄워서 보내 봅니다. 인터넷에는 나가지 않습니다.
// ⚠️ 진짜 브라우저 회사(구글·애플)가 받아 주는지는 배포 서버에서 휴대폰으로 확인해야 합니다.
class WebPushSenderTest {

  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  // WebPushSender 를 한 번 쓰면 암호 계산기(BouncyCastle)가 등록됩니다.
  static {
    new WebPushSender("", "", "", HttpClient.newHttpClient());
  }

  private HttpServer server;
  // 가짜 푸시 서버가 받은 요청의 헤더 몇 개
  private final AtomicReference<String> receivedEncoding = new AtomicReference<>();
  private final AtomicReference<Integer> receivedBodyLength = new AtomicReference<>();

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  // P-256 타원 곡선 열쇠 한 쌍을 만듭니다. (VAPID 키도, 브라우저의 p256dh 도 이 모양)
  private static KeyPair newKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
    generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"), new SecureRandom());
    return generator.generateKeyPair();
  }

  private static String publicKeyText(KeyPair pair) {
    return BASE64_URL.encodeToString(Utils.encode((ECPublicKey) pair.getPublic()));
  }

  private static String privateKeyText(KeyPair pair) {
    return BASE64_URL.encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()));
  }

  // 진짜 VAPID 키로 켠 보내기 도구
  private static WebPushSender enabledSender() throws Exception {
    KeyPair vapid = newKeyPair();
    return new WebPushSender(
        publicKeyText(vapid),
        privateKeyText(vapid),
        "https://github.com/Tmax-IT-Hope-School",
        HttpClient.newHttpClient());
  }

  // 가짜 브라우저의 구독 (endpoint 는 가짜 푸시 서버 주소)
  private static PushSubscription subscription(String endpoint) throws Exception {
    byte[] auth = new byte[16];
    new SecureRandom().nextBytes(auth);
    return new PushSubscription(
        7L, endpoint, publicKeyText(newKeyPair()), BASE64_URL.encodeToString(auth), Instant.now());
  }

  // 정해 둔 상태 번호로 답하는 가짜 푸시 서버를 띄우고 그 주소를 돌려줍니다.
  private String startServer(int status) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/push",
        exchange -> {
          receivedEncoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
          receivedBodyLength.set(exchange.getRequestBody().readAllBytes().length);
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/push";
  }

  private static final WebPushSender.Payload PAYLOAD =
      new WebPushSender.Payload("오늘의 작전", "5분만 먼저 시작해 볼까?", "/timer");

  @Test
  @DisplayName("VAPID 키가 둘 다 있으면 켜지고, 공개 키를 알려 준다")
  void enabledWithKeys() throws Exception {
    KeyPair vapid = newKeyPair();
    WebPushSender sender =
        new WebPushSender(
            publicKeyText(vapid),
            privateKeyText(vapid),
            "https://example.org",
            HttpClient.newHttpClient());

    assertThat(sender.isEnabled()).isTrue();
    assertThat(sender.publicKey()).isEqualTo(publicKeyText(vapid));
  }

  @Test
  @DisplayName("키가 없거나 모양이 틀리면 꺼지고, 보내지 않고 FAILED 다 (에러를 던지지 않는다)")
  void disabledWithoutKeys() throws Exception {
    WebPushSender noKeys = new WebPushSender("", "", "", HttpClient.newHttpClient());
    WebPushSender badKeys = new WebPushSender("abc", "def", "", HttpClient.newHttpClient());

    assertThat(noKeys.isEnabled()).isFalse();
    assertThat(noKeys.publicKey()).isEmpty();
    assertThat(badKeys.isEnabled()).isFalse();
    assertThat(noKeys.send(subscription("https://example.org/push"), PAYLOAD))
        .isEqualTo(WebPushSender.Result.FAILED);
  }

  @Test
  @DisplayName("보내는 요청: 내용을 aes128gcm 으로 잠그고, VAPID 서명(Authorization: vapid …)과 TTL 을 붙인다")
  void preparedRequestIsEncryptedAndSigned() throws Exception {
    HttpPost post =
        enabledSender().preparePost(subscription("https://example.org/push/1"), PAYLOAD);

    assertThat(post.getURI().toString()).isEqualTo("https://example.org/push/1");
    assertThat(post.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
    assertThat(post.getFirstHeader("Authorization").getValue()).startsWith("vapid t=");
    assertThat(post.getFirstHeader("TTL")).isNotNull();
    // 잠긴 내용이라 알림 글자가 그대로 들어 있지 않습니다.
    String body =
        new String(
            post.getEntity().getContent().readAllBytes(),
            java.nio.charset.StandardCharsets.ISO_8859_1);
    assertThat(body).doesNotContain("작전").isNotEmpty();
  }

  @Test
  @DisplayName("가짜 푸시 서버가 201 이면 SENT 이고, 잠긴 내용이 실제로 도착한다")
  void sendsToTheServer() throws Exception {
    String endpoint = startServer(201);

    assertThat(enabledSender().send(subscription(endpoint), PAYLOAD))
        .isEqualTo(WebPushSender.Result.SENT);
    assertThat(receivedEncoding.get()).isEqualTo("aes128gcm");
    assertThat(receivedBodyLength.get()).isGreaterThan(0);
  }

  @Test
  @DisplayName("410·404 면 GONE(구독이 끝남), 500 이면 FAILED")
  void goneAndFailed() throws Exception {
    assertThat(enabledSender().send(subscription(startServer(410)), PAYLOAD))
        .isEqualTo(WebPushSender.Result.GONE);
    stopServer();
    assertThat(enabledSender().send(subscription(startServer(500)), PAYLOAD))
        .isEqualTo(WebPushSender.Result.FAILED);
  }

  @Test
  @DisplayName("푸시 서버에 연결할 수 없어도 에러를 던지지 않고 FAILED 다")
  void connectionFailure() throws Exception {
    String endpoint = startServer(201);
    stopServer();
    server = null;

    assertThat(enabledSender().send(subscription(endpoint), PAYLOAD))
        .isEqualTo(WebPushSender.Result.FAILED);
  }

  @Test
  @DisplayName("상태 번호 → 결과: 2xx SENT · 404/410 GONE · 그 밖 FAILED")
  void resultOfStatus() {
    assertThat(WebPushSender.resultOf(201)).isEqualTo(WebPushSender.Result.SENT);
    assertThat(WebPushSender.resultOf(404)).isEqualTo(WebPushSender.Result.GONE);
    assertThat(WebPushSender.resultOf(410)).isEqualTo(WebPushSender.Result.GONE);
    assertThat(WebPushSender.resultOf(429)).isEqualTo(WebPushSender.Result.FAILED);
    assertThat(WebPushSender.resultOf(500)).isEqualTo(WebPushSender.Result.FAILED);
  }
}
