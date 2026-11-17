## 2026-11-17 T-204 후속: //fixblocks 뒤 레드스톤 가루 한 칸이 실행마다 갈리는 원인 (t204-redstone-flaky)

V5 실측(`2026-11-17-t204-v5-fawe-desktop.md`)에서 Furnace 세 번 중 한 번만 F3 가루 판 `-35,101,4` 의 `north` 가 `none`(Paper 두 번·Furnace 두 번은 `side`)이었다.

### 결론

**Furnace 결함이 아니라 FAWE 2.16.2 자체의 경쟁이다.** Furnace 는 그 경쟁이 터질 창을 Paper 보다 넓힌다(주인 아닌 스레드의 첫 섹션 읽기가 느림). Paper 에서도
같은 경쟁이 일어날 수 있다 — 확률이 낮을 뿐이다(아래 재현에서 첫 읽기 5 µs 이면 0.2 %, 140 µs 이면 13 %).

### 경로(FAWE 코드로 확인)

- `//fixblocks` = `editSession.setBlocks(region, PaperweightPlacementStateProcessor)` → `ParallelQueueExtent.apply` → `ApplyTask` 가 청크마다 스레드별 큐·스레드별
  처리기 사본(fork)으로 `applyBlock(FilterBlock)` 을 부른다. 여기서는 `processSet` 길(가장자리 2차 패스)이 아니다.
- 놓기 계산의 이웃 읽기: `PaperweightLevelProxy.getBlockState` → `getBlockStateAt` → (`processChunkSet` 없음) `extent.getBlock` → 그 스레드 큐의 `ChunkHolder` →
  `chunkExisting.getBlock`(GET — 이번 편집의 쓰기는 안 보고 월드 내용만). GET(`PaperweightGetBlocks`)은 월드 캐시(`ChunkCache`, 약한 참조)로 **스레드끼리 같은 객체**다.
- 가루 칸의 `north` 가 `none` 이 되려면 북쪽 이웃(`-35,101,3`, 가루)을 가루가 아닌 것으로 읽어야 한다 — 위가 열려 있고 이웃이 가루면 `shouldConnectTo` 가 바로
  `side`(Furnace `RedStoneWireBlock.getConnectingSide` = 바닐라). 문맥·클릭 자리는 스레드별 사본이라 섞이지 않는다.

### 경쟁

`CharBlocks.Section.EMPTY.get` 은 층 잠금 안에서 `update` 를 부른다. 두 스레드가 같은 층을 동시에 처음 읽으면 둘째는 잠금을 기다리는 사이 첫째가 실어 공개한 배열을
보고 `update(layer, 그 배열)` 로 **그 배열을 제자리에서 다시 채운다**. `PaperweightGetBlocks.update` 는 먼저 `toRaw` 로 팔레트 번호를 쓰고 그다음 팔레트를 사상하므로,
그 몇 µs 동안 그 배열을 읽는 스레드(첫째 — 이미 그 배열로 거르는 중)는 블록 순번 대신 팔레트 번호를 본다.

- 장면: 청크 (-3,0) 6층을 A(그 청크를 거르는 스레드)가 처음 싣는 동안, D(옆 청크 (-4,0) — F2 계단 `-49,100,z` 의 동쪽 이웃 `-48,100,z`)가 같은 층을 처음 읽는다.
- 그 층 팔레트는 처음 나온 순서로 공기 0, 계단 1, 돌 2, **가루 3**, 레드스톤 블록 4 쯤이고, FAWE 순번 3 은 `void_air` 다. 북쪽을 공기로 읽으면 아래(돌)에도 이어지지
  않아 `none`, 다른 세 방향은 다시 사상된 뒤라 `side` — 관측과 같은 꼴(`north=none, south=side, west=side, east=none`).
- 다른 꼴도 같은 원인으로 나올 수 있다: 거르는 칸 자신을 그 사이에 읽으면 가림에 안 걸려 날것 그대로(네 방향 모두 `none`) 남는다.

### 재현·측정(서버 없이)

- `tools/compat/fawe-paper/charblocks-race/` — FAWE 의 진짜 `CharBlocks`(Maven Central worldedit-core 2.16.2)로 위 두 스레드를 돌린다. 3000번씩, 첫 읽기 시간별
  제자리 다시 채움 / 북쪽 이웃 잘못 읽음: 5 µs 7/6, 20 µs 27/27, 50 µs 99/99, **140 µs 397/386**, 300 µs 876/864(이 컨테이너 4코어).
- Furnace 의 주인 아닌 스레드 첫 섹션 읽기(FAWE `update` 꼴: 섹션 배열 사본 → `getStates` → `data` 리플렉션 장벽 실체화): 시험 JVM(단언 켬)에서 중앙값 141 µs,
  p90 502 µs. 떨어진 컨테이너의 `data` 읽기(Paper 꼴 — 필드 그대로)는 5 µs. 수치는 임시 측정 시험으로 한 번 잰 것(커밋하지 않음).
- Furnace 쪽을 뺀 시험: `NmsMirrorTest.concurrentFirstDataReadsAgree` — 서버가 섹션을 바꿀 때마다 주인 아닌 6스레드가 동시에 `data` 를 처음 읽어 모두 그 판 내용
  그대로인지 30번. 실체화가 낡은 data 를 그대로 두게 고치면 `판 1` 에서 실패한다(변이 확인).

### 남는 것

- 이 경쟁은 FAWE 안의 것이라 Furnace 가 결과를 Paper 와 "같게" 고칠 수는 없다(특정 플러그인 저격 금지). 줄일 수 있는 것은 Furnace 의 첫 섹션 읽기 시간 — 줄이면
  창이 Paper 쪽으로 좁아지고 FAWE 큰 편집 읽기도 빨라진다(섹션 1만 개면 지금 약 1.4 s). 후속 후보로 남긴다.
- 실서버 확인(하지 않음): FAWE `queue.parallel-threads: 1` 이면 `//fixblocks` 가 한 스레드로 돌아 이 경쟁이 없다 — 데스크탑에서 그 설정으로 여러 번 돌려 갈림이 사라지는지
  보면 확정된다.
