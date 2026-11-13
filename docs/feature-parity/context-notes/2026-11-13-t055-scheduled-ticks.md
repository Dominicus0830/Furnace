# 2026-11-13 T-055 예약 틱 청크별 컨테이너 — 바닐라 대조 기록

바닐라 근거: 1.21.1 `net.minecraft.world.ticks.LevelTicks`·`LevelChunkTicks`·`ScheduledTick`·`SavedTick`, `LevelAccessor.scheduleTick/createTick`,
`ServerLevel.startTickingChunk`·`unload`, `LevelChunk.registerTickContainerInLevel`·`unpackTicks`(Mojang 이름, 1.21.1 소스로 확인).

## 이번에 바닐라 모양으로 바꾼 것

- `BlockTicks` = `LevelTicks`: 청크 키 → 컨테이너(`LevelChunkTicks`: `PriorityQueue(DRAIN_ORDER)` + (칸, 종류) 집합), `nextTickForContainer`,
  `containersToTick(INTRA_TICK 머리 비교)`, `drainContainers`/`drainFromCurrentContainer`/`rescheduleLeftoverContainers`, 지연 계산 `toRunThisTickSet`.
  옛 구현의 `parked` 맵과 `drainByContainer` 흉내는 없어졌다(바닐라 구조가 순서를 그대로 보장).
- **로드 안 된 청크의 예약은 버린다**: `LevelTicks.schedule` 은 `allContainers.get(chunk)` 가 없으면 `Util.pauseInIde` 뒤 return.
  `createTick` 이 먼저 `nextSubTickCount()` 를 부르므로 버린 예약도 순번 하나를 쓴다 → `ChunkLevel.scheduleTick/scheduleFluidTick` 이 순번을
  먼저 쓰고 `chunk(cx, cz) == null` 이면 버린다(`markDirty` 도 하지 않음). "로드됨" = Furnace 의 이 region 소유 로드 청크(바닐라 FULL 컨테이너 등록과 같은 때 —
  Region 은 `world.putChunk` 뒤에 `loadChunkTicks` 를 부른다). 적재 중 청크의 저장 예약(`loadChunkTicks`)은 이 문을 타지 않는다.
- 빈 컨테이너는 그 틱 실행이 끝난 뒤 내려 놓는다(바닐라는 청크가 내려갈 때까지 둔다 — 관측 차이 없음, 메모리만).

## 아직 바닐라와 다를 수 있는 것 (후속)

1. **저장 목록 순서**: 바닐라 `LevelChunkTicks.save` 는 컨테이너 `PriorityQueue` 를 배열 순서(힙 순서)로 쓴다. Furnace 는 실행 순서로 정렬해 쓴다
   (옛 구현은 `chunkTicks` 는 정렬, 내리기 `removeChunk` 는 전역 힙 순서였다 — 이번 PR 에서 둘 다 정렬로 맞춤). 다시 불러오면 목록 순서가 순번이 되므로,
   같은 (시각, 우선순위)인 예약끼리의 실행 순서가 청크를 내렸다 올린 뒤 바닐라와 다를 수 있다. 컨테이너가 이제 바닐라와 같은 `PriorityQueue` 라
   반복 순서로 쓰면 맞출 수 있다(후속 PR).
2. **불러온 예약의 순번**: 바닐라 `unpack` 은 `-n .. -1`(목록 순서)을 주고 레벨 순번 카운터를 쓰지 않는다. Furnace 는 `subTickCount++` 를 쓴다.
   같은 (시각, 우선순위)에서 바닐라는 불러온 예약이 다른 청크의 기존 예약보다 먼저 돈다(음수 순번). 후속 PR.
3. **풀기 시점**: 바닐라는 FULL 때 컨테이너를 등록하되 저장 예약은 `pendingTicks` 로 두고, `startTickingChunk`(청크가 BLOCK_TICKING 이 될 때)
   `unpack(그때 게임 시각)` 으로 실행 시각을 정한다. Furnace 는 적재 때 바로 건다(적재 시각 + 남은 틱). 보기 거리 + 1 테두리 청크처럼 로드는 됐지만
   BLOCK_TICKING 이 아닌 청크에서, 나중에 틱 범위에 들 때 바닐라는 그때부터 남은 틱을 세고 Furnace 는 이미 지난 예약으로 바로 돈다.
4. **/clone 복사**: 바닐라 `copyAreaFrom` 은 컨테이너 외에 `alreadyRunThisTick`·`toRunThisTick` 도 모은다(명령 블록의 예약 틱 안에서 /clone 하면
   이번 틱에 이미 돈 예약도 복사됨). 넣는 순서도 정렬이 아니라 (이미 돈 것, 남은 것, 청크 x→z 순 컨테이너 힙 순서)다. Furnace 는 컨테이너 것만 정렬해 넣는다. 후속 PR.
5. **동점 순서**: (우선순위, 순번)이 같은 두 청크 머리(불러온 예약의 음수 순번, /clone 순번 겹침에서만 생김)는 바닐라에서 `Long2LongOpenHashMap` 순회
   순서로 정해진다. Furnace 는 region 마다 컨테이너 집합이 달라 이 순서를 그대로 옮길 수 없다(구조적 한계 — 기록만).
6. 저장 목록에 같은 (칸, 종류)가 두 번 있으면(외부 편집기로만 생김) 바닐라 `unpack` 은 둘 다 큐에 넣는다. Furnace 는 하나만 건다.

## 시험

- `BlockTicksIdentityTest`: 옛 구현 사본(`identity/legacy/blockticks/LegacyBlockTicks`, dec5d2c7)과 무작위 대본 차분(고정 400개, 수동 scale=100 + 무작위 시드 5개 = 24만 대본 확인).
  옛 `removeChunk` 목록 순서만 정렬해 맞댄다(위 1).
- `ScheduledTickSceneGoldenTest`(`scheduled-ticks-scene`): 물·용암·레드스톤·미뤄 둔 청크·청크 저장/재적재 장면 300틱.
- `ScheduledTickUnloadedChunkTest`: 버림·순번 건너뜀·이웃 청크·적재 예약 유지.
- `DeferredBlockTicksTest.matchesVanillaLevelTicksOrderOnRandomScenes`: 바이트코드에서 옮긴 바닐라 모델과 대조(기존).
