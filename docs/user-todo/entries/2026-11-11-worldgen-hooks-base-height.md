# 월드젠 훅과 구조물 배치 높이 — 2026-11-11

설명: `docs/api/worldgen-hooks.md` "규칙", PR #228·표면 바이옴 후속 PR.

## 결정할 것

- **플러그인 지형 높이를 구조물 배치에 반영하는 훅을 만들까?** 지금 구조물 배치 높이(바닐라 `getBaseHeight`·`getBaseColumn`)는 바닐라 노이즈로만 판정한다.
  플러그인이 `NOISE`·`SURFACE` 훅으로 지형을 크게 바꾼 월드에서는 마을·폐허 포털 등이 바닐라 지형 높이에 놓여 떠 있거나 묻힐 수 있다.
  예전 문서는 "훅 지형 위에 선다"고 적었지만, 높이 질의를 노이즈만으로 바꾼 뒤(b7e159a6)로는 실제와 달랐다 — 이번에 문서를 실제에 맞췄다.
  - 추천: 기본은 지금처럼 바닐라 높이로 두고, CraftBukkit `ChunkGenerator.getBaseHeight(WorldInfo, Random, x, z, HeightMap)` 와 같은 모양의
    선택 훅(`baseHeight(x, z, heightmap)`)을 `ChunkGenerationHooks` 에 더한다. 구현하지 않은 플러그인은 지금과 같다. 바닥 열(`getBaseColumn`, 화석·폐허 포털)은 바닐라 그대로 둔다 — CraftBukkit 도 늘 바닐라에 맡기므로 Bukkit 과 같다. Bukkit 생성기 호환 어댑터도 이 훅으로 잇는다.
  - 다른 길: 훅 월드에서는 구조물 높이 질의마다 훅을 적용한 청크를 만든다(예전 방식, 느리고 훅이 비결정적이면 구조물이 어긋남), 또는 지금대로 둔다.
