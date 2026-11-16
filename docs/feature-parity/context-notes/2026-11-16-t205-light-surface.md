# 2026-11-16 T-205 빛 표면 묶음 B — 함정·교훈

설계·결과: `docs/feature-parity/compat-nms-t205-light-surface-2026-11-16.md`.

- **Paper 의 빛 쓰기 면은 막혀 있다**: Moonrise `LevelLightEngineMixin`·`ThreadedLevelLightEngineMixin` 이 `queueSectionData` 를 빈 몸통으로 덮고, 층 읽개의
  `getDataLayerData` 는 `SWMRNibbleArray#toVanillaNibble()` — 늘 새 사본이다. T-205 표의 "queueSectionData = 거울 일지 쓰기"는 바닐라(Spigot) 동작이었다.
  NMS 면을 옮길 때는 바닐라가 아니라 Paper 가 덮어쓴 몸통(믹스인 `@Overwrite`)부터 본다.
- **Starlight checkBlock 은 원천까지 본다**: Furnace 는 하늘빛 원천(열 높이)을 따로 두므로 플러그인의 checkBlock 은 검사만이 아니라 `blockChanged`(원천 칸 고치기 + 검사)로
  받아야 원시 쓰기 뒤 Paper 와 같은 하늘빛이 된다(`LightSurfaceTest#checkAfterRawWritesMatchesNormalWrites` — 돌판으로 하늘을 가리는 장면).
- **스냅샷에 빛을 담을 때 복사하지 말 것**: 층 참조를 넘기고 `ChunkLight` 가 넘긴 층에 처음 쓸 때 복사한다(비트 하나). 층을 통째로 갈아 끼우는 길(게시·relight 설치·
  FULL 채우기·compact)은 비트를 지워야 한다 — 안 지우면 쓸 때마다 한 번 더 복사할 뿐 값은 맞지만, 반대로 지우지 말아야 할 때 지우면 스냅샷이 바뀐다.
- 시험 함정: 발광석에서 맨해튼 거리 15 안의 칸은 블록빛이 0 이 아니다 — "멀리" 칸은 15 칸 넘게 떨어뜨린다.
