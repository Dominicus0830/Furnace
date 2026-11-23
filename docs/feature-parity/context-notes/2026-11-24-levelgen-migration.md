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
- `forkPositional()` 는 구현했다(`PositionalRandomFactory`·`LegacyPositionalRandomFactory`·`Mth.getSeed`) — 값은 Paper 서버 jar 에서 뽑은 기대값과 `LevelgenValueShimTest` 로 견준다. 크로소로 난수원(`XoroshiroRandomSource`)과 그 공장은 아직 없다.

## 다음 묶음으로 미룬 것 (검토 권고)
`XoroshiroRandomSource`·`ThreadSafeLegacyRandomSource` 의 `forkPositional`(와 그 공장)은 아직 없다 — 해당 난수원 shim 자체가 없어서 함께 만든다. `Mth.getSeed` 는 영역 9(PR 596)의 것을 쓴다.
