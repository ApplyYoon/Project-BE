-- 이미 테이블이 있는 Neon DB 에 "온보딩을 마친 시각"(users.onboarded_at) 칸을 더하는 SQL 입니다. (2026-10-17 멘토 결정)
--
-- 왜?
--   FE 가 온보딩을 어디까지 했는지를 브라우저에만 적어 두어서, 이미 가입한 사람도 다른 기기 · 브라우저에서 로그인하면
--   온보딩을 처음부터 다시 했습니다. 서버가 "마쳤다" 를 기억하고 로그인 응답(user.onboarded)으로 알려 줍니다.
--
-- 누가 · 언제 실행하나요?
--   07 까지 실행한 Neon 브랜치에 실행합니다. (모르겠으면 01 부터 다시 순서대로 실행해도 됩니다)
--   dev 브랜치는 지금 바로, main 브랜치는 이 칸이 들어간 develop → main 배포 **전에** 실행합니다.
--
-- 여러 번 실행해도 되나요?
--   네. 칸은 "없으면 더하기(if not exists)" 이고, 아래 2) 는 정해진 시각 전에 가입한 사람만 채웁니다.
--   그래서 나중에 다시 실행해도, 그 뒤에 가입해서 아직 온보딩을 안 마친 사람이 "마쳤다" 가 되지 않습니다.
--   begin ~ commit 으로 묶여 있어서, 중간에 에러가 나면 하나도 바뀌지 않습니다.

begin;

-- 1) 온보딩을 마친 시각. 아직 안 마쳤으면 비어 있습니다.
alter table users add column if not exists onboarded_at timestamp(6) with time zone;

-- 2) 이 칸이 생기기 전에 가입한 사람은 모두 "온보딩을 마쳤다" 로 봅니다. (멘토 결정: 이미 가입한 사람에게는 온보딩을 다시 띄우지 않음)
--    마친 시각은 정확히 알 수 없어서 가입한 시각으로 채웁니다. 이미 채워진 사람은 건너뜁니다.
update users
   set onboarded_at = created_at
 where onboarded_at is null
   and created_at < timestamp with time zone '2026-10-18 06:00:00+09:00';

commit;
