# 2026-11-11 A06-02·A06-07 보물 지도·구조물 꺼짐 /locate

- 전리품 `exploration_map`(난파선 지도 상자·바다 폐허 상자)은 `ServerCore.lootWorldFor` 의 `LootWorld` 가 `findNearestMapStructure`·`createExplorationMap` 을
  그 월드의 탐험 지도 백엔드(`explorerMapBackend(World)`)로 잇는다. 바닐라 `ExplorationMapFunction.run`(esx): 찾으면 `MapItem.create(x, z, zoom, true, true)`
  → `renderBiomePreviewMap` → `addTargetDecoration("+", 장식)` 의 새 지도 한 장, 이름은 뒤의 `set_name` 이 붙인다. 지도 그리기는 거래와 같은 `ExplorerMaps.createAt`.
- 탐험 지도 백엔드는 이제 월드마다 하나(그 월드 생성기·차원·확대 지터 시드). 오버월드가 아닌 차원의 `LevelServices` 는 여전히 백엔드를 주지 않는다(거래·엔더의 눈은 예전대로).
- `skip_existing_chunks`(skipKnownStructures)는 아직 무시한다 — A06-06(시작점 references 저장)이 먼저다.
- `StructureEngine.startOf` 는 구조물 생성이 꺼졌으면 null(바닐라: STRUCTURE_STARTS 단계가 시작점을 만들지 않아 `/locate`·탐험 지도·엔더의 눈이 못 찾는다).
  단서: 바닐라는 예전에 구조물이 켜진 채 저장된 청크의 시작점은 디스크에서 읽어 찾는다. Furnace 찾기는 예측만 하므로 그 경우는 못 찾는다.
