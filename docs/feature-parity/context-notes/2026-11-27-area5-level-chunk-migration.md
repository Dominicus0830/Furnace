# 영역 5 레벨·청크·저장 — 손 shim 명세 이전 1묶음 (2026-11-27)

- `net/minecraft/world/level/`(block·levelgen 제외) 손 shim 57개를 `level-chunk.spec`(class 줄만) + 조각(`src/shims/java`)으로 옮겼다. 몸통은 그대로, 머리는 잠금 파일. 전후 클래스 폴더를 `javap-same.sh` 로 견주니 달라진 것은 바닐라와 맞춘 곳뿐이다: GameRules·BiomeSpecialEffects·MapDecorationTypes 가 final 이 아니게 됨, BiomeSource 가 BiomeResolver 를, ChunkAccess 가 NoiseBiomeSource·LightChunk·StructureAccess 를 구현. 서명 관문 기준선 낡은 줄 10개 삭제.
- **못 옮긴 것(생성 머리와 손 구조가 어긋남):**
  - 인터페이스 사슬 LevelAccessor·LevelWriter·LevelReader·ServerLevelAccessor·WorldGenLevel(·CommonLevelAccessor): 손 구조는 LevelAccessor 가 LevelWriter 를 잇지만 바닐라는 CommonLevelAccessor→LevelSimulatedRW 로 setBlock 을 받는다. 옮기면 Level·NmsFeatures·DummyGeneratorAccess 의 setBlock/destroyBlock 이 깨진다. 바닐라 사슬(LevelSimulatedRW·EntityGetter 등)을 먼저 만들어야 한다.
  - Level: 위 인터페이스에 기댐. ChunkSource·DimensionDataStorage: 바닐라가 AutoCloseable 을 구현해 close() 를 안 갖춤. MapItemSavedData: 상위 SavedData 의 생성자 모양. ImposterProtoChunk: 바닐라 상위 ProtoChunk 가 손에 없음. ChunkPyramid: 바닐라 필드 형식 ImmutableList. LightEngine: 제네릭 `<M, S>` 가 잠금 머리에서 빠짐(생성기 한계).
  - WorldDataConfiguration·DimensionType: 바닐라 record 인데 손은 일반 클래스(성분 형식 DataPackConfig·MonsterSettings 가 shim 에 없음).
  - 열거형 LightLayer·GameType·PushReaction·EmptyBlockGetter: 다음 묶음.
