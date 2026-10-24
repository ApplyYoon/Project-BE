-- 이미 테이블이 있는 Neon DB 에 "관리 4단계 앱 안 강제"(이슈 #234) 칸을 더하는 SQL 입니다.
--
-- 무엇이 바뀌나요?
--   daily_care_stages.forced_card_id   : 4단계라서 그날 하루만 절대 카드로 보는 카드 번호 (없으면 비어 있음)
--   daily_care_stages.forced_card_date : 그 카드를 절대로 보는 날짜
--   daily_care_stages.released_at      : 4단계를 푼 시각 (증빙이 있는 실행 1회 → 3단계). 비어 있으면 안 풀림
--   study_sessions.proof_required      : 4단계라서 증빙이 꼭 필요했던 세션인지 (비어 있는 옛 기록은 "필요 없었음")
--   모두 비어 있어도 되는 칸이라, 이미 있는 줄은 그대로 둡니다.
--
-- 누가 · 언제 실행하나요?
--   12(daily_care_stages)까지 실행한 Neon 브랜치에 실행합니다. (모르겠으면 01 부터 다시 순서대로 실행해도 됩니다)
--   dev 브랜치는 지금 바로, main 브랜치는 이 PR 이 들어간 develop → main 배포 **전에** 실행합니다.
--   (이 칸이 없으면 관리 단계 · 공부 기록 API 가 500 입니다)
--
-- 여러 번 실행해도 되나요?
--   네. 모두 "없으면 더하기(if not exists)" 입니다. (MigrationSqlTest 가 검사)
--   begin ~ commit 으로 묶여 있어서, 중간에 에러가 나면 하나도 바뀌지 않습니다.

begin;

alter table daily_care_stages add column if not exists forced_card_id bigint;
alter table daily_care_stages add column if not exists forced_card_date date;
alter table daily_care_stages add column if not exists released_at timestamp(6) with time zone;

alter table study_sessions add column if not exists proof_required boolean;

commit;
