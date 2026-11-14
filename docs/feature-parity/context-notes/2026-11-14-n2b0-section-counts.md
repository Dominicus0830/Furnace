# 바닐라 섹션 개수 특이점과 빈 열 WORLD_SURFACE (2026-11-14, N2b-0)

- 바닐라 `LevelChunkSection.recalcBlockCounts`(생성자 = 디스크에서 읽을 때)는 유체가 있는 칸을 `nonEmptyBlockCount` 에 두 번 세고(블록 + 유체), `tickingFluidCount` 는 무작위 틱 유체(용암)만 센다. `setBlockState`(하나씩)는 nonEmpty 를 한 번, tickingFluid 는 물 포함 모든 유체로 센다. 그래서 읽은 섹션에서 물을 지우면 tickingFluid 가 음수가 되고, 같은 블록이라도 "놓은 섹션"과 "읽은 섹션"의 `isRandomlyTicking` 이 다르다(무작위 섹션 300개 중 103개). 근거: `VanillaSectionOracleTest` 정답표(바닐라 jar 로 계산).
- Furnace `ChunkSection.nonAirCount`·`tickingCount` 는 다시 세기 판정과 같다(유체 두 번 세기 없음). 바닐라 개수를 흉내 내야 하는 곳(NMS shim 의 `LevelChunkSection`)은 두 규칙을 따로 구현해야 한다.
- 함정: `ChunkColumn.updateSurface`/`updateSurfaceWg` 는 `surface==0` 을 "프라임 전"으로 보고 갱신을 건너뛴다 — 열 전체가 공기여도 0 이라 빈 열에 놓은 블록이 WORLD_SURFACE 에 반영되지 않는다(바닐라는 반영). 별도 수정 과제로 넘겼다(`VanillaSectionOracleTest.knownEmptyColumn`).
- 바닐라 jar 클래스를 클래스패스로 직접 실행할 때 내부 서버 jar 의 서명 파일(`META-INF/*.SF`·`*.RSA`)을 지우지 않으면 `SecurityException: signer information does not match` 가 난다. `ProtoChunk` 는 섹션 배열을 다 채워 넘기면 바이옴 레지스트리 null 로 만들 수 있다.
