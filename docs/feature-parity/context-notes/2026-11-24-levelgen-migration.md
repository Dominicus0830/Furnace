# 영역 6 levelgen — 손 shim 이전 1차 (2026-11-24)

명세 `src/shimspec/levelgen.spec` 로 옮긴 12개: `RandomSupport`·`BitRandomSource`·`MarsagliaPolarGaussian`·`SingleThreadedRandomSource`·`LegacyRandomSource`·`WorldOptions`·`BelowZeroRetrogen`·`DebugLevelSource`·`FlatLevelSource`·`NoiseBasedChunkGenerator`·`RandomState`·`structure/BoundingBox`.
몸통은 `src/shims/java` 조각(`git mv`), 상수 4개는 명세 줄. 이전 전후 클래스를 `tools/ci/javap-same.sh` 로 견줬다(32 클래스 중 5 다름).

## 달라진 곳 (모두 Paper 1.21.1 에 맞춘 것)
- 순서만 다름: `BitRandomSource`(추상 `next` 가 생성 위치로), `RandomSupport`(비공개 필드 위치). 동작 같음.
- `NoiseBasedChunkGenerator`: Paper 처럼 `final`.
- `WorldOptions`: `DEMO_OPTIONS`·`defaultWithRandomSeed`·`parseSeed`·`randomSeed`·옛 사용자 지정 설정 칸 추가. `withSeed` 는 Paper 처럼 `seed.orElse(randomSeed())` — 예전에는 `java.util.Random` 으로 뽑아 난수원이 달랐다. 기준선 줄(shim 만 final) 삭제.
- `BelowZeroRetrogen`: 생성자 비공개, 칸 표는 `BitSet`, `hasBedrockHole`(예전 이름 `isBedrockHole` 은 Paper 에 없음)·`hasBedrockHoles` 는 `!isEmpty()`(예전에는 Optional 이 있기만 하면 참 — 틀렸다). 시험용 만들기는 Furnace 전용 `furnaceCreate`.

## 남은 것 (다음 조각)
- 이전 안 한 손 shim 9개: `Heightmap`·`Structure`·`StructureStart`·`StructureTemplateManager`·`Feature`·`ConfiguredFeature`·`PlacedFeature`(영역 5 region 소유 규칙·Holder/Registry 설계 — 엔티티 스레드 몫과 맞춰야 함), Furnace 전용 `FurnaceStructure`·`FurnaceFeature`(바닐라 이름이 아니라 명세 대상 아님).
- `LegacyRandomSource`/`SingleThreadedRandomSource` 의 `forkPositional()`(위치 난수 공장 `PositionalRandomFactory` 와 안쪽 클래스)와 `RandomSource#forkPositional` 이 없다 — 지금은 T-261 껍데기가 던진다. 난수 호출 순서가 걸려 있어 바닐라 해시(MD5·`Mth.getSeed`)대로 따로 이식해야 한다.
