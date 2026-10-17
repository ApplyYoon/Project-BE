package com.ithopeschool.projectbe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 엔티티를 보고 H2 가 "실제로 만든" 테이블의 모양이 설계(docs/DB.md)와 똑같은지 확인합니다.
// information_schema 는 DB 가 자기 테이블 정보를 적어 두는 곳이라, 거기를 읽으면 H2 콘솔에서 보는 것과 같은 내용이 나옵니다.
//
// 이 테스트는 이슈 #3 에서 만든 4개 테이블(users, subjects, study_plans, study_sessions)만 검사합니다.
// 다른 기능에서 테이블을 새로 만들어도 이 테스트는 깨지지 않습니다.
// 4개 테이블의 컬럼을 일부러 바꿨다면 이 테스트의 기대값과 docs/DB.md 를 같이 고치세요.
@DataJpaTest
class TableSchemaTest {

  // 검사할 테이블 4개 (SQL 의 in (...) 에 그대로 붙인다)
  private static final String OUR_TABLES = "('USERS', 'SUBJECTS', 'STUDY_PLANS', 'STUDY_SESSIONS')";

  @Autowired private EntityManager em;

  @Test
  @DisplayName("users, subjects, study_plans, study_sessions 테이블이 만들어진다")
  void fourTablesExist() {
    List<String> tables =
        query(
            "select table_name from information_schema.tables"
                + " where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'");

    assertThat(tables).contains("USERS", "SUBJECTS", "STUDY_PLANS", "STUDY_SESSIONS");
  }

  @Test
  @DisplayName("컬럼 이름·타입·길이·NULL 허용 여부가 설계와 똑같다")
  void columnsMatchDesign() {
    Map<String, String> expected = new LinkedHashMap<>();
    expected.put("USERS.ID", "BIGINT NOT NULL");
    expected.put("USERS.NICKNAME", "CHARACTER VARYING(50) NOT NULL");
    expected.put("USERS.CREATED_AT", "TIMESTAMP WITH TIME ZONE NOT NULL");

    expected.put("SUBJECTS.ID", "BIGINT NOT NULL");
    expected.put("SUBJECTS.USER_ID", "BIGINT NOT NULL");
    expected.put("SUBJECTS.NAME", "CHARACTER VARYING(30) NOT NULL");
    expected.put("SUBJECTS.COLOR", "ENUM NOT NULL");

    expected.put("STUDY_PLANS.ID", "BIGINT NOT NULL");
    expected.put("STUDY_PLANS.USER_ID", "BIGINT NOT NULL");
    // 노는 계획(PLAY)은 과목이 없어서 비어도 됩니다. 공부 계획은 검사 규칙이 막습니다. (#12)
    expected.put("STUDY_PLANS.SUBJECT_ID", "BIGINT NULL");
    expected.put("STUDY_PLANS.PLAN_DATE", "DATE NOT NULL");
    expected.put("STUDY_PLANS.CONTENT", "CHARACTER VARYING(200) NOT NULL");
    expected.put("STUDY_PLANS.TARGET_MINUTES", "INTEGER NOT NULL");
    expected.put("STUDY_PLANS.DONE", "BOOLEAN NOT NULL");
    expected.put("STUDY_PLANS.START_TIME", "TIME NULL");
    expected.put("STUDY_PLANS.KIND", "ENUM NOT NULL");
    expected.put("STUDY_PLANS.ROUTINE_ID", "BIGINT NULL");

    expected.put("STUDY_SESSIONS.ID", "BIGINT NOT NULL");
    expected.put("STUDY_SESSIONS.USER_ID", "BIGINT NOT NULL");
    expected.put("STUDY_SESSIONS.SUBJECT_ID", "BIGINT NULL");
    expected.put("STUDY_SESSIONS.STARTED_AT", "TIMESTAMP WITH TIME ZONE NOT NULL");
    expected.put("STUDY_SESSIONS.ENDED_AT", "TIMESTAMP WITH TIME ZONE NOT NULL");
    expected.put("STUDY_SESSIONS.PAUSE_COUNT", "INTEGER NOT NULL");
    expected.put("STUDY_SESSIONS.PAUSED_MS", "BIGINT NOT NULL");
    expected.put("STUDY_SESSIONS.PLAN_ID", "BIGINT NULL");
    expected.put("STUDY_SESSIONS.TARGET_MINUTES", "INTEGER NULL");
    expected.put("STUDY_SESSIONS.STOP_REASON", "ENUM NULL");
    expected.put("STUDY_SESSIONS.KIND", "ENUM NOT NULL");

    Map<String, String> actual = new LinkedHashMap<>();
    for (Object[] row :
        queryRows(
            "select table_name, column_name, data_type, character_maximum_length, is_nullable"
                + " from information_schema.columns"
                + " where table_schema = 'PUBLIC' and table_name in "
                + OUR_TABLES
                + " order by table_name, ordinal_position")) {
      String length = row[3] == null ? "" : "(" + row[3] + ")";
      String nullable = "NO".equals(row[4]) ? " NOT NULL" : " NULL";
      actual.put(row[0] + "." + row[1], row[2] + length + nullable);
    }

    // 하나라도 더 있거나 빠지거나 다르면 실패한다
    assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
  }

