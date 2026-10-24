package com.ithopeschool.projectbe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// 엔티티 "코드" 가 팀 규칙(CLAUDE.md 3장, 이슈 #3 완료 조건)과 FE 타입 이름에 맞는지 확인합니다.
@DataJpaTest
class EntityRuleTest {

  @Autowired private EntityManager em;

  private List<Class<?>> entityClasses() {
    return em.getMetamodel().getEntities().stream()
        .<Class<?>>map(EntityType::getJavaType)
        .sorted((a, b) -> a.getSimpleName().compareTo(b.getSimpleName()))
        .toList();
  }

  private static List<Field> fields(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
        .toList();
  }

  private static Set<String> fieldNames(Class<?> type) {
    return fields(type).stream().map(Field::getName).collect(Collectors.toSet());
  }

  // 이슈 #3 에서 만든 엔티티 4개. 아래 테스트 중 "4개" 라고 적은 것은 이 4개만 검사하고,
  // 나머지(팀 공통 규칙)는 앞으로 다른 기능에서 만드는 엔티티까지 모두 검사합니다.
  private static final List<Class<?>> OUR_ENTITIES =
      List.of(User.class, Subject.class, StudyPlan.class, StudySession.class);

  @Test
  @DisplayName("4개: 사용자·과목·공부 계획·공부 기록이 JPA 엔티티로 등록돼 있다")
  void fourEntitiesAreRegistered() {
    assertThat(entityClasses())
        .contains(User.class, Subject.class, StudyPlan.class, StudySession.class);
  }

  @Test
  @DisplayName("모든 엔티티는 domain/<기능>/ 폴더 하나 안에 있다")
  void entitiesLiveInFeatureFolders() {
    for (Class<?> type : entityClasses()) {
      assertThat(type.getPackageName())
          .as("%s 의 위치", type.getSimpleName())
          .matches("com\\.ithopeschool\\.projectbe\\.domain\\.[a-z]+");
    }
    assertThat(OUR_ENTITIES)
        .extracting(Class::getPackageName)
        .containsExactly(
            "com.ithopeschool.projectbe.domain.user",
            "com.ithopeschool.projectbe.domain.subject",
            "com.ithopeschool.projectbe.domain.plan",
            "com.ithopeschool.projectbe.domain.session");
  }

  @Test
  @DisplayName("모든 엔티티에는 값을 바꾸는 public set 메서드가 없다 (@Setter 금지)")
  void noPublicSetters() {
    for (Class<?> type : entityClasses()) {
      List<String> setters =
          Arrays.stream(type.getDeclaredMethods())
              .filter(method -> Modifier.isPublic(method.getModifiers()))
              .map(Method::getName)
              .filter(name -> name.startsWith("set"))
              .toList();
      assertThat(setters).as("%s 의 set 메서드", type.getSimpleName()).isEmpty();
    }
  }

  @Test
  @DisplayName("4개: @Data 를 쓰면 생기는 equals·hashCode·toString 이 없다")
  void noDataGeneratedMethods() {
    for (Class<?> type : OUR_ENTITIES) {
      List<String> generated =
          Arrays.stream(type.getDeclaredMethods())
              .map(Method::getName)
              .filter(name -> Set.of("equals", "hashCode", "toString", "canEqual").contains(name))
              .toList();
      assertThat(generated).as("%s 의 @Data 흔적", type.getSimpleName()).isEmpty();
    }
  }

  @Test
  @DisplayName("4개: 기본 생성자는 protected 이고(JPA 전용), 값을 받는 public 생성자가 따로 있다")
  void constructors() {
    for (Class<?> type : OUR_ENTITIES) {
      Constructor<?> noArgs;
      try {
        noArgs = type.getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        throw new AssertionError(type.getSimpleName() + " 에 기본 생성자가 없습니다", e);
      }
      assertThat(Modifier.isProtected(noArgs.getModifiers()))
          .as("%s 의 기본 생성자는 protected", type.getSimpleName())
          .isTrue();
      assertThat(
              Arrays.stream(type.getDeclaredConstructors())
                  .anyMatch(c -> Modifier.isPublic(c.getModifiers()) && c.getParameterCount() > 0))
          .as("%s 에 값을 받는 public 생성자", type.getSimpleName())
          .isTrue();
    }
  }

