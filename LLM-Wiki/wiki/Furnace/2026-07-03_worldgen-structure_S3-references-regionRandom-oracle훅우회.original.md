# 2026-07-03 M4g-e S3 — structure references·regionRandom·oracle Paper 훅 우회

## 작업 요약
사막 피라미드 구조물 byte-exact 완성(start 청크 0 mismatch, 커밋 730999a). 17×17 references + regionRandom + oracle Paper 훅 NoBukkit 우회 + fixture (a) 실측 start로 교체.

## 문제 / 목적
- S3 = 17×17 STRUCTURE_STARTS ring에서 이웃 청크 references 수집해 경계 걸침 조각 배치.
- fixture (a)=(9,14)가 0 mismatch로 통과했으나 실은 oracle chest 배치 NPE로 유효 start가 생성된 적 없는 순환 검증(양쪽 다 "구조물 없음" 우연 일치).
- S2b의 "level.getRandom() 비결정적 → cellar sand·collapsed roof 스킵" 판단이 오류였음.

## 해결 방법 / 구현 내용
- **regionRandom**: vanilla `WorldGenRegion.random` = `randomState.getOrCreateRandomFactory("worldgen_region_random").at(청크 center 원점)` — positional fork로 결정적. Furnace에 XoroshiroRandomSource 미러 도입, cellar 플랫폼·collapsed roof 구현.
- **oracle Paper 훅 우회**: featurestruct 모드에서 AsyncStructureSpawnEvent·CraftStructureTransformer·CraftChest(placed block state 요구)가 `getMinecraftWorld()` null NPE → OracleWorldGenLevel에 NoBukkit 미러(vanilla 라인만 미러, 블록 출력 무영향 검증).
- **fixture (a) 교체**: structplacement 전역 스캔(txt)에서 desert_pyramids 실측 hit 2개(-781,-299 / -763,-280) 확인 → (-763,-280) 채택, 0 mismatch.
- **mirrorLeftRight 버그**: vanilla `Mirror.LEFT_RIGHT.mirror(Direction)`는 NORTH/SOUTH만 반전(EAST/WEST 불변)인데 반대로 구현돼 있었음. 디컴파일 Mirror.java 대조로 수정. 현 fixture(NORTH orientation)로는 직접 검증 불가 — SOUTH/WEST 피스에서 드러날 것.
- **(b) 이웃 청크 테스트 @Disabled 이연**: 잔여 184 = stairs shape auto-connection 미구현(4: BlockRegistry 키가 facing만이라 full property 키 매치 실패 → default straight 폴백) + 심층 y27~31 gravel/stone 발산(180: 구조물 파이프라인 밖, carver/aquifer/노이즈 상류).

## 핵심 코드
```java
/** 바닐라 Mirror.LEFT_RIGHT.mirror(Direction) — Z축(NORTH/SOUTH)만 반전, X축(EAST/WEST)은 불변. */
private static Direction mirrorLeftRight(Direction d) {
    return switch (d) {
        case NORTH -> Direction.SOUTH;
        case SOUTH -> Direction.NORTH;
        default -> d;
    };
}
```

## 관련 파일
[[2026-07-02_worldgen-feature_다중청크-파이프라인-0mismatch]]

## 태그
#Furnace #worldgen-structure #Feature #BugFix
