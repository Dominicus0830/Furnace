# 2026-07-02 worldgen-oracle — 태그 미바인딩·center-only 쓰기로 fixture 오염

## 작업 요약
oracle 환경 버그 2개(FLUID/블록 태그 미바인딩, center-only setBlock)로 featurefull fixture가 바닐라와 달랐음. 수정 후 fixture 6개 재생성.

## 문제 / 목적
seagrass_deep parity에서 oracle이 tall_seagrass를 1개도 안 놓음(RNG는 Furnace와 완전 일치). fixture 자체가 바닐라 불일치.

## 해결 방법 / 구현 내용
1. **태그 미바인딩**: `VanillaRegistries.createLookup()`(datagen 모드)는 태그 자동 바인딩 없음. BLOCK 일부만 hardcode돼 있었고 FLUID는 전무 → `FluidTags.WATER` 빈 태그 → `TallSeagrassBlock.canSurvive`(LOWER: `fluidState.is(FluidTags.WATER) && amount==8`) 항상 false. short는 fluid 검사 없어 정상 → "short만 생존" 증상. `leaves`·`replaceable_by_trees`도 미바인딩 → oracle 트리도 오염(기존 tree 디버깅 라운드 3~10 결론 재평가 필요). server-1.21.1.jar의 `data/minecraft/tags/*.json` 내용 그대로 바인딩(추측 금지).
2. **center-only 쓰기**: `OracleWorldGenLevel.setBlock/ensureCanWrite`가 center 청크만 허용. vanilla WorldGenRegion FEATURES writeRadius=1(3×3). 이웃 걸친 tall_seagrass 드롭 → 다음 scatter 재방문 시 water 판정 분기 → RNG 연쇄 desync. 3×3 grid 쓰기로 수정.
3. fixture 재생성: featurefull 6개(0,0 / -5,3 / 7,-2 / 9,-64 / -49,-30 / -53,-26).
4. 디버그 도구: `-Doracle.dumpColumns="x,z[,yFrom,yTo]"` 컬럼 상태·heightmap 덤프 추가.

## 핵심 코드
```java
// OracleWorldGenLevel.setBlock — vanilla writeRadius=1 재현
int dx = chunkX(pos) - cx;
int dz = chunkZ(pos) - cz;
if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return false;
return grid[(dz + 1) * 3 + (dx + 1)].setBlockState(pos, state, false) != null;
```
```java
// 플루이드 태그 바인딩 (data/minecraft/tags/fluid/*.json 그대로)
addFluidTag.accept("minecraft:water", new String[]{"minecraft:water","minecraft:flowing_water"});
addFluidTag.accept("minecraft:lava", new String[]{"minecraft:lava","minecraft:flowing_lava"});
((MappedRegistry<Fluid>)(Object) BuiltInRegistries.FLUID).bindTags(fluidTagMap);
```

## 관련 파일
[[2026-07-02_worldgen-feature_featurefull-0mismatch]], [[레이어-월드젠]]

## 태그
#Furnace #worldgen-oracle #BugFix
