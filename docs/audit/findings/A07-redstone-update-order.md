# A07 레드스톤·피스톤·업데이트 순서 — 2차 전체 점검 조사 보고서 (2026-11-08)

- **기준 커밋**: `claude/quirky-wright-lra834` `8f8b188f` (PR #170 병합 시점. 계획서 기준 `6e8fec9a` 이후 바뀐 것은 문서뿐)
- **조사관**: A07 조사 스레드 (Opus high). 코드는 고치지 않았다.
- **바닐라 대조**: `main` 의 `tools/vanilla-data/server-1.21.1` 을 Vineflower 1.10.1 로 스크래치패드에 풀어 읽었다(커밋 안 함). 이 보고서에 쓴 난독화 이름: `dmb` RedStoneWireBlock, `dsv` PistonBaseBlock, `dsz` PistonStructureResolver, `dsy` PistonMovingBlockEntity, `dia` DiodeBlock, `dmg` Repeater, `dhi` Comparator, `dlj` Observer, `dmd` RedstoneTorch, `dfn` BaseRailBlock, `dlv` PoweredRail, `dmc` RedstoneLamp, `aqu` ServerLevel, `dcw` Level, `dvi` LevelChunk, `eyq` LevelTicks, `eyo` LevelChunkTicks, `aqk` DistanceManager, `ara` TickingTracker, `aqf` ChunkTracker, `aqa` ChunkLevel(레벨 상수), `aqs` ServerChunkCache.
- **재현 시험**: `docs/audit/findings/A07-repro/*.java.txt`. 첫 줄 패키지에 맞춰 `.txt` 를 떼고 넣는다.
  - `A07TickRangeReproTest.java.txt` → `furnace-test/src/test/java/org/domi/furnace/test/` , `gradle :furnace-test:test --tests '*A07TickRange*'`
  - `A07ReproTest.java.txt`, `A07DeviceTimingTest.java.txt` → `furnace-server/src/test/java/org/domi/furnace/server/block/` , `gradle :furnace-server:test --tests '*A07*'`
  - **실패하는 것이 재현**이다(통과하면 고쳐진 것). 예외: `A07DeviceTimingTest` 전체와 `A07ReproTest.torchBurnoutControlSingleLevel` 은 대조용이라 지금도 통과해야 한다.
  - `A07ReproTest.recentTogglesMapIsNotThreadSafe` 는 재현되면 데몬 스레드 8개가 CPU 를 100% 쓰며 계속 돈다(무한 반복이 바로 재현 내용이다). 다른 시험과 같은 JVM 에서 돌리면 느려지므로 따로 돌린다.

## 조사한 범위
- 이웃 갱신기 `block/NeighborUpdater`(바닐라 CollectingNeighborUpdater 의 층 쌓기·연쇄 한도), `ChunkLevel.setBlock`/`setBlockStateInChunk` 의 플래그별 순서(onRemove → onPlace → 블록 엔티티 → 이웃 갱신 → 비교기 출력 → 간접·모양 갱신 → POI), `B.updateNeighbourShapes`·`updateOrDestroy`, 신호 질의(`getSignal`·`getDirectSignalTo`·`hasNeighborSignal`·`getBestNeighborSignal`·`updateNeighbourForOutputSignal`).
- 레드스톤 가루 전체(연결 모양, 세기 계산, `Sets.newHashSet` 순회 순서, 모서리 갱신, 클릭 토글), 피스톤(신호·준연결, 블록 이벤트 종류 0/1/2, `moveBlocks` 의 HashMap 순서·플래그, 밀기 한도 12·슬라임/꿀 가지·충돌 재배열, 움직이는 피스톤 블록 엔티티 tick·finalTick·저장), 다이오드(중계기·비교기: 잠금, 우선순위, 아날로그 입력·아이템 액자), 관측기, 레드스톤 횃불(과열), 레일(바탕·전동/활성화 신호 전파 8칸), 조명, 소리 블록, 디스펜서·크래프터·문·다락문·TNT 의 `neighborChanged`.
- 예약 틱(`BlockTicks`: 중복 제거, 실행 순서, willTickThisTick, 저장·불러오기 순번, region 합치기·나누기 보정), 블록 이벤트 큐(중복 제거·순서·합치기·나누기), 블록 엔티티 틱 목록(대기 목록, 지워진 것 정리, 교체), region 레벨 틱 순서(`RegionLevel.tickBlocks`)와 바닐라 `ServerLevel.tick` 순서.
- 플레이어 거리에 따른 틱 범위(예약 틱·블록 이벤트·블록 엔티티), region 스레드끼리 함께 쓰는 정적 상태(레드스톤 쪽 `ThreadLocal`·정적 표).
- X1(PR #133)이 바꾼 레드스톤 가루·전동 레일 경로: 값 번호 처리와 바닐라 분기를 다시 대조했다(검토 보고서 `x1-pr133` 의 지적은 다시 올리지 않는다).

## 조사하지 못한 범위 (이유)
- 스컬크 감지기·보정 감지기·표적·덫 갈고리·햇빛 감지기·독서대·압력판·버튼·종·피뢰침의 줄 단위 대조 — 시간 안에 끝내지 못했다. 위 장치들의 신호는 같은 `getSignal`/이웃 갱신 길을 쓰므로 아래 A07-02·03 의 영향은 똑같이 받는다. 다음 차수나 A08 에서 같은 방식(바이트코드 대조 + 틱 시험)으로 보면 된다.
- 깔때기·드로퍼의 아이템 이동(A09), 레드스톤 성능(A17).
- 실제 클라이언트로만 보이는 것(피스톤 애니메이션, 소리) — 서버 상태로 재현되는 것만 올렸다.
- region 경계에 걸친 장치: `Regionizer.sectionFor` 가 새 구역을 반경 안 이웃 region 에 붙이고, 서로 다른 region 은 언제나 반경(기본 2구역) 이상 떨어져 있어 장치 하나가 두 region 에 걸칠 수 없다. 걸칠 수 있는 건 합치기 직전 1~2틱(`RegionLevel` 머리말)뿐이라 재현 장면을 만들지 못했다 — "가정"으로 두고 올리지 않는다. 대신 region 이 합쳐지고 나뉠 때 레벨 객체가 바뀌는 데서 생기는 차이(A07-04)를 찾았다.
- 바닐라 서버 실행 대조는 컨테이너에서 Mojang 라이브러리 403 이라 못 했다. 기대값은 모두 바이트코드를 읽어 계산했다.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 1 | A07-01 |
| 높음 | 2 | A07-02, A07-03 |
| 중간 | 1 | A07-04 |
| 낮음 | 2 | A07-05, A07-06 |
| 참고 | 3 | A07-07, A07-08, A07-09 |

도미니코 결정이 필요한 항목은 없다. A07-02·03 은 바닐라와 같게 고치는 것이 원칙(결과 동일)이고, 고치면 틱 범위가 넓어져 일이 조금 늘어난다(성능 영향은 A17 이 잰다). user-todo 항목은 만들지 않았다.

바닐라와 같다고 확인한 것(아래 표에 올리지 않음): 이웃 갱신기의 층 순서·건너뛰는 방향·연쇄 한도와 로그, `setBlock` 플래그 처리 순서(옛 블록으로 `blockUpdated`, 플래그 `& ~33`), 가루의 모든 분기(연결·`getConnectionState`·간접 모양 갱신·세기 계산·HashSet 순서·클릭), 피스톤 기반·구조 해석·움직이는 블록 엔티티(진행도 0.5씩, 1 이 된 다음 틱 마무리, finalTick), 중계기·비교기·관측기·횃불·조명·레일의 `tick`/`neighborChanged`/우선순위, 블록 이벤트 중복 제거와 순서, region 레벨 틱 단계 순서(예약 틱 → 습격 → 청크 틱 → 변경 방송 → 블록 이벤트 → 엔티티 → 블록 엔티티). `A07DeviceTimingTest`(중계기 지연 1~4, 횃불 2틱·8번째 과열, 관측기 2틱 지연·2틱 펄스, 비교기 빼기 모드)가 모두 통과한다.

---

## 항목

### A07-01 — 레드스톤 횃불 과열 기록(정적 `WeakHashMap`)을 region 스레드 여럿이 잠금 없이 써서, region 스레드가 무한 반복에 빠질 수 있다

| 칸 | 내용 |
|---|---|
| 심각도 | **치명** (region 스레드 정지 → 그 region 의 모든 틱 정지, 워치독 크래시) |
| 종류 | 스레드 안전 |
| 근거 | `block/types/RedstoneTorchBlock.java:20` `private static final Map<BlockLevel, List<Toggle>> RECENT_TOGGLES = new WeakHashMap<>();` 를 `tick`(`:47` `get`)과 `isToggledTooFrequently`(`:78` `computeIfAbsent` + `add`)가 **잠금 없이** 쓴다. 키가 `RegionLevel` 이라 region 마다 다른 키지만, 표는 하나라서 region 스레드들이 동시에 넣고(크기 재조정 포함) 읽는다. `WeakHashMap.get` 도 `expungeStaleEntries` 로 표를 고친다(버려진 RegionLevel 이 수거될 때). 바닐라는 서버 스레드 하나라 문제가 없다(`dmd` 의 같은 정적 표). 다른 레드스톤 정적 상태(`RedStoneWireBlock.NO_SIGNAL`, `PistonMovingBlockEntity.NOCLIP`)는 이미 `ThreadLocal` 로 바꿔 두었는데 이 표만 빠졌다. |
| 재현 | `A07ReproTest.recentTogglesMapIsNotThreadSafe`: 스레드 8개가 각자 레벨 객체를 3000개 만들며(region 이 생기고 합쳐지고 나뉠 때 새 `RegionLevel` 이 생기는 것과 같다) 횃불을 한 번씩 끈다. 기준 커밋에서 **8개 스레드 모두 30초 안에 끝나지 않음**(실패). 스레드 덤프: 모두 `WeakHashMap.get(WeakHashMap.java:415)` ← `RedstoneTorchBlock.tick(:47)` 에서 RUNNABLE, 각 300초 넘게 CPU 를 씀(버킷 사슬이 고리가 됨). 운영에서는 두 region 이 같은 틱에 횃불을 끄고 그중 하나가 처음 쓰는 레벨(새로 생긴 region)일 때 생긴다. 확률은 시험보다 훨씬 낮지만 한 번 걸리면 그 region 이 영원히 멈춘다. |
| 제안 | 표를 레벨(차원) 하나에 하나로 두되 region 스레드끼리 겹치지 않게: (가) `ChunkLevel` 에 region 별 기록을 두고 `absorb`/`splitTo` 에서 칸 위치로 옮긴다(예약 틱·블록 이벤트와 같은 방식, A07-04 도 함께 풀림), 또는 (나) `World` 에 `ConcurrentHashMap<Long(칸), …>` 하나. (가) 가 바닐라 의미(차원 하나에 기록 하나)와 스레드 모델에 맞다. 정적 `WeakHashMap` 은 없앤다. `RedstoneTorchBlock`·`ChunkLevel`, 크기 **작음~중간**, 위험 낮음. 권장 **Opus medium**(region 합치기·나누기 경로라). **가장 먼저 고칠 것.** 고친 뒤 같은 방식으로 "region 스레드가 함께 쓰는 정적 가변 상태" 전체 검색(A03)을 권한다 — `block/` 안에서는 이 표 하나만 찾았다. |

### A07-02 — 블록 엔티티(움직이는 피스톤·깔때기·비교기 등)의 틱 범위가 바닐라보다 두 청크 좁다

| 칸 | 내용 |
|---|---|
| 심각도 | 높음 (눈에 띄는 바닐라 차이: 플레이어에게 보이는 거리에서 피스톤이 중간 상태로 멈춘다) |
| 종류 | 바닐라 차이 |
| 근거 | Furnace `world/Region.java:1573-1584` `blockEntityTicking`: 플레이어 청크와의 체비셰프 거리 ≤ `max(sim, 2) - 1`. `RegionLevel.java:301` 이 이 판정으로 `tickBlockEntities` 를 돌린다(엔티티 틱도 같은 판정, `:294`).<br>바닐라: 블록 엔티티는 `dvi` 의 묶인 틱 `a()` 가 `dvi.h(pos)` = 보더 안 && 청크 상태 ≥ BLOCK_TICKING && 엔티티 로드됨일 때, 그리고 `dcw.T()`(tickBlockEntities) 가 `shouldTickBlocksAt`(`aqu.a(long)` → `aqk.d(long)` = TickingTracker 레벨 ≤ 32)일 때 돈다. 플레이어 표는 `aqk.a(kf, aqv)` 에서 `TickingTracker` 에 레벨 `max(0, 31 - sim)`(`aqk.g()`, `aqa.a(ENTITY_TICKING)=31`)으로 걸리고 `aqf` 가 청크마다 +1 로 퍼진다. 그래서 레벨 ≤ 32 는 **체비셰프 ≤ sim + 1**. 시뮬레이션 거리 값은 `server.properties` 그대로 들어간다(`apm` 생성자 `this.b($$3.H)` → `aqs.b(int)` → `aqk.b(int)`).<br>즉 바닐라는 `sim + 1` 까지, Furnace 는 `sim - 1` 까지. 기본 10 이면 바닐라 반경 11, Furnace 반경 9. |
| 재현 | `A07TickRangeReproTest`(헤드리스 시험 서버, 시뮬레이션 거리 4·시야 10, 플레이어 청크 (0,0)): 동쪽 d 청크마다 피스톤 뒤에 레드스톤 블록을 놓고 8틱 뒤. 기준 커밋 결과 — d=2·3: 피스톤 머리까지 늘어남(바닐라와 같음), **d=4·5: `moving_piston` 에서 멈춤**(바닐라: 늘어남). 실패. 3번 돌려 같은 결과. |
| 제안 | `Region.blockEntityTicking` 을 바닐라 레벨 계산으로: 블록 엔티티는 블록 틱 범위(플레이어 표 `sim + 1`, 강제 로드 청크는 그 청크 + 둘레 1칸, 포털·플러그인 티켓도 같은 식)와 보더 안, 시야 거리 바깥(청크 상태가 BLOCK_TICKING 미만)은 제외. 엔티티 틱은 A07-07 참고(따로 판정을 나눠야 한다). 판정을 블록 엔티티용·엔티티용·블록 틱용(A07-03) 셋으로 나누고 한 곳에서 계산한다. `Region.java`·`RegionLevel.java`, 크기 **중간**, 위험 중간(틱 범위가 넓어져 일이 는다 — 고친 뒤 A17 측정 장면에서 다시 잰다). 권장 **Opus high**. |

### A07-03 — 예약 틱·블록 이벤트가 틱 범위와 상관없이 region 이 가진 모든 청크에서 돈다

| 칸 | 내용 |
|---|---|
| 심각도 | 높음 (눈에 띄는 바닐라 차이, 장치가 반만 도는 상태가 생긴다) |
| 종류 | 바닐라 차이 |
| 근거 | Furnace `block/ChunkLevel.java:492-504` `runScheduledTicks` 는 큐의 모든 예약을 돌리고, `:934-950` `runBlockEvents` 도 모든 이벤트를 돌린다. 청크가 틱 범위 안인지 보지 않는다(블록 엔티티만 A07-02 판정을 본다).<br>바닐라: `eyq`(LevelTicks)는 생성자 술어 `aqu.d(long)` = `areEntitiesLoaded && aqs.a(long)`(isPositionTicking: 블록 틱 범위 + 틱 청크 준비됨)인 청크의 예약만 꺼낸다(`eyq` `sortContainersToTick`, `this.b.test`). 범위 밖 예약은 그대로 남아 청크가 틱 범위에 들 때 돈다. 블록 이벤트는 `aqu.aw()`(runBlockEvents)가 `shouldTickBlocksAt` 아닌 칸의 이벤트를 `Y` 에 모아 다음 틱으로 되돌린다. |
| 재현 | `A07TickRangeReproTest` 같은 장면의 d=8(시뮬레이션 거리 4, 시야 10 — 플레이어에게 보이는 청크): 기준 커밋에서 **중계기 켜짐, 피스톤은 `extended=true` + 앞 칸 `moving_piston`**(블록 이벤트는 돌았는데 블록 엔티티는 안 돌아 멈춤). 바닐라: 중계기 꺼짐, 피스톤 그대로(이벤트 대기). 실패.<br>영향: 시야 거리가 시뮬레이션 거리보다 큰 서버(흔한 설정)에서 먼 곳의 관측기 시계·중계기 루프·물/용암 흐름(유체 예약 틱도 같은 큐)은 계속 돌고, 같은 장치의 피스톤·깔때기는 멈춘다. 바닐라는 둘 다 멈췄다가 함께 다시 돈다. 플레이어가 없는 region 이 청크를 잠시 들고 있는 동안(퇴출 유예)도 같다. |
| 제안 | `BlockTicks.tick` 에 청크 술어를 넘겨(바닐라 `tickCheck`) 범위 밖 청크의 예약은 꺼내지 않고 남긴다. 같은 시각·우선순위 안 순서가 바뀌지 않게, 꺼내지 않은 것은 큐에 그대로 둔다(바닐라도 컨테이너째 남긴다). `runBlockEvents` 는 범위 밖 이벤트를 모아 끝에 다시 넣는다(바닐라 `blockEventsToReschedule`). 술어는 A07-02 의 블록 틱 판정과 같은 것. `ChunkLevel.java`·`BlockTicks.java`·`RegionLevel.java`, 크기 **중간**, 위험 중간(예약이 오래 쌓일 수 있으니 저장·청크 내리기 경로를 같이 확인). A07-02 와 한 작업으로 묶는 것을 권한다. 권장 **Opus high**. |

### A07-04 — region 이 합쳐지거나 나뉘면 그 region 횃불들의 과열 기록이 사라진다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 (드문 시점에만 생기는 바닐라 차이) |
| 종류 | 바닐라 차이 |
| 근거 | `RedstoneTorchBlock.java:20,47,78` 기록의 키가 `RegionLevel` 객체다. `ChunkLevel.absorb`(`:653`)는 흡수된 레벨의 예약 틱·블록 이벤트·블록 엔티티·엔티티는 옮기지만 이 기록은 옮기지 않고, 흡수된 레벨은 버려진다. `splitTo`(`:672`)로 새 레벨이 생길 때도 기록이 따라가지 않는다. 바닐라 `dmd` 는 레벨(차원) 하나에 기록 하나다. |
| 재현 | `A07ReproTest.torchBurnoutHistoryIsLostWhenRegionLevelsMerge`: 레벨 B 에서 같은 칸 횃불을 7번 끄고 `A.absorb(B)` 뒤 A 에서 8번째로 끄고 받침 신호를 끊는다 → 기준 커밋에서 **횃불이 다시 켜짐**(바닐라: 60틱 안 8번째에서 타 버려 꺼진 채). 실패. 대조 `torchBurnoutControlSingleLevel`(레벨 하나에서 8번)은 꺼진 채로 통과 — 기대값이 바닐라 규칙대로임을 보인다. |
| 제안 | A07-01 제안 (가)로 함께 고친다(기록을 칸 위치와 함께 `absorb`/`splitTo` 에서 옮긴다). 별도 크기 **작음**. |

### A07-05 — 청크를 불러올 때 저장된 예약 틱이 이미 걸린 같은 시각 예약보다 뒤에 돈다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | 바닐라 차이 |
| 근거 | `ChunkLevel.java:479` `loadChunkTicks` 가 불러온 예약에 `subTickCount++`(지금까지 쓴 순번 뒤, 양수)를 준다. 바닐라 `eyo.a(long)`(LevelChunkTicks.unpack)은 `-n` 부터 하나씩(`int $$1 = -this.b.size(); … $$2.a(gameTime, $$1++)`) 음수 순번을 준다 — 같은 실행 시각·우선순위면 **불러온 것이 먼저** 돈다. |
| 재현 | `A07ReproTest.loadedScheduledTicksRunBeforeSameTickLiveOnes`: 세션 중 건 예약(칸 1,-60,1)과 같은 시각·우선순위로 옆 청크 예약(17,-60,1)을 불러온 뒤 실행 순서 → 기준 커밋 `[(1,-60,1), (17,-60,1)]`, 바닐라 기대 `[(17,…), (1,…)]`. 실패.<br>관측: 청크 경계에 걸친 장치에서 한쪽 청크가 막 불러와진 틱에, 같은 틱에 도는 두 부품의 순서가 바뀐다(예: 관측기·중계기 시계가 청크 경계에 걸쳐 있고 플레이어가 다가올 때). |
| 제안 | `loadChunkTicks` 에서 그 청크 목록 크기 n 으로 `-n..-1` 순번을 준다(바닐라 식 그대로). 저장 쪽(`chunkTicks` 정렬)은 바닐라가 우선순위 큐 배열 순서로 쓰는 것과 다르지만 같은 시각·우선순위 안의 순서만 영향이 있어 같이 고칠 필요는 낮다(참고). `ChunkLevel.java`, 크기 **작음**, 권장 **Sonnet medium**. |

### A07-06 — `isHandlingTick()` 이 예약 틱 동안만 참이라, 무작위 틱·블록 이벤트 중 피스톤 수축 종류가 바닐라와 다를 수 있다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | 바닐라 차이 |
| 근거 | `ChunkLevel.java:494,503` `handlingTick` 은 `runScheduledTicks` 안에서만 참. 바닐라 `aqu.a(BooleanSupplier)`(ServerLevel.tick)는 `this.Z = true` 를 틱 맨 앞(보더·날씨·시간 전)에 켜고 **블록 이벤트 처리(`aw()`) 뒤에** 끈다 — 예약 틱, 습격, 청크 틱(무작위 틱·스폰), 블록 이벤트 동안 모두 참. 이 값을 읽는 곳은 피스톤 `checkIfExtend`(`PistonBaseBlock.java:72`, 바닐라 `dsv.a(dcw,jd,dtc)`)뿐이다: 앞 두 칸에서 늘어나던 움직이는 피스톤이 있을 때 수축 종류를 2(떨어뜨리기)로 정하는 조건 중 하나. |
| 재현 | 가정(코드 대조). 블록 이벤트 중 다른 피스톤의 움직임으로 수축 신호를 받고, 앞 두 칸의 움직이는 피스톤 진행도가 0.5 이상·이번 틱에 아직 안 돈 경우에만 갈린다. 서버 쪽에서는 `triggerEvent` 가 그 움직이는 피스톤을 어차피 `finalTick` 해서 결과 블록은 같고, 다른 것은 클라이언트로 가는 블록 이벤트 종류(1 대신 2)뿐이라 시험으로 결과 차이를 만들지 못했다. 클라이언트 쪽 피스톤 예측 표시가 달라질 수 있다. |
| 제안 | `RegionLevel.tickBlocks` 에서 `handlingTick` 을 예약 틱 앞에서 켜고 `runBlockEvents()` 뒤에 끈다(`ChunkLevel` 에 켜고 끄는 메서드). 크기 **작음**, 권장 **Sonnet medium**. A07-02·03 작업에 같이 넣어도 된다. |

### A07-07 — (참고, A10·A11·A05 로) 엔티티 틱 범위도 바닐라보다 한 청크 좁다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고(이 파트 밖 — 해당 파트가 확인) |
| 종류 | 바닐라 차이 |
| 근거 | Furnace 엔티티 틱은 A07-02 와 같은 판정(`RegionLevel.java:294`, 체비셰프 ≤ sim − 1). 바닐라 `aqu.a(BooleanSupplier)` 의 엔티티 순회는 `this.I.a.j().c(chunkPos)` = `aqk.c(long)` = TickingTracker 레벨 ≤ 31 = **체비셰프 ≤ sim**. 기본 10 이면 바닐라 반경 10, Furnace 9. 2026-11-06 봇 시험(`bot-connection-test-2026-11-06.md`, 시뮬레이션 거리 2 에서 거리 4 갑옷 거치대 정지)은 두 계산 모두 "정지"라 이 차이를 가리지 못했다. |
| 재현 | 이 파트에서는 블록 엔티티(A07-02)만 시험했다. 엔티티는 바이트코드 계산(가정). |
| 제안 | A07-02 를 고칠 때 엔티티용 판정(≤ sim, 강제 로드는 그 청크)을 따로 두고, A10/A11 조사관이 몹 AI·물리 쪽 영향을 확인. 무작위 틱 청크 고르기(`Region.collectTickingChunks` 의 `min(clamp(sim,2,11)−1, 8)`)도 같은 바이트코드 계산으로 A08 이 다시 확인할 것을 권한다(이 파트에서는 보지 않았다). |

### A07-08 — (참고) 강제 로드 청크의 둘레 한 칸은 바닐라에서 블록 틱 범위다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | 바닐라 차이 |
| 근거 | 바닐라 `/forceload` 표(FORCED)는 레벨 31 로 TickingTracker 에도 걸려 그 청크는 엔티티 틱, 둘레 1칸은 레벨 32(블록 틱: 예약 틱·블록 이벤트·블록 엔티티)다. Furnace `Region.blockEntityTicking` 은 강제 로드 청크 자신만 참. 강제 로드 청크 경계에 걸친 장치(피스톤이 옆 청크로 밀어 넣는 블록 엔티티)는 바닐라에서 돌고 Furnace 에서 멈춘다. 시험은 만들지 않았다(A07-02 와 같은 판정 함수라 함께 고치면 된다). |
| 제안 | A07-02 작업에 포함. |

### A07-09 — (참고·시험 공백) 레드스톤 장치 타이밍 시험이 거의 없다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | 시험 공백 |
| 근거 | `furnace-server` 시험 중 레드스톤 장치 타이밍을 보는 것은 `PistonTest`, 가루 HashSet 순서(`BlockTypesTest.redstoneWire_updateOrderMatchesHashSet`), 전동 레일 대조(`PoweredRailSignalParityTest`)뿐이다. 중계기 지연·횃불 과열·관측기 펄스·비교기 모드, 틱 범위(A07-02·03)는 시험이 없어 이번 차이들이 그대로 남았다. |
| 재현 | `A07DeviceTimingTest` 4종은 기준 커밋에서 **통과**(바닐라 바이트코드로 센 틱 수와 같다). |
| 제안 | `A07DeviceTimingTest` 를 정식 시험으로 옮기고, A07-02·03 을 고칠 때 `A07TickRangeReproTest` 를 `furnace-test` 정식 시험으로 둔다(거리 sim, sim+1, sim+2 경계 확인). 크기 **작음**, 권장 **Haiku low**(옮기기만). 시험 자리 정리는 A19 가 정한다. |

---

## 다시 올리지 않은 것
- 스폰 청크 상시 로드(`spawnChunkRadius`) 없음 — 이미 `unimplemented-survey-2026-10-31.md` 에 있다. 스폰 청크의 레드스톤 장치가 플레이어 없이 돌지 않는 것도 여기에 든다.
- `x1-pr133` 검토의 지적(유혹 경계 시험, 엔티티 y 순서, `tickBlockEntities` 판정 기억 시험) — 그대로 유효하고 새 근거는 없다.
- 블록 엔티티 틱 중 /tp·/forceload 로 판정이 바뀌는 경우(X1 문서의 알려진 차이) — A07-02 를 고쳐도 그대로 둘 수 있다.
