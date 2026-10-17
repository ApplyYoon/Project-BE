package com.ithopeschool.projectbe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

// db/migrations 의 SQL 을 검사합니다. (이미 테이블이 있는 Neon 을 다음 배포 상태로 올리는 SQL)
// Neon 에서는 "이 파일을 전에 실행했는지" 모르는 채로 다시 돌리는 일이 많아서, 몇 번을 실행해도 에러가 나면 안 됩니다.
//
//   1) 빈 H2 에 최신 db/schema.sql 을 실행해 "이미 다 반영된 DB" 를 만들고
//   2) db/migrations 의 파일을 번호 순서대로 두 번씩 실행해서 에러가 나지 않는지 봅니다.
//
// ⚠️ H2 의 PostgreSQL 흉내 모드라서, PostgreSQL 에서만 나는 문법 문제까지는 못 잡습니다. (SchemaSqlTest 와 같음)
//    새 파일을 만들면 Neon 의 dev 브랜치에서 먼저 실행해 보고 서버를 켜 봅니다. (README "운영 DB(Neon)")
class MigrationSqlTest {

  private static final Path MIGRATIONS = Path.of("db/migrations");

  // 검사가 끝나면 사라지는 빈 H2 입니다.
  private static final String URL = "jdbc:h2:mem:migration_sql_test;MODE=PostgreSQL";

  // db/migrations 의 .sql 파일을 이름 순서(= 번호 순서)로 돌려줍니다.
  private static List<Path> migrationFiles() throws IOException {
    try (Stream<Path> files = Files.list(MIGRATIONS)) {
      return files.filter(file -> file.toString().endsWith(".sql")).sorted().toList();
    }
  }

  // 주석을 빼고 ; 로 나눈 문장들 (소문자)
  private static List<String> statementsOf(Path file) throws IOException {
    String sql = Files.readString(file).replaceAll("(?m)--[^\\r\\n]*", "");
    return Arrays.stream(sql.split(";"))
        .map(String::strip)
        .filter(statement -> !statement.isEmpty())
        .map(statement -> statement.toLowerCase(Locale.ROOT))
        .toList();
  }

  @Test
  @DisplayName("파일 이름은 두 자리 번호로 시작하고, 번호가 겹치지 않는다 (실행 순서가 보이게)")
  void fileNamesShowTheOrder() throws IOException {
    List<Path> files = migrationFiles();

    assertThat(files).isNotEmpty();
    List<String> numbers = files.stream().map(file -> file.getFileName().toString()).toList();
    assertThat(numbers).allMatch(name -> name.matches("\\d{2}-[a-z0-9-]+\\.sql"));
    assertThat(numbers.stream().map(name -> name.substring(0, 2)).distinct().count())
        .isEqualTo(files.size());
  }

  @Test
  @DisplayName("파일마다 begin 으로 시작해 commit 으로 끝난다 (중간에 에러가 나면 하나도 안 바뀌게)")
  void eachFileIsOneTransaction() throws IOException {
    for (Path file : migrationFiles()) {
      List<String> statements = statementsOf(file);

      assertThat(statements.getFirst()).as(file.toString()).isEqualTo("begin");
      assertThat(statements.getLast()).as(file.toString()).isEqualTo("commit");
    }
  }

  @Test
  @DisplayName("이미 최신인 DB 에 db/migrations 를 순서대로 두 번 실행해도 에러가 나지 않는다")
  void migrationsCanRunAgain() throws IOException, SQLException {
    try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
      ScriptUtils.executeSqlScript(connection, new FileSystemResource("db/schema.sql"));

      // 에러가 나면 executeSqlScript 가 어느 파일 몇 번째 문장인지 알려 주며 멈춥니다.
      for (int round = 1; round <= 2; round++) {
        for (Path file : migrationFiles()) {
          ScriptUtils.executeSqlScript(connection, new FileSystemResource(file));
        }
      }

      // 두 번 돌린 뒤에도 탈퇴 등에 꼭 필요한 테이블이 그대로 있는지 하나 봅니다.
      try (ResultSet tables =
          connection.getMetaData().getTables(null, "PUBLIC", "RISK_FORECASTS", null)) {
        assertThat(tables.next()).isTrue();
      }
    }
  }
}
