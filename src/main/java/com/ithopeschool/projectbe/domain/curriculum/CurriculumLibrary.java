package com.ithopeschool.projectbe.domain.curriculum;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 성취기준 목록을 서버가 켜질 때 한 번 읽어 메모리에 둡니다. "가벼운 RAG" 의 "자료" 부분입니다. (2026-10-18)
//
// ■ 자료: src/main/resources/curriculum/*.json
//   교육부 고시 제2022-33호(2022 개정 교육과정)의 교과별 별책(국어 · 수학 · 영어 · 과학 · 사회 · 도덕) 원문에서
//   고등학교 과목의 성취기준 문장을 고치지 않고 옮긴 것입니다. 파일 맨 위에 출처(source · sourceUrl)와 받은 날짜가 있습니다.
//   국가의 고시는 저작권법 제7조(보호받지 못하는 저작물)라서 옮겨 써도 됩니다.
// ■ DB 를 쓰지 않습니다. (새 테이블 · 칸 없이 — 테이블 설계는 학생이 합니다)
//   파일이 jar 안에 같이 들어가서 서버가 켜질 때마다 같은 내용을 읽습니다. 고치려면 파일을 고치고 다시 배포합니다.
// ■ 2015 개정 교육과정(2026년 고3)의 성취기준은 아직 넣지 않았습니다. 그 학생에게는 교육과정 이름만 알려 줍니다.
@Component
public class CurriculumLibrary {

  private static final Logger log = LoggerFactory.getLogger(CurriculumLibrary.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();
  static final String FILES = "classpath:curriculum/*.json";

  private final List<AchievementStandard> standards;

  // Spring 이 쓰는 생성자: 파일을 읽습니다. 읽지 못한 파일은 건너뛰고 경고만 남깁니다. (서버는 켜지게)
  public CurriculumLibrary() {
    this(loadAll());
  }

  // 테스트에서 성취기준을 직접 넣을 때 쓰는 생성자
  CurriculumLibrary(List<AchievementStandard> standards) {
    this.standards = List.copyOf(standards);
  }

  // 그 교육과정의 성취기준 전체 (파일 순서 = 과목 · 단원 순서). 없으면 빈 목록
  public List<AchievementStandard> standards(String curriculum) {
    return standards.stream().filter(s -> s.curriculum().equals(curriculum)).toList();
  }

  // 그 교육과정에서 질문에 맞는 성취기준 limit 줄 (CurriculumSearch)
  public List<AchievementStandard> search(
      String curriculum, List<String> todaySubjects, String query, int limit) {
    return CurriculumSearch.rank(standards(curriculum), todaySubjects, query, limit);
  }

  // 읽은 줄 수 (서버 켤 때 확인 · 테스트용)
  public int size() {
    return standards.size();
  }

  static List<AchievementStandard> loadAll() {
    List<AchievementStandard> all = new ArrayList<>();
    try {
      Resource[] files = new PathMatchingResourcePatternResolver().getResources(FILES);
      for (Resource file : files) {
        try (InputStream in = file.getInputStream()) {
          all.addAll(read(JSON.readValue(in, FileShape.class)));
        } catch (IOException | JacksonException e) {
          log.warn("[교육과정] 파일을 읽지 못했어요: {} ({})", file.getFilename(), e.getClass().getSimpleName());
        }
      }
    } catch (IOException e) {
      log.warn("[교육과정] 파일 목록을 읽지 못했어요: {}", e.getClass().getSimpleName());
    }
    log.info("[교육과정] 성취기준 {}줄을 읽었어요", all.size());
    return all;
  }

  // 파일 한 개 → 성취기준 줄들. 코드 · 문장이 빈 줄은 건너뜁니다.
  static List<AchievementStandard> read(FileShape file) {
    List<AchievementStandard> out = new ArrayList<>();
    if (file == null || file.subjects() == null || file.curriculum() == null) {
      return out;
    }
    for (SubjectShape subject : file.subjects()) {
      if (subject.standards() == null) {
        continue;
      }
      for (StandardShape standard : subject.standards()) {
        if (standard.code() == null || standard.text() == null || standard.text().isBlank()) {
          continue;
        }
        out.add(
            new AchievementStandard(
                file.curriculum(),
                subject.name(),
                standard.code(),
                standard.area() == null ? "" : standard.area(),
                standard.text()));
      }
    }
    return out;
  }

  // ---- 파일의 JSON 모양 (이 파일 안에서만 씁니다) ----
  //   { "source": "…", "sourceUrl": "…", "retrievedAt": "2026-10-18", "curriculum": "2022",
  //     "subjects": [ { "name": "미적분Ⅰ", "type": "일반 선택 과목",
  //                     "standards": [ { "code": "12미적Ⅰ-01-01", "grade": "고2~3", "area": "…",
  // "text": "…" } ] } ] }
  @JsonIgnoreProperties(ignoreUnknown = true)
  record FileShape(String source, String curriculum, List<SubjectShape> subjects) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SubjectShape(String name, List<StandardShape> standards) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record StandardShape(String code, String area, String text) {}
}
