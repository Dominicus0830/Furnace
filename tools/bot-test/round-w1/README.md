# 다중 월드(W1) 봇 시험

```
D=$(srv/newsrv.sh w1)
srv/run.sh <furnace-server.jar> $D && srv/waitup.sh $D
SRV=$D node round-w1/worlds.js 1      # 만들기·들어가기·블록·내리기(거절/성공)·다시 불러오기·네더 환경 빈 월드
srv/stop.sh $D
echo "worlds.autoload=mining" > $D/furnace.properties
srv/run.sh <furnace-server.jar> $D && srv/waitup.sh $D
SRV=$D node round-w1/worlds.js 2      # 마지막에 mining 에서 나갔으므로 그 차원으로 다시 접속
srv/stop.sh $D
```
화면(하늘·안개)은 봇이 못 본다 — `docs/user-todo.md` 1장.