  @Test
  @DisplayName("모든 엔티티의 필드는 private 이고 camelCase 이다")
  void fieldsArePrivateCamelCase() {
    for (Class<?> type : entityClasses()) {
      for (Field field : fields(type)) {
        assertThat(Modifier.isPrivate(field.getModifiers()))
            .as("%s.%s 는 private", type.getSimpleName(), field.getName())
            .isTrue();
        assertThat(field.getName()).matches("[a-z][a-zA-Z0-9]*");
      }
    }
  }

  @Test
  @DisplayName("4개: id 는 Long 이고 DB 가 자동으로 만들어 준다")
  void idIsGeneratedLong() {
    for (Class<?> type : OUR_ENTITIES) {
      Field id = fieldOf(type, "id");
      assertThat(id.getType()).as("%s.id", type.getSimpleName()).isEqualTo(Long.class);
      assertThat(id.isAnnotationPresent(Id.class)).isTrue();
      assertThat(id.isAnnotationPresent(GeneratedValue.class)).isTrue();
    }
  }

  @Test
  @DisplayName("4개: 사용자 테이블을 뺀 3개 엔티티에 Long userId 가 있다")
  void everyEntityExceptUserHasUserId() {
    for (Class<?> type : OUR_ENTITIES) {
      if (type == User.class) {
        assertThat(fieldNames(type)).doesNotContain("userId");
        continue;
      }
      assertThat(fieldOf(type, "userId").getType())
          .as("%s.userId", type.getSimpleName())
          .isEqualTo(Long.class);
    }
  }

  @Test
  @DisplayName(
      "모든 엔티티에서 시각은 Instant, 날짜는 LocalDate, 하루 안의 몇 시 몇 분은 LocalTime 만 쓴다 (Date·LocalDateTime 금지)")
  void onlyInstantAndLocalDateForTime() {
    // LocalTime 은 계획의 시작 시각(19:00)처럼 "날짜와 상관없는 몇 시 몇 분" 에만 씁니다. (#12)
    Set<Class<?>> allowedTimeTypes = Set.of(Instant.class, LocalDate.class, LocalTime.class);
    for (Class<?> type : entityClasses()) {
      for (Field field : fields(type)) {
        String typeName = field.getType().getName();
        boolean looksLikeTime =
            typeName.startsWith("java.time.")
                || typeName.equals("java.util.Date")
                || typeName.startsWith("java.sql.");
        if (looksLikeTime) {
          assertThat(allowedTimeTypes)
              .as("%s.%s 의 타입 %s", type.getSimpleName(), field.getName(), typeName)
              .contains(field.getType());
        }
      }
    }
  }

  @Test
  @DisplayName("모든 엔티티에서 enum 컬럼은 글자(EnumType.STRING)로 저장한다")
  void enumsAreStoredAsString() {
    for (Class<?> type : entityClasses()) {
      for (Field field : fields(type)) {
        if (field.getType().isEnum()) {
          Enumerated enumerated = field.getAnnotation(Enumerated.class);
          assertThat(enumerated)
              .as("%s.%s 에 @Enumerated", type.getSimpleName(), field.getName())
              .isNotNull();
          assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
        }
      }
    }
  }

  // ---- FE 타입(src/types/index.ts)과 이름 맞추기 ----

  @Test
  @DisplayName("과목 색 enum 은 FE 의 SubjectColor 다섯 가지와 같다")
  void subjectColorsMatchFrontend() {
    List<String> frontendColors = List.of("red", "orange", "green", "blue", "purple");

    assertThat(Arrays.stream(SubjectColor.values()).map(c -> c.name().toLowerCase()))
        .containsExactlyElementsOf(frontendColors);
  }

