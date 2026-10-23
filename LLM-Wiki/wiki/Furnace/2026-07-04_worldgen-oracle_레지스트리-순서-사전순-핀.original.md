# 2026-07-04 worldgen-oracle — 레지스트리 순서 사전순 핀 고정

## 작업 요약
oracle fixture 재생성의 JVM-간 비결정성(STRUCTURE registry 순서 요동) 해소 — ResourceLocation 사전순 핀, i1=22→1 동기화. 커밋 4b8114e.

## 문제 / 목적
`VanillaRegistries.createLookup().listElements()` 순서가 JVM 실행마다 요동 → STRUCTURE registry stream 기반 i1 순번(setFeatureSeed 인덱스) 비결정 → fixture 재생성 신뢰 불가. m4g-e에서 발견돼 이연됐던 항목.

## 해결 방법 / 구현 내용
- 실측 특정: 새 `structorder` 진단 덤프 모드로 vanilla `ChunkGenerator.applyBiomeDecoration`의 `registry.stream().collect(groupingBy(step ordinal))` 재현, 5회 fresh-JVM 실행 → 4/26/12/68줄 diff. 반면 `feature_order.txt`(PLACED_FEATURE·possibleBiomes 경로)는 원래 결정적(5/5 clean) — "createLookup 전체가 랜덤"은 과잉 일반화였음.
- 핀: `sortedElements()` 헬퍼 — `listElements()`를 `ref.key().location().toString()` 사전순 정렬. `copyRegistry`/biome/carver registry 빌드 3곳 적용. 핀 후 5회 재실행 byte-diff 0.
- 사전순 STEP 4 그룹 내 desert_pyramid 순번 = 1 (bastion_remnant=0). 공유 컨벤션 상수 `FeaturePlacer.DESERT_PYRAMID_STRUCTURE_INDEX` 22→1 + oracle 미러의 `effectiveI1` 하드코딩도 동기화.
- fixture 3종 재생성 → byte-identical (i1은 chest loot RNG에만 영향, 블록 상태 무영향). full suite green 330 tests.

## 핵심 코드
```java
private static <T> java.util.List<Holder.Reference<T>> sortedElements(HolderLookup.RegistryLookup<T> lookup) {
    java.util.List<Holder.Reference<T>> refs = new java.util.ArrayList<>();
    lookup.listElements().forEach(refs::add);
    refs.sort(java.util.Comparator.comparing(ref -> ref.key().location().toString()));
    return refs;
}
```

## 한계 (기록)
- 사전순 핀은 재실행 결정성만 보장. 실서버(RegistryDataLoader, 등록 순서 고정) 순서와 일치 미보장 — 최종 판정은 .mca 자가검증 게이트(이연).
- oracle `structuresByStep()`(applyBiomeDecoration 미러 캐시)은 정렬 미적용으로 남음 — desert_pyramid i1은 별도 오버라이드라 무영향, start 없는 구조물 reseed 순서만 흔들림(블록 무영향, S2c 판정 논리).

## 관련 파일
[[2026-07-04_worldgen_wgOceanFloor-라이브갱신-심층발산-해소]] [[2026-07-03_worldgen-structure_S3-references-regionRandom-oracle훅우회]]

## 태그
#Furnace #worldgen-oracle #BugFix
