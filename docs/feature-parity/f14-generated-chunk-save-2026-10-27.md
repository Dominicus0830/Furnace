# F-14 생성만 한 청크의 디스크 저장 (2026-10-27)

결정(사용자 위임): 바닐라처럼 **생성(FULL 승격)된 청크는 편집하지 않아도 저장한다.**

## 1. 현재 동작 확정 (코드 기준)

결론: 저장 정책 자체는 이미 1c712174(2026-10-10 "generated chunks saved")에서 바닐라 쪽으로 들어가 있었다. 감사 문서 N-09 행의 "디스크 경로는 편집 청크·외부 월드만 탄다(F-14)" 는 그 뒤 코드와 맞지 않는 서술이다.

| 경로 | 위치 | 동작 |
|---|---|---|
| 비동기 로드 | `world/gen/AnvilWorldSource.loadChunkAsync` | 디스크에 FULL 청크가 있으면 디코드 후 `clearDirty`. 없거나 proto 면 생성 후 `markDirty` |
| 동기 로드 | `AnvilWorldSource.generate(cx,cz[,ioLane])` → `fromBytesOrGenerate` | 같음(생성이면 `markDirty`) |
| 설치 | `Region.finishLoad`, `Region.chunkAtBlocking` | `col.dirty()` 면 `dirtyChunks` 에 넣는다. 월드젠 엔티티를 실제 엔티티로 바꾼 청크도 dirty |
| autosave | `Region.beginAutosave/stepAutosave` | `dirtyChunks` 를 큐로 떠서 틱당 4 ms 예산(N-12) 안에서 `ChunkSnapshot.forSave` → `World.saveChunkAsync`(IoLanes 직렬, NBT 인코딩·deflate 는 IO 레인) |
| 퇴출 | `Region.evictIdleChunks` | 구독·티켓 없는 청크를 유예 뒤 내린다. 엔티티·POI 저장, 예약 틱이 있거나 dirty 면 청크 저장 |
| 종료 | `World.saveAll` → `Region.saveDirtyChunks` | 남은 dirty 전부 |
| 저장 실패 | `World.onChunkSaveFailed` | 재시도 끝 실패면 다시 dirty |

- `freshlyGenerated` 는 저장 여부와 무관하다. 노이즈 생성기에서만 서며, 청크 생성 때 동물 배치(`spawnOriginalMobs`)에만 쓴다(평지 생성기는 바닐라도 없음). 평지·노이즈 모두 생성 청크는 dirty.
- `AnvilWorldSource` 가 아닌 순수 생성 소스(시험·벤치)는 `saveChunkAsync` 가 아무것도 안 한다(디스크 없음).
- 저장 항목: 블록·바이옴(구역별 팔레트), 높이맵 4종, 블록 엔티티(월드젠 상자 NBT 포함), 구조물 `starts`/`References`(`ChunkSnapshot.forSave` 가 `col.structures()` 를 `structures` 로), 예약 틱, 빛, `Status=minecraft:full`, `DataVersion=3955`. 엔티티는 `entities/`, POI 는 `poi/` 따로(바닐라와 같은 분리).

## 2. 바닐라 기준 (1.21.1 디컴파일 대조)

- `ChunkMap.save(ChunkAccess)`: `isUnsaved()` 가 거짓이면 저장 안 함 → `setUnsaved(false)` → 상태가 LEVELCHUNK 가 아니면 (a) 디스크에 이미 FULL 청크가 있으면 건너뛰고 (b) EMPTY 이면서 유효한 구조물 시작점이 없으면 건너뛴다 → 직렬화해 쓴다.
- `LevelChunk(ServerLevel, ProtoChunk, …)` 생성자 끝에서 `unsaved = true`. 즉 FULL 로 승격된 모든 청크는 한 번은 저장된다. 디스크에서 읽은 청크는 unsaved=false.
- 자동 저장(`saveAllChunks(false)`): 청크마다 10 초 쿨다운(`chunkSaveCooldowns`), 틱 루프에서는 시간이 남는 동안 틱당 최대 20 청크. 언로드는 저장 후 내린다.
- 차이(가정 아님, 의도): Furnace 는 proto(중간 단계) 청크를 저장하지 않는다 — 생성이 한 번에 FULL 까지 가고 proto 를 보관하지 않으므로. 바닐라 월드 가장자리의 proto 청크는 읽을 때 다시 생성한다(기존 동작).

