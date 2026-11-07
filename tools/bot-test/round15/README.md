# E3: 스폰 정책·엔티티 수·자세·주민 평판·POI API

`probe/E3Probe.java`(`/e3 <동작> [인자]` — 부른 플레이어 기준으로 API 를 쓰고 `[E3]` 로그)를 넣은 서버에 봇이 접속해 확인한다.
- `e3.js`(기본): 청크·월드 종류별 수, 자세(엔티티·플레이어), noPhysics, 주민 평판·소문, 종·퇴비통 POI, 습격 목록, 몬스터 상한 0(늘지 않음)·해제(다시 늘어남), 필터 등록·해제. 15항목.
- `MODE=npcs e3.js`: `spawn-animals=false spawn-npcs=false` 서버에서 소·주민은 지워지고 좀비는 남는다(바닐라 `ServerLevel.shouldDiscardEntity` 와 같음). 1항목.
```
javac -cp furnace-api.jar -d out round15/probe/E3Probe.java && cp round15/probe/furnace-plugin.yml out/ && (cd out && jar cf ../e3.jar .)
D=$(srv/newsrv.sh e3); mkdir $D/plugins; cp e3.jar $D/plugins/
srv/run.sh furnace-server.jar $D && srv/waitup.sh $D
SRV=$D node round15/e3.js
srv/stop.sh $D
# 2번째: D=$(srv/newsrv.sh e3n spawn-animals=false spawn-npcs=false) 로 새로 띄운 뒤 SRV=$D MODE=npcs node round15/e3.js
```
- 평지는 슬라임 청크가 있어 몬스터가 곧바로 쌓인다. "상한 0" 은 절대값이 아니라 상한을 건 뒤의 증가 여부로 본다.
