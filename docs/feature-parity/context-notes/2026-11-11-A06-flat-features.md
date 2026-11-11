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
- 바닐라는 getLayers 목록을 adjustGenerationSettings 가 그 자리에서 null 로 바꾼다. 1.21.1 jar 에는 ChunkGenerator.validate(duz.a())를 부르는 곳이 없고,
  adjust 는 첫 장식 때 memoize 로 한 번 불린다(검토 확인). Furnace 는 처음부터 지운 층을 쓴다 — 바닐라가 adjust 한 뒤의 정상 상태와 같다.
  서버 기동 직후 첫 장식 전에 노이즈를 채운 청크는 바닐라에서 그 층이 노이즈로 들어가지만, fill_layer 가 같은 블록을 놓으므로 블록 결과는 같다(가정: 그 사이 높이 질의 차이는 무시).
- 인라인 지형지물(fill_layer)은 레지스트리 id 가 없어 월드젠 훅 `feature(id, …)` 에 묻지 않는다.
- 아직: 평지 구조물(structure_overrides — 기본 요새·마을)은 엔진에서 꺼 두었다(`StructureEngine.setAllowedSets(빈 집합)`). 다음 PR 에서
  getBaseHeight·찾기·탐험 지도와 함께 켠다. `SuperflatWorldSource.settingsNbt` 가 generator-settings 에 structure_overrides 가 없을 때
  기본(요새·마을)을 넣는 것은 바닐라(없으면 레지스트리 전부)와 다르다 — 그때 함께 고친다.
- 평지 생성기는 청크를 만들 때 동물을 놓지 않는다(FlatLevelSource.spawnOriginalMobs 가 빈 메서드). `NoiseWorldSource` 라는 이유로
  `freshlyGenerated` 를 켜던 두 곳(AnvilWorldSource 생성, World 재생성)에 `!isFlat()` 를 더했다.
- `/place structure`·`/place jigsaw`·직소 블록 높이는 FlatLevelSource.getBaseHeight·getBaseColumn 대로 지운 층을 건너뛴 층 목록에서 낸다.
  바닐라 getBaseHeight 의 층 번호 상한은 높이(384)가 아니라 getMaxBuildHeight(320)다 — 그대로 따른다.
- voidGen·fill_layer 는 월드 높이로 자르기 전 층 전체(높이 합 최대 4064)로 정하고, 노이즈 채우기만 min(높이, 층 수)에서 멈춘다(바닐라와 같음).
- 가정(미검증): 장식 결과를 바닐라 실서버·oracle 덤프와 블록 단위로 대조하지 않았다. 시험은 단계 목록의 구조와 질적 결과(식물이 자람,
  같은 시드면 같음)뿐이다. worldgen-oracle 평지 덤프나 실서버 .mca 대조가 남은 일이다.
- 알려진 차이: 평지 바이옴이 플러그인·데이터 팩 바이옴이라 월드젠 JSON 이 없으면 장식기를 만들지 못해 호수·fill_layer 가 빠진다
  (눈 층 같은 비-MOTION_BLOCKING 층이 사라진다). 레지스트리에 없는 바이옴은 FurnaceMain 이 평원으로 바꾸므로 기본 경로에서는 생기지 않는다.
