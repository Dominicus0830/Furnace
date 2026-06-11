# M4g-c1 Surface Rules (바닐라 시드 비트일치) — Design Spec

> 부모: `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` §M4g.
> M4g-c 분할: **c1 표층 rule** → c2 aquifer → c3 ore veins → c4 carver.

## 목표

바닐라 1.21.1 동일 시드 → **표층 블록 비트일치**. stone/water/air 위에 grass/dirt/sand/gravel/bedrock 등 표층 블록을 바닐라와 동일하게 배치. `noise_settings/overworld.json`의 `surface_rule` 트리를 JSON에서 파싱해 데이터 주도 평가.

## 범위

**In (M4g-c1 산출물).**
- `surface_rule` JSON 전체 파싱 및 평가 (overworld 한정).
- `initialDensityWithoutJaggedness` DF → `NoiseChunk.getPreliminarySurfaceLevel()` 구현.
- `SurfaceSystem.buildSurface()` — noise fill 후 post-processing 패스.
- oracle 확장: `surface:<seed> cx:cz` → 전체 블록 state 덤프 (aquifer/ore veins OFF).
- Parity test: 3 fixture 전수 비트 비교.

**Out (후속).**
- aquifer (local underground fluid) = M4g-c2.
- ore veins = M4g-c3.
- carver (동굴/협곡) = M4g-c4.

## 아키텍처

### 데이터 흐름
```
NoiseWorldSource.generate(cx, cz)
  1. NoiseChunk(cx, cz, router, settings)   ← 기존
  2. stoneMask()                             ← 기존
  3. block fill: stone/water/lava/air        ← 기존
  4. biome fill                              ← 기존
  5. [신규] surfaceSystem.buildSurface(col, biomeIds, nc)
```

### 파일 구조
```
world/gen/
  surface/
    SurfaceRule.java        # sealed interface + Rule/Condition 구현체
    SurfaceRuleParser.java  # JSON → SurfaceRule 트리
    SurfaceContext.java     # 블록 평가 컨텍스트
    SurfaceSystem.java      # buildSurface() 오케스트레이터
  NoiseRouter.java          # initialDensityWithoutJaggedness 필드 추가
  NoiseChunk.java           # getPreliminarySurfaceLevel(quartX, quartZ) 추가
  NoiseWorldSource.java     # generate() 끝에 buildSurface() 호출
```

## 컴포넌트 상세

### SurfaceRule (sealed interface)

**Rule 타입:**
```java
public sealed interface SurfaceRule {
    record Sequence(List<SurfaceRule> rules) implements SurfaceRule {}
    record IfTrue(SurfaceCondition condition, SurfaceRule then) implements SurfaceRule {}
    record Block(int blockStateId) implements SurfaceRule {}
}
```

**Condition 타입:**
```java
public sealed interface SurfaceCondition {
    record VerticalGradient(String randomName, YOffset trueBelow, YOffset falseAbove)
        implements SurfaceCondition {}
    record AbovePreliminarySurface() implements SurfaceCondition {}
    record StoneDepth(int offset, boolean addSurfaceDepth,
                      int secondaryDepthRange, SurfaceType surfaceType)
        implements SurfaceCondition {} // surfaceType: FLOOR | CEILING
    record BiomeIs(Set<String> biomes) implements SurfaceCondition {}
    record YAbove(YOffset anchor, int surfaceDepthMultiplier, boolean addStoneDepth)
        implements SurfaceCondition {}
    record Water(int offset, int surfaceDepthMultiplier, boolean addStoneDepth)
        implements SurfaceCondition {}
    record NoiseThreshold(String noise, double minInclusive, double maxExclusive)
        implements SurfaceCondition {}
    record Temperature() implements SurfaceCondition {} // isColdAt(biome)
    record Steep() implements SurfaceCondition {}
    record Not(SurfaceCondition inner) implements SurfaceCondition {}
    record Hole() implements SurfaceCondition {}
}

// 앵커 타입
public sealed interface YOffset {
    record Absolute(int y) implements YOffset {}
    record AboveBottom(int offset) implements YOffset {}
    record BelowTop(int offset) implements YOffset {}
}
```

### SurfaceContext
```java
public final class SurfaceContext {
    // 현재 위치
    int blockX, blockY, blockZ;
    // 바이옴
    int biomeId;
    String biomeName;
    // 표면 깊이
    int stoneDepthAbove;   // 현재 y 위 solid 블록 수 (top-down 루프 실시간)
    int stoneDepthBelow;   // 현재 y 아래 solid 블록 수 (pre-scan)
    int surfaceY;          // 컬럼 최상단 solid y
    int waterY;            // 컬럼 최상단 water y (-1 = 없음)
    // noise/random 접근
    NoiseChunk noiseChunk;
    Map<String, NormalNoise> noises;     // noise_threshold용
    PositionalRandomFactory randomFactory; // vertical_gradient용
    // 설정
    int minY;
    int seaLevel;
}
```

