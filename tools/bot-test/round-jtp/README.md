# 조인 텔레포트 봇 시험 (2026-11-16)

PlayerJoinEvent 안·접속 직후 플러그인 텔레포트(다른 월드·같은 월드 먼 곳)가 실제 프로토콜 클라이언트(mineflayer)에서 목적 차원·목적지 청크로
이어지는지 본다. 기록: `docs/feature-parity/context-notes/2026-11-16-join-world-teleport.md`.

## 준비·실행
상위 `README.md` 의 준비(서버 jar·바닐라 데이터·`npm install`)를 마친 뒤:
```
javac -cp furnace-api.jar -d out round-jtp/probe/JoinTpProbe.java && cp round-jtp/probe/furnace-plugin.yml out/ && (cd out && jar cf ../jtp.jar .)
D=$(srv/newsrv.sh jtp) && mkdir -p $D/plugins && cp jtp.jar $D/plugins/
srv/run.sh <furnace-server.jar> $D -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 && srv/waitup.sh $D
node round-jtp/jtp.js           # 봇 넷이 차례로 접속, 한 줄 JSON 결과
grep '\[JTP\]' $D/server.log    # 플러그인 쪽 결과(텔레포트 결과·PlayerChangedWorldEvent)
srv/stop.sh $D
```
봇 이름 앞머리로 방식이 정해진다: `Neth` 조인 이벤트에서 네더, `Far` 조인 이벤트에서 같은 월드 먼 곳, `Late` 접속 2초 뒤 네더,
`Back` 조인 이벤트에서 네더 → 3초 뒤 오버월드 먼 곳. 새 월드에서 돌려야 한다(두 번째부터는 저장된 자리에서 접속한다).
