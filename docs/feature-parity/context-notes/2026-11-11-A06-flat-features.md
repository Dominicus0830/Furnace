# 2026-11-11 A06-04(앞쪽) 평지 월드 지형지물·호수·빈 공간 발판

- 서버의 `level-type=flat`(level.dat 생성기 `minecraft:flat`)은 이제 `NoiseWorldSource.flat(...)` — 바닐라 FlatLevelSource 처럼 같은 생성 경로
  (ChunkPipeline 장식·유체 틱·구조물 엔진)를 쓰고 노이즈 대신 층을 쌓는다. 카버·표면 규칙·대수층은 없다. 단순 층 쌓기 `SuperflatWorldSource` 는
  시험 월드·플러그인 추가 월드(FLAT·VOID, 높이가 다른 차원 타입)용으로 남겼다.
- 바닐라 `FlatLevelGeneratorSettings.adjustGenerationSettings`(eht.a(jm)) 그대로 평지 바이옴의 단계별 목록을 만든다:
  lakes 면 LAKES 단계에 `lake_lava_underground`·`lake_lava_surface`, `(!voidGen || 바이옴이 the_void) && features` 면 바이옴 지형지물
  (UNDERGROUND_STRUCTURES·SURFACE_STRUCTURES 단계, lakes 일 때 LAKES 단계는 빼고), 끝으로 MOTION_BLOCKING 이 아닌 층마다
  TOP_LAYER_MODIFICATION 에 인라인 `fill_layer`(그 층은 노이즈 채우기에서 지운다). voidGen = 모든 층이 Blocks.AIR(층이 없어도 참).
- 지형지물 순서(FeatureSorter)는 가능한 바이옴 = 평지 바이옴 하나, 그 바꾼 목록으로 정한다(바닐라 ChunkGenerator 의 generationSettingsGetter).
- 생성 문맥: FlatLevelSource.getMinY 0, getGenDepth 384, getSeaLevel -63 → minGenY = max(-64, 0) = 0(above_bottom 기준이 0).
- 가정: 바닐라는 getLayers 목록을 adjustGenerationSettings 가 그 자리에서 null 로 바꾸고, ChunkGenerator.validate 가 생성 전에 그것을 부른다고 본다.
- 인라인 지형지물(fill_layer)은 레지스트리 id 가 없어 월드젠 훅 `feature(id, …)` 에 묻지 않는다.
- 아직: 평지 구조물(structure_overrides — 기본 요새·마을)은 엔진에서 꺼 두었다(`StructureEngine.setAllowedSets(빈 집합)`). 다음 PR 에서
  getBaseHeight·찾기·탐험 지도와 함께 켠다. `SuperflatWorldSource.settingsNbt` 가 generator-settings 에 structure_overrides 가 없을 때
  기본(요새·마을)을 넣는 것은 바닐라(없으면 레지스트리 전부)와 다르다 — 그때 함께 고친다.