  @Test
  @DisplayName("4개 테이블의 기본키는 id 하나이고, 번호는 DB 가 자동으로 만든다")
  void primaryKeysAreAutoIncrementId() {
    List<Object[]> primaryKeys =
        queryRows(
            "select k.table_name, k.column_name from information_schema.key_column_usage k"
                + " join information_schema.table_constraints c"
                + "   on k.constraint_name = c.constraint_name and k.table_schema = c.table_schema"
                + " where c.constraint_type = 'PRIMARY KEY' and k.table_schema = 'PUBLIC'"
                + "   and k.table_name in "
                + OUR_TABLES
                + " order by k.table_name");
    assertThat(primaryKeys)
        .extracting(row -> row[0] + "." + row[1])
        .containsExactly("STUDY_PLANS.ID", "STUDY_SESSIONS.ID", "SUBJECTS.ID", "USERS.ID");

    List<String> identityColumns =
        query(
            "select table_name from information_schema.columns"
                + " where table_schema = 'PUBLIC' and column_name = 'ID' and is_identity = 'YES'"
                + "   and table_name in "
                + OUR_TABLES
                + " order by table_name");
    assertThat(identityColumns)
        .containsExactly("STUDY_PLANS", "STUDY_SESSIONS", "SUBJECTS", "USERS");
  }

  @Test
  @DisplayName("중복을 막는 제약은 subjects 의 (user_id, name) 하나뿐이다")
  void onlyOneUniqueConstraint() {
    List<Object[]> uniques =
        queryRows(
            "select k.table_name, c.constraint_name, k.column_name"
                + " from information_schema.key_column_usage k"
                + " join information_schema.table_constraints c"
                + "   on k.constraint_name = c.constraint_name and k.table_schema = c.table_schema"
                + " where c.constraint_type = 'UNIQUE' and k.table_schema = 'PUBLIC'"
                + "   and k.table_name in "
                + OUR_TABLES
                + " order by k.table_name, c.constraint_name, k.ordinal_position");

    assertThat(uniques)
        .extracting(row -> row[0] + "." + row[1] + "." + row[2])
        .containsExactly(
            "SUBJECTS.UK_SUBJECTS_USER_ID_NAME.USER_ID", "SUBJECTS.UK_SUBJECTS_USER_ID_NAME.NAME");
  }

