# E2: 두뇌(Brain) 몹 기억·센서·활동 API

`probe/BrainProbe.java`(`/br <동작> <몹 종류> [인자]` — 16칸 안의 그 몹에 `editAi` 로 두뇌 API 를 쓰고 `[BR]` 로그)를 넣은 서버에 봇이 접속해
주민·피글린·아홀로틀·소에서 손잡이·활동 목록, 집 기억 쓰기(+ `/data` 로 바닐라 저장 형식), 플러그인 활동 고정·놓기, 플러그인 행동·센서, 피글린 분노 기억을 확인한다.
```
javac -cp furnace-api.jar -d out round13/probe/BrainProbe.java && cp round13/probe/furnace-plugin.yml out/ && (cd out && jar cf ../brain.jar .)
D=$(srv/newsrv.sh brain); mkdir $D/plugins; cp brain.jar $D/plugins/
srv/run.sh furnace-server.jar $D && srv/waitup.sh $D
SRV=$D node round13/brain.js
srv/stop.sh $D
```
