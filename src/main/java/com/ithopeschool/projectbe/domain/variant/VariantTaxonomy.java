package com.ithopeschool.projectbe.domain.variant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 단원 · 유형 분류체계(고1 공통수학1)입니다. 서버가 켜질 때 src/main/resources/variant/taxonomy.json 을 한 번 읽습니다.
// (docs/VARIANT-SPEC.md 0-1 · 5장)
//
// 단원 · 유형 코드는 이 파일에 있는 것만 씁니다. 통계가 코드 기준으로 쌓이기 때문에 코드는 바꾸지 않고, 이름만 바꿀 수 있습니다.
//   단원 코드: CM1-U01      유형 코드: CM1-U01-T01 (반드시 자기 단원 코드로 시작)
//
// 파일이 잘못돼 있으면(코드가 겹치거나, 유형이 다른 단원 코드로 시작) 서버가 켜지지 않게 바로 알려 줍니다.
// 분류체계가 틀린 채로 돌면 잘못된 코드가 DB 에 쌓이고, 나중에 고치기 어렵기 때문입니다.
@Component
public class VariantTaxonomy {

  static final String FILE = "variant/taxonomy.json";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  // 단원 하나
  public record Unit(String code, String name, List<Type> types) {}

  // 유형 하나. misconceptions 는 그 유형에서 학생들이 자주 하는 실수입니다. (없으면 빈 목록)
  public record Type(String code, String name, String unitCode, List<String> misconceptions) {}

  private final List<Unit> units;
  // 유형 코드 → 유형 (빨리 찾으려고 따로 둡니다)
  private final Map<String, Type> typesByCode = new LinkedHashMap<>();

  // Spring 이 쓰는 생성자: 파일을 읽습니다.
  public VariantTaxonomy() {
    this(readFile());
  }

  // 테스트에서 분류체계를 직접 넣을 때 쓰는 생성자
  VariantTaxonomy(FileShape file) {
    this.units = validate(file);
    for (Unit unit : units) {
      for (Type type : unit.types()) {
        typesByCode.put(type.code(), type);
      }
    }
  }

  public List<Unit> units() {
    return units;
  }

  // 유형 코드로 유형을 찾습니다. 없는 코드면 빈 값입니다.
  public Optional<Type> findType(String typeCode) {
    return Optional.ofNullable(typeCode).map(typesByCode::get);
  }

  // 단원 코드로 단원을 찾습니다. 없는 코드면 빈 값입니다.
  public Optional<Unit> findUnit(String unitCode) {
    return units.stream().filter(u -> u.code().equals(unitCode)).findFirst();
  }

  // 이 단원 · 유형 코드 짝이 분류체계에 있고, 유형이 그 단원에 속하는지 봅니다.
  public boolean isValidPair(String unitCode, String typeCode) {
    return findType(typeCode).map(type -> type.unitCode().equals(unitCode)).orElse(false);
  }

  // AI 에게 주는 분류 목록 글. 한 줄에 하나씩 "단원코드 단원이름 / 유형코드 유형이름" 입니다.
  public String toPromptList() {
    StringBuilder text = new StringBuilder();
    for (Unit unit : units) {
      for (Type type : unit.types()) {
        text.append(unit.code())
            .append(' ')
            .append(unit.name())
            .append(" / ")
            .append(type.code())
            .append(' ')
            .append(type.name())
            .append('\n');
      }
    }
    return text.toString().strip();
  }

  // 파일을 읽습니다. 읽지 못하면 서버가 켜지지 않습니다.
  static FileShape readFile() {
    try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
      return JSON.readValue(in, FileShape.class);
    } catch (IOException e) {
      throw new IllegalStateException("분류체계 파일을 읽지 못했어요: " + FILE, e);
    }
  }

  // 파일 내용을 검사하고 Unit 목록으로 바꿉니다.
  //   - 단원 · 유형 코드가 비었거나 겹치면 안 됩니다.
  //   - 유형 코드는 "자기 단원 코드-" 로 시작해야 합니다. (CM1-U01 의 유형은 CM1-U01-T01 처럼)
  static List<Unit> validate(FileShape file) {
    if (file == null || file.units() == null || file.units().isEmpty()) {
      throw new IllegalStateException("분류체계에 단원이 없어요");
    }
    Set<String> seen = new HashSet<>();
    List<Unit> out = new ArrayList<>();
    for (UnitShape unit : file.units()) {
      requireCode(unit.code(), "단원");
      if (!seen.add(unit.code())) {
        throw new IllegalStateException("단원 코드가 겹쳐요: " + unit.code());
      }
      List<Type> types = new ArrayList<>();
      for (TypeShape type : unit.types() == null ? List.<TypeShape>of() : unit.types()) {
        requireCode(type.code(), "유형");
        if (!seen.add(type.code())) {
          throw new IllegalStateException("유형 코드가 겹쳐요: " + type.code());
        }
        if (!type.code().startsWith(unit.code() + "-")) {
          throw new IllegalStateException(
              "유형 " + type.code() + " 은(는) 단원 " + unit.code() + " 에 속하지 않아요");
        }
        List<String> misconceptions =
            type.misconceptions() == null ? List.of() : List.copyOf(type.misconceptions());
        types.add(new Type(type.code(), type.name(), unit.code(), misconceptions));
      }
      if (types.isEmpty()) {
        throw new IllegalStateException("단원 " + unit.code() + " 에 유형이 없어요");
      }
      out.add(new Unit(unit.code(), unit.name(), List.copyOf(types)));
    }
    return List.copyOf(out);
  }

  private static void requireCode(String code, String what) {
    if (code == null || code.isBlank()) {
      throw new IllegalStateException(what + " 코드가 비어 있어요");
    }
  }

  // ---- 파일 모양 (이 파일 안과 테스트에서만 씁니다) ----

  @JsonIgnoreProperties(ignoreUnknown = true)
  record FileShape(String version, List<UnitShape> units) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record UnitShape(String code, String name, List<TypeShape> types) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TypeShape(String code, String name, List<String> misconceptions) {}
}
