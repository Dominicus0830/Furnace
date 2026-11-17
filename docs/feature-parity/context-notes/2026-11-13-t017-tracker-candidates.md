# 2026-11-13 T-017 추적기 `playerMoved` 후보 줄이기

근거: O05 보고서 P-O05-03(`furnace/reviews/opt-review/O05-entities.md` 1.2·2.2절), 로드맵 `docs/roadmap/optimization-tasks.md` T-017.

## 무엇을 바꿨나

`ChunkLevel.playerMoved(p)`(바닐라 `ChunkMap.move`)는 region 의 모든 엔티티에 `EntityTracker.updatePlayer(p)` 를 불렀다.
`updatePlayer` 가 무엇이든 바꾸는 경우는 (a) 지금 p 를 보는 엔티티(보임 → 안 보임, 또는 grace 값 0 으로)와
(b) p 에서 `r = min(추적 거리, 시야 × 16)` 안의 엔티티(안 보임 → 보임)뿐이다. 둘 다 아니면 거리 판정이 거짓이고
`seenBy.remove(p)` 는 빈 동작이다(`broadcastToPlayer`·`chunkTracked`·플러그인 규칙은 `&&` 단락으로 불리지 않는다).
그래서 엔티티가 64 개 이상이고 p 가 그 절반 미만을 볼 때는 (a)∪(b) 만 **틱 목록 순서(`EntityStore.all()`)로** 돈다.
패킷·순서·`seenBy` 는 전체 훑기와 같다.

- (a) **역색인** `ChunkLevel.seenIndex`: 플레이어 → 그 플레이어를 `seenBy` 에 넣은 이 레벨의 엔티티. `updatePlayer` 가 새로
  보기 시작할 때 `Viewers.startedSeeing` 으로 붙고, 판정(전체·후보 모두)이 끝나면 그 판정 결과로 정확히 다시 만든다.
  엔티티가 다른 레벨로 갈 때(`adoptEntity`·`absorb`·`splitTo`) 보는 사람을 새 레벨 색인에 올린다. 상위 집합이어도 된다
  (안 보는 엔티티에 `updatePlayer` 를 더 불러도 결과가 같다) — 쓸 때 `holds`·추적기 상태로 거른다.
- (b) **청크 열 훑기** `EntityStore.forEachInColumns`: x 마다 구역 TreeMap 의 한두 구간. 범위 = `min(시야 × 16, 이 레벨에 들어온
  추적 거리 최댓값) + 1` 블록(1 은 부동소수 경계 여유).
