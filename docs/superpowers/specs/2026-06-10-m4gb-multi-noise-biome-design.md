# M4g-b Multi-Noise 바이옴 (바닐라 시드 비트일치) — Design Spec

> 부모: `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` §M4g.
> M4g 5단계(a 노이즈지형 → **b 바이옴** → c 표층+carver+aquifer+광맥 → d features → e 구조물) 중 두 번째.

## 목표

같은 바닐라 1.21.1 시드 → **바닐라와 비트 단위로 동일한 바이옴 배치** 생성. vanilla-like 근사 아님. M4g-b는 M4g-a 지형 위에 올라가는 두 번째 레이어: 6개 기후 density function 평가 → vanilla Climate 거리 공식으로 최근접 바이옴 선택 → ChunkSection.biomes[] 채움 → 클라이언트가 올바른 바이옴 색상/사운드/안개를 받음.

## 범위

**In (M4g-b 산출물).**
- 오버월드 한정. 6개 기후 DF(temperature, vegetation, continents, erosion, depth, ridges)를 quart(4블록) 단위로 평가.
- Vanilla `Climate.Sampler.sample` 로직 비트일치 재구현: double→float 캐스트 후 `(long)(f * 10000.0F)` quantization, 6-파라미터 fitness 공식.
- 64개 오버월드 바이옴 climate 파라미터를 `multi_noise_biome_source_parameter_list/overworld.json`에서 추출.
- `BiomeSampler.java`: linear search (64개 비표, RTree 불필요) → biomeId 반환. 바이옴 ID는 `registries/1.21.1.txt`의 `minecraft:worldgen/biome` 엔트리 0-기반 인덱스와 동일.
- `NoiseRouter` 에 6 기후 DF 필드 추가 (noise_settings/overworld.json의 noise_router에 이미 있음).
- `NoiseWorldSource.generate()` 에서 biome quart 채움 루프 추가.
- Oracle 확장: `biome:<seed> cx:cz` 서브커맨드 → `biome_<seed>_<cx>_<cz>.bin` fixture 덤프. Oracle도 `registries/1.21.1.txt` 기준으로 biomeId 결정 (양쪽 ID 동일 보장).
- Parity 테스트: 3개 fixture(seed0 0:0+5:5, seed123456789 0:0) 1536 quart 전부 비트비교.

**Out (후속).**
- 표층 rule(grass/dirt/sand 등 표층 블록) = M4g-c.
- Carver(동굴/협곡), aquifer(지하 fluid 정확), 광맥 = M4g-c.
- Features, 구조물 = M4g-d, M4g-e.
- 네더/엔드 바이옴.

## 알고리즘 상세

### 1. Quart 좌표

청크당 quart: quartX∈[0..3], quartZ∈[0..3], quartY∈[0..95] (minY=-64, height=384).
블록 좌표:
```
blockX = cx * 16 + quartX * 4
blockY = -64   + quartY * 4   // → -64, -60, ..., 316
blockZ = cz * 16 + quartZ * 4
```
총 4 × 4 × 96 = 1536 quart/청크.

### 2. Climate DF 평가

바닐라 `Climate.Sampler.sample(quartX, quartY, quartZ)` 로직 그대로:
```java
DensityFunction.SinglePointContext ctx = new SinglePointContext(blockX, blockY, blockZ);
float t  = (float) router.temperature().compute(ctx);
float h  = (float) router.humidity().compute(ctx);     // vegetation = humidity
float c  = (float) router.continents().compute(ctx);
float e  = (float) router.erosion().compute(ctx);
float d  = (float) router.depth().compute(ctx);
float w  = (float) router.ridges().compute(ctx);       // ridges = weirdness

// quantize: (long)(f * 10000.0F)
long qt = quantize(t); long qh = quantize(h); long qc = quantize(c);
long qe = quantize(e); long qd = quantize(d); long qw = quantize(w);
```

**중요:** double→float 먼저 캐스트 후 quantize. float 생략 시 비트 발산.

### 3. Climate 거리 공식 (vanilla Climate.java 검증됨)

```java
// Parameter.distance(long point):
long l  = point - max;    // > 0: 범위 위
long l1 = min - point;    // > 0: 범위 아래
return l > 0L ? l : Math.max(l1, 0L);  // 범위 내 = 0

// ParameterPoint.fitness(TargetPoint):
long fitness = sq(temperature.distance(qt))
             + sq(humidity.distance(qh))
             + sq(continents.distance(qc))
             + sq(erosion.distance(qe))
             + sq(depth.distance(qd))
             + sq(weirdness.distance(qw))
             + sq(offset);               // offset: quantized long (0-기반 bias)
// sq(x) = x * x
```

linear search: 64개 바이옴 중 fitness 최소 → 해당 biomeId 반환.

### 4. 바이옴 ID 매핑

`registries/1.21.1.txt`의 `minecraft:worldgen/biome` 엔트리 순서 = 0-기반 ID.
Oracle도 동일 파일 참조 → 양쪽 ID 일치 보장.

## 컴포넌트 구조

