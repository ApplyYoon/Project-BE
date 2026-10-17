# 보안·안전 완료 조건 검증 (#70, #56)

검증 기준: 2026-10-17, BE `933f8f5`에서 분리한 `test/security-acceptance`.
솔민님 담당 두 이슈만 확인했으며 다른 사람의 기능·SQL·공통 API/DB 문서는 수정하지 않았다.

## 자료와 판단 기준

- [BE #70](https://github.com/Tmax-IT-Hope-School/Project-BE/issues/70), [BE #56](https://github.com/Tmax-IT-Hope-School/Project-BE/issues/56): 본문 및 댓글 확인.
- [공용 Project #8](https://github.com/Tmax-IT-Hope-School/Project/issues/8): 개인정보 체크리스트·전문가 검토 조건 확인.
- [FE #95](https://github.com/Tmax-IT-Hope-School/Project-FE/issues/95), [FE #96](https://github.com/Tmax-IT-Hope-School/Project-FE/issues/96): 예시와 도움 화면 조건 확인.
- FE `origin/develop`의 `src/lib/safetyBrake.ts` (`c0c44de`) 주석 예시를 BE `CrisisTextTest`, `SafetyBrakeTest` 및 경계값 테스트와 대조했다. FE를 실행하거나 수정하지 않았다.
- 기획서 v2 원문 전체는 확인하지 못했다. 이슈가 인용한 5장·7장 조건만 대조했다.

## #70 판정

| 완료 조건 | 판정 | 근거와 남은 범위 |
| --- | --- | --- |
| 모든 API의 타 사용자 접근 자동 차단 | 부분 확인 | `LoginRequiredFlowTest`는 등록 API 전체의 무인증 목록을 검사한다. `SubjectFlowTest`, `ExamFlowTest`, `MessageFlowTest`, `SettingsFlowTest`, `OnboardingFlowTest`, `StudySessionQueryFlowTest` 등의 소유권 테스트가 있다. **무인증 검사와 두 사용자 접근 검사는 다르므로 전체 완료로 보지 않는다.** 모든 경로·메서드의 두 사용자 읽기/변경/삭제 매트릭스는 남아 있다. |
| 저장소·로그·응답에 비밀 없음 | 부분 확인 | 현재 추적 파일에서 긴 OpenAI 키·비밀번호 포함 PostgreSQL URL 패턴 검사. 후보 2파일은 README 자리표시자와 pre-commit 안내 예시다. `ExceptionPrivacyTest`는 중첩 예외의 DB 주소·원문 비노출을 검증한다. 운영·외부 제공자 로그, 모든 이력·키 종류는 이번 검사 범위 밖이다. |
| 통화 원본 미저장 및 보관 기간 고지 | 부분 확인 | `CallRecord`에 녹음·자막 원본 저장 필드가 없다. 통화 서비스 테스트는 요약 저장과 위기 통화 내용 제외를 검증한다. **최근 30일 조회는 만료 삭제가 아니다.** 보관 기간 합의·삭제 작업·FE 고지는 남았다. |
| 일기·기분 원문 로그 미노출 | 부분 확인 | 체크인 자유입력 3칸 각각 위기 입력을 넣고 서버 출력에 원문이 없는 통합 회귀 테스트를 추가했다. 예외 로그 테스트도 실행한다. 일기 API는 아직 없고 운영 로깅 설정은 별도 검증이 필요하다. |
| 미성년자 돈 걸기·상금 없음 | 서버 범위 확인 | 현재 서버 도메인에 결제·베팅·상금 기능이 없다. FE 전체 및 향후 기능까지 보증하지 않는다. |

## #56 판정

| 완료 조건 | 판정 | 근거와 남은 범위 |
| --- | --- | --- |
| 모든 글 입력이 공통 보호 경로 사용 | 부분 확인 | 체크인 세 자유입력은 `SafetyService.check`를 통과한다. 통화는 기존 키워드·통화 정리 GPT 판정을 사용하고 같은 `activate`를 호출한다. 문자 그대로 검사 함수 하나로 통합된 것은 아니며 일기 #60 연결은 미구현이다. |
| 키워드 또는 GPT 하나라도 감지 | 구현 경로 확인 | `SafetyServiceTest`, `CallRecordServiceTest`가 키워드·GPT 판정과 장애를 검증한다. AI가 꺼지면 체크인은 키워드만 검사한다. 실제 외부 GPT 호출 품질은 단위 테스트로 입증하지 않는다. 통화 AI 장애의 503/429와 체크인의 보수적 보호 동작은 서로 다르다. |
| 강도 0·예약 채찍 취소 | 부분 확인 | 기존 강도 상승 예약 제거, 앱 잠금 해제, STICK 저장 차단을 검증한다. 채찍 대기열이 아직 없으므로 대기열 취소를 구현·검증했다고 하지 않는다. 동시 발송 원자 취소도 보장하지 않는다. |
| 감지 기록·즉시 도움 응답 | 구현 경로 확인 | `SafetyFlowTest`가 체크인 각 자유입력의 `showHelp`, 안내와 1388/109, 사용자별 보호를 검증한다. 회복 메시지와 시각을 저장하며 감지 원문은 해당 메시지에 넣지 않는다. 체크인 자체의 자유입력 저장과는 별개다. |
| 전문가 검토 필요 명시 | 문서 있음 / 실제 검토 미확인 | `docs/SAFETY-INTEGRATION.md`가 전문가 검토 필요를 명시한다. 전문가 승인 증거는 없다. |
| FE 예시와 같은 결과 | 자료 대조·회귀 검증 | `CrisisTextTest`의 FE 예시 4종과 `SafetyBrakeTest`·세부 경계 테스트가 주석 사례를 검증한다. FE 런타임과 동시 실행한 비교는 아니다. |

## 참고 자료 중 별도 범위

공용 Project #8의 14세 미만 동의, 데이터 암호화·본인 삭제, 공유 동의, 전화망 규제 검토는
이번 두 이슈 회귀 테스트만으로 완료 처리할 수 없다. FE #96의 버튼 크기·모바일 레이아웃·전화 연결도 이번 BE 검증 범위 밖이다.
기존 오탐 가능 단어와 상담 문구를 임의 수정하지 않았다.

## 실행 결과

Java 21로 아래 명령을 실행했다.

```powershell
./gradlew.bat spotlessApply test --tests '*Safety*' --tests '*CrisisText*' --tests '*CallRecordServiceTest' --tests '*ExceptionPrivacyTest'
./gradlew.bat spotlessCheck build --max-workers=2
```

첫 번째 대상 검사: 64개, 실패 0. 새 파라미터 사례 3개를 포함한다.
전체 검사: 2,278개, 실패 0·오류 0·건너뜀 0. 2026-10-17 17:49 KST에 `BUILD SUCCESSFUL`을 확인했다. 운영 DB·외부 GPT·브라우저 UI는 호출하지 않았다.
새 API나 서버 동작 변경 없이 테스트·검증 문서만 추가했다.
위 부분 확인 항목 때문에 두 이슈 모두 전체 완료 판정은 아니다.