- **순서**: `EntityStore` 가 넣을 때 매기는 순번(`storeSeq` — `LinkedHashMap` 넣은 순서와 같다)으로 후보를 늘어놓고 겹친 것을 지운다.
- 몰림(p 가 절반 이상을 봄)·엔티티 64 개 미만·좌표 이상·범위가 80 열 초과면 옛 전체 훑기(`playerMovedFullScan`).
- **판정 도중 변경 감지**: 스폰 묶음 직전에 플러그인 `PlayerTrackEntityEvent`(와 `onStartSeenBy`)가 불리므로, 처리기가 엔티티를
  넣고 빼거나 다른 청크 열로 옮기거나 p 를 옮기면 미리 정한 후보가 틀린다(옛 길은 틱 목록 순서로 계속 돌며 그 엔티티까지 본다).
  그래서 `updatePlayer` 마다 `EntityStore.modCount`(넣기·빼기·구역 이동)·p 좌표·시야가 그대로인지 보고, 바뀌었으면 **그 엔티티 뒤(틱 목록
  순서)를 전체로** 이어 돈다 — 옛 길과 같은 결과. 같은 열 안에서의 이동은 범위 밖 열이면 여전히 범위 밖이라 감지가 필요 없다. 규칙(`canSee`)·
  `chunkTracked` 변경은 거리 안 엔티티에만 영향이 있고 그것들은 이미 후보다. 처음 판(PR #299 첫 커밋)은 구독자가 있으면 늘 전체 훑기였으나,
  ProtocolLib 다리가 엔티티 패킷 리스너가 있으면 이 이벤트를 MONITOR 로 구독해 이득이 사라져(검토 R1) 이 방식으로 바꿨다.
  처리기가 판정 밖 길(`refreshViews` 등)로 바꾼 `seenBy` 는 역색인에 남긴다(`putSeenList` 가 합친다).
- 판정 도중 예외가 나도 역색인을 잃지 않는다(`finally` 에서 p 를 보는 엔티티를 전체로 다시 모은다 — 검토 R3).

## 같이 고친 것 (바닐라·Paper 쪽으로)

`addFreshEntity` 에서 저장소에 넣은 뒤·추적기를 만들기 전에 플러그인 `EntitySpawnEvent` 처리기가 엔티티를 옮기면, 추적기가 없어
`setPosRaw` 가 구역 이동을 알리지 않아 **저장소 구역 목록이 옛 구역에 남았다**(상자 조회 `getEntities` 에서도 옛 자리로 보임).
이벤트 뒤 구역이 바뀌었으면 `entities.moved` 로 맞춘다. Paper 는 이벤트 뒤의 위치로 엔티티 관리자에 넣으므로 이쪽이 같다.
바닐라에는 이 이벤트가 없다. 플러그인 없는 서버의 결과는 바뀌지 않는다.

## 찾았지만 고치지 않은 것 (바닐라와 다를 수 있음)

- **추적기 없는 엔티티(추적 거리 0 — `minecraft:marker`)의 구역이 갱신되지 않는다.** `Entity.setPosRaw` 는
  `level != null && tracker != null` 일 때만 `entitySectionChanged` 를 부른다. 바닐라는 레벨에 든 엔티티면 늘 `onMove` 로 구역을 옮긴다.
  그래서 `/tp` 로 옮긴 마커는 저장소 구역 목록에서 처음 자리에 남는다(상자 조회 순서·청크별 목록에 영향 가능, 가정 — 확인 안 함).
  추적 후보에는 영향 없다(추적기 없는 엔티티는 후보가 아니다). 별도 작은 정확성 TASK 로 넘긴다.
- 바닐라 `ChunkMap.entityMap` 은 `Int2ObjectOpenHashMap` 이라 한 플레이어에게 가는 스폰 패킷들의 엔티티 순서가 Furnace(넣은 순서)와
  원래 다르다(O05 보고서에 적힌 기존 차이). 이번 변경은 Furnace 의 옛 순서를 그대로 지킨다.

## 증명

`EntityTrackerCandidatesIdentityTest`(IdentityKit `Differential` + `PacketTape`): 무작위 장면 40개(엔티티 40~260, 플레이어 2~7,
150~300 걸음, 몰림/흩어짐)를 옛 전체 훑기와 후보 길에 따로 돌려, 걸음마다 플레이어별 패킷 열(바이트·순서)과 엔티티별 보는 사람 목록(순서까지)
해시가 같은지 본다. 장면 조작: 걷기·멀리 순간이동·판정 없이 옮기기·시야 바꾸기·엔티티 이동(구역 바뀜)·추적 틱(grace)·넣기·지우기·
region 넘김(adoptEntity)·합치기·나누기·플레이어 region 넘김·청크 받음 여부 바뀜·차원 이동·접속 끊김·스폰 이벤트가 엔티티를 옮김.
변이 점검 2개(역색인 없이 가까운 것만 / 순서 뒤집기)를 잡는다. 자동 선택 길(`playerMoved`)도 20장면 대조. 두 시험 모두 장면 절반은
`PlayerTrackEntityEvent` 처리기 흉내(판정 도중 다음 엔티티를 보는 사람 곁으로 옮기고, 반은 `refreshViews`)를 켰다 껐다 한다(후보 길
시험도 같은 장면 — 변경 감지를 끄면 두 시험 모두 실패함을 확인). 넘김 대기 상태(어느 저장소에도 없음, 그 사이 이동·옛 레벨 `refreshViews`)도 넣었다.

시험(단언 켬)에서는 `playerMoved` 마다 역색인이 빠짐없는지, 후보 길마다 후보가 빠짐없고 순서대로인지 전체로 확인한다
(`VERIFY_TRACK_CANDIDATES` — 운영·벤치에서는 꺼짐). 그래서 다른 모든 시험(region 넘김·합치기를 실제로 도는 것 포함)이 색인 불변식을 같이 검사한다.

## 측정 (`EntityViewTrackerBench`, E=2000 좀비 × P=32, 시야 10, `-Dfurnace.bench.path=ab` 같은 JVM 교대, 뒤쪽 회차)

| 배치 | 옛 전체 훑기 | 후보 길 | 틱당(32명) |
|---|---|---|---|
| 몰림(25×25 칸) | 80~89 ns/쌍 | 76~89 ns/쌍(전체 훑기로 빠짐) | 같음 |
| ±400 블록 | 19.5~24.5 | 15.3~17.5 | 약 −20% |
| ±800 | 13.9~17.4 | 5.4~6.9 | 1.0ms → 0.4ms(약 −60%) |
| ±1500 | 9.1~10.4 | 2.9~3.5 | 약 −65% |

남는 비용은 거리 안 엔티티의 실제 판정(`chunkTracked`·`seenBy`)이라 후보 줄이기로는 못 줄인다. 실서버 `chunkTracked`(`World.chunkReady` =
`ChunkPos` 할당 + CHM 두 번)는 벤치(늘 참)보다 비싸 거리 안 비용이 더 크다 — P-O05-04 몫. 벤치 회차 사이 잡음이 ±30% 라 JVM 을 나눠 잰 숫자끼리 비교하지 말 것.

## 검토(PR #299, `furnace/reviews/t017-pr299.md`)에서 별도 TASK 로 넘긴 것 (이 PR 전부터 있던 차이)

- R5: 전체 훑기는 `entities.all()` 라이브 뷰를 돈다 — `PlayerTrackEntityEvent` 처리기가 같은 레벨에 엔티티를 스폰·지우면 CME 가능(옛 코드도 같음).
- R6: Furnace 는 `EntitySpawnEvent` **전에** 저장소에 넣어, 처리기 안의 주변 엔티티 조회가 그 엔티티 자신을 본다. Paper 는 이벤트 뒤에 넣는다.
