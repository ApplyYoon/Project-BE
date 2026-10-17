# Render 서버 휴면 줄이기

`GET /api/v1/ping`은 로그인 없이 `200 {"status":"ok"}`를 반환합니다.
DB나 외부 API를 호출하지 않고, 응답에 `Cache-Control: no-store`를 붙입니다.
`/api/v1/health`는 기존처럼 DB 상태 확인에 사용합니다.

외부 스케줄러에서 10분마다 다음 명령을 실행합니다.

```bash
bash scripts/ping-render.sh
```

- cron 식: `*/10 * * * *` (매시 0, 10, 20, 30, 40, 50분)
- 호출 주소: `https://project-be-mxxc.onrender.com/api/v1/ping`
- 다른 서버를 호출하려면 `RENDER_PING_URL` 환경변수를 설정합니다.
- API 키, DB 접속 정보, 작업용 비밀키는 필요하지 않습니다.
- HTTP 오류나 예상과 다른 응답은 실패로 처리합니다.
- 서버 내부 스케줄러는 휴면 시 함께 멈추므로 사용하지 않습니다.

## 적용 순서

1. ping API를 `develop`에 병합하고 Render 자동 배포 완료를 확인합니다.
2. 실제 `/api/v1/ping` 응답이 HTTP 200인지 확인합니다.
3. 선택한 외부 스케줄러에 위 주기를 등록하고 수동 실행으로 검증합니다.
4. 예약 실행 기록에서도 성공을 확인합니다.

## 사용량과 한계

Render Cron은 작업 하나당 월 최소 $1이며 실제 사용량에 따라 달라집니다.
GitHub Actions 예약 실행은 지연될 수 있으며 비공개 저장소의 실행 시간을 소비합니다.
10분 주기는 하루 144회이므로 실행당 최소 1분으로 월 약 4,320~4,464분입니다.
무료 외부 HTTP 스케줄러를 사용한다면 위 호출 주소를 GET으로 등록하면 됩니다.

주기적인 ping은 무료 서버의 상시 가동을 보장하지 않습니다.
Render 무료 사용 시간은 워크스페이스에서 공유하므로 다른 서비스 사용량도 확인합니다.
ping 자체는 DB를 깨우지 않지만 기존 health 확인이나 다른 작업의 DB 접속은 별개입니다.

참고: [Render 무료 서버](https://render.com/docs/free),
[Render Cron](https://render.com/docs/cronjobs),
[GitHub 예약 실행](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)
