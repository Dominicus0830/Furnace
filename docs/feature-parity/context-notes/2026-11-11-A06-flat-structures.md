# 2026-11-11 A06-04(뒤쪽) 평지 구조물·고정 바이옴 요새 링·지그소 경계

- 평지 구조물: FlatLevelSource.createState(dyt) 는 settings 의 structure_overrides 세트만(없으면 레지스트리 전부) 쓴다 →
  `StructureEngine.setAllowedSets`. 바이옴 검사(hasBiomesForStructureSet)는 그대로.
- 평지 높이(getBaseHeight·getBaseColumn, Beardifier 없음)는 앞쪽 PR(#216)에서 넣었다. 구조물 시작점·지그소가 그 높이를 쓴다.
- server.properties generator-settings: 바닐라 DedicatedServerProperties(apo) 는 FlatLevelGeneratorSettings 코덱으로 읽고 실패하면
  (필수 layers 없음, 높이 합 > 4064) 기본 프리셋 전체를 쓴다. 읽히면 structure_overrides 가 없을 때 Optional.empty = 모든 세트.
  예전 Furnace 는 layers 없이 준 biome·features 를 썼고, overrides 가 없으면 요새·마을을 넣었다.
- FixedBiomeSource.findBiomeHorizontal(deh): 바이옴이 맞으면 (x - r + nextInt(2r+1), z - r + nextInt(2r+1)) 블록(x 먼저). 요새 링 보정이
  이것을 쓰므로 평지와 single_biome_surface 월드의 요새 위치가 multi_noise 식과 다르다. `TerrainAccess.fixedBiome()` 으로 가른다.
  (예전에는 고정 바이옴 월드에도 multi_noise 식을 써서 요새 링이 바닐라와 달랐다.)
- 지그소 경계(JigsawPlacement.addPieces, ekv)와 폐허 차원문 findSuitableY(emg)는 생성기 minY 가 아니라 차원 높이
  (LevelHeightAccessor getMinBuildHeight/getMaxBuildHeight)를 쓴다 → `TerrainAccess.levelMinY/levelMaxY`. 평지는 생성기 minY 가 0 이라
  예전 식이면 지표(-60) 마을 조각이 경계 밖이었다. 네더의 지그소 위쪽 경계도 노이즈 높이 128 이 아니라 차원 256 이 된다(A06-09).
- 평지 요새는 moveBelowSeaLevel(getSeaLevel -63, getMinY 0) 이라 바닐라에서도 땅 위로 드러난다(코드 그대로).
- structure_overrides 의 태그(#…)는 아직 풀지 않는다(바닐라 구조물 세트 태그는 없다).
