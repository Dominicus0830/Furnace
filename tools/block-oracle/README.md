# 블록 동작 차분 비교(block oracle)

같은 봇 시나리오를 순정 1.21.1 서버와 Furnace 에 각각 돌리고, 저장된 월드의 블록 상태를 칸 단위로 비교한다.
설치·상호작용·이웃 갱신·틱 결과가 바닐라와 같은지 보는 기본 도구.

```
tools/block-oracle/run.sh <시나리오> <x1,y1,z1,x2,y2,z2> <작업 폴더>
```

- 순정 jar: `VANILLA_JAR`(기본 `tools/vanilla-data/minecraft_server.1.21.1.jar`, 커밋하지 않는다).
- 두 서버 모두 평지(`level-type=minecraft:flat`, 구조물 없음, 시드 1), 크리에이티브, 오프라인, 압축 끔으로 새로 만든다.
  봇(`Oracle`)은 OP 로 등록돼 `/tp`·`/save-all` 을 쓴다.
- 결과: `<작업 폴더>/vanilla.txt`, `furnace.txt`(줄마다 `x y z 상태`), `diff.txt`.
- 시나리오 문법은 `furnace-server/src/test/java/org/domi/furnace/server/oracle/ScenarioBot.java` 머리말. 예시는 `scenarios/`.
- 평지 잔디 윗면은 y=-61, 서 있는 높이는 y=-60. 크리에이티브 손 닿는 거리(약 5칸) 밖은 `tp` 로 옮긴 뒤 놓는다.