  @Test
  @DisplayName("자주 쓰는 조회를 위한 색인은 (user_id, plan_date) 와 (user_id, started_at) 이다")
  void indexesForFrequentQueries() {
    List<Object[]> indexes =
        queryRows(
            "select table_name, index_name, column_name from information_schema.index_columns"
                + " where table_schema = 'PUBLIC' and index_name like 'IDX%'"
                + "   and table_name in "
                + OUR_TABLES
                + " order by table_name, index_name, ordinal_position");

    assertThat(indexes)
        .extracting(row -> row[0] + "." + row[1] + "." + row[2])
        .containsExactly(
            "STUDY_PLANS.IDX_STUDY_PLANS_USER_ID_PLAN_DATE.USER_ID",
            "STUDY_PLANS.IDX_STUDY_PLANS_USER_ID_PLAN_DATE.PLAN_DATE",
            "STUDY_SESSIONS.IDX_STUDY_SESSIONS_USER_ID_STARTED_AT.USER_ID",
            "STUDY_SESSIONS.IDX_STUDY_SESSIONS_USER_ID_STARTED_AT.STARTED_AT");
  }

  @Test
  @DisplayName("users 를 뺀 3개 테이블에 user_id 가 있다 (누구의 데이터인지 항상 알 수 있다)")
  void everyTableExceptUsersHasUserId() {
    List<String> tablesWithUserId =
        query(
            "select table_name from information_schema.columns"
                + " where table_schema = 'PUBLIC' and column_name = 'USER_ID'"
                + "   and table_name in "
                + OUR_TABLES
                + " order by table_name");

    assertThat(tablesWithUserId).containsExactly("STUDY_PLANS", "STUDY_SESSIONS", "SUBJECTS");
  }

  @Test
  @DisplayName("4개 테이블에는 지금 FOREIGN KEY(외래키) 제약이 없다 — userId·subjectId 는 API 에서 검사한다")
  void noForeignKeysYet() {
    // 우리 규칙대로 엔티티에 Long userId 처럼 번호만 적었기 때문에 DB 가 "그런 사용자가 진짜 있는지" 는 검사하지 않는다.
    // 나중에 @ManyToOne 으로 바꿔서 외래키를 걸기로 팀이 정하면 이 테스트를 함께 고친다.
    List<String> foreignKeys =
        query(
            "select constraint_name from information_schema.table_constraints"
                + " where table_schema = 'PUBLIC' and constraint_type = 'FOREIGN KEY'"
                + "   and table_name in "
                + OUR_TABLES);

    assertThat(foreignKeys).isEmpty();
  }

  // DB 예약어를 이름으로 쓰면 PostgreSQL(Neon)로 옮길 때 에러가 납니다. 흔한 것들을 막아 둡니다.
  private static final Set<String> RESERVED =
      Set.of(
          "user",
          "date",
          "order",
          "group",
          "table",
          "time",
          "timestamp",
          "year",
          "month",
          "day",
          "key",
          "value",
          "values",
          "end",
          "start",
          "desc",
          "select",
          "from",
          "where",
          "check",
          "primary",
          "column",
          "index",
          "default",
          "limit",
          "offset",
          "like",
          "all",
          "any");

  // 이 검사는 4개 테이블만이 아니라 앞으로 생기는 모든 테이블에 적용됩니다. (팀 공통 규칙: snake_case, 예약어 금지)
  @Test
  @DisplayName("테이블·컬럼 이름은 모두 snake_case 이고 DB 예약어가 없다")
  void namesAreSnakeCaseAndNotReserved() {
    List<String> names =
        query(
            "select table_name from information_schema.tables where table_schema = 'PUBLIC'"
                + " union select column_name from information_schema.columns where table_schema = 'PUBLIC'");

    assertThat(names).isNotEmpty();
    for (String name : names) {
      assertThat(name).as("이름 %s", name).matches("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");
      assertThat(RESERVED).as("예약어 %s", name).doesNotContain(name.toLowerCase());
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> query(String sql) {
    return em.createNativeQuery(sql).getResultList();
  }

  @SuppressWarnings("unchecked")
  private List<Object[]> queryRows(String sql) {
    return em.createNativeQuery(sql).getResultList();
  }
}
