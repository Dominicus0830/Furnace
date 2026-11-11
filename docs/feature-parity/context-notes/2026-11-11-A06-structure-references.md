# 2026-11-11 A06-06 탐험 지도 references·skipKnownStructures

- 바닐라 ChunkGenerator.findNearestMapStructure(duz) 의 skipKnownStructures:
  - 후보마다 StructureCheck(ejs).checkStart: 저장된 시작점의 references 가 -1(시작점 없음)이거나 `skip && refs != 0` 이면 START_NOT_PRESENT.
  - 아니면 시작 청크를 STRUCTURE_STARTS 까지 불러 `start.isValid() && (!skip || tryAddReference)`. tryAddReference 는 canBeReferenced
    (references < getMaxReferences()=1)일 때 StructureManager.addReference 로 +1(시작점 객체와 StructureCheck 캐시 둘 다).
  - 그래서 skip 이면 references 가 0 인 시작점만 고르고 고른 것을 1 로 만든다. 동심원 배치는 "더 가까운 후보"를 찾을 때마다 올리고
    (가장 가까운 것이 아니어도 올라간다), 무작위 분산 배치는 배치마다 테두리에서 처음 찾은 것에 올린다.
- 누가 skip 을 켜나: 제도사 거래 TreasureMapForEmeralds = true, 전리품 exploration_map 의 skip_existing_chunks(기본 true),
  /locate·엔더의 눈·돌고래 = false.
- Furnace 구현:
  - `StructureReferences`(차원마다, StructureEngine.references()): (구조물 id, 시작 청크) → references. 시작 청크를 만들지 않고 찾으므로 값은 여기 모은다.
  - 아직 모르는 시작점은 디스크 청크 NBT `structures.starts.<id>.references` 를 읽는다(AnvilWorldSource.storedReferences — 바닐라 tryLoadFromStorage 몫).
    바닐라로 만든 월드를 가져와도 이미 지도가 가리킨 구조물을 건너뛴다.
  - 청크를 불러오면(ChunkStructures.attach) 그 NBT 값을 들이고, 저장할 때 시작점 NBT 의 references 를 저장소 값으로 쓴다(생성 경로·원본 보존 경로 모두).
  - 아직 만들어지지 않은 시작 청크의 값은 `<차원>/data/furnace_structure_references.dat`(gzip NBT, 1 이상만)에 남긴다. World.flushAll 이 저장한다.
- 바닐라와 다른 점(가정·관측 차이 거의 없음):
  - 바닐라는 지도를 만들 때 시작 청크가 STRUCTURE_STARTS proto 청크로 생겨 저장된다. Furnace 는 만들지 않고 data 파일에 둔다.
    그래서 Furnace 월드를 바닐라로 옮기면, 아직 만들어지지 않은 시작 청크와 "불러온 뒤 다시 저장되지 않은" 시작 청크의 references 는 옮겨지지 않는다
    (옮긴 뒤 같은 구조물을 지도가 한 번 더 가리킬 수 있다). Furnace 안에서는 같다.
  - 값을 올려도 이미 불러온 시작 청크를 dirty 로 표시하지 않는다. data 파일에 남으므로 Furnace 안에서는 잃지 않는다.
- 시험: StructureReferencesTest — 감사 재현(같은 자리 해저 신전 지도 두 번 → 두 번째는 다른 곳, /locate 는 그대로), 한 번만 올림, data 파일, 청크 NBT 왕복, 디스크 값 읽기.
