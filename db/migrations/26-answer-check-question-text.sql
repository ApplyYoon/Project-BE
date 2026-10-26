-- 이미 테이블이 있는 Neon DB 의 "사진 채점" 문제 표(answer_check_items)에 칸 두 개를 더하는 SQL 입니다. (2026-10-26, BE #401)
--
-- 무엇이 생기나요?
--   question_text : AI 가 사진에서 읽은 문제 글 (200자까지). 사진은 지금처럼 저장하지 않고 이 글만 남깁니다.
--   unit          : 문제의 단원 = 교육과정 성취기준의 영역 (예: 다항식, 문학). 못 고르면 비어 있습니다.
--   오답노트 · 주간 오답 테스트 · 일간 리포트의 "비슷한 문제 다시" 가 이 두 칸을 씁니다.
--   이미 있는 줄은 두 칸 모두 비어 있는(null) 채로 시작합니다. (예전 채점 기록에는 문제 글이 없습니다)
--
-- 누가 · 언제 실행하나요?
--   answer_check_items 표가 있는 Neon 브랜치(18 을 실행한 곳)에 실행합니다. 다른 번호와는 상관없어서 순서가 바뀌어도 됩니다.
--   Neon 콘솔 → 브랜치 고르기 → SQL Editor 에 이 파일 전체를 붙여 넣고 Run.
--   dev 브랜치는 지금 바로, main 브랜치는 이 칸이 들어간 develop → main 배포 **전에** 실행합니다.
--   (이 칸이 없으면 /api/v1/answer-checks 가 500 이고, 탈퇴도 500 입니다)
--
-- 여러 번 실행해도 되나요?
--   네. 칸은 "없으면 더하기(if not exists)" 라서 다시 실행해도 에러가 나지 않습니다. (MigrationSqlTest 가 검사)
--   지우는 데이터는 없습니다.

begin;

alter table answer_check_items add column if not exists question_text varchar(200);
alter table answer_check_items add column if not exists unit varchar(30);

commit;
