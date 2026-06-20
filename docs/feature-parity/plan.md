# Feature/Decoration Parity 계획 (1.21.1)

## 목표
Furnace의 feature(장식) 생성을 vanilla 1.21.1과 좌표 단위로 일치시킨다.
동일 seed·동일 좌표에서 광석·장식 블럭이 vanilla와 같은 위치에 나오게 한다.

## 확인된 근본 원인
1. `FeatureSorter.build`가 vanilla 위상정렬을 안 함 → feature index가 vanilla와 다름.
   - `data.biomes()` = `Map.copyOf(HashMap).keySet()` → 비결정적 순서.
   - 위상정렬 없음(naive first-seen).
   - 미구현 feature를 `BiomeFeatureData`가 drop → index 압축.
   - feature index는 `setFeatureSeed(decorationSeed, index, step)`에 들어가 위치를 결정 → index 틀리면 좌표 틀림.
2. feature 타입 5종(ore·scattered_ore·spring·disk·freeze_top_layer)·placement modifier 5종만 구현.
   - 나머지(나무·풀·꽃 등) 미생성 → "잡블럭 없음".

## 핵심 통찰
광석 위치는 자기 index+seed로만 결정(feature마다 재시드, 독립).
→ **index만 vanilla와 맞추면** 다른 feature 미구현이어도 구현된 feature는 좌표까지 정확.
→ 미구현 feature를 no-op placeholder로 목록에 남겨 index 보존. 나중에 placeholder를
   실제 구현으로 교체하면 자동으로 올바른 좌표에 생성됨.

## Phase
- Phase 1: FeatureSorter를 vanilla(`net.minecraft.world.level.biome.FeatureSorter`)대로 재구현.
  전체 feature 목록을 placeholder 포함해 보존. 결정적 biome 순서.
- Phase 1b: OracleMain에 decoration 덤프 모드 추가 → fixture 생성 → 좌표 parity 테스트.
- Phase 2: placeholder를 실제 feature/placement modifier 구현으로 점진 교체.

## 전제
- vanilla 비교는 반드시 1.21.1 (사용자 확인 완료).
- 정확한 index 일치는 vanilla `possibleBiomes()` 순서 재현 필요 → Phase 1b oracle로 검증·보정.