```
world/gen/
  BiomeSampler.java        (NEW)
    - BiomeEntry record: ParameterPoint params + int registryId
    - load(WorldgenData, RegistrySet): List<BiomeEntry> 빌드
    - sampleChunk(cx, cz, router): int[1536] 반환 (index = qy*16 + qz*4 + qx)
    - sample(quartX, quartY, quartZ, router): int biomeId
    - private: quantize(float), fitness(ParameterPoint, long[6]), paramDistance(min,max,v)

  NoiseRouter.java         (수정)
    + DensityFunction temperature, humidity, continents, erosion, depth, ridges
    + NoiseRouter.build() → noise_router JSON의 6 기후 DF 파싱 추가

  NoiseWorldSource.java    (수정)
    + BiomeSampler biomeSampler 필드
    + overworld() 팩토리에서 BiomeSampler.load() 초기화
    + generate(): sampleChunk() 결과로 ChunkColumn 바이옴 채움

resources/worldgen/1.21.1/
  multi_noise_biome_source_parameter_list/
    overworld.json          (NEW, 바닐라 jar에서 추출)
```

**Oracle 확장 (`worldgen-oracle/`):**
```
OracleMain.java  (수정)
  + "biome:<seed> cx:cz" 서브커맨드
  + BiomeOracleRunner: VanillaRegistries → MultiNoiseBiomeSource.overworld()
    + RandomState → Climate.Sampler
    + registries/1.21.1.txt로 name→id 변환
    + biome_<seed>_<cx>_<cz>.bin 덤프

FixtureFormat.java  (수정 또는 신규 BiomeFixtureFormat.java)
  BIOM 매직 상수 추가
```

**테스트:**
```
BiomeSamplerParityTest.java  (NEW)
  seed 0: chunk (0,0), (5,5)
  seed 123456789: chunk (0,0)
  → assertArrayEquals(fixture, sampleChunk())  // 1536 entries, 0 mismatch
```

## Fixture 포맷

`biome_<seed>_<cx>_<cz>.bin`:
```
Header (20 bytes):
  int32  magic = 0x42494F4D  ("BIOM" ASCII)
  int64  seed
  int32  cx
  int32  cz

Body: 1536 × int32  (6144 bytes)
  index = quartY * 16 + quartZ * 4 + quartX
  quartX, quartZ ∈ [0..3],  quartY ∈ [0..95]
  value = 바이옴 registry ID (0-기반, registries/1.21.1.txt 순서)

총 크기: 20 + 6144 = 6164 bytes
```

## 데이터 추출

`multi_noise_biome_source_parameter_list/overworld.json`: 바닐라 jar `data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json` 복사. 기존 T1 WorldgenData 추출 스크립트로 동일 처리.

JSON 형식 (vanilla Codec 기준):
```json
{
  "type": "minecraft:multi_noise",
  "biomes": [
    {
      "biome": "minecraft:plains",
      "parameters": {
        "temperature": [-0.45, 0.2],
        "humidity": [-0.35, 0.1],
        "continentalness": [-0.11, 0.55],
        "erosion": [-0.375, 0.45],
        "weirdness": [-0.1, 0.1],
        "depth": [0.0, 0.0],
        "offset": 0.0
      }
    },
    ...  // 64개 엔트리
  ]
}
```
각 파라미터 범위는 float, quantize는 `BiomeSampler.load()` 시 수행.

## 검증

**Tier 1 — BiomeSampler 단위 테스트:**
- `quantize(0.9f)` = 9000L (float 기반 확인)
- `paramDistance(5000, 9000, 3000)` = 2000 (3000 < 5000, min-point = 2000)
- `paramDistance(5000, 9000, 11000)` = 2000 (11000 > 9000, point-max = 2000)
- `paramDistance(5000, 9000, 7000)` = 0 (범위 내)

**Tier 2 — Biome Parity (oracle-기반 fixture):**
oracle 생성 → 커밋 → `BiomeSamplerParityTest` 전부 0 mismatch.

**Tier 3 — In-game 시각 검증:**
서버 실행 후 클라이언트 `F3` 화면의 바이옴 이름이 바닐라 동일 시드 결과와 일치하는지 스폿 체크.

## 성공 기준

1. `BiomeSamplerParityTest`: 3 fixture × 1536 quart 전부 0 mismatch.
2. `NoiseWorldSource`가 올바른 biomeId로 ChunkSection.biomes[] 채움 → 클라이언트 F3 바이옴명 일치.
3. M4g-a 지형 회귀 없음 (기존 terrain parity 테스트 통과 유지).
4. `./gradlew build` green.

## 리스크

- **double→float 캐스트 누락:** quantize 전 float 캐스트 빠지면 quantized value 달라져 바이옴 배치 어긋남. 단위 테스트로 잡힘.
- **overworld.json 파라미터 필드명:** 바닐라 JSON이 `"continentalness"`, `"weirdness"` 사용(NoiseRouter의 `continents`/`ridges`와 이름 다름). 파서 시 키 매핑 주의.
- **offset 부호:** `ParameterPoint.offset`는 0-1 float → quantize. fitness에서 `sq(offset)` 는 항상 양수 — 근접 바이옴 선호 bias. 구현 빠뜨리면 edge biome에서 mismatch.
- **WorldgenData 확장:** `multi_noise_biome_source_parameter_list/overworld.json` 로드 경로를 `WorldgenData`에 추가.