## 3. 이번 변경

1. **퇴출에도 시간 예산**: `evictIdleChunks` 가 내릴 청크를 한 틱에 전부 처리했다. 생성 청크가 모두 dirty 라 순간이동·로그아웃처럼 수백 청크가 한꺼번에 유예를 넘기면 한 틱이 크게 튄다. autosave 와 같은 4 ms 예산으로 끊고 남은 것은 다음 틱에 내린다(유예 카운트는 이미 넘었으므로 다음 틱에 바로 후보). 관측 차이: 청크가 몇 틱 늦게 내려갈 뿐, 그 사이 다시 구독되면 내리지 않는다.
2. **예산 하나 공유**(검토 반영): 처음엔 퇴출과 autosave 가 각자 4 ms 마감을 정해, 둘이 겹치는 틱은 8 ms + 두 항목까지 썼다. 지금은 틱 첫머리(퇴출 직전)에 `saveDeadline` 하나를 정하고 퇴출이 먼저 쓴 나머지를 autosave 가 쓴다. 퇴출이 예산을 다 쓰는 틱엔 autosave 가 쉬지만, 퇴출도 저장이라 손해는 없다.
3. **IO 레인 역압**(검토 반영): `IoLanes` 레인마다 대기열 길이(`pending`, volatile)와 최대 대기열·최대 대기 시간을 센다. `World.ioBackedUp()`(가장 긴 레인 ≥ `ioBacklogLimit`, 기본 256, `-Dfurnace.io.backlogLimit`)이면 퇴출·autosave 루프가 그 틱 저장 제출을 멈춘다 — 퇴출은 내리지 않고 남기고, autosave 는 큐 머리에 되돌린다. 로드 우선순위는 바꾸지 않는다(같은 파일 FIFO 가 저장 후 재로드의 정확성을 보장하므로).
4. **저장소 로그**: 자동 저장 시작 때 월드당 한 번, IO 풀에서 `region/` 의 .mca 수·크기, `FileStore.getUsableSpace()`, 레인 최대 대기열·최대 대기 시간을 INFO 한 줄. 여유 1 GB 미만이면 WARN.
5. 시험 `GeneratedChunkSaveTest` 추가.

측정(이 샌드박스, region 스레드 CPU 시간 `ThreadMXBean`, 평지 24×24=576 청크, 시험 4회 실행 중 마지막 값):

| 상황 | 틱 최대 | 중앙 | p95 | 합계 | 틱 수 |
|---|---|---|---|---|---|
| 저장 없는 기준 틱 | 4.96 ms(20틱 최대) | | | | |
| autosave 576 dirty | 24.98 ms(2번째 틱, JIT 추정) | 4.67 ms | 5.59 ms | 606 ms | 127 |
| 퇴출 576 — 수정 전 | 495.38 ms(한 틱) | | | | 1 |
| 퇴출 576 — 수정 후 | 5.82 ms | 4.46 ms | 5.50 ms | 458 ms | 102 |
| (2차) autosave 576 | 27.80 ms(3번째 틱) | 4.34 ms | 5.26 ms | 551 ms | 122 |
| (2차) 퇴출 576 | 5.62 ms | 4.47 ms | 5.04 ms | 473 ms | 107 |
| (2차) 퇴출 288 + autosave 288 같은 틱 시작 | 5.34 ms(기준 1.70) | 4.44 ms | 5.20 ms | | 97 |

역압 시험(바탕 풀을 막고 임계 8, 144 청크): 51틱 동안 레인 대기열 8 에서 멈추고 dirty 136 이 남았다가, 풀자 모두 저장.

autosave 의 첫 몇 틱 최대값은 실행마다 17.7~26.0 ms 로 흔들렸다(한 항목이 길었다 — JIT·첫 할당으로 추정, 확인 안 함). 시험은 p95 와 틱 수로 분산을 보고, 최대는 100 ms 미만만 본다.

