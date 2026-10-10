# Project-BE

IT 희망학교 11기 프로젝트(학생·입시생 자기관리 앱)의 백엔드 서버입니다.
프론트엔드는 [Project-FE](https://github.com/Tmax-IT-Hope-School/Project-FE)에 있어요.

| 항목 | 사용 기술 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1 (Web MVC, Data JPA, Validation) |
| 빌드 | Gradle (wrapper 포함, 따로 설치 불필요) |
| 코드 정리 | Spotless (google-java-format) |
| 테스트 | JUnit 5 |
| DB | 로컬: H2 메모리 DB / 배포: 미정 (Supabase Postgres 후보) |
| 배포 | 미정 (GCP Cloud Run 후보, `Dockerfile` 준비됨) |

## 처음 받았을 때

```bash
# 1) JDK 21 설치 (한 번만)
brew install --cask temurin@21     # Windows: https://adoptium.net 에서 Temurin 21 설치

# 2) 받기
git clone https://github.com/ApplyYoon/Project-BE.git
cd Project-BE

# 3) 서버 켜기 (처음엔 라이브러리 받느라 좀 걸려요)
./gradlew bootRun                  # Windows: gradlew.bat bootRun
```

- `http://localhost:8080/api/v1/health` → `{"status":"ok"}`가 나오면 성공이에요.
- 로컬에서는 DB 설치 없이 H2 메모리 DB를 써요. 서버를 끄면 데이터가 사라져요. 데이터는 `http://localhost:8080/h2-console`에서 볼 수 있어요(JDBC URL `jdbc:h2:mem:projectbe`, 사용자 `sa`).
- IntelliJ를 쓴다면 폴더를 그대로 열고, Lombok 플러그인과 Annotation Processing을 켜 주세요.

## 자주 쓰는 명령

```bash
./gradlew bootRun          # 서버 실행
./gradlew test             # 테스트
./gradlew spotlessApply    # 코드 자동 정리 (커밋 전에)
./gradlew build            # CI와 같은 전체 검사
```

## 폴더 구조

```
src/main/java/com/ithopeschool/projectbe/
├── ProjectBeApplication.java   # 앱 시작점
├── global/                     # 여러 기능이 같이 쓰는 것 (설정, 공통 예외 등)
│   └── config/WebConfig.java   # CORS 설정
└── domain/                     # 기능별 폴더 하나씩
    └── health/                 # 예시: Controller + 응답 DTO
src/main/resources/
├── application.yml             # 공통 설정
├── application-local.yml       # 로컬 (H2)
└── application-prod.yml        # 배포 (환경변수로 DB 주입)
```

## 협업 규칙

자세한 건 [AGENTS.md](./AGENTS.md)에 있어요. Claude Code 같은 AI도 이 파일을 읽고 그대로 따라요.

1. 할 일은 GitHub 이슈로 만들고, 칸반 보드에서 담당자를 정해요.
2. `main`에 직접 push하지 않아요. 브랜치를 따서 PR을 올려요.
3. PR은 CI(코드 정리 검사 + 빌드 + 테스트)를 통과하고, 리뷰 1명 승인을 받으면 squash merge해요.
