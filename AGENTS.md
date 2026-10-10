# AGENTS.md

이 레포에서 작업하는 사람과 AI 에이전트(Claude Code, Codex 등)가 따르는 규칙입니다.

## 프로젝트

- 학생·입시생 자기관리 앱의 백엔드 API 서버 (FastAPI, Python 3.12, uv)
- 프론트엔드: https://github.com/Tmax-IT-Hope-School/Project-FE
- 팀원 대부분이 코딩 입문자입니다. 코드는 **짧고 읽기 쉽게**, 똑똑한 트릭보다 평범한 코드를 씁니다.

## 명령어

| 하려는 것 | 명령 |
| --- | --- |
| 설치 | `uv sync` |
| 서버 실행 | `uv run uvicorn app.main:app --reload` |
| 테스트 | `uv run pytest` |
| 린트 | `uv run ruff check . --fix` |
| 포맷 | `uv run ruff format .` |
| 라이브러리 추가 | `uv add <패키지>` (pip install 쓰지 않기) |

작업을 끝내기 전에 **테스트·린트·포맷을 모두 통과**시킵니다. CI에서도 똑같이 검사합니다.

## 코드 구조 규칙

- 새 API는 `app/api/v1/<기능>.py`에 `router = APIRouter(prefix="/<기능>", tags=["<기능>"])`로 만들고 `app/api/v1/__init__.py`에 등록합니다.
- 요청·응답 모양은 `app/schemas/<기능>.py`에 Pydantic 모델로 정의합니다. dict를 그대로 반환하지 않습니다.
- DB 조회나 외부 API 호출 같은 로직은 `app/services/<기능>.py`에 둡니다. 라우터 함수는 얇게 유지합니다.
- 설정값·비밀키는 `app/core/config.py`의 `Settings`로 읽습니다. 코드에 키를 직접 쓰지 않고, 새 값은 `.env.example`에도 추가합니다.
- API를 만들거나 고치면 `tests/test_<기능>.py`에 테스트를 같이 씁니다.
- URL은 소문자 kebab-case 복수형(`/study-sessions`), JSON 필드는 snake_case로 씁니다.

## Git 규칙

- `main`에 직접 push 금지. 항상 브랜치 → PR.
- 브랜치 이름: `<타입>/<이슈번호>-<짧은설명>` (예: `feat/12-timer-api`, `fix/20-health-500`)
- 커밋 메시지: `<타입>: <무엇을 했는지>` (예: `feat: 타이머 시작 API 추가`)
  - 타입: `feat` 기능 · `fix` 버그 · `refactor` 리팩터 · `test` 테스트 · `docs` 문서 · `chore` 설정·기타
- PR 본문에 `Closes #이슈번호`를 써서 이슈와 연결합니다.
- PR 하나에는 이슈 하나만. 커지면 쪼갭니다.
- merge는 리뷰 1명 승인 + CI 통과 후 squash merge.

## 하지 말 것

- `.env`, API 키, 비밀번호 커밋 (레포가 public입니다)
- 테스트를 지우거나 skip해서 CI 통과시키기
- 이슈와 상관없는 파일 대량 수정·포맷 변경
