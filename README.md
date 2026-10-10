# Project-BE

IT 희망학교 11기 프로젝트(학생·입시생 자기관리 앱)의 백엔드 서버입니다.
프론트엔드는 [Project-FE](https://github.com/Tmax-IT-Hope-School/Project-FE)에 있어요.

| 항목 | 사용 기술 |
| --- | --- |
| 언어 | Python 3.12 |
| 프레임워크 | FastAPI |
| 패키지 관리 | [uv](https://docs.astral.sh/uv/) |
| 린트·포맷 | ruff |
| 테스트 | pytest |
| DB | 미정 (Supabase Postgres 후보) |
| 배포 | 미정 (GCP Cloud Run 후보, `Dockerfile` 준비됨) |

## 처음 받았을 때

```bash
# 1) uv 설치 (한 번만)
brew install uv            # Windows: powershell -c "irm https://astral.sh/uv/install.ps1 | iex"

# 2) 받기
git clone https://github.com/ApplyYoon/Project-BE.git
cd Project-BE

# 3) 파이썬과 라이브러리 설치 (.venv 폴더가 생겨요)
uv sync

# 4) 환경변수 파일 만들기
cp .env.example .env

# 5) 서버 켜기
uv run uvicorn app.main:app --reload
```

`http://localhost:8000/docs`에 들어가면 API 목록을 보고 직접 호출해 볼 수 있어요.

## 자주 쓰는 명령

```bash
uv run pytest              # 테스트
uv run ruff check . --fix  # 린트 (자동 수정 포함)
uv run ruff format .       # 코드 정리
uv add <패키지>             # 라이브러리 추가
```

## 폴더 구조

```
app/
├── main.py          # 앱 시작점 (라우터·CORS 등록)
├── core/config.py   # 환경변수 설정
├── api/v1/          # API 엔드포인트 (기능별 파일 하나씩)
├── schemas/         # 요청·응답 모양 (Pydantic)
└── services/        # 실제 로직 (DB 조회, 외부 API 호출 등)
tests/               # 테스트 (api/v1 파일마다 test_ 파일 하나씩)
```

## 협업 규칙

자세한 건 [AGENTS.md](./AGENTS.md)에 있어요. Claude Code 같은 AI도 이 파일을 읽고 그대로 따라요.

1. 할 일은 GitHub 이슈로 만들고, 칸반 보드에서 담당자를 정해요.
2. `main`에 직접 push하지 않아요. 브랜치를 따서 PR을 올려요.
3. PR은 CI(린트+테스트)를 통과하고, 리뷰 1명 승인을 받으면 squash merge해요.
