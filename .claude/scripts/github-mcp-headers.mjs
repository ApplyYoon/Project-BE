// GitHub MCP 에 보낼 로그인 정보(헤더)를 만드는 스크립트입니다.
// .mcp.json 의 headersHelper 가 GitHub MCP 에 연결할 때마다 이 파일을 실행합니다.
//
// 왜 필요한가요?
// GitHub MCP 는 /mcp 화면의 Authenticate(브라우저 로그인)를 지원하지 않아서
// 토큰(로그인 열쇠)을 직접 넣어줘야 합니다. 토큰을 파일에 적어두면 새어 나가기 쉬우니,
// `gh auth login` 으로 로그인해 둔 토큰을 연결할 때마다 꺼내 씁니다.

import { execSync } from 'node:child_process'

let token = ''
try {
  // gh 에 로그인돼 있으면 토큰 문자열 한 줄이 나옵니다.
  token = execSync('gh auth token', { encoding: 'utf8' }).trim()
} catch {
  // gh 가 설치돼 있지 않거나 로그인이 안 된 경우 → 아래에서 안내합니다.
}

if (!token) {
  console.error('GitHub 토큰을 찾지 못했습니다. 터미널에서 `gh auth login` 을 먼저 하세요.')
  process.exit(1)
}

// Claude Code 는 { "헤더 이름": "값" } 모양의 JSON 을 받아서 GitHub 에 보내는 요청에 붙입니다.
console.log(JSON.stringify({ Authorization: `Bearer ${token}` }))
