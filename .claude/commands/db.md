---
description: 지금 우리 테이블들을 그림(다이어그램)으로 그려서 보여줍니다 - 테이블을 만들 때마다 쓰세요
---

엔티티(`@Entity`)를 읽어 **있는 그대로** 다이어그램을 그립니다.
설계를 대신 해 주는 명령이 아닙니다. 고칠 점이 보여도 그림은 그대로 그리고, 의견은 질문으로 따로 말합니다.

## 1. 엔티티 찾기

```bash
grep -rl '@Entity' src/main/java
```

하나도 없으면 "아직 테이블이 없어요. 엔티티를 만든 뒤 다시 `/db` 를 쳐 주세요" 라고 알리고 끝냅니다.

## 2. 그림 글자(mermaid) 만들기

찾은 파일을 **전부 읽고** 아래 규칙으로 옮깁니다. 추측해서 컬럼을 더하거나 빼지 않습니다.

| 엔티티에서 | 그림에서 |
| --- | --- |
| `@Table(name = "memos")` | 테이블 이름 `memos` (없으면 클래스 이름을 snake_case 로) |
| 필드 `userId` | 컬럼 `user_id` |
| `Long` / `Integer`·`int` / `String` / `Boolean`·`boolean` | `bigint` / `int` / `varchar` / `boolean` |
| `Instant` / `LocalDate` | `timestamptz` / `date` |
| `enum` 타입 | `varchar` + 설명에 가능한 값 |
| `@Id` 가 붙은 필드 | 뒤에 `PK` |
| `xxxId` 필드, 그리고 `xxxs` 테이블이 있으면 | 뒤에 `FK`, 그리고 선 `xxxs ||--o{ 이_테이블 : "xxx_id"` |
| 필드 위의 한국어 주석 | 컬럼 설명 `"..."` |

```
erDiagram
    users ||--o{ memos : "user_id"

    users {
        bigint id PK "고유 번호"
        varchar nickname "화면에 보이는 이름"
    }
    memos {
        bigint id PK "고유 번호"
        bigint user_id FK "누구의 메모인지"
        varchar content "메모 내용"
    }
```

(위는 문법을 보여 주는 예시입니다. 실제 그림은 우리 엔티티로 그립니다.)

## 3. `docs/DB.md` 에 반영

`docs/DB.md` 의 `<!-- DB-DIAGRAM:START -->` 와 `<!-- DB-DIAGRAM:END -->` 사이를 새 그림으로 바꿉니다.
그림은 ` ```mermaid ` 코드 블록에 넣습니다. GitHub 에서 이 문서를 열면 그림으로 보입니다.

그 아래 "테이블 설명" 표에 새 테이블이 있으면 한 줄 추가합니다. 설명은 **만든 사람에게 물어서** 적습니다.

## 4. 지금 바로 그림으로 보여주기

같은 그림을 브라우저로 엽니다. `build/` 폴더는 git 에 올라가지 않으니 마음껏 덮어써도 됩니다.

`build/db-diagram.html` 을 아래 모양으로 만들고 (`{{그림 글자}}` 자리에 2번 결과):

```html
<!doctype html>
<html lang="ko">
  <head>
    <meta charset="utf-8" />
    <title>DB 다이어그램</title>
  </head>
  <body style="font-family: sans-serif; margin: 24px">
    <h1>지금 우리 DB 의 테이블</h1>
    <p>네모 = 테이블 · 선 = "왼쪽 하나가 오른쪽 여러 개를 가진다" · PK = 고유 번호 · FK = 다른 테이블을 가리키는 번호</p>
    <pre class="mermaid">
{{그림 글자}}
    </pre>
    <script type="module">
      import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs'
      mermaid.initialize({ startOnLoad: true })
    </script>
  </body>
</html>
```

브라우저로 엽니다.

```bash
open build/db-diagram.html          # Mac
start build/db-diagram.html         # Windows
```

인터넷이 연결돼 있어야 그림이 그려집니다. 안 열리면 파일 위치를 알려주고 직접 더블클릭하라고 안내하세요.

## 5. 보고

> 테이블 3개를 그렸어요: `users`, `subjects`, `study_plans`
> 지난번과 달라진 점: `study_plans` 에 `plan_date` 컬럼이 생겼어요
> 브라우저에 그림을 열어 두었고, `docs/DB.md` 에도 반영했어요.
>
> 실제로 DB 에 만들어진 모양은 서버를 켠 뒤 http://localhost:8080/h2-console 에서 볼 수 있어요.

그림과 엔티티가 다르면 안 됩니다. 확신이 없는 부분은 그리지 말고 물어보세요.
