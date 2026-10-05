#!/usr/bin/env bash
#
# 되돌리기 어려운 명령을 막는 안전장치입니다.
# Claude Code 의 PreToolUse 훅으로 실행되며, 표준입력으로 도구 호출 정보(JSON)를 받습니다.
#
# 설계 원칙
#  1) "실수로 날려먹는 것"만 막는다. 평소 쓰는 명령(add/commit/push -u/stash/checkout -- 파일)은 통과.
#  2) 막을 때는 왜 막혔는지와 대신 뭘 하면 되는지를 한국어로 알려준다.
#  3) 명령이 && 나 ; 로 이어져 있어도 잡히도록 전체 문자열에서 찾는다.
#
set -u

INPUT="$(cat)"
CMD="$(printf '%s' "$INPUT" | jq -r '.tool_input.command // ""')"
[ -z "$CMD" ] && exit 0

ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
BRANCH="$(git -C "$ROOT" symbolic-ref --short -q HEAD 2>/dev/null)"

deny() {
  jq -n --arg r "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $r
    }
  }'
  exit 0
}

# check <정규식> <막는 이유>
check() {
  printf '%s' "$CMD" | grep -qiE "$1" && deny "$2"
  return 0
}

# ─────────────────────────────────────────────────────────────
# A. 원격 저장소를 망가뜨리는 것 — 팀원 작업이 통째로 사라집니다
# ─────────────────────────────────────────────────────────────

check 'git[[:space:]]+push[^|;&]*--mirror' \
'git push --mirror 는 막아두었습니다.

내 컴퓨터에 없는 원격 브랜치를 전부 삭제합니다.
팀원들이 올려둔 브랜치가 한 번에 다 날아갑니다. 되돌릴 수 없습니다.

저장소를 옮기거나 백업하려는 거라면 선생님께 먼저 말씀드리세요.'

check 'git[[:space:]]+push[^|;&]*--force' \
'강제 푸시(force push)는 막아두었습니다.

원격에 있던 커밋을 내 것으로 덮어씁니다. 팀원이 그 사이 올린 작업이 사라집니다.
(--force-with-lease 도 같은 이유로 막았습니다.)

대부분은 "내가 pull 을 안 해서" 생긴 문제입니다. 이렇게 해보세요:
  /sync          (또는  git pull origin develop)
그래도 안 되면 선생님/팀장에게 물어보세요.'

check 'git[[:space:]]+push[^|;&]*[[:space:]]-[a-z]*f[a-z]*([[:space:]]|$)' \
'강제 푸시(git push -f)는 막아두었습니다.

원격 커밋을 덮어써서 팀원 작업이 사라집니다.
먼저 /sync 로 최신 코드를 받아오세요.'

check 'git[[:space:]]+push[^|;&]*[[:space:]]\+[A-Za-z0-9_./-]+' \
'강제 푸시를 막았습니다.

refspec 앞에 + 를 붙이면(예: git push origin +main) --force 와 똑같이 동작합니다.
원격 커밋을 덮어써서 팀원 작업이 사라집니다.'

check 'git[[:space:]]+push[^|;&]*([[:space:]](--delete|--prune)|[[:space:]]-[a-z]*d[a-z]*([[:space:]]|$))' \
'원격 브랜치를 지우는 명령은 막아두었습니다.

지우면 그 브랜치의 작업 기록이 사라집니다.
정리가 필요하면 GitHub 웹에서 PR 이 merge 된 브랜치만 골라 지우세요.'

check 'git[[:space:]]+push[^|;&]*[[:space:]]:[A-Za-z]' \
'원격 브랜치를 지우는 명령은 막아두었습니다.

git push origin :브랜치 는 그 원격 브랜치를 삭제한다는 뜻입니다.
정말 필요하면 사람에게 먼저 물어보세요.'

# ─────────────────────────────────────────────────────────────
# B. 내 작업을 지우는 것 — 저장 안 한 코드는 되살릴 수 없습니다
# ─────────────────────────────────────────────────────────────

check 'git[[:space:]]+reset[^|;&]*--hard' \
'git reset --hard 는 막아두었습니다.

저장(커밋)하지 않은 작업을 전부 지웁니다. 되돌릴 수 없습니다.

잠깐 치워두고 싶은 거라면:
  git stash        (나중에  git stash pop  으로 되살림)'

check 'git[[:space:]]+clean[^|;&]*((^|[[:space:]])-[a-z]*f|--force)' \
'git clean -f 는 막아두었습니다.

아직 git 에 등록되지 않은 파일(새로 만든 파일)을 전부 삭제합니다.
방금 만든 파일이 통째로 날아갈 수 있습니다.

뭐가 지워질지 먼저 확인하려면:
  git clean -n     (실제로 지우지 않고 목록만 보여줍니다)'

check 'git[[:space:]]+(checkout|restore)[[:space:]]+(--[a-z-]+[[:space:]]+)*\.([[:space:]]|$)' \
'작업 폴더 전체를 되돌리는 명령은 막아두었습니다.

git checkout . / git restore . 는 저장하지 않은 수정을 전부 버립니다.

특정 파일 하나만 되돌리는 건 괜찮습니다:
  git checkout -- db/schema.sql'

check 'git[[:space:]]+stash[[:space:]]+(clear|drop)' \
'git stash clear / drop 은 막아두었습니다.

치워둔 작업(stash)을 영구 삭제합니다. 되살릴 수 없습니다.

무엇이 들어 있는지 먼저 확인하세요:
  git stash list
  git stash pop     (꺼내 쓰기)'

