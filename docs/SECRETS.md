# 개발용 비밀값 받기 (#76)

## 선택안과 범위

**Infisical Cloud를 개발용 값이 필요한 최대 5명의 개별 계정으로 사용하는 안**입니다.
가입·권한 설정·값 입력 및 학생 컴퓨터 실행은 아직 하지 않았습니다. 그 확인 전에는 #76을 닫지 않습니다.

2026-10-17 공식 자료 확인:

| 후보 | 무료 범위 | 판단 |
| --- | --- | --- |
| [Infisical](https://infisical.com/blog/secrets-manager-pricing) | 사람과 기계 계정을 합쳐 5 identities | 선택안. Windows CLI가 있고 실행할 프로세스에 환경변수를 전달함 |
| [Phase](https://phase.dev/pricing/) | 사용자 또는 서비스 계정 5개, 앱 3개 | 대안. 10명 전체를 무료로 수용하지는 못함 |

선생님과 외부 API 담당 학생만 초대하고, 나머지는 기존 H2로 개발합니다.
공유 계정·공유 토큰으로 무료 인원 제한을 우회하지 않습니다. 5명을 넘으면 유료/자체 호스팅을 별도로 결정합니다.
이번 안은 학생에게 기계 토큰을 나눠 주지 않고 **개인 로그인**을 사용합니다. 개인별로 접근을 회수할 수 있기 때문입니다.

개발 전용 프로젝트만 만들고 운영 값은 아예 넣지 않습니다. 무료 플랜의 세밀한 환경별 권한에 의존하지 않고
`dev` 프로젝트에는 Neon `dev`, 사용 한도를 낮춘 개발 GPT 키, 개발 SNS 값만 둡니다.
운영 값은 기존처럼 Render 환경변수에만 둡니다. 운영 값을 개발 프로젝트에서 참조하는 기능도 사용하지 않습니다.

## 관리자가 먼저 할 일

1. 공식 사이트에서 가입하고 무료 플랜과 초대 인원 수를 확인합니다. 결제가 필요하면 진행하지 않습니다.
2. 개발 전용 프로젝트와 `dev` 환경을 만들고 필요한 담당자만 개인 계정으로 초대합니다.
3. 개발용 값만 입력합니다. `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `OPENAI_API_KEY` 등 실제 코드가 읽는 이름을 그대로 씁니다.
4. 학생에게는 프로젝트 ID와 로그인 안내만 전달합니다. 값·토큰 자체를 채팅이나 이슈에 붙이지 않습니다.
5. 학생 계정으로 운영 프로젝트 접근이 불가능한지 확인합니다. 프로젝트 ID 자체는 비밀번호가 아닙니다.

## 설치 → 로그인 → 실행

[공식 설치 안내](https://infisical.com/docs/cli/install)에 따라 사용자가 CLI를 설치합니다.
Windows는 `winget install infisical`, macOS는 `brew install infisical/get-cli/infisical`입니다.
Linux는 공식 설치 안내를 사용합니다. 오래된 Cloudsmith 설치 명령은 사용하지 않습니다.

```text
infisical --version
infisical login
infisical run --help
```

로그인은 본인이 브라우저에서 합니다. 실행 예시의 `PROJECT_ID`는 관리자가 알려 준 개발 프로젝트 ID로 바꿉니다.
[공식 프로세스 주입 예시](https://infisical.com/blog/secure-secrets-management-for-cursor-cloud-agents)의 `run` 방식을 사용합니다.
CLI 버전에 따라 명령 구성이 바뀌면 `--help`의 Secret Management 실행 명령을 확인하고 기록합니다.

Windows:

```powershell
infisical run --env=dev --projectId=PROJECT_ID -- .\gradlew.bat --no-daemon bootRun
```

macOS / Linux:

```bash
infisical run --env=dev --projectId=PROJECT_ID -- ./gradlew --no-daemon bootRun
```

이 명령은 비밀값을 `.env`로 내보내지 않습니다. 로그인 자격 증명은 CLI/운영체제의 저장소에 남을 수 있으므로
"컴퓨터에 아무 인증 정보도 남지 않는다"는 뜻은 아닙니다. `export`, `printenv`, 디버그 로그로 값을 출력하지 않습니다.
`--no-daemon`은 이번 실행용 Gradle 프로세스가 끝난 뒤 계속 남지 않도록 사용합니다.

**기본 Spring 프로필은 계속 local/H2입니다.** DB 환경변수를 받았다고 자동으로 Neon에 연결되지는 않습니다.
개발 Neon 연결은 README의 운영 DB 안내에 따라 프로필까지 명시적으로 선택할 때만 합니다.
개발 프로젝트에는 `SPRING_PROFILES_ACTIVE=prod`를 넣지 않습니다.

비밀값이 필요 없는 평소 실행:

```powershell
.\gradlew.bat bootRun
```

관리자가 값을 넣은 뒤 학생 한 명이 실행하고 `/api/v1/health`의 200 응답과 필요한 개발 기능을 확인합니다.
인증 실패 시 값을 출력해 조사하지 말고 계정 초대·프로젝트 ID·환경 이름부터 확인합니다.

## 노출·탈퇴 시

1. 관리자가 해당 계정/세션 또는 토큰을 즉시 회수합니다. 단순 `logout`만으로 노출된 키가 무효화되지는 않습니다.
2. 접근 가능했던 DB 비밀번호·GPT·SNS 키를 각 발급 서비스에서 폐기하고 새로 만듭니다.
3. 새 개발 값을 Infisical에 넣고 실행 중인 개발 서버를 다시 켭니다. 운영 영향이 있으면 Render 값도 교체합니다.
4. 저장소나 로그에 노출됐다면 위치와 시각만 관리자에게 알립니다. 비밀값을 다시 복사하지 않습니다.
5. 팀을 나간 사람은 프로젝트에서 제거하고 세션을 회수합니다.

## 완료 확인 (실제 수행 후 체크)

- [ ] 관리자가 선택안·접근 인원·비용을 확인하고 개발 프로젝트를 만들었다.
- [ ] 학생 개인 계정으로 개발 값만 받을 수 있고 운영 값은 접근할 수 없다.
- [ ] 학생 한 명이 위 명령으로 필요한 개발 기능을 실행했다.
- [ ] CLI 없이 기존 H2 실행도 된다.
- [ ] 코드·커밋·로그에 실제 비밀값이 없다.

선택 이유는 이 문서에 남겼습니다. 이슈 댓글 공유와 실제 계정 설정은 팀 담당자가 확인 후 진행합니다.
