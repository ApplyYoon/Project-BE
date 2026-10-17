package com.ithopeschool.projectbe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// docs/DB.md 의 그림(mermaid)이 H2 에 "실제로 만들어진" 테이블과 같은지 확인합니다.
// 컬럼을 바꿨는데 그림을 안 고치면(또는 그 반대면) 이 테스트가 실패합니다.
// 이슈 #3·#67·#85 에서 만든 7개 테이블과 카드(#222) · 목표(#221)만 비교하므로, 다른 사람이 그림에 테이블을 더해도 영향이 없습니다.
// (카드를 넣은 이유: 분석의 기본 단위가 카드이고, 공부 기록이 카드를 가리킬 예정(#224)이라 그림이 늘 맞아야 하기 때문)
@DataJpaTest
class DbDiagramTest {

  private static final List<String> OUR_TABLES =
      List.of(
          "users",
          "subjects",
          "study_plans",
          "study_sessions",
          "routines",
          "routine_applications",
          "lock_events",
          "cards",
          // 카드의 goal_id 가 가리키는 목표 (#221). 목록에 있어야 goals → cards 선을 비교합니다.
          "goals");

  // 그림에서 쓰는 타입 이름 → H2 가 실제로 만든 타입 이름
  // (color 는 그림에서 varchar 라고 적지만 H2 는 ENUM 으로 만들고, PostgreSQL 은 varchar + check 로 만듭니다)
  private static final Map<String, Set<String>> TYPE_MAP =
      Map.of(
          "bigint", Set.of("BIGINT"),
          "varchar", Set.of("CHARACTER VARYING", "ENUM"),
          "int", Set.of("INTEGER"),
          "boolean", Set.of("BOOLEAN"),
          "date", Set.of("DATE"),
          "time", Set.of("TIME"),
          "timestamptz", Set.of("TIMESTAMP WITH TIME ZONE"));

  // 그림에 적힌 컬럼 한 줄: 타입, 이름, 키(PK/FK 또는 빈 글자), 설명
  private record DiagramColumn(String type, String name, String key, String comment) {}

  @Autowired private EntityManager em;

  @Test
  @DisplayName("그림에 7개 테이블과 카드 · 목표가 모두 그려져 있다")
  void diagramHasOurFourTables() throws IOException {
    assertThat(diagramTables().keySet()).containsAll(OUR_TABLES);
  }

  @Test
  @DisplayName("그림의 컬럼 이름과 DB 에 실제로 만들어진 컬럼 이름이 똑같다")
  void columnNamesMatch() throws IOException {
    Map<String, List<DiagramColumn>> diagram = diagramTables();
    Map<String, Map<String, String>> real = realTables();

    for (String table : OUR_TABLES) {
      List<String> drawn = diagram.get(table).stream().map(DiagramColumn::name).toList();
      assertThat(drawn)
          .as("%s 테이블의 그림 컬럼", table)
          .containsExactlyInAnyOrderElementsOf(real.get(table).keySet());
    }
  }

  @Test
  @DisplayName("그림의 컬럼 타입이 DB 에 실제로 만들어진 타입과 맞다")
  void columnTypesMatch() throws IOException {
    Map<String, List<DiagramColumn>> diagram = diagramTables();
    Map<String, Map<String, String>> real = realTables();

    for (String table : OUR_TABLES) {
      for (DiagramColumn column : diagram.get(table)) {
        String realType = real.get(table).get(column.name());
        assertThat(TYPE_MAP.get(column.type()))
            .as("%s.%s : 그림은 %s, DB 는 %s", table, column.name(), column.type(), realType)
            .contains(realType);
      }
    }
  }

  @Test
  @DisplayName("그림에서 PK 표시는 각 테이블의 id 하나뿐이다")
  void primaryKeysMatch() throws IOException {
    Map<String, List<DiagramColumn>> diagram = diagramTables();

    for (String table : OUR_TABLES) {
      List<String> primaryKeys =
          diagram.get(table).stream()
              .filter(column -> column.key().equals("PK"))
              .map(DiagramColumn::name)
              .toList();
      assertThat(primaryKeys).as("%s 의 PK", table).containsExactly("id");
    }
  }

