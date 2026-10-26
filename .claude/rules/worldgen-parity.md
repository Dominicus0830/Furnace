---
paths:
  - "furnace-server/src/main/java/org/domi/furnace/server/world/gen/**"
  - "furnace-server/src/main/resources/worldgen/**"
  - "furnace-server/src/test/**/worldgen/**"
  - "furnace-server/src/test/resources/worldgen/**"
  - "worldgen-oracle/**"
  - "tools/parity-audit/**"
---
# worldgen parity 규칙 (1.21.1)

시작 전 `docs/feature-parity/vanilla-audit-2026-10-07.md`("확정된 편차", "후속 과제")와 `docs/feature-parity/context-notes.md`를 읽는다.

## RNG·재시드
- feature마다 `setFeatureSeed(decorationSeed, globalIndex, step)`로 독립 재시드된다. index만 맞으면 다른 feature 구현 여부와 무관하게 해당 feature 좌표가 정해진다.
- feature index는 `FeatureSorter`(바닐라 위상정렬)가 정한다. 입력 biome 순서는 `possibleBiomes()` = `biome_parameters.json` 첫 등장 distinct(53개)이며 registry 전체(네더·엔드 포함)가 아니다.
- 미구현 feature를 목록에서 **drop하지 않는다.** `NoopFeature`로 남겨야 index가 보존된다.
- `fromHashOfPositional()`은 `fromHashOf()`와 다른 알고리즘이다. 서로 치환하면 parity가 깨진다.
- state provider의 noise는 `LegacyRandomSource` 경로(`LegacyNormalNoise`)다. Xoroshiro(MD5) 경로 `NormalNoise`를 재사용할 수 없다.
- `getState`는 생존 판정보다 먼저 호출된다(RNG 소비 순서).
- `noise_threshold_count`/`noise_based_count`는 RNG를 소비하지 않는다. random_patch는 try당 `nextInt` 6회다.

## 정직성 가드
- 지원하지 않는 변형(provider·config)은 Noop 유지. "대충 비슷하게" 활성화 금지.
- 바닐라의 알려진 quirk는 **그대로 재현**한다(예: `MultifaceGrowthFeature` 탐색이 origin+1에서 전진하지 않음). 고치지 않는다.
- 다중 청크 decoration(나무 leaves 갱신, lake, geode 등)은 현재 center 청크 1개만 읽고 쓴다는 아키텍처 제약이 있다. 우회하지 말고 별도 마일스톤으로 다룬다.

## 검증 방법
- fixture는 `worldgen-oracle`(paperweight `1.21.1-R0.1-SNAPSHOT`)로 생성한다. oracle 환경은 태그가 자동 바인딩되지 않으니 `canSurvive`류 로직이 이상하면 태그(FLUID 포함) 바인딩부터 의심한다. 과거 fixture 오염 사례가 있다.
- oracle의 `setBlock`은 바닐라 `WorldGenRegion` writeRadius=1(3×3)과 같아야 한다.
- 분기 추적: `oracle.rngTrace` ↔ `furnace.rngTrace` 호출 시퀀스 diff, 이어서 `oracle.dumpColumns`로 월드 상태 대조.
- 최종 권위는 바닐라 실서버 `.mca` 대조(`tools/parity-audit/README.md`)다. fixture 0 mismatch만으로 "일치"라고 쓰지 않는다.
- 바닐라 자체도 같은 명령 2회에서 0.05~0.61% 달라진다. 비교 시 순정 2회 이상의 노이즈 바닥을 분리해서 hard 불일치를 본다.

## 데이터
- 블록 state id의 정답은 `resources/blocks/blockstates_1.21.1.txt`(canonical key `name;k1=v1,k2=v2`, property 이름 오름차순)다. `blocks/1.21.1.txt`는 블록당 default state 1개뿐이라 Properties가 있는 state에 쓰면 byte-exact가 깨진다.
- 바닐라 데이터 추출은 `tools/vanilla-data/`. `ExportBlockAttrs`는 난독화 이름을 쓰는 1.21.1 전용이다.
- 순정 지형 단독 정답지는 모든 바이옴 features를 비운 데이터팩(`MakePack`)으로 만든다.
