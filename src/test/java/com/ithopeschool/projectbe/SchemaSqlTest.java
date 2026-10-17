package com.ithopeschool.projectbe;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// db/schema.sql(운영 DB 의 테이블을 만드는 SQL)이 엔티티와 맞는지 검사합니다. (이슈 #75)
//
// 1) 빈 H2 DB 에 db/schema.sql 을 실행해 테이블을 만들고
// 2) Hibernate 가 ddl-auto: validate 로 "엔티티와 테이블이 맞는지" 검사합니다. (Neon 에서 서버가 켜질 때와 같은 검사)
// 엔티티에 필드를 추가하고 schema.sql 을 안 고치면, 이 테스트가 켜지지 못하고 어느 테이블·컬럼이 없는지 알려 줍니다.
//
// ⚠️ H2 의 PostgreSQL 흉내 모드로 검사하므로, PostgreSQL 에서만 나는 문법 문제까지 잡지는 못합니다.
//    schema.sql 을 고치면 Neon 의 dev 브랜치에서 한 번 실행해 보고 서버를 켜 봅니다. (README "운영 DB(Neon)")
@DataJpaTest(
    properties = {
      // 테스트용 DB 로 바꿔치기하지 않고 아래 주소의 빈 H2 를 그대로 씁니다.
      "spring.test.database.replace=none",
      "spring.datasource.url=jdbc:h2:mem:schema_sql_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      // 서버가 켜질 때 db/schema.sql 을 실행합니다. (Neon SQL Editor 에서 실행하는 것과 같은 역할)
      "spring.sql.init.mode=always",
      "spring.sql.init.schema-locations=file:db/schema.sql",
      // 테이블을 만들지 않고 검사만 합니다. (application-neon.yml 과 같은 값)
      "spring.jpa.hibernate.ddl-auto=validate"
    })
class SchemaSqlTest {

  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("schema.sql 로 만든 테이블이 엔티티와 맞아서 저장하고 다시 읽을 수 있다")
  void schemaMatchesEntities() {
    // 여기까지 왔다는 것은 validate 검사를 통과했다는 뜻입니다. 실제로 한 줄 넣고 읽어 봅니다.
    User saved = userRepository.save(new User("스키마 검사"));

    assertThat(userRepository.findById(saved.getId())).isPresent();
  }
}
