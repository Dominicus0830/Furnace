# W2: 커스텀 바이옴·차원 타입 등록 (W2)

`probe/GlowBoot.java`(부트스트랩에서 바이옴 glow:cave·glow:plain, 차원 타입 glow:deep 등록) + `probe/GlowProbe.java`(/gbget /gbset)를 넣은 서버에 봇이 접속해
구성 단계 레지스트리 데이터(플러그인 항목 hasData=true)와 World.biomeAt/setBiome, /fillbiome·/execute if biome 의 플러그인 바이옴 인식을 확인한다.
```
javac -cp furnace-api.jar -d out round-w2/probe/GlowBoot.java round-w2/probe/GlowProbe.java && cp round-w2/probe/furnace-plugin.yml out/ && (cd out && jar cf ../glow.jar .)
D=$(srv/newsrv.sh glow); mkdir $D/plugins; cp glow.jar $D/plugins/
srv/run.sh furnace-server.jar $D && srv/waitup.sh $D
SRV=$D node round-w2/glow.js
srv/stop.sh $D
```
mineflayer 는 바이옴 본문의 의미 검증을 하지 않는다 — 진짜 클라이언트가 색·소리·하늘을 바르게 그리는지는 `docs/user-todo.md` 1장 W2 행.