# ─────────────────────────────────────────────────────────────
# C. 커밋 기록을 다시 쓰는 것 — 팀원과 기록이 어긋납니다
# ─────────────────────────────────────────────────────────────

check 'git[[:space:]]+filter-(branch|repo)' \
'git filter-branch / filter-repo 는 막아두었습니다.

저장소의 모든 커밋을 다시 씁니다. 팀원 저장소와 완전히 어긋나게 됩니다.
실수로 올린 비밀키를 지우려는 거라면 선생님께 말씀드리세요.'

check 'git[[:space:]]+reflog[^|;&]*expire|git[[:space:]]+gc[^|;&]*--prune' \
'이 명령은 막아두었습니다.

reflog 는 실수했을 때 되돌릴 수 있는 마지막 안전망입니다.
그걸 지우면 정말로 복구가 불가능해집니다.'

check 'git[[:space:]]+update-ref[^|;&]*(^|[[:space:]])-d([[:space:]]|$)' \
'git update-ref -d 는 막아두었습니다.

브랜치나 태그를 기록 없이 직접 삭제합니다.'

check 'git[[:space:]]+branch[^|;&]*(^|[[:space:]])-[dD]([[:space:]]|$)' \
'브랜치 삭제는 사람이 직접 하기로 한 작업입니다.

왜 지워야 하는지 먼저 설명하고 허락을 받으세요.
PR 이 merge 된 브랜치라면 GitHub 웹에서 지우는 게 안전합니다.'

# ─────────────────────────────────────────────────────────────
# D. 저장소 설정 — 잘못 건드리면 엉뚱한 곳에 푸시됩니다
# ─────────────────────────────────────────────────────────────

check 'git[[:space:]]+remote[[:space:]]+(remove|rm|set-url)' \
'원격 저장소 주소를 바꾸는 명령은 막아두었습니다.

잘못 바꾸면 엉뚱한 저장소로 푸시되거나 더 이상 푸시가 안 됩니다.
사람에게 먼저 물어보세요.'

check '(^|[[:space:]])rm[[:space:]]+(-[a-z]+[[:space:]]+)*[^|;&]*\.git([[:space:]/]|$)' \
'.git 폴더를 지우는 명령은 막아두었습니다.

.git 안에 모든 커밋 기록이 들어 있습니다. 지우면 전부 사라집니다.'

# ─────────────────────────────────────────────────────────────
# D-2. git 훅 끄기 — 이 저장소는 GitHub 브랜치 보호를 켤 수 없어서
#      .githooks/ 가 사람이 치는 명령을 막는 유일한 장치입니다
# ─────────────────────────────────────────────────────────────

check 'git[[:space:]]+(commit|push|merge)[^|;&]*--no-verify|git[[:space:]]+commit([[:space:]]+--?[a-zA-Z-]+)*[[:space:]]+-[a-zA-Z]*n[a-zA-Z]*([[:space:]]|$)' \
'--no-verify 는 막아두었습니다.

git 훅(.githooks/)을 통째로 건너뜁니다.
이 저장소는 GitHub 브랜치 보호를 켤 수 없어서, git 훅이 main·develop 을 지키는 유일한 장치입니다.

훅이 막았다면 이유가 있습니다. 훅이 보여준 안내문을 읽고 그대로 고치세요.'

# 훅 위치를 확인하는 것(git config core.hooksPath)과 .githooks 로 켜는 것만 허용합니다.
if printf '%s' "$CMD" | grep -qiE 'core\.hookspath' \
  && ! printf '%s' "$CMD" | grep -qE 'git[[:space:]]+config[[:space:]]+(--local[[:space:]]+)?core\.hooksPath[[:space:]]+\.githooks([[:space:]]|$|[;&|])' \
  && ! printf '%s' "$CMD" | grep -qE 'git[[:space:]]+config[[:space:]]+(--get[[:space:]]+)?core\.hooksPath[[:space:]]*($|[;&|])'; then
  deny 'git 훅 위치(core.hooksPath)를 바꾸는 명령은 막아두었습니다.

다른 곳을 가리키게 하거나 지우면 .githooks/ 의 안전장치가 꺼집니다.

훅을 켜는 명령은 이것 하나입니다:
  git config core.hooksPath .githooks'
fi

# ─────────────────────────────────────────────────────────────
# E. 보호 브랜치 — 작업 브랜치 → develop → main
# ─────────────────────────────────────────────────────────────

is_commit_or_push() {
  printf '%s' "$CMD" | grep -qE 'git[[:space:]]+(commit|push)([[:space:]]|$)'
}

case "$BRANCH" in
  main|master)
    is_commit_or_push && deny "지금 '$BRANCH' 브랜치입니다. main 에는 직접 커밋/푸시하지 않습니다.
main 으로는 develop 에서 PR 로만 들어갑니다.

작업하려면 develop 에서 새 브랜치를 만드세요:
  git switch develop && git pull origin develop && git switch -c feat/작업이름
(브랜치 이름이 고민되면 사용자에게 후보를 제시하고 고르게 하세요.)"
    ;;
  develop)
    is_commit_or_push && deny "지금 'develop' 브랜치입니다. develop 에도 직접 커밋/푸시하지 않습니다.
develop 으로는 작업 브랜치에서 PR 로만 들어갑니다.

먼저 작업 브랜치를 만드세요:
  git switch -c feat/작업이름
(브랜치 이름이 고민되면 사용자에게 후보를 제시하고 고르게 하세요.)"
    ;;
esac

exit 0
