-- 이미 테이블이 있는 Neon DB 에서 "설문의 목표 시험 · 시험 날짜" 를 선택 칸으로 바꾸는 SQL 입니다. (2026-10-17)
--
-- 왜?
--   새 온보딩(FE #417 정리 뒤)은 목표 시험 · 시험 날짜를 더 묻지 않습니다.
--   그런데 이 두 칸이 not null 이라, 새 학생은 설문(취침 · 기상 · 아침형/밤형 · 하루 공부 가능 시간)을 서버에 저장할 수 없었습니다.
--   AI 하루 계획이 이 값을 읽어서 수면 시간을 피하고 아침형/밤형에 맞춰 짭니다.
--
-- 누가 · 언제 실행하나요?
--   07 까지 실행한 Neon 브랜치에 실행합니다. (모르겠으면 01 부터 다시 순서대로 실행해도 됩니다)
--   dev 브랜치는 지금 바로, main 브랜치는 이 PR 이 들어간 develop → main 배포 **전에** 실행합니다.
--
-- 여러 번 실행해도 되나요?
--   네. 이미 not null 이 없는 칸에 drop not null 을 다시 해도 에러가 나지 않습니다. (MigrationSqlTest 가 검사)
--   지우는 데이터는 없습니다. 이미 적힌 시험 이름 · 날짜는 그대로 남습니다.

begin;

alter table onboarding_answers alter column target_exam drop not null;
alter table onboarding_answers alter column target_exam_date drop not null;

commit;