### Preliminary Surface Level
바닐라 `NoiseChunk.getPreliminarySurfaceLevel(quartX, quartZ)` 복제:
- `NoiseRouter.initialDensityWithoutJaggedness` DF를 quart 열(quartX, quartZ)에서 위→아래 샘플
- 첫 번째로 `> 0`인 y(블록 단위) 반환; 없으면 `minY - 1`
- Cache2D 래핑으로 동일 quartXZ 재계산 방지

`NoiseRouter` 확장:
```java
// 기존 필드 외 추가
public final DensityFunction initialDensityWithoutJaggedness;
```
`overworld.json` `noise_router.initial_density_without_jaggedness` 파싱 (JSON에 이미 존재).

### SurfaceSystem
```java
public final class SurfaceSystem {
    private final SurfaceRule rule;
    private final PositionalRandomFactory randomFactory;
    private final Map<String, NormalNoise> noises;
    private final int minY, seaLevel;

    public void buildSurface(ChunkColumn col, int[] biomeIds,
                             NoiseChunk nc, BlockRegistry reg) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                // 1. 컬럼 pre-scan: surfaceY, waterY, stoneDepthBelow[]
                // 2. top-down y 루프 (minY+height-1 → minY)
                //    a. SurfaceContext 갱신 (stoneDepthAbove 실시간)
                //    b. Integer result = eval(rule, ctx)
                //    c. result != null → col.setBlock(lx, y, lz, result)
            }
        }
    }
}
```

`eval(SurfaceRule, SurfaceContext)` = 패턴 매치 + condition 평가. `Sequence`는 첫 non-null result에서 중단.

### NoiseWorldSource 수정
```java
// overworld() 팩토리
SurfaceSystem surfaceSystem = SurfaceSystem.create(data, settings, seed, reg);

// generate()
surfaceSystem.buildSurface(col, biomeIds, nc, reg);
```

## Oracle 확장

서브커맨드: `surface <seed> <cx> <cz>`

**Fixture 포맷** `surface_<seed>_<cx>_<cz>.bin`:
```
Header: 20B
  0x53524643 (magic "SRFC", 4B)
  seed (long, 8B)
  cx   (int, 4B)
  cz   (int, 4B)
Body: 196608B
  short[16][384][16] — block state id
  순서: lx 0..15 → lz 0..15 → y (-64..319, 즉 index 0..383)
```

oracle 실행 옵션: `aquifersEnabled=false`, `oreVeinsEnabled=false`, surface rule ON.
바닐라 `NoiseBasedChunkGenerator` 사용, 동일 시드.

명령: `./gradlew :worldgen-oracle:run --args="<seed> surface:<cx>:<cz>"`

## Parity Test

`SurfaceParityTest.java` — 3 fixture:

| fixture | seed | cx | cz |
|---|---|---|---|
| 1 | 0 | 0 | 0 |
| 2 | 0 | 5 | 5 |
| 3 | 123456789 | 0 | 0 |

비교 방식:
- oracle short[] vs Furnace generate() 후 col.getBlock(lx, y, lz)
- mismatch 시 첫 5개: `[cx,cz] lx=X y=Y lz=Z expected=<name> actual=<name>`
- 전수 0 mismatch 통과 기준

## 핵심 함정

- `VerticalGradient` random: `PositionalRandomFactory` = `XoroshiroRandomSource.forkPositional()` 기반. random_name으로 시드 파생. 바닐라 `SurfaceRules.VerticalGradientConditionSource` 복제 필수.
- `StoneDepth` CEILING vs FLOOR: ceiling = 현재 y 위의 solid 블록까지 거리, floor = 아래. 방향 혼동 주의.
- `above_preliminary_surface` 앵커: `initialDensityWithoutJaggedness` 결과. `final_density`와 다름 — jaggedness noise 미포함이라 더 smooth.
- `temperature` 조건: 바이옴 temperature float < 0.15 = cold. `worldgen/1.21.1/worldgen/biome/*.json` 전체 로드 → `name→baseTemperature` 맵 구성. `WorldgenData.biomeTemperature(name)` 추가.
- `steep` 조건: 인접 4방향 `surfaceY` 차이 > 4. 청크 경계(lx=0/15, lz=0/15)는 인접 청크 미참조 — 청크 내부 컬럼만 비교 (바닐라 동일).
- oracle 시드 단위: `NoiseBasedChunkGenerator` 생성자에 long seed 직접 전달. Paper/Bukkit World 경유 X.

## 실행 순서

1. `NoiseRouter` — `initialDensityWithoutJaggedness` 파싱
2. `NoiseChunk` — `getPreliminarySurfaceLevel()` + Cache2D 래핑
3. `SurfaceRule` / `SurfaceRuleParser` / `SurfaceContext`
4. `SurfaceSystem.buildSurface()`
5. `NoiseWorldSource` 수정
6. oracle `surface` 서브커맨드 + 3 fixture 커밋
7. `SurfaceParityTest` 작성 + 0 mismatch 확인
8. `ChunkGenBenchmark` 실행 (buildSurface 단계 추가)
