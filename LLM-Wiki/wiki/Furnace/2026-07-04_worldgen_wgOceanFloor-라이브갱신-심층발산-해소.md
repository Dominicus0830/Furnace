# 2026-07-04 worldgen — wgOceanFloor 라이브 갱신으로 심층 y27~31 발산 180 해소

## 작업 요약
ChunkColumn OCEAN_FLOOR_WG heightmap이 noise-fill 스냅샷 고정 버그 수정. desertPyramidNeighborChunk 180 mismatch → 0. @Disabled 해제. 커밋 9478014.

## 문제 / 목적
StructureFeatureParityTest (b) 청크(-780,-298) y27~31 대역 180셀 oracle 대비 발산(유령 dirt/gravel vein). 초기 추정(carver/aquifer/노이즈 상류) 오진 — pre/post-carve 대조로 배제. BLOCK_DEBUG 트레이스로 step=6 ore_dirt/ore_gravel vein 오발생 확정.

## 해결 방법 / 구현 내용
- vanilla `ProtoChunk.setBlockState`는 worldgen 중 매 블록 쓰기마다 `Heightmap.update()`로 OCEAN_FLOOR_WG 라이브 갱신. Furnace `ChunkColumn.wgOceanFloor`는 `loadGenerated()` 시점 스냅샷 고정 — `setBlock()` 갱신 안 함.
- 발산 메커니즘: 구조물 조각 step 4(SURFACE_STRUCTURES) 블록 쓰기 → vanilla heightmap 즉시 반영, Furnace 고정 → step 6(UNDERGROUND_ORES) `OreFeature.place()` OCEAN_FLOOR_WG 게이트 판정 marginal 지점서 뒤집힘 → 게이트 실패 시 `doPlace()` RNG 소비 통째 스킵 → 이후 모든 vein 위치 발산.
- 수정: `setBlock()`에 `updateWgOceanFloor()` 1줄 + vanilla `Heightmap.update()`(Heightmap.java:76-103) 1:1 미러 메서드 추가. opaque 판정 기존 `isOceanFloorBlock` 재사용. 미프라임(UNSET_HEIGHT) 시 no-op.
- 음수/대좌표 가설 불발 — 좌표 산술 전부 `Math.floorDiv` 정상.

## 핵심 코드
```java
// ChunkColumn.setBlock 내
updateWgOceanFloor(x, worldY, z, stateId);

// vanilla Heightmap.update 미러
private void updateWgOceanFloor(int x, int worldY, int z, int stateId) {
    int index = z * 16 + x;
    int first = wgOceanFloor[index]; // firstAvailable - MIN_Y
    if (first == UNSET_HEIGHT) return;
    int y = worldY - MIN_Y;
    if (y <= first - 2) return;
    if (isOceanFloorBlock(stateId)) {
        if (y >= first) wgOceanFloor[index] = y + 1;
    } else if (first - 1 == y) {
        for (int i = y - 1; i >= 0; i--) {
            if (isOceanFloorBlock(getBlock(x, MIN_Y + i, z))) {
                wgOceanFloor[index] = i + 1;
                return;
            }
        }
        wgOceanFloor[index] = 0;
    }
}
```

## 잠재 이슈 (기록)
- `surface`(WORLD_SURFACE 계열) heightmap 동일 스냅샷 고정 — vanilla는 WORLD_SURFACE_WG도 라이브 갱신. 트리 등 heightmap 기반 placement 같은 부류 잠재 발산 가능. 별도 이슈.
- `isOceanFloorBlock` leaves 제외, vanilla 술어(blocksMotion)는 leaves 포함 — 기존 근사, 비접촉.

## 관련 파일
[[2026-07-04_worldgen_stairs-facing-폴백-버그]] [[2026-07-04_worldgen-oracle_레지스트리-순서-사전순-핀]] [[2026-07-03_worldgen-structure_S3-references-regionRandom-oracle훅우회]]

## 태그
#Furnace #worldgen #BugFix