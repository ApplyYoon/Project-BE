# DB 다이어그램

우리 DB 에 어떤 테이블이 있고 서로 어떻게 이어지는지 **그림 한 장**으로 보는 문서입니다.

- 테이블은 각 기능을 맡은 사람이 **직접 설계**합니다.
- 테이블(엔티티)을 만들거나 고칠 때마다 Claude Code 에서 **`/db`** 를 치면, 지금 엔티티를 읽어 아래 그림을 새로 그리고 브라우저로도 열어 줍니다.
- GitHub 에서 이 문서를 열면 그림으로 보입니다.

## 지금 테이블

<!-- DB-DIAGRAM:START — /db 명령이 이 사이를 새로 씁니다 -->

```mermaid
erDiagram
    users ||--o{ subjects : "user_id"
    users ||--o{ study_plans : "user_id"
    users ||--o{ study_sessions : "user_id"
    users ||--o{ accounts : "user_id"
    users ||--o{ login_tokens : "user_id"
    users ||--o{ messages : "user_id"
    users ||--o{ call_records : "user_id"
    users ||--o{ thinking_tests : "user_id"
    users ||--o{ push_subscriptions : "user_id"
    thinking_tests ||--o{ thinking_test_scores : "thinking_test_id"
    thinking_tests ||--o{ thinking_test_weaknesses : "thinking_test_id"
    subjects ||--o{ study_plans : "subject_id"
    subjects ||--o{ study_sessions : "subject_id"

    users {
        bigint id PK "사용자를 구분하는 고유 번호"
        varchar nickname "화면에 보이는 이름 (실명 아님, 50자까지)"
        timestamptz created_at "가입한 시각"
    }
    subjects {
        bigint id PK "과목을 구분하는 고유 번호"
        bigint user_id FK "이 과목의 주인"
        varchar name "과목 이름 (30자까지, 같은 사용자 안에서 중복 불가)"
        varchar color "과목 태그 색 (RED, ORANGE, GREEN, BLUE, PURPLE 중 하나)"
    }
    study_plans {
        bigint id PK "계획을 구분하는 고유 번호"
        bigint user_id FK "이 계획의 주인"
        bigint subject_id FK "어떤 과목의 계획인지 (노는 계획이면 비어 있음)"
        date plan_date "계획한 날짜"
        varchar content "할 일 내용 (200자까지)"
        int target_minutes "목표 공부 시간(분)"
        boolean done "다 했으면 true, 아직이면 false"
        time start_time "시작할 시각, 몇 시 몇 분 (안 정했으면 비어 있음)"
        varchar kind "공부 계획인지 노는 계획인지 (STUDY, PLAY 중 하나)"
        bigint routine_id "어떤 루틴이 만든 계획인지 (직접 만들었으면 비어 있음)"
    }
    study_sessions {
        bigint id PK "기록을 구분하는 고유 번호"
        bigint user_id FK "이 기록의 주인"
        bigint subject_id FK "어떤 과목을 공부했는지"
        timestamptz started_at "공부를 시작한 시각"
        timestamptz ended_at "공부를 끝낸 시각"
        int pause_count "일시정지를 누른 횟수"
    }
    accounts {
        bigint id PK "계정을 구분하는 고유 번호"
        bigint user_id FK "이 계정의 주인 (사용자 한 명에 계정은 하나)"
        varchar email "로그인할 때 쓰는 이메일 (소문자로 저장, 중복 불가, 100자까지)"
        varchar password_hash "비밀번호를 되돌릴 수 없게 바꾼 값 (진짜 비밀번호는 저장하지 않음)"
        varchar name "실명 (20자까지)"
        varchar school_code "고른 고등학교의 번호 (안 골랐으면 비어 있음)"
        varchar school_name "고른 고등학교의 이름 (안 골랐으면 비어 있음)"
        boolean diary_analysis_agreed "일기·감정 기록을 분석에 써도 된다고 했으면 true (선택 동의)"
        timestamptz agreed_at "이용약관과 개인정보 수집·이용에 동의한 시각 (필수 동의)"
    }
    login_tokens {
        bigint id PK "토큰을 구분하는 고유 번호"
        bigint user_id FK "이 토큰의 주인"
        varchar token_hash "토큰의 해시 (토큰 원문은 저장하지 않음, 중복 불가)"
        timestamptz expires_at "이 시각이 지나면 쓸 수 없음 (로그인하고 30일 뒤)"
        timestamptz created_at "토큰을 내준 시각 (= 로그인한 시각)"
    }
    messages {
        bigint id PK "메시지를 구분하는 고유 번호 (나중에 보낸 것일수록 큼)"
        bigint user_id FK "이 메시지를 받은 사람"
        varchar kind "메시지 종류 (CARROT, NUDGE, STICK, EVENT, RECOVERY 중 하나)"
        varchar text "메시지 내용 (500자까지)"
        timestamptz sent_at "메시지를 보낸 시각"
        timestamptz read_at "처음 읽은 시각 (아직 안 읽었으면 비어 있음)"
    }
    call_records {
        bigint id PK "통화를 구분하는 고유 번호"
        bigint user_id FK "이 통화를 한 사람"
        date call_date "통화한 날 (새벽 4시에 날짜가 바뀜)"
        timestamptz started_at "통화를 시작한 시각 (임시 키를 받은 시각)"
        varchar asked_bias "이번 통화에서 확인하려던 생각 습관 (PLANNING_FALLACY, FUTURE_SELF, WHAT_THE_HELL, EMOTIONAL_REASONING, EXTERNAL_BLAME, ALL_OR_NOTHING, MORAL_LICENSING, OVERGENERALIZING 중 하나, 없으면 비어 있음)"
        varchar asked_question_key "고른 기본 질문의 이름 (예: PLANNING_FALLACY-1)"
        timestamptz finished_at "통화를 마무리한 시각 (마무리 전이면 비어 있음)"
        varchar summary "통화 요약 2~3문장 (300자까지, 대화 원문은 저장하지 않음)"
        varchar found_bias "대답에서 보인 생각 습관 (asked_bias 와 같은 값 중 하나, 없으면 비어 있음)"
        varchar found_evidence "그 습관이 보인 근거 한 줄 (100자까지)"
        varchar actual_question "AI 가 실제로 한 생각 습관 질문 (200자까지)"
        varchar miss_reason "어제 계획을 못 채운 이유 (TIRED, EVENT, PHONE, TOO_MUCH, UNWILLING, SICK, ETC 중 하나)"
        varchar promise_if "오늘의 약속 '만약 ~하면' (50자까지)"
        varchar promise_then "오늘의 약속 '~한다' (50자까지)"
        boolean promise_kept "지난 약속을 지켰다고 했는지 (이야기하지 않았으면 비어 있음)"
        boolean crisis_detected "위기 표현이 감지됐는지 (감지되면 내용 칸은 비워 둠)"
    }
    thinking_tests {
        bigint id PK "테스트 결과를 구분하는 고유 번호 (나중에 한 것일수록 큼)"
        bigint user_id FK "테스트를 한 사람"
        timestamptz taken_at "테스트를 마친 시각"
        int test_version "테스트 모양 번호 (2 = 리커트 7점 척도)"
    }
    thinking_test_scores {
        bigint thinking_test_id FK "어느 테스트의 점수인지"
        varchar habit "생각 습관 이름 (PLANNING_FALLACY 등 8개 중 하나)"
        double average "그 습관의 평균 (1 ~ 7, 역문항은 뒤집어 센 값)"
    }
    push_subscriptions {
        bigint id PK "구독을 구분하는 고유 번호"
        bigint user_id FK "알림을 받을 사람"
        varchar endpoint "푸시를 보낼 브라우저 회사의 주소 (500자까지, 중복 불가, 기기마다 하나)"
        varchar p256dh "알림 내용을 잠그는 브라우저의 공개 열쇠 (200자까지)"
        varchar auth "알림 내용을 잠그는 비밀 값 (100자까지)"
        timestamptz subscribed_at "구독을 저장한(다시 저장한) 시각"
    }
    thinking_test_weaknesses {
        bigint thinking_test_id FK "어느 테스트의 약점인지"
        varchar weakness "내가 꼽은 약점 칩 (GAME, PHONE, VIDEO, SLEEP, LAZY, DELAY, FOCUS, FRIENDS, TIRED, MOOD, PLANNING 중 하나)"
    }
```

<!-- DB-DIAGRAM:END -->

## 테이블 설명

| 테이블 | 무엇을 담나 | 만든 이슈 |
| ------ | ----------- | --------- |
| `users` | 서비스를 쓰는 사람 한 명이 한 줄 (별명, 가입 시각) | #3 |
| `subjects` | 사용자가 만든 과목 하나가 한 줄 (이름, 색) | #3 |
| `study_plans` | 하루의 계획 하나가 한 줄 (날짜, 과목, 내용, 목표 시간, 완료 여부, 시작 시각, 공부/노는 계획, 루틴) | #3 · #12 |
| `study_sessions` | 타이머로 한 번 공부한 기록 하나가 한 줄 (과목, 시작·끝 시각, 일시정지 횟수) | #3 |
| `accounts` | 이메일로 가입한 계정 하나가 한 줄 (이메일, 비밀번호 해시, 실명, 학교, 동의). 사용자 한 명에 하나 | #97 |
| `login_tokens` | 로그인 한 번으로 내준 토큰 하나가 한 줄 (토큰 해시, 끝나는 시각) | #97 |
| `messages` | AI 가 사용자에게 보낸 메시지 하나가 한 줄 (종류, 내용, 보낸 시각, 읽은 시각) | #30 |
| `call_records` | AI 통화 한 번이 한 줄 (확인하려던 생각 습관, 요약, 보인 생각 습관과 근거, 약속, 위기 표현 표시) | #28 · #29 |
| `push_subscriptions` | 웹 푸시(앱을 닫아도 오는 알림)를 받겠다고 한 기기 하나가 한 줄 (보낼 주소, 잠금 열쇠) | #32 |
| `thinking_tests` | 생각 습관 테스트 한 번이 한 줄 (마친 시각, 테스트 모양 번호). 다시 하면 새 줄이 쌓임 | #35 |
| `thinking_test_scores` | 테스트 한 번의 습관 8개 평균이 한 줄씩 (`@ElementCollection`) | #35 |
| `thinking_test_weaknesses` | 테스트 한 번에서 고른 약점 칩이 한 줄씩 (`@ElementCollection`) | #35 |

> `accounts` 와 `login_tokens` 는 2026-10-11 에 SNS 로그인을 빼고 일반 로그인으로 정하면서 **선생님 요청으로 AI 가 먼저 만든 테이블**입니다.
> 학생이 설계한 `users` 는 그대로 두려고 로그인 정보를 따로 뺐습니다. 고칠 점이 보이면 이슈로 남겨 주세요.

> `messages` 는 2026-10-12 에 이슈 #30 을 만들면서 **AI 가 초안을 먼저 만든 테이블**입니다. (사용자가 "AI 가 초안을 만들어 달라" 고 해서 예외로 했습니다)
> 이슈 완료 조건에 적힌 것(종류, 보낸 시각, 읽은 시각, 주인)에 메시지 내용 글자(`text`)만 더했습니다.
> FE 의 `AiMessage` 에 있는 `careLevel`(관리 강도), `eventLabel`(이벤트 이름), `source`(AI/앱) 는 **아직 담지 않았습니다.** 필요한지는 위 그림을 보고 정해 주세요.

> `call_records` 는 2026-10-14 에 **선생님이 "FE·BE·테이블까지 AI 가 만들라" 고 맡긴 기능**(인지편향 잡기)에서 AI 가 만든 테이블입니다.
> - 통화를 **시작할 때** 한 줄을 만들고(`asked_*`), 통화가 **끝나면** 같은 줄을 채웁니다(`finished_at` 아래 칸들). 마무리하는 API 는 다음 PR(#29)에서 만듭니다.
> - **대화 원문(자막)과 녹음은 저장하지 않습니다.** 요약과 정해진 분류값만 남깁니다. 위기 표현이 감지된 통화는 내용 칸을 비우고 `crisis_detected` 만 남깁니다.
> - 다음 통화는 최근 2주 기록을 읽어 자주 보인 생각 습관을 다시 확인하고, 지난 약속을 지켰는지 묻습니다. 최근 7일 안에 위기 표현이 있었으면 위로하는 통화로 합니다.
> - 체크인(#24) 테이블과 겹치는 칸(못 채운 이유·약속)이 있습니다. 체크인 테이블이 생기면 합칠지 정해 주세요.

> `thinking_tests` 와 딸린 테이블 두 개는 2026-10-15 에 같은 기능(인지편향 잡기)에서 AI 가 만들었습니다.
> - **문장별 답은 저장하지 않고 습관 8개의 평균만** 저장합니다. 테스트 문장은 앞으로 바뀔 수 있어서(선생님 말씀), 문장이 바뀌어도 테이블은 그대로 쓰게 하려는 것입니다.
> - 한 테스트에 점수 8개 · 약점 여러 개라서 **딸린 테이블(`@ElementCollection`)** 에 담았습니다. 엔티티는 `ThinkingTest` 하나이고, JPA 가 두 테이블을 알아서 같이 읽고 씁니다. 그래서 딸린 테이블에는 `id` 가 없습니다.
> - "기타" 에 직접 쓴 약점 글은 서버로 받지 않아서 저장하지 않습니다.

> `study_plans` 의 `start_time` · `kind` · `routine_id` 는 2026-10-12 에 이슈 #12 에서 더했습니다. (칸 이름과 뜻은 Project#12 의 표, 시작 시각을 `time` 으로 저장하는 것은 학생이 정함)
> - 공부 계획(`STUDY`)은 `subject_id` 가 꼭 있어야 하고, 노는 계획(`PLAY`)만 비어도 됩니다. DB 에도 검사 규칙(`ck_study_plans_study_has_subject`)으로 걸어 두었습니다.
> - 루틴 테이블은 아직 없어서(#85) `routine_id` 는 지금 항상 비어 있고, 그림에 선도 없습니다.

## 그림 읽는 법

- **네모 하나 = 테이블 하나 = 엔티티 클래스 하나**
- **선** `||--o{` = "왼쪽 하나가 오른쪽 여러 개를 가진다" (예: 사용자 한 명이 메모 여러 개)
- **PK** = 그 줄을 구분하는 고유 번호
- **FK** = 다른 테이블의 줄을 가리키는 번호 (예: `user_id` 는 `users` 의 어느 줄인지)
- ⚠️ 지금은 DB 가 FK **제약**을 걸지 않습니다. (엔티티에 `Long userId` 처럼 번호만 적기 때문입니다)
  그래서 "없는 사용자", "남의 과목" 이 붙지 않게 하는 검사는 **API 쪽(#10~#13)에서** 합니다.

## 실제로 만들어진 모양 보기

그림은 엔티티 코드를 읽어 그린 것입니다. DB 에 **실제로** 만들어진 테이블은 서버를 켠 뒤 여기서 봅니다.

👉 http://localhost:8080/h2-console (`JDBC URL` 에 `jdbc:h2:mem:projectbe`, `User Name` 에 `sa`)
