# 2026-11-11 A06-04(뒤쪽) 평지 구조물·고정 바이옴 요새 링·지그소 경계

- 평지 구조물: FlatLevelSource.createState(dyt) 는 settings 의 structure_overrides 세트만(없으면 레지스트리 전부) 쓴다 →
  `StructureEngine.setAllowedSets`. 바이옴 검사(hasBiomesForStructureSet)는 그대로.
- 평지 높이(getBaseHeight·getBaseColumn, Beardifier 없음)는 앞쪽 PR(#216)에서 넣었다. 구조물 시작점·지그소가 그 높이를 쓴다.
- server.properties generator-settings: 바닐라 DedicatedServerProperties(apo) 는 FlatLevelGeneratorSettings 코덱으로 읽고, 부분 결과도
  없으면(필수 layers 가 없거나 배열이 아님) 기본 프리셋 전체를 쓴다. 읽히면 structure_overrides 가 없을 때 Optional.empty = 모든 세트.
  부분 결과 규칙은 아래 검토 반영 절.
  예전 Furnace 는 layers 없이 준 biome·features 를 썼고, overrides 가 없으면 요새·마을을 넣었다.
- FixedBiomeSource.findBiomeHorizontal(deh): 바이옴이 맞으면 (x - r + nextInt(2r+1), z - r + nextInt(2r+1)) 블록(x 먼저). 요새 링 보정이
  이것을 쓰므로 평지와 single_biome_surface 월드의 요새 위치가 multi_noise 식과 다르다. `TerrainAccess.fixedBiome()` 으로 가른다.
  (예전에는 고정 바이옴 월드에도 multi_noise 식을 써서 요새 링이 바닐라와 달랐다.)
- 지그소 경계(JigsawPlacement.addPieces, ekv)와 폐허 차원문 findSuitableY(emg)는 생성기 minY 가 아니라 차원 높이
  (LevelHeightAccessor getMinBuildHeight/getMaxBuildHeight)를 쓴다 → `TerrainAccess.levelMinY/levelMaxY`. 평지는 생성기 minY 가 0 이라
  예전 식이면 지표(-60) 마을 조각이 경계 밖이었다. 네더의 지그소 위쪽 경계도 노이즈 높이 128 이 아니라 차원 256 이 된다(A06-09).
- 평지 요새는 moveBelowSeaLevel(getSeaLevel -63, getMinY 0) 이라 바닐라에서도 땅 위로 드러난다(코드 그대로).
- structure_overrides 의 태그(#…)는 아직 풀지 않는다(바닐라 구조물 세트 태그는 없다).
- 평지 요새 링: 바닐라 FlatLevelSource.createState 는 ChunkGeneratorStructureState.createForFlat(dva.a(dzm,long,dea,Stream))을 부르고,
  이것은 concentricRingsSeed 자리에 0 을 넣는다. 그래서 평지 요새 위치는 월드 시드와 무관하다 → `StructureEngine.setConcentricRingsSeed(0)`.
  single_biome_surface 는 createForNormal 이라 월드 시드 그대로다(검토 #225 R1).
- generator-settings 는 `CODEC.parse(...).resultOrPartial` 이다(검토 #225 R2, DFU 실측):
  - 높이 합 > 4064 는 오류지만 부분 결과(입력 그대로)를 쓴다.
  - 층 하나가 0..4064 밖이거나 height 가 없으면 그 층만 빠진다. block 이 없으면 공기.
  - features·lakes 는 JSON 불리언만 참이다("true" 문자열은 거짓).
  - structure_overrides 는 lenient 라서 모르는 세트 id 나 태그가 하나라도 있으면 필드가 없는 것(= 모든 세트)이 된다.
