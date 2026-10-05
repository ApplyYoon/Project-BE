#!/usr/bin/env bash
# 세션이 시작될 때 현재 git 상태를 Claude 에게 알려줍니다.
set -u
ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
BRANCH="$(git -C "$ROOT" symbolic-ref --short -q HEAD 2>/dev/null)"
[ -z "$BRANCH" ] && BRANCH="(알 수 없음)"
CHANGED="$(git -C "$ROOT" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
HOOKS="$(git -C "$ROOT" config core.hooksPath 2>/dev/null)"

CONTEXT="[프로젝트 상태] 현재 브랜치: ${BRANCH} / 수정된 파일: ${CHANGED}개
[브랜치 전략] 작업 브랜치(feat/fix/...) → develop → main. 작업은 항상 develop 에서 딴 브랜치에서 합니다.
[스택] FastAPI + uv(pip 금지) + Neon 에 SQL 직접(psycopg, ORM 안 씀). 그 밖의 세부 도구는 이슈 #4 에서 정하므로 임의로 정하지 않습니다."

case "$BRANCH" in
  main|master)
    CONTEXT="${CONTEXT}
⚠️ main 은 보호 브랜치입니다. 코드를 고치기 전에 사용자에게 브랜치 이름 후보를 제시하고,
   develop 에서 새 작업 브랜치를 만드세요. (git switch develop && git pull origin develop && git switch -c feat/...)" ;;
  develop)
    CONTEXT="${CONTEXT}
⚠️ develop 도 보호 브랜치입니다. 여기서 직접 커밋하지 말고 작업 브랜치를 먼저 만드세요. (git switch -c feat/...)" ;;
esac

# FE 는 pnpm install 때 git 훅이 자동으로 켜지지만, BE 는 한 번 직접 켜야 합니다.
if [ "$HOOKS" != ".githooks" ]; then
  CONTEXT="${CONTEXT}
⚠️ git 훅(.githooks)이 꺼져 있습니다. 사용자에게 /setup 을 안내하거나, 허락을 받고
   git config core.hooksPath .githooks 를 실행하세요. 켜기 전에는 main·develop 직접 커밋이 터미널에서 막히지 않습니다."
fi

jq -n --arg c "$CONTEXT" '{
  hookSpecificOutput: { hookEventName: "SessionStart", additionalContext: $c }
}'
