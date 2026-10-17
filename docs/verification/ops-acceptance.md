# 운영 이슈 완료 조건 검증

검증일: 2026-10-17. 대상: solmin6320 담당 #8, #9, #75, #76.
기준 코드: `933f8f5aeff946819e061ec8afb9a836be70605b` (origin/develop).
각 이슈의 본문·댓글을 읽고 현재 코드와 대조했습니다. 외부 서비스 설정은 변경하지 않았습니다.
`통과`는 해당 범위만 확인했다는 뜻이며, `대기`가 남은 이슈는 완료로 판정하지 않습니다.

## #8 배포

원문: https://github.com/Tmax-IT-Hope-School/Project-BE/issues/8

| 완료 조건 | 판정 | 증거·남은 확인 |
| --- | --- | --- |
| 후보와 선택 이유를 README에 기록 | 통과(문서) | README의 배포 후보 비교·Render 선정 이유. 요금표의 현재 가격을 이번 작업에서 재검증하지는 않음 |
| 배포 health가 200 | 통과(실서버) | `https://project-be-mxxc.onrender.com/api/v1/health` → HTTP 200, `{"status":"ok","db":"connected"}` |
| DB·GPT 비밀값은 배포 서비스에만 | 대기 | prod 설정은 환경변수 참조. 실제 Render 설정과 전체 커밋·외부 로그 무노출까지 증명하지 않음 |
| 운영 main / 개발 dev DB 분리 | 대기 | 설정·안내는 분리되어 있으나 접속 대상의 Neon 브랜치 신원을 확인하지 못함. health 성공만으로 분리 입증 불가 |

참고 조건: 선행 #4는 CLOSED. 댓글에서 추가한 #75는 아래 검증에서 대기 항목이 남음.
FE #53 배포 주소에서의 실제 로그인·업무 API 연결은 이번 서버 health 확인 범위 밖.

## #9 예약 작업

원문: https://github.com/Tmax-IT-Hope-School/Project-BE/issues/9

| 완료 조건 | 판정 | 증거·남은 확인 |
| --- | --- | --- |
| 실행 방식과 이유 README 기록 | 통과(문서) | README 예약 작업: 외부 스케줄러 + POST 작업 API, 대안 비교 |
| 배포에서 10분마다 연습 작업 로그 | 대기 | cron-job.org 실행 이력 및 Render 로그 대조 필요. ping 200은 작업 실행 증거가 아님 |
| 중복 호출에도 결과 안전 | 통과(로컬 테스트) | `JobFlowTest.callingTwiceKeepsTheResultSafe`, `PracticeJobTest`, `JobRunnerTest`의 동시 실행 건너뛰기·실패 후 재시도 |
| 임의 호출 차단 | 통과(로컬 테스트) | `JobControllerTest`, `JobControllerNoKeyConfiguredTest`, `JobFlowTest`: 누락·오류 키 거절 및 실행되지 않음 |

참고 조건: #8 배포의 health는 확인했으나 운영 설정 검증은 남음.
이슈에서 언급하는 기획서 v2 3장 2번 원문은 저장소에서 찾지 못해 독립적으로 대조하지 못함.
연습 결과는 메모리 저장소이며 재시작 후 보존되지 않음. 실제 일별 요약의 DB 덮어쓰기는 이 테스트만으로 보증하지 않음.

## #75 Neon

원문: https://github.com/Tmax-IT-Hope-School/Project-BE/issues/75

| 완료 조건 | 판정 | 증거·남은 확인 |
| --- | --- | --- |
| 프로젝트와 main/dev 생성 | 대기 | Neon 콘솔에서 두 브랜치 존재 및 접속 대상 대조 필요 |
| 스키마 방식 결정 이유 댓글 | 대기 | README·CLAUDE.md에 수동 schema.sql 이유 있음. 이슈 댓글은 조회 시 0개이므로 댓글 조건 미충족 |
| 접속 후 health 200 | 부분 통과 | 공개 배포 health의 DB 연결 성공. Neon 프로젝트·브랜치 신원은 미확인 |
| 설정 없이 H2 부팅 | 부분 통과(테스트) | `ProjectBeApplicationTests.contextLoads` 통과. 테스트 전용 H2 설정을 사용하므로 실제 기본 실행의 HTTP 확인은 미확인 |
| 서버 재시작 후 데이터 보존 | 대기 | 실제 Neon dev에서 승인된 테스트 데이터로 재시작 전후 대조 필요. 운영 DB에는 연습 데이터를 쓰지 않음 |
| 모든 테이블 PostgreSQL 생성 | 대기 | `SchemaSqlTest`는 H2 PostgreSQL 호환 모드로 엔티티 대조. 실제 PostgreSQL 문법·전체 배포 스키마 검증을 대체하지 않음 |
| 주소·비밀번호 코드/커밋 무노출 | 대기 | 현재 설정은 환경변수. 모든 과거 커밋의 무노출은 이번 검사로 보증하지 않음 |
| README 가입→설정 절차 | 통과(문서) | README 운영 DB: 가입, main/dev, SQL, JDBC 형식, 프로필 설정 |
| CLAUDE.md 원격 DB 규칙 변경 | 통과(문서) | 원격 DB 절에 local/H2, local+neon/dev(validate), prod/main(none) 구분 |