  @Test
  @DisplayName("xxx_id 컬럼에는 FK 표시와 xxxs 테이블로 이어지는 선이 있고, 그 밖의 선은 없다")
  void foreignKeysAndLinesMatch() throws IOException {
    String mermaid = mermaid();
    Map<String, List<DiagramColumn>> diagram = diagramTables();

    List<String> expectedLines = new ArrayList<>();
    for (String table : OUR_TABLES) {
      for (DiagramColumn column : diagram.get(table)) {
        // xxx_id 이고 xxxs 테이블이 그림에 있으면 FK 입니다. (/db 규칙)
        // 가리킬 테이블이 아직 없으면 FK 로 그리지 않습니다.
        String parent = column.name().replace("_id", "") + "s";
        boolean looksLikeReference = column.name().endsWith("_id") && diagram.containsKey(parent);
        assertThat(column.key().equals("FK"))
            .as("%s.%s 의 FK 표시", table, column.name())
            .isEqualTo(looksLikeReference);
        if (looksLikeReference) {
          expectedLines.add(parent + " -> " + table + " : " + column.name());
        }
      }
    }

    // 그림 안의 "A ||--o{ B : "컬럼"" 선을 모두 읽는다
    List<String> drawnLines = new ArrayList<>();
    Matcher line =
        Pattern.compile(
                "^\\s*(\\w+)\\s+\\|\\|--o\\{\\s+(\\w+)\\s*:\\s*\"(\\w+)\"\\s*$", Pattern.MULTILINE)
            .matcher(mermaid);
    while (line.find()) {
      String parent = line.group(1);
      String child = line.group(2);
      // 다른 사람이 그린 테이블의 선은 비교하지 않는다
      if (OUR_TABLES.contains(parent) && OUR_TABLES.contains(child)) {
        drawnLines.add(parent + " -> " + child + " : " + line.group(3));
      }
    }

    assertThat(drawnLines).containsExactlyInAnyOrderElementsOf(expectedLines);
  }

  @Test
  @DisplayName("그림의 컬럼마다 왜 필요한지 설명이 한 줄씩 적혀 있다")
  void everyColumnHasComment() throws IOException {
    Map<String, List<DiagramColumn>> diagram = diagramTables();

    for (String table : OUR_TABLES) {
      for (DiagramColumn column : diagram.get(table)) {
        assertThat(column.comment()).as("%s.%s 설명", table, column.name()).isNotBlank();
      }
    }
  }

  // ---- docs/DB.md 읽기 ----

  private String mermaid() throws IOException {
    String markdown = Files.readString(Path.of("docs", "DB.md"));
    Matcher block = Pattern.compile("```mermaid\\s*(.*?)```", Pattern.DOTALL).matcher(markdown);
    assertThat(block.find()).as("docs/DB.md 에 mermaid 그림이 있어야 합니다").isTrue();
    return block.group(1);
  }

  // 그림 안의 "테이블이름 { ... }" 덩어리를 모두 읽어 테이블 이름 → 컬럼 목록으로 만든다
  private Map<String, List<DiagramColumn>> diagramTables() throws IOException {
    Map<String, List<DiagramColumn>> tables = new LinkedHashMap<>();
    Matcher table =
        Pattern.compile(
                "^\\s*(\\w+)\\s*\\{\\s*$(.*?)^\\s*\\}\\s*$", Pattern.MULTILINE | Pattern.DOTALL)
            .matcher(mermaid());
    Pattern columnLine =
        Pattern.compile("^\\s*(\\w+)\\s+(\\w+)(?:\\s+(PK|FK))?(?:\\s+\"([^\"]*)\")?\\s*$");

    while (table.find()) {
      List<DiagramColumn> columns = new ArrayList<>();
      for (String text : table.group(2).split("\\R")) {
        if (text.isBlank()) {
          continue;
        }
        Matcher column = columnLine.matcher(text);
        assertThat(column.matches()).as("읽을 수 없는 그림 줄: %s", text).isTrue();
        columns.add(
            new DiagramColumn(
                column.group(1),
                column.group(2),
                column.group(3) == null ? "" : column.group(3),
                column.group(4) == null ? "" : column.group(4)));
      }
      tables.put(table.group(1), columns);
    }
    return tables;
  }

  // ---- DB 에 실제로 만들어진 것 읽기: 소문자 테이블 이름 → (소문자 컬럼 이름 → 타입) ----

  @SuppressWarnings("unchecked")
  private Map<String, Map<String, String>> realTables() {
    List<Object[]> rows =
        em.createNativeQuery(
                "select table_name, column_name, data_type from information_schema.columns"
                    + " where table_schema = 'PUBLIC' order by table_name, ordinal_position")
            .getResultList();
    Map<String, Map<String, String>> tables = new LinkedHashMap<>();
    for (Object[] row : rows) {
      tables
          .computeIfAbsent(row[0].toString().toLowerCase(), key -> new LinkedHashMap<>())
          .put(row[1].toString().toLowerCase(), row[2].toString());
    }
    return tables;
  }
}
