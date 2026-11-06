# 10차: 메뉴·보스바·사이드바 플러그인 API

`probe/UiProbe.java` 플러그인(/uimenu /uibar /uibarset /uisb /uisbset /uihide)을 넣은 서버에 봇이 접속해 패킷과 클릭 동작을 확인한다.
```
javac -cp furnace-api.jar -d out round10/probe/UiProbe.java && cp round10/probe/furnace-plugin.yml out/ && (cd out && jar cf ../probe.jar .)
D=$(srv/newsrv.sh ui9); mkdir $D/plugins; cp probe.jar $D/plugins/
srv/run.sh furnace-server.jar $D && srv/waitup.sh $D
SRV=$D node round10/ui.js
srv/stop.sh $D
```
