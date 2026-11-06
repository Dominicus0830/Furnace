# K1 가상 엔티티·NPC·가짜 탭 줄 봇 시험

시험 플러그인 `probe/VirtualProbe` 를 묶어 `plugins/` 에 넣고 서버를 띄운다.
```
javac -cp furnace-api.jar -d out probe/VirtualProbe.java && cp probe/furnace-plugin.yml out/ && (cd out && jar cf ../probe.jar .)
SRV=<서버 폴더> node round11/virtual.js
```
봇(minecraft-protocol)이 패킷을 실제로 해석하므로 와이어 형식(엔티티 데이터·플레이어 정보·팀·장비)이 맞는지 확인된다. 화면에 어떻게 보이는지(스킨·이름표·자세)는 실제 클라이언트 확인 항목(`docs/user-todo.md`).
