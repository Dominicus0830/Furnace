# 영역 5 레벨·청크·저장 — 손 shim 명세 이전 1묶음 (2026-11-27)

- `net/minecraft/world/level/`(block·levelgen 제외) 손 shim 57개를 `level-chunk.spec`(class 줄만) + 조각(`src/shims/java`)으로 옮겼다. 몸통은 그대로, 머리는 잠금 파일. 전후 클래스 폴더를 `javap-same.sh` 로 견주니 달라진 것은 바닐라와 맞춘 곳뿐이다: GameRules·BiomeSpecialEffects·MapDecorationTypes 가 final 이 아니게 됨, BiomeSource 가 BiomeResolver 를, ChunkAccess 가 NoiseBiomeSource·LightChunk·StructureAccess 를 구현. 서명 관문 기준선 낡은 줄 10개 삭제.
- **못 옮긴 것(생성 머리와 손 구조가 어긋남):**
  - 인터페이스 사슬 LevelAccessor·LevelWriter·LevelReader·ServerLevelAccessor·WorldGenLevel(·CommonLevelAccessor): 손 구조는 LevelAccessor 가 LevelWriter 를 잇지만 바닐라는 CommonLevelAccessor→LevelSimulatedRW 로 setBlock 을 받는다. 옮기면 Level·NmsFeatures·DummyGeneratorAccess 의 setBlock/destroyBlock 이 깨진다. 바닐라 사슬(LevelSimulatedRW·EntityGetter 등)을 먼저 만들어야 한다.
  - Level: 위 인터페이스에 기댐. ChunkSource·DimensionDataStorage: 바닐라가 AutoCloseable 을 구현해 close() 를 안 갖춤. MapItemSavedData: 상위 SavedData 의 생성자 모양. ImposterProtoChunk: 바닐라 상위 ProtoChunk 가 손에 없음. ChunkPyramid: 바닐라 필드 형식 ImmutableList. LightEngine: 제네릭 `<M, S>` 가 잠금 머리에서 빠짐(생성기 한계).
  - WorldDataConfiguration·DimensionType: 바닐라 record 인데 손은 일반 클래스(성분 형식 DataPackConfig·MonsterSettings 가 shim 에 없음).

## 열거형 4개 (같은 날 2묶음)
- LightLayer·PushReaction(상수만 — 조각 삭제)·GameType(생성자 인자 상수, 조각은 생성자·필드·메서드)·EmptyBlockGetter(상수 INSTANCE)를 `level-chunk.spec` 으로 옮겼다. javap 대조: GameType 만 달라졌다 — 바닐라처럼 `StringRepresentable` 을 구현하므로 조각에 `getSerializedName`(이름 반환)을 손으로 더했다. 기준선 낡은 줄 1개 삭제.

## 3묶음 — 레벨 인터페이스 사슬 (같은 날)
- CommonLevelAccessor·LevelAccessor·LevelWriter·LevelReader·ServerLevelAccessor·WorldGenLevel·Level·ChunkSource 를 조각으로 옮겼다(앞 묶음에서 막힌 것). 막힌 이유는 한 클래스씩 옮겨서였다 — 사슬 전체를 한꺼번에 옮기면 잠금 머리가 바닐라 사슬(CommonLevelAccessor→EntityGetter·LevelReader·LevelSimulatedRW, LevelReader→BlockAndTintGetter·CollisionGetter·SignalGetter)을 만들어 setBlock 이 LevelSimulatedRW→LevelWriter 로 이어진다.
- 조각 머리는 `abstract class` 가 아니라 `class Level {` 로 써야 한다(생성기가 접근·abstract 를 잠금에서 붙인다). 컴파일 로그에서 "fragment header not found" 를 `error:` 로 grep 하면 놓친다.
- Level·ChunkSource 가 바닐라처럼 AutoCloseable 이라 `close()` 가 필요했다: ChunkSource.close() 는 빈 몸통, Level.close() 는 `getChunkSource().close()`(바닐라 같음 — javap 확인). ServerChunkCache 는 바닐라가 close 를 덮어 스레드를 멈추지만 Furnace 의 청크 공급원은 서버가 따로 닫아 지금처럼 빈 몸통을 받는다.
- 서명 관문 기준선 낡은 줄 64개 삭제(Level 계열·ServerLevel·BlockStateListPopulator·DummyGeneratorAccess 의 type-missing). javap 전후 대조: 달라진 클래스 5개 모두 바닐라와 맞춘 방향(상위 인터페이스 추가, close 추가).