## 4. 트레이드오프·위험

- **디스크 크기**: 탐험한 만큼 `region/`·`entities/`·`poi/` 가 커진다(바닐라와 같은 성질). 사전 생성(pregen)이나 엘리트라 탐험 서버는 GB 단위가 된다. 크기 제한·가지치기는 없다(바닐라도 없음). 섹터 할당기가 옛 섹터를 재사용하지 않는 F-05 와 겹치면 같은 청크를 여러 번 저장할수록 파일이 더 자란다 — F-05 를 먼저 고칠 이유가 하나 늘었다.
- **autosave I/O 몰림**: region 스레드 쪽은 퇴출·autosave 가 틱당 4 ms 예산 하나를 나눠 쓴다(처음엔 각자 4 ms 라 겹치는 틱은 8 ms+). 인코딩·deflate·쓰기는 IoLanes 에서 하며, 레인 대기열이 임계(기본 256)를 넘으면 region 쪽이 저장 제출을 멈춘다. 남는 위험: 임계 아래에서도 대기열 256 개가 밀려 있으면 같은 레인 로드가 그만큼 늦다(실제 디스크 측정 없음, 주기 로그로 관찰). 역압이 오래가면 dirty 청크가 메모리에 남아 퇴출이 미뤄진다(메모리 증가).
- **이미 저장된 월드와의 호환**: 1c712174 이후 Furnace 월드는 이미 생성 청크를 저장해 왔다. 그 이전 월드는 저장 안 된 청크를 처음 방문할 때 생성·저장한다 — 형식 변화 없음. 바닐라 월드를 열면 FULL 청크는 그대로 읽고 proto 는 다시 생성한다.
- **월드젠 고정(세대 변경)**: 한 번 저장된 청크는 이후 Furnace 월드젠을 고쳐도 다시 생성되지 않는다. parity 수정(예: 새 feature 구현) 효과는 **새로 생성되는 청크에만** 나타나고 경계에 단차가 생긴다. 바닐라 업그레이드와 같은 성질. 월드젠 개발 중 parity 측정은 새 월드나 `tools/parity-audit`(디스크를 안 거치고 직접 생성)으로 해야 한다. 기존 월드를 새 월드젠으로 다시 만들려면 `region/` 을 지우는 것 말고 방법이 없다(블롭 단위 재생성 도구 없음).
- **parity-audit 호환**: 저장 결과는 `tools/parity-audit/Harness.readChunk` 의 헤더·오프셋·압축(1/2/3) 처리 방식으로 읽힌다(시험 (c), 파서 자체는 서버 NBT). 다만 parity-audit 의 생성 비교 경로는 디스크를 읽지 않으므로 이 변경의 영향을 받지 않는다.

## 5. 시험

`furnace-server/src/test/java/org/domi/furnace/server/world/GeneratedChunkSaveTest.java`
- (a) `untouchedGeneratedChunk_isSavedAndReloadedWithoutRegenerating`: 생성 카운터 1 → 퇴출 저장 → 재로드 후에도 1, 저장소를 새로 연 월드에서는 0. 블록 전부·바이옴·높이맵 3종·구조물 참조·월드젠 주민 수(1) 동일.
- (b) `autosaveOfManyGeneratedChunks_isSpreadAcrossTicks`, `massEviction_isSpreadAcrossTicks`: 위 표.
- (b') `evictionAndAutosaveInSameTick_shareOneBudget`, `ioBacklog_pausesSavesAndResumes`, `saveThenReload_readsLatestUnderBacklog`(레인이 막힌 동안 퇴출 저장 → 재로드가 편집을 읽는다).
- (c) `savedGeneratedChunk_isReadableLikeParityAudit`: Harness 는 빌드 밖 단일 파일 도구라 직접 부르지 못해 `readChunk` 의 헤더·오프셋·압축 처리 방식을 그대로 옮겨 읽는다(NBT 파서는 서버 것). Status·좌표·DataVersion·팔레트·바이옴·높이맵 4종·structures.References.