참고 조건: #3·#4는 CLOSED, #76은 아래 대기 조건 존재. H2 데이터 이관은 요구하지 않음.
이슈 본문은 prod `validate`로 설명하지만 현재 prod는 `none`이다. 이는 DB 장애에도 서버가 기동하도록 하는 #4 정책으로 문서화되어 있다.
따라서 운영 health 성공을 엔티티·스키마 일치 검사 통과로 해석하지 않는다.

## #76 시크릿 매니저

원문: https://github.com/Tmax-IT-Hope-School/Project-BE/issues/76

| 완료 조건 | 판정 | 증거·남은 확인 |
| --- | --- | --- |
| 서비스·학생 수신·dev/prod 결정 이유 댓글 | 대기 | docs/SECRETS.md에 Infisical 개인 계정 선택안 있음. 이슈 댓글은 조회 시 0개. 실제 인원·공식 가격 재확인 및 결정 필요 |
| 관리자 입력 후 학생 PC 실행 | 대기 | 가입·초대·주입 실행을 하지 않았으므로 통과 처리 불가 |
| 시크릿 매니저 없이 H2 | 부분 통과(테스트) | 시크릿 매니저 실행 없이 H2 Spring 컨텍스트 테스트 통과. 별도 JVM의 실제 HTTP 확인은 미확인 |
| 코드·커밋·로그 실제 비밀값 없음 | 대기 | 테스트의 가짜 비밀값 로그 억제는 확인 가능하나 전체 운영 로그·과거 커밋 무노출의 증거는 아님 |
| README 설치→로그인→실행 | 통과(문서) | README 및 docs/SECRETS.md 절차 있음 |
| /setup·/run 반영 | 통과(문서) | `.claude/commands/setup.md`, `run.md`에 개인 로그인 및 run 안내 |
| 유출 시 폐기·재발급 README | 통과(문서) | README 비밀값 절과 docs/SECRETS.md 노출·탈퇴 절 |
| CLAUDE.md 비밀값 규칙 갱신 | 통과(문서) | Infisical 개인 로그인 선택안, 운영 값 Render 분리, 사람 실행 전 미완료 명시 |

참고 조건: 현재 기본 local 프로필은 `OPENAI_API_KEY`를 AI 설정으로 읽지 않는다.
Infisical로 환경변수를 전달하는 것과 GPT 개발 실행 성공은 다르므로 SECRETS.md에 제한을 명시했다.
Google client ID는 local에서도 읽지만 실제 브라우저 로그인 검증은 별도이다.
공식 설치/가격 링크는 SECRETS.md에 있으며 이번 작업에서는 CLI 설치·계정 로그인·서비스 가격의 독립 재검증을 수행하지 않았다.

## 실행 결과

- `gradlew.bat spotlessCheck build` 성공: 2026-10-17 17:42 KST, 5분 50초.
- `build/test-results/test/TEST-*.xml`을 XML 파서로 집계: 2,275개, 실패 0, 오류 0, 건너뜀 0.
- 예약 작업 핵심 5개 클래스: `JobControllerNoKeyConfiguredTest` 5개, `JobControllerTest` 20개,
  `JobFlowTest` 4개, `JobRunnerTest` 7개, `PracticeJobTest` 5개 — 총 41개 모두 통과.
- `ProjectBeApplicationTests` 1개 및 `SchemaSqlTest` 1개 통과.
- 공개 서버 재확인: 2026-10-17 17:46:41 KST에 health HTTP 200 (`status: ok`, `db: connected`),
  ping HTTP 200 (`status: ok`). 인증 정보 전송이나 운영 데이터 변경 없이 조회했다.
- 이번 변경은 문서 3개뿐이며 Java·설정·SQL 변경은 없다.

별도 JVM을 띄워 로컬 HTTP health/practice를 확인하는 명령은 자동 승인 검토에서 `blocked by policy`로 거절되었다.
구체적인 거절 사유는 제공되지 않았다. 우회 실행하지 않았으며 실제 로컬 HTTP smoke는 미확인이다.
SpringBootTest는 로컬 H2 기반이며 테스트 전용 설정이 DB 이름을 임의 UUID로 격리한다.
따라서 테스트 성공을 학생 PC의 실제 Infisical 실행 또는 Neon 연결 성공으로 확대 해석하지 않는다.
