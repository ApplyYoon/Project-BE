package com.ithopeschool.projectbe.domain.variant.sandbox;

import com.ithopeschool.projectbe.domain.variant.VariantSettings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 샌드박스 프로그램(VariantSandboxMain)을 새 프로세스로 띄워 일감 하나를 계산시키고 결과를 받아 옵니다. (G4 · G5)
// (docs/VARIANT-SPEC.md 6-3)
//
//   - 지금 서버와 같은 자바, 같은 클래스 경로로 띄웁니다. 그래서 Symja 를 따로 설치할 필요가 없습니다.
//   - 메모리는 -Xmx 로, 시간은 sandbox-timeout-sec 으로 제한합니다. 시간을 넘기면 강제로 끕니다.
//   - 임시 폴더를 작업 폴더로 쓰고, 끝나면 지웁니다. 환경변수는 비워서 넘깁니다(비밀값이 새지 않게).
//   - 배포 서버처럼 Spring Boot 실행 jar 하나로 돌 때는, jar 안의 클래스를 찾을 수 있게
//     Spring Boot 의 PropertiesLauncher 로 VariantSandboxMain 을 띄웁니다.
//
// 이 클래스는 AI 가 쓴 글자를 계산하지 않습니다. 넘기고 받기만 합니다.
@Component
@RequiredArgsConstructor
public class VariantSandbox {

  private static final Logger log = LoggerFactory.getLogger(VariantSandbox.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  // 결과로 읽을 최대 글자 수. 이보다 많이 찍으면 뒷부분은 버립니다
  private static final int MAX_OUTPUT_BYTES = 1024 * 1024;

  // Spring Boot 실행 jar 안의 다른 main 클래스를 띄우는 도구
  static final String BOOT_LAUNCHER = "org.springframework.boot.loader.launch.PropertiesLauncher";

  private final VariantSettings settings;

  public SandboxResult run(SandboxRequest request) {
    Path workDir = null;
    Process process = null;
    try {
      workDir = Files.createTempDirectory("variant-sandbox-");
      ProcessBuilder builder = new ProcessBuilder(command(workDir)).directory(workDir.toFile());
      keepOnlySafeEnvironment(builder);
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      process = builder.start();

      // 결과는 따로 읽습니다. (프로세스가 끝나기를 기다리는 동안 출력이 꽉 차서 서로 멈추는 일을 막으려고)
      InputStream stdout = process.getInputStream();
      CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> readLimited(stdout));

      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(JSON.writeValueAsBytes(request));
      }

      boolean finished = process.waitFor(settings.sandboxTimeoutSec(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return SandboxResult.failed("시간 제한(" + settings.sandboxTimeoutSec() + "초)을 넘었어요");
      }
      if (process.exitValue() != 0) {
        return SandboxResult.failed(
            "샌드박스가 비정상 종료했어요(종료 코드 " + process.exitValue() + "). 메모리 제한을 넘었을 수 있어요");
      }
      return parse(new String(output.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8));
    } catch (IOException | ExecutionException | TimeoutException e) {
      log.warn("[변형] 샌드박스를 실행하지 못했어요: {}", e.getClass().getSimpleName());
      return SandboxResult.failed("샌드박스를 실행하지 못했어요: " + e.getClass().getSimpleName());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return SandboxResult.failed("샌드박스를 기다리다 중단됐어요");
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
      deleteQuietly(workDir);
    }
  }

  // 결과 글에서 RESULT_MARKER 로 시작하는 줄을 찾아 JSON 을 읽습니다.
  static SandboxResult parse(String output) {
    for (String line : output.split("\\R")) {
      if (line.startsWith(VariantSandboxMain.RESULT_MARKER)) {
        try {
          return JSON.readValue(
              line.substring(VariantSandboxMain.RESULT_MARKER.length()), SandboxResult.class);
        } catch (JacksonException e) {
          return SandboxResult.failed("샌드박스 결과의 모양이 달라요");
        }
      }
    }
    return SandboxResult.failed("샌드박스가 결과를 돌려주지 않았어요");
  }

  // 띄울 명령. 클래스 경로가 길 수 있어서(특히 Windows) 옵션은 인자 파일(@파일)에 적어 넘깁니다.
  private List<String> command(Path workDir) throws IOException {
    String javaHome = System.getProperty("java.home");
    String java = Path.of(javaHome, "bin", "java").toString();
    String classpath = System.getProperty("java.class.path");

    List<String> options = new ArrayList<>();
    options.add("-Xmx" + settings.sandboxMemoryMb() + "m");
    options.add("-XX:+UseSerialGC");
    options.add("-XX:TieredStopAtLevel=1");
    // 메모리가 모자라면 오래 버티지 말고 바로 끝냅니다 (종료 코드가 0 이 아니게 됨)
    options.add("-XX:+ExitOnOutOfMemoryError");
    options.add("-Djava.awt.headless=true");
    options.add("-Dfile.encoding=UTF-8");
    options.add("-cp");
    options.add(quote(classpath));
    if (isBootJar(classpath)) {
      options.add("-Dloader.main=" + VariantSandboxMain.class.getName());
      options.add(BOOT_LAUNCHER);
    } else {
      options.add(VariantSandboxMain.class.getName());
    }

    Path argFile = workDir.resolve("java-args.txt");
    Files.writeString(argFile, String.join("\n", options), StandardCharsets.UTF_8);
    return List.of(java, "@" + argFile);
  }

  // 클래스 경로가 Spring Boot 실행 jar 하나인지 (배포 서버: java -jar app.jar)
  static boolean isBootJar(String classpath) {
    if (classpath == null
        || classpath.contains(File.pathSeparator)
        || !classpath.endsWith(".jar")) {
      return false;
    }
    try (JarFile jar = new JarFile(classpath)) {
      return jar.getEntry("BOOT-INF/classes/") != null;
    } catch (IOException e) {
      return false;
    }
  }

  // 인자 파일 안의 경로는 따옴표로 감싸고, Windows 의 \ 는 / 로 바꿉니다. (자바는 둘 다 알아듣습니다)
  private static String quote(String value) {
    return "\"" + value.replace('\\', '/') + "\"";
  }

  // 환경변수를 비웁니다. Windows 는 SystemRoot 가 없으면 자바가 제대로 켜지지 않아서 그것만 남깁니다.
  private static void keepOnlySafeEnvironment(ProcessBuilder builder) {
    String systemRoot = System.getenv("SystemRoot");
    builder.environment().clear();
    if (systemRoot != null) {
      builder.environment().put("SystemRoot", systemRoot);
    }
  }

  private static byte[] readLimited(InputStream in) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    try {
      int read;
      while ((read = in.read(buffer)) != -1) {
        int room = MAX_OUTPUT_BYTES - out.size();
        if (room > 0) {
          out.write(buffer, 0, Math.min(read, room));
        }
      }
    } catch (IOException e) {
      // 강제로 끈 경우 등. 읽은 데까지만 씁니다.
      return out.toByteArray();
    }
    return out.toByteArray();
  }

  private static void deleteQuietly(Path dir) {
    if (dir == null) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException e) {
      log.warn("[변형] 샌드박스 임시 폴더를 지우지 못했어요: {}", e.getClass().getSimpleName());
    }
  }
}
