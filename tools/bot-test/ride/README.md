# 탄 채 접속 종료 → 탈것 저장(RootVehicle) 봇 시험

`tools/bot-test/README.md` 의 준비를 마친 뒤 `tools/bot-test` 에서(`SRV` 는 서버 폴더, `difficulty=peaceful` 권장):
- `node ride/ride.js mount` — 보트(이름 Bote, 닭 승객)를 소환해 타고 나간다. 나간 뒤 보트·닭 수가 0 이어야 한다.
- `node ride/ride.js mount stay` 로 탄 채 두고 서버를 끄면(`srv/stop.sh`) 종료 저장 경로를 본다.
- `node ride/ride.js check` — 다시 들어와 보트에 탔는지(`set_passengers`), 보트 1·닭 1(복제 없음)인지 본다.
- `node ride/dis.js` — 재접속으로 탄 보트에서 내려 나간다. 다음 `check` 에서 안 탄 채 보트가 월드에 1 개 남아야 한다.

2026-11-04 결과: 고치기 전 jar 는 나간 뒤에도 보트가 월드에 남고 재접속 때 타지 않았다. 고친 뒤 위 네 경우 모두 바닐라와 같다.