  // FE 타입의 필드 목록 (FE 저장소 src/types/index.ts 에서 옮겨 적음)
  private static final Map<String, List<String>> FRONTEND_FIELDS =
      Map.of(
          "User", List.of("id", "name"), // FE 의 AuthUser
          "Subject", List.of("id", "name", "color"),
          "StudyPlan",
              List.of(
                  "id",
                  "date",
                  "subjectId",
                  "content",
                  "targetMinutes",
                  "done",
                  "startTime",
                  "kind",
                  "routineId"),
          "StudySession",
              List.of(
                  "id",
                  "subjectId",
                  "startedAt",
                  "endedAt",
                  "pauseCount",
                  "pausedMs",
                  "planId",
                  "targetMinutes",
                  "stopReason",
                  "kind",
                  // 카드 기준으로 넓힌 칸 (#224)
                  "cardId",
                  "mode",
                  "pauses",
                  "endedEarly",
                  "endReport",
                  "memos",
                  "playTag",
                  "overRestMs"));

  // FE 와 이름이 달라지는 필드는 이 두 가지뿐이다
  private static final Map<String, String> RENAMED =
      Map.of(
          "StudyPlan.date", "planDate", // date 는 DB 예약어라 쓸 수 없다
          "User.name", "nickname"); // 실명이 아니라 별명이라는 뜻을 분명히 하려고

  // FE 의 묶음 칸 하나가 엔티티에서는 여러 칸으로 나뉜 것 (DB 의 한 줄에는 묶음을 담을 수 없어서)
  // FE StudySession.endReport
  //   = { checked, shortfallReason, withWhat, proof: { link, summary }, goodReasons }
  //   → 팝업에 답했는지(endReported) + 칸마다 하나씩 (#224)
  //   + 관리 4단계라서 증빙이 꼭 필요했는지(proofRequired, FE 의 endReport.proofRequired, #234)
  private static final Map<String, List<String>> SPLIT =
      Map.of(
          "StudySession.endReport",
          List.of(
              "endReported",
              "checkedItems",
              "shortfallReason",
              "withWhat",
              "proofLink",
              "proofSummary",
              "goodReasons",
              "proofRequired"));

  @ParameterizedTest(name = "{0} 엔티티")
  @ValueSource(strings = {"User", "Subject", "StudyPlan", "StudySession"})
  @DisplayName("FE 타입의 필드는 엔티티에 모두 있고, 엔티티에만 있는 필드는 정해진 것뿐이다")
  void frontendFieldsAreCovered(String entityName) {
    Class<?> type =
        entityClasses().stream()
            .filter(c -> c.getSimpleName().equals(entityName))
            .findFirst()
            .orElseThrow();

    Set<String> expected = new HashSet<>();
    for (String frontendField : FRONTEND_FIELDS.get(entityName)) {
      String key = entityName + "." + frontendField;
      if (SPLIT.containsKey(key)) {
        expected.addAll(SPLIT.get(key));
      } else {
        expected.add(RENAMED.getOrDefault(key, frontendField));
      }
    }
    // 엔티티에만 있는 필드: 누구의 것인지 알려 주는 userId,
    // 그리고 users 의 가입 시각 createdAt · 온보딩을 마친 시각 onboardedAt (FE 에는 마쳤는지 onboarded 로만 보냄, 2026-10-17)
    if (entityName.equals("User")) {
      expected.add("createdAt");
      expected.add("onboardedAt");
    } else {
      expected.add("userId");
    }

    assertThat(fieldNames(type)).containsExactlyInAnyOrderElementsOf(expected);
  }

  private static Field fieldOf(Class<?> type, String name) {
    try {
      return type.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      throw new AssertionError(type.getSimpleName() + " 에 " + name + " 필드가 없습니다", e);
    }
  }
}
