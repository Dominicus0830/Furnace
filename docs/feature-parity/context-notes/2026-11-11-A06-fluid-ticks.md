# 2026-11-11 A06-03 생성 직후 유체 틱(샘·폭포·구조물 속 물·대수층 경계)

바닐라는 생성 단계에서 유체 틱을 두 갈래로 남긴다. 둘 다 Furnace 청크 `fluid_ticks`(지연 0)로 옮겼다.

- **예약 틱(ProtoChunkTicks, eyr)**: `scheduleTick` 이 proto 청크에서는 지연을 버리고 SavedTick(지연 0)으로 쌓는다. (종류, 위치) 중복은 먼저 것만.
  - `SpringFeature`(edp): 놓은 칸에 그 유체 틱.
  - `StructurePiece.placeBlock`(ejv 154-157): 놓은 상태의 유체가 비어 있지 않으면 틱(바다 유적 물, 폐광 등).
  - `NetherFortress` CastleStalkRoom 가운데 용암: 상자 안일 때만 용암 틱.
  - `StructureTemplate.placeInWorld`(ent) `placeLiquid`: 물 원천만(Fluids.WATER) 물 잠김 칸에 넣고 틱 5 → proto 에서는 0. 용암은 잠기지 않는다.
- **후처리 표시(postProcessing)**: `NoiseBasedChunkGenerator.doFill`·`WorldCarver.carveBlock` 이 `aquifer.shouldScheduleFluidUpdate()` 이고 유체를 놓으면 표시한다.
  `LevelChunk.postProcessGeneration`(dvi.H)이 청크가 FULL 이 될 때 유체 칸은 `fluidState.tick` 을 바로 부른다(그 외는 updateFromNeighbourShapes, flag 20).
  - Furnace 는 이 즉시 tick 을 "지연 0 예약 틱" 으로 흉내 낸다: 청크 `fluid_ticks` 맨 앞에 후처리 유체, 그다음 생성 예약 틱. 설치 후 첫 틱에 같은 순서로 돈다.
    차이(가정, 실서버 대조 안 함): 바닐라는 FULL 승격 순간 바로 흐르고, Furnace 는 그 청크가 처음 틱할 때 흐른다. 같은 틱 안의 다른 틱과의 순서는 다를 수 있다.
  - `NoiseBasedAquifer.shouldScheduleFluidUpdate`: 바로 전에 본 칸이 고체였는지(`solidVisited`). `NoiseChunk` 에서 블록 단위로 갱신(고체로만 된 셀을 건너뛸 때도 고체로 친다).
- 구현 위치: `ChunkColumn.scheduleGeneratedFluidTick`·`postProcessMarks`, `ChunkPipeline.finishFluidTicks`(독립·공유 경로 둘 다), 이미 설치된 청크에 쓰는 라이브 경로는 `LiveGrid.commit`.

## 시험할 때 함정
- `TestServers.core` 에 맨 `NoiseWorldSource` 를 주면 Region 은 노이즈만 만든다(feature·구조물 없음). 생성 결과를 보려면 `AnvilWorldSource` 로 감싼다.
- 시험 서버는 스스로 틱하지 않는다. region 스레드에서 `clock.advanceGameTime(); r.tick();` 을 직접 돌려야 예약 틱이 돈다.
