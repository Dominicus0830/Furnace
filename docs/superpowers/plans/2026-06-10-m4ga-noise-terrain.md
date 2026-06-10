# M4g-a 기본 노이즈 지형 (시드 비트일치) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 바닐라 1.21.1 시드 → 비트 동일한 오버월드 지형 셰이프(stone/water/lava/air) 생성. 바이옴/표층/동굴/aquifer/광석/구조물 제외.

**Architecture:** 바닐라 worldgen 클래스를 mojang-mapped 소스서 우리 패키지로 **비트정확 포팅**(RNG→노이즈→density function 그래프→NoiseChunk 셀보간). 데이터(noise_settings/density_function JSON)는 서버 jar서 추출. 검증 = 별도 오라클 모듈이 바닐라 `fillFromNoise`(aquifer/oreVein 끈)로 fixture 덤프→커밋, furnace-server 테스트가 fixture와 비트비교(vanilla 의존 격리).

**Tech Stack:** Java 21, JUnit 5. mojang-mapped 바닐라 소스 = `~/.gradle/caches/paperweight-userdev/v2/work/setupMacheSources_*/output.zip`([[mc-vanilla-decompiled-source]]). 서버 jar = `build/versions/1.21.1/server-1.21.1.jar`. 브랜치 `m4ga-noise-terrain`.

**spec:** `docs/superpowers/specs/2026-06-10-m4ga-noise-terrain-design.md`

---

## 포팅 원칙 (전 Task 공통)

- **대형 바닐라 클래스는 전사하지 말고 소스서 포팅.** 수백 줄 수기 전사는 비트일치를 깨는 오타 위험. 각 Task는 정확한 바닐라 소스 경로를 주고, 적응 델타(패키지명, Mojang `Codec`/`Util`/`Mth` 의존 제거 또는 미니 대체)만 명시. 바닐라 소스가 권위.
- **소스 추출 명령(필요 시):**
  ```bash
  S=$(echo ~/.gradle/caches/paperweight-userdev/v2/work/setupMacheSources_*)
  unzip -o "$S/output.zip" "net/minecraft/world/level/levelgen/synth/ImprovedNoise.java" -d /tmp/mcsrc
  ```
- **부동소수 정확성:** 바닐라와 동일 연산 순서 유지. `Math` vs `StrictMath` 바닐라대로(대부분 `Math`). 곱셈/덧셈 결합순서 변경 금지.
- **`Mth` 의존:** 작은 헬퍼(`Mth.lerp`, `Mth.floor`, `Mth.getSeed`, `Mth.smoothstep`, `Mth.clamp`, `Mth.wrapDegrees`)는 우리 `world/gen/MathHelper.java`에 바닐라 시그니처/구현 그대로 모아 포팅. 각 Task서 필요분 추가.
- **커밋:** 각 Task 끝 `git add <명시경로>` 후 커밋. world/·furnace.properties는 .gitignore.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `worldgen-oracle/` (신규 gradle 모듈) | 바닐라 fillFromNoise/RNG/noise 덤프 → fixture. mojang-mapped 서버 의존. furnace 빌드와 분리 |
| `furnace-server/src/test/resources/worldgen/fixtures/*.bin` | 커밋된 바닐라 ground-truth fixture |
| `world/gen/MathHelper.java` | Mth 헬퍼 포팅(lerp/floor/getSeed/smoothstep/clamp...) |
| `world/gen/noise/Xoroshiro128PlusPlus.java` | Xoroshiro128++ 코어 |
| `world/gen/noise/RandomSupport.java` | upgradeSeedTo128bit/mixStafford13/Seed128bit/seedFromHashOf |
| `world/gen/noise/XoroshiroRandomSource.java` | RandomSource(nextInt/Double/forkPositional/at) |
| `world/gen/noise/ImprovedNoise.java` | 단일옥타브 perlin |
| `world/gen/noise/PerlinNoise.java` | 다중옥타브 |
| `world/gen/noise/NormalNoise.java` | 2-perlin 합성 |
| `world/gen/noise/BlendedNoise.java` | old_blended_noise |
| `world/gen/df/DensityFunction.java` | 평가 인터페이스 + FunctionContext + ContextProvider |
| `world/gen/df/DensityFunctions.java` | 전 노드 구현 |
| `world/gen/df/DensityFunctionParser.java` | JSON → DF 트리 |
| `world/gen/NoiseRouter.java` | noise_router → final_density 등 |
| `world/gen/NoiseSettings.java` | overworld 파라미터 |
| `world/gen/NoiseChunk.java` | 셀 4×8×4 샘플+삼선형 보간 |
| `world/gen/NoiseWorldSource.java` | WorldSource 구현 + fill rule |
| `world/gen/WorldgenData.java` | 추출 JSON 로드 헬퍼 |
| `furnace-server/src/main/resources/worldgen/1.21.1/**` | 추출 worldgen JSON |
| `furnace-server/build.gradle` (수정) | worldgen JSON 추출 task |
| `settings.gradle` (수정) | worldgen-oracle 모듈 등록 |
| `world/gen/AnvilWorldSource` 배선 = `FurnaceMain.java` (수정) | base를 NoiseWorldSource로 |

---

## Task 1: 브랜치 + worldgen JSON 추출

**Files:**
- Modify: `furnace-server/build.gradle`
- Create (생성물): `furnace-server/src/main/resources/worldgen/1.21.1/**`

- [ ] **Step 1: 브랜치 생성**

```bash
git checkout -b m4ga-noise-terrain
```

- [ ] **Step 2: 서버 jar서 worldgen JSON 추출(PowerShell, repo 루트)**

```powershell
$jar = "build/versions/1.21.1/server-1.21.1.jar"
$out = "furnace-server/src/main/resources/worldgen/1.21.1"
New-Item -ItemType Directory -Force $out | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $jar))
foreach ($e in $zip.Entries) {
  if ($e.FullName -match '^data/minecraft/worldgen/(noise_settings/overworld|density_function/|noise/)' -and $e.FullName.EndsWith('.json')) {
    $rel = $e.FullName -replace '^data/minecraft/worldgen/',''
    $dst = Join-Path $out $rel
    New-Item -ItemType Directory -Force (Split-Path $dst) | Out-Null
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dst, $true)
  }
}
$zip.Dispose()
```

검증: `Test-Path furnace-server/src/main/resources/worldgen/1.21.1/noise_settings/overworld.json` → True. `density_function/overworld/` 하위 json 다수, `noise/` 하위 json 다수 존재.

- [ ] **Step 3: 커밋**

```bash
git add furnace-server/src/main/resources/worldgen
git commit -m "chore(worldgen): extract vanilla 1.21.1 worldgen JSON (distill)"
```

> 이 데이터는 인터프리터(Task 10~11)가 읽음. 손편집 금지.

---

## Task 2: MathHelper (Mth 헬퍼 포팅)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/MathHelper.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/MathHelperTest.java`

- [ ] **Step 1: 실패 테스트**

```java
// Mth 헬퍼 비트일치 검증(바닐라 동작과 동일)
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MathHelperTest {
    @Test void floor_neg() { assertEquals(-1, MathHelper.floor(-0.5)); assertEquals(0, MathHelper.floor(0.9)); }
    @Test void lerp_mid() { assertEquals(5.0, MathHelper.lerp(0.5, 0.0, 10.0), 0.0); }
    @Test void smoothstep_half() { assertEquals(0.5, MathHelper.smoothstep(0.5), 0.0); }
    @Test void getSeed_known() {
        // 바닐라 Mth.getSeed(1,2,3) = 위치 해시. 값은 바닐라 소스 공식 그대로라 자동 일치.
        long s = MathHelper.getSeed(1, 2, 3);
        assertEquals(MathHelper.getSeed(1, 2, 3), s); // 결정적
        assertNotEquals(MathHelper.getSeed(1,2,3), MathHelper.getSeed(3,2,1));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*MathHelperTest"` → 컴파일 실패(MathHelper 없음).

- [ ] **Step 3: MathHelper 포팅**

바닐라 `net/minecraft/util/Mth.java`서 아래 메서드만 시그니처/구현 **그대로** 포팅: `floor(double)`, `floor(float)`, `lerp(double,double,double)`, `lerp2(...)`, `lerp3(...)`, `clamp(double,double,double)`, `clamp(int,int,int)`, `clampedLerp`, `smoothstep(double)`, `smoothstepDerivative`, `getSeed(int,int,int)`, `wrapDegrees`, `square(double)`, `frac`, `inverseLerp`, `map`. 헤더 1줄 한국어 주석.

```java
// 바닐라 net.minecraft.util.Mth 핵심 수학 헬퍼 비트정확 포팅(worldgen 전용)
package org.domi.furnace.server.world.gen;

public final class MathHelper {
    private MathHelper() {}
    public static int floor(double v) { int i = (int) v; return v < i ? i - 1 : i; }
    public static int floor(float v) { int i = (int) v; return v < i ? i - 1 : i; }
    public static double lerp(double t, double a, double b) { return a + t * (b - a); }
    public static double clampedLerp(double a, double b, double t) {
        if (t < 0.0) return a; return t > 1.0 ? b : lerp(t, a, b);
    }
    public static double smoothstep(double v) { return v * v * v * (v * (v * 6.0 - 15.0) + 10.0); }
    public static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }
    public static double square(double v) { return v * v; }
    // 필요 시 lerp2/lerp3/wrapDegrees/inverseLerp/map 추가 — 전부 Mth 원본 그대로
}
```
> 후속 Task가 추가 메서드 필요하면 그때 Mth서 동일 포팅.

- [ ] **Step 4: 통과 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*MathHelperTest"` → PASS.
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/MathHelper.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/MathHelperTest.java
git commit -m "feat(worldgen): port Mth helpers"
```

---

## Task 3: 오라클 모듈 + fixture 덤프 인프라

> **이 Task가 검증 토대.** mojang-mapped 바닐라 서버를 헤드리스로 돌려 ground-truth fixture 생성. M4g b~e서 재사용.

**Files:**
- Create: `worldgen-oracle/build.gradle`
- Create: `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java`
- Create: `worldgen-oracle/src/main/java/org/domi/oracle/FixtureFormat.java`
- Modify: `settings.gradle`
- 생성물(커밋): `furnace-server/src/test/resources/worldgen/fixtures/*.bin`

- [ ] **Step 1: 모듈 등록**

`settings.gradle`에 추가:
```gradle
include 'worldgen-oracle'
```

- [ ] **Step 2: 오라클 build.gradle — mojang-mapped 서버 + 라이브러리 의존**

mojang-mapped 컴파일 산출물 경로 확인(1회):
```bash
ls ~/.gradle/caches/paperweight-userdev/v2/work/applyDevBundlePatches_*/output.jar
ls ~/.gradle/caches/paperweight-userdev/v2/work/extractFromBundler_*/minecraftLibraries
```
`worldgen-oracle/build.gradle`:
```gradle
plugins { id 'java' }
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
def pw = "${System.getProperty('user.home')}/.gradle/caches/paperweight-userdev/v2/work"
dependencies {
    // mojang-mapped 서버(remap 산출). 경로는 위 ls로 확정해 정확히 기입.
    implementation files("${pw}/applyDevBundlePatches_8296811d0593b352ab16b08f005df682241489b414761d106df4bd30e1e65809/output.jar")
    implementation fileTree(dir: "${pw}/extractFromBundler_5e4288fdbb90bffa23bfa44f32efe07f3099cd8dafaabcef917fe9b93b503f6b/minecraftLibraries", include: '**/*.jar')
}
```
> 해시 디렉토리명은 머신마다 다름 — Step의 ls 결과로 교체. output.jar이 mojang-mapped 아니면(spigot-mapped) 폴백: 사용자 Paper 플러그인 dev 환경서 동일 OracleMain 실행해 fixture 생성·커밋(spec 리스크 폴백). fixture만 커밋되면 furnace-server 테스트는 동일 동작.

- [ ] **Step 3: FixtureFormat — 공유 바이너리 포맷**

```java
// 오라클↔테스트 공유 fixture 바이너리 포맷. 카테고리 바이트: 0=air 1=stone 2=water 3=lava 4=other
package org.domi.oracle;

public final class FixtureFormat {
    public static final byte AIR = 0, STONE = 1, WATER = 2, LAVA = 3, OTHER = 4;
    public static final int MIN_Y = -64, HEIGHT = 384; // y: MIN_Y..MIN_Y+HEIGHT-1
    // 청크 fixture 레이아웃: magic "FXC1"(4B) + seedLo(8) + seedHi(8) + chunkX(4) + chunkZ(4)
    //   + category[HEIGHT*16*16] (index = (y-MIN_Y)*256 + z*16 + x)
    public static int blockIndex(int localX, int y, int localZ) {
        return (y - MIN_Y) * 256 + localZ * 16 + localX;
    }
}
```
furnace-server 테스트도 이 인덱싱을 쓰므로, 동일 상수를 `furnace-server` 테스트 쪽에 복제하거나 `FixtureFormat`을 furnace-server test로 복사(모듈 분리상 복제가 단순). 복제본 헤더에 "oracle FixtureFormat 미러" 주석.

- [ ] **Step 4: OracleMain — RNG/노이즈/청크 덤프**

`OracleMain.java`(바닐라 API 사용). 핵심 동작:
```
main(args): Bootstrap.bootStrap();
  RegistryAccess.Frozen ra = (builtin worldgen 등록된 레지스트리; VanillaRegistries.createLookup() 사용);
  long seed = parse;
  // (a) RNG 덤프: XoroshiroRandomSource(seed) → nextLong() 100개 → rng_<seed>.bin (long[] LE)
  // (b) noise 덤프: NormalNoise(특정 파라미터, RandomSource) 고정좌표 샘플 → noise_<id>.bin
  // (c) 청크 덤프: overworld NoiseGeneratorSettings 가져와 copy(aquifersEnabled=false, oreVeinsEnabled=false);
  //     NoiseBasedChunkGenerator + MultiNoiseBiomeSource(overworld preset);
  //     RandomState.create(settings, ra.lookup(NOISE), seed);
  //     각 (cx,cz): ProtoChunk 생성 → generator.fillFromNoise(blender=Blender.empty(), randomState, structureManager, chunk).join();
  //     각 블록 BlockState → 카테고리 매핑(default_block=stone→STONE, water→WATER, lava→LAVA, isAir→AIR, else OTHER) → chunk_<seed>_<cx>_<cz>.bin
```
> 정확한 Mojang API 시그니처(클래스/메서드)는 mojang-mapped 소스 정독해 확정: `net/minecraft/server/Bootstrap`, `net/minecraft/data/registries/VanillaRegistries`, `net/minecraft/world/level/levelgen/{NoiseBasedChunkGenerator,RandomState,NoiseGeneratorSettings,Blender}`, `net/minecraft/world/level/biome/MultiNoiseBiomeSource`, `net/minecraft/world/level/chunk/ProtoChunk`. fillFromNoise 시그니처 = `CompletableFuture<ChunkAccess> fillFromNoise(Blender, RandomState, StructureManager, ChunkAccess)`. NoiseGeneratorSettings는 record 유사 — aquifersEnabled/oreVeinsEnabled 필드 끈 복사본 생성(생성자 직접 호출).

- [ ] **Step 5: fixture 생성·커밋(1회 실행)**

```bash
./gradlew :worldgen-oracle:run --args="0 -3:8 0:0 5:5 0 100"   # 예: seed 0, 청크 3개, rng 100개
./gradlew :worldgen-oracle:run --args="123456789 0:0 10:10 -8:-8"
# 출력 → furnace-server/src/test/resources/worldgen/fixtures/
git add furnace-server/src/test/resources/worldgen/fixtures worldgen-oracle settings.gradle
git commit -m "test(worldgen): vanilla noise-stage oracle + committed fixtures (aquifer/oreVein off)"
```
검증: `fixtures/chunk_0_0_0.bin` 크기 = 16+HEIGHT*256 = 16 + 98304 byte. `rng_0.bin` = 800 byte(long 100개).

> 시드 셋: 0(평지/바다 섞임) + 비자명 1개, 청크는 스폰 근처+원거리(지형 다양성). 부족하면 추가.

---

## Task 4: Xoroshiro RNG (비트일치 토대)

**Files:**
- Create: `world/gen/noise/Xoroshiro128PlusPlus.java`, `RandomSupport.java`, `XoroshiroRandomSource.java`
- Test: `world/gen/noise/XoroshiroRandomSourceTest.java`

- [ ] **Step 1: 실패 테스트 — fixture 시퀀스 일치**

```java
// Xoroshiro RNG가 바닐라 nextLong 시퀀스와 비트일치
package org.domi.furnace.server.world.gen.noise;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import org.junit.jupiter.api.Test;

class XoroshiroRandomSourceTest {
    private static long[] readRngFixture(long seed) throws IOException {
        try (InputStream in = XoroshiroRandomSourceTest.class.getResourceAsStream("/worldgen/fixtures/rng_" + seed + ".bin")) {
            assertNotNull(in, "fixture 없음");
            byte[] b = in.readAllBytes();
            LongBuffer lb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
            long[] out = new long[lb.remaining()]; lb.get(out); return out;
        }
    }
    @Test void matchesVanillaSequence() throws Exception {
        long[] expected = readRngFixture(0L);
        XoroshiroRandomSource r = new XoroshiroRandomSource(0L);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], r.nextLong(), "idx " + i);
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :furnace-server:test --tests "*XoroshiroRandomSourceTest"` → 컴파일 실패.

- [ ] **Step 3: 구현 (inline, 바닐라 정확)**

`Xoroshiro128PlusPlus.java`:
```java
// Xoroshiro128++ 코어(바닐라 net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus)
package org.domi.furnace.server.world.gen.noise;

public final class Xoroshiro128PlusPlus {
    private long seedLo, seedHi;
    public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo; this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) { this.seedLo = -7046029254386353131L; this.seedHi = 7640891576956012809L; }
    }
    public long nextLong() {
        long l = this.seedLo, l1 = this.seedHi;
        long l2 = Long.rotateLeft(l + l1, 17) + l;
        l1 ^= l;
        this.seedLo = Long.rotateLeft(l, 49) ^ l1 ^ l1 << 21;
        this.seedHi = Long.rotateLeft(l1, 28);
        return l2;
    }
}
```
`RandomSupport.java`:
```java
// 시드 128비트 확장 + Stafford13 믹스(바닐라 RandomSupport)
package org.domi.furnace.server.world.gen.noise;

public final class RandomSupport {
    private RandomSupport() {}
    public static long mixStafford13(long s) {
        s = (s ^ s >>> 30) * -4658895280553007687L;
        s = (s ^ s >>> 27) * -7723592293110705685L;
        return s ^ s >>> 31;
    }
    public record Seed128bit(long seedLo, long seedHi) {
        public Seed128bit xor(long lo, long hi) { return new Seed128bit(seedLo ^ lo, seedHi ^ hi); }
        public Seed128bit mixed() { return new Seed128bit(mixStafford13(seedLo), mixStafford13(seedHi)); }
    }
    public static Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long l = seed ^ 7640891576956012809L; long l1 = l + -7046029254386353131L;
        return new Seed128bit(l, l1);
    }
    public static Seed128bit upgradeSeedTo128bit(long seed) { return upgradeSeedTo128bitUnmixed(seed).mixed(); }
}
```
`XoroshiroRandomSource.java`: 바닐라 `XoroshiroRandomSource` 포팅. inline 핵심 — `nextLong/nextInt/nextInt(bound)/nextDouble/nextBits/forkPositional/at`. Codec/gaussian/MarsagliaPolarGaussian 제외(worldgen 노이즈는 nextDouble/nextInt/nextLong만 씀; gaussian 불필요). `nextBits(int) = randomNumberGenerator.nextLong() >>> 64-bits`, `nextDouble() = nextBits(53) * 1.110223E-16`, `forkPositional()→PositionalRandomFactory.at(x,y,z)= new Xoroshiro(Mth.getSeed(x,y,z) ^ seedLo, seedHi)`. 시그니처는 바닐라 소스(/tmp 추출본) 그대로.

```java
// 바닐라 XoroshiroRandomSource 포팅(worldgen 노이즈 시딩용; gaussian/Codec 생략)
package org.domi.furnace.server.world.gen.noise;
import org.domi.furnace.server.world.gen.MathHelper;
// ... 필드 Xoroshiro128PlusPlus rng; 생성자 (long seed)→ new Xoroshiro128PlusPlus(upgradeSeedTo128bit(seed)),
//     (long lo,long hi), nextLong/nextInt/nextInt(bound)/nextDouble/nextBits/forkPositional/at — 바닐라 소스 그대로 옮김.
```
> `nextInt(bound)` 알고리즘은 바닐라 소스(Lemire) 정확히 복제 — 노이즈가 셔플에 씀.

- [ ] **Step 4: 통과 + 커밋**

Run: `./gradlew :furnace-server:test --tests "*XoroshiroRandomSourceTest"` → PASS.
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/noise/ furnace-server/src/test/java/org/domi/furnace/server/world/gen/noise/XoroshiroRandomSourceTest.java
git commit -m "feat(worldgen): bit-exact Xoroshiro128++ RNG"
```

---

## Task 5: ImprovedNoise (단일 옥타브 perlin)

**Files:**
- Create: `world/gen/noise/ImprovedNoise.java`
- Test: `world/gen/noise/ImprovedNoiseTest.java`

- [ ] **Step 1: 실패 테스트 — fixture 샘플 일치**

오라클이 덤프한 `noise_improved_<seed>.bin`(고정좌표 N개 샘플, double LE) 대상. 테스트는 `ImprovedNoise(new XoroshiroRandomSource(seed))` 생성 후 동일좌표 `noise(x,y,z)` 호출, fixture와 `assertEquals(exp, act, 0.0)`(비트동일). (오라클 Step에 noise 덤프 메서드 추가 필요 — Task 3 OracleMain에 `ImprovedNoise` 샘플 덤프 포함.)

```java
package org.domi.furnace.server.world.gen.noise;
import static org.junit.jupiter.api.Assertions.*;
// readDoubleFixture("/worldgen/fixtures/noise_improved_0.bin") + 좌표표(코드 내 고정 배열)
// for each (x,y,z,expected): assertEquals(expected, n.noise(x,y,z), 0.0);
```

- [ ] **Step 2: 실패 확인** — 컴파일 실패.

- [ ] **Step 3: 포팅**

바닐라 `net/minecraft/world/level/levelgen/synth/ImprovedNoise.java` 포팅. 적응: 패키지, `RandomSource`→우리 `XoroshiroRandomSource`(또는 작은 인터페이스), `Mth`→`MathHelper`. permutation `p[]` 초기화(`p[i]=i` 후 shuffle via `random.nextInt(256-i)`), `xo/yo/zo` 오프셋(`random.nextDouble()*256`), `noise(x,y,z, yScale, yMax)`, `sampleAndLerp`, `gradDot`, `SimplexNoise.GRADIENT`(grad 벡터 12개 — `net/minecraft/world/level/levelgen/synth/SimplexNoise.java`의 `GRADIENT` 상수 포팅). 전부 바닐라 그대로.

- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/noise/ImprovedNoise.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/noise/ImprovedNoiseTest.java
git commit -m "feat(worldgen): bit-exact ImprovedNoise (single-octave perlin)"
```

---

## Task 6: PerlinNoise + NormalNoise (다중옥타브/합성)

**Files:**
- Create: `world/gen/noise/PerlinNoise.java`, `NormalNoise.java`
- Test: `world/gen/noise/NormalNoiseTest.java`

- [ ] **Step 1: 실패 테스트** — 오라클 `noise_normal_<id>.bin`(특정 NoiseParameters로 만든 NormalNoise 고정좌표 샘플) 대상. `NormalNoise.create(random, firstOctave, amplitudes)` 후 동일좌표 `getValue(x,y,z)` 비트일치 assert.

- [ ] **Step 2: 실패 확인.**

- [ ] **Step 3: 포팅**

- `PerlinNoise.java` ← 바닐라 `synth/PerlinNoise.java`: `create(random, firstOctave, amplitudes)`, octave별 `ImprovedNoise` 생성(`PositionalRandomFactory`/`forkPositional` + `fromHashOf("octave_-N")` 시딩 — **시딩 방식 정확 복제가 핵심**; 바닐라 `PerlinNoise(RandomSource, IntStream/octaves, boolean)` 생성자 로직 그대로), `getValue`, `wrapStrict`(=`Mth.wrapDegrees`류 좌표 wrap, 바닐라 `wrap`), `getOctaveNoise`, `maxValue`.
- `NormalNoise.java` ← 바닐라 `synth/NormalNoise.java`: 2개 PerlinNoise(`first`,`second`) + `valueFactor`, `INPUT_FACTOR=1.0181268882175227`, `create`, `getValue(x,y,z)= first.getValue(x*xz,..) + second.getValue(x*xz*INPUT_FACTOR,...)` × valueFactor. `NoiseParameters(firstOctave, amplitudes)` 레코드 포팅.

> 옥타브 시딩(`fromHashOf`/`forkPositional`)은 `XoroshiroRandomSource.PositionalRandomFactory` + `RandomSupport.seedFromHashOf`(MD5) 필요 → Task 4 XoroshiroRandomSource에 `forkPositional`/`fromHashOf` 포함됐어야 함(없으면 여기서 추가, MD5 = `java.security.MessageDigest`).

- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/noise/PerlinNoise.java furnace-server/src/main/java/org/domi/furnace/server/world/gen/noise/NormalNoise.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/noise/NormalNoiseTest.java
git commit -m "feat(worldgen): bit-exact Perlin + NormalNoise"
```

---

## Task 7: BlendedNoise (old_blended_noise)

**Files:**
- Create: `world/gen/noise/BlendedNoise.java`
- Test: `world/gen/noise/BlendedNoiseTest.java`

- [ ] **Step 1: 실패 테스트** — 오라클 `noise_blended_<seed>.bin`(BlendedNoise 고정좌표 샘플) 대상 비트일치.
- [ ] **Step 2: 실패 확인.**
- [ ] **Step 3: 포팅** — 바닐라 `synth/BlendedNoise.java`: `createUnseeded`/생성자(`PerlinNoise minLimit/maxLimit/main` + `xzScale/yScale/xzFactor/yFactor/smearScaleMultiplier`), `compute(DensityFunction.FunctionContext)` 또는 `calculateNoise`. DF 노드(`old_blended_noise`)가 이걸 감쌈 → Task 8서 연결. 파라미터는 추출 JSON의 `old_blended_noise` 노드서.
- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/noise/BlendedNoise.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/noise/BlendedNoiseTest.java
git commit -m "feat(worldgen): bit-exact BlendedNoise"
```

---

## Task 8: DensityFunction 인터페이스 + 비-노이즈 노드

**Files:**
- Create: `world/gen/df/DensityFunction.java`, `world/gen/df/DensityFunctions.java`
- Test: `world/gen/df/DensityFunctionsTest.java`

- [ ] **Step 1: 실패 테스트 — 합성 입력으로 노드 단위검증**

```java
// 단순 노드(const/add/mul/min/max/clamp/abs/square/y_clamped_gradient) 단위검증
package org.domi.furnace.server.world.gen.df;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DensityFunctionsTest {
    static DensityFunction.FunctionContext at(int x,int y,int z){ return new DensityFunction.SinglePointContext(x,y,z); }
    @Test void constant(){ assertEquals(3.0, DensityFunctions.constant(3.0).compute(at(0,0,0)), 0.0); }
    @Test void addMul(){
        var f = DensityFunctions.mul(DensityFunctions.constant(2.0), DensityFunctions.add(DensityFunctions.constant(1.0), DensityFunctions.constant(4.0)));
        assertEquals(10.0, f.compute(at(0,0,0)), 0.0);
    }
    @Test void yGradient(){
        var f = DensityFunctions.yClampedGradient(0, 100, 0.0, 1.0);
        assertEquals(0.5, f.compute(at(0,50,0)), 1e-12);
    }
}
```

- [ ] **Step 2: 실패 확인.**

- [ ] **Step 3: 구현**

`DensityFunction.java` ← 바닐라 `world/level/levelgen/DensityFunction.java` 인터페이스 핵심: `double compute(FunctionContext)`, `void fillArray(double[], ContextProvider)`, `FunctionContext{int blockX/blockY/blockZ}`, `SinglePointContext`, `ContextProvider`, `minValue/maxValue`. (Codec/mapAll 제외, 매핑은 파서가.)
`DensityFunctions.java` ← 바닐라 `DensityFunctions.java`서 노드 구현 포팅(이 Task 범위 = 노이즈/보간 빼고): `constant`, `Constant`, `add/mul/min/max`(`TwoArgumentSimpleFunction`/`Ap2`), `abs/square/cube/half_negative/quarter_negative/squeeze`(`Mapped`/`PureTransformer`), `clamp`(`Clamp`), `yClampedGradient`(`YClampedGradient`), `rangeChoice`(`RangeChoice`), `spline`(`Spline` + `CubicSpline` 포팅 — `world/level/levelgen/synth/...` 아님, `util/CubicSpline.java`), `constantRange`. 헤더 한국어 주석.

- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/df/DensityFunction.java furnace-server/src/main/java/org/domi/furnace/server/world/gen/df/DensityFunctions.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/df/DensityFunctionsTest.java
git commit -m "feat(worldgen): DensityFunction interface + non-noise nodes"
```

---

## Task 9: 노이즈/캐시/마커 DF 노드

**Files:**
- Modify: `world/gen/df/DensityFunctions.java`
- Test: `world/gen/df/DensityFunctionNoiseNodeTest.java`

- [ ] **Step 1: 실패 테스트** — `noise`/`shifted_noise` 노드를 알려진 NormalNoise로 구성, 고정좌표서 직접계산값과 노드 `compute` 일치(비트). `old_blended_noise` 노드 = Task 7 BlendedNoise 래핑값 일치.

- [ ] **Step 2: 실패 확인.**

- [ ] **Step 3: 구현** — 바닐라 `DensityFunctions.java`서 포팅: `noise`(`Noise`), `shiftedNoise2d`/`shiftNoise`/`shiftA`/`shiftB`(`ShiftedNoise`,`ShiftNoise`), `weirdScaledSampler`, `blendDensity`/`blendAlpha`/`blendOffset`(blend 비활성=`Blender.empty()`라 패스스루로 단순화 — 바닐라 `BlendingFunction`/`Beardifier` 미사용 경로 그대로), `interpolated`(`Interpolated` — NoiseChunk 연동), `flatCache`(`FlatCache`), `cache2d`(`Cache2D`), `cacheOnce`(`CacheOnce`), `cacheAllInCell`(`CacheAllInCell`), `oldBlendedNoise`(`BlendedNoise` 래퍼), `markerOrMarked`. 캐시/보간 노드는 `NoiseChunk`(Task 12)의 `ContextProvider`와 연동 — 인터페이스만 먼저, 보간 동작은 Task 12서 NoiseChunk가 채움.

> blend 비활성 처리: 오버월드 신규 생성은 `Blender.empty()` → blend_* 노드는 입력 패스스루. 바닐라 동작 그대로(blend 안 함) 복제.

- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/df/DensityFunctions.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/df/DensityFunctionNoiseNodeTest.java
git commit -m "feat(worldgen): noise/cache/marker density-function nodes"
```

---

## Task 10: DF 파서 + 데이터 로드

**Files:**
- Create: `world/gen/df/DensityFunctionParser.java`, `world/gen/WorldgenData.java`
- Test: `world/gen/df/DensityFunctionParserTest.java`

- [ ] **Step 1: 실패 테스트** — 추출 JSON 로드해 `minecraft:overworld/base_3d_noise` 등 알려진 작은 DF를 파싱→`compute` 값이 손계산/오라클 노드값과 일치. id 상호참조(`"minecraft:overworld/offset"` 문자열 참조)가 해석되는지.

```java
// 추출 density_function JSON 파싱 → DF 트리, 참조 해석, compute 일치
package org.domi.furnace.server.world.gen.df;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DensityFunctionParserTest {
    @Test void parsesAndResolvesRefs() {
        var data = org.domi.furnace.server.world.gen.WorldgenData.load("worldgen/1.21.1");
        DensityFunctionParser p = new DensityFunctionParser(data, /*noiseParams*/ data.noiseParams(), /*seed*/ 0L);
        DensityFunction f = p.parse("minecraft:overworld/base_3d_noise"); // old_blended_noise 래퍼
        assertNotNull(f);
        double v = f.compute(new DensityFunction.SinglePointContext(0,0,0));
        assertTrue(Double.isFinite(v));
    }
}
```

- [ ] **Step 2: 실패 확인.**

- [ ] **Step 3: 구현**

`WorldgenData.java`: 리소스(`worldgen/1.21.1/**`)서 모든 `density_function/**.json`, `noise/*.json`, `noise_settings/overworld.json`을 맵으로 로드(id→JsonObject). gson 사용(기존 의존 확인; 없으면 build.gradle에 추가). NormalNoise 파라미터(`noise/*`)도 로드.
`DensityFunctionParser.java`: JSON → DensityFunction 트리. `type` 디스패치(`minecraft:constant/add/mul/min/max/abs/square/cube/half_negative/quarter_negative/squeeze/clamp/y_clamped_gradient/range_choice/spline/noise/shifted_noise/shift_a/shift_b/shift/weird_scaled_sampler/blend_density/blend_alpha/blend_offset/old_blended_noise/interpolated/flat_cache/cache_2d/cache_once/cache_all_in_cell/end_islands(미사용)`). 문자열값 = 다른 DF id 참조(재귀 parse, 순환참조 캐시로 방지). 숫자값 = constant. 노이즈 노드의 `noise` 필드 = NormalNoise.create(파라미터, positional random). **누락 type은 명시적 예외**(추측 평가 금지).

- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/df/DensityFunctionParser.java furnace-server/src/main/java/org/domi/furnace/server/world/gen/WorldgenData.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/df/DensityFunctionParserTest.java
git commit -m "feat(worldgen): density-function JSON parser + data loader"
```

---

## Task 11: NoiseRouter + NoiseSettings

**Files:**
- Create: `world/gen/NoiseRouter.java`, `world/gen/NoiseSettings.java`
- Test: `world/gen/NoiseRouterTest.java`

- [ ] **Step 1: 실패 테스트** — overworld.json 로드 → `NoiseSettings`(min_y=-64,height=384,sea_level=63,cellWidth=4,cellHeight=8,default_block/fluid) 값 assert. `NoiseRouter` 빌드 후 `finalDensity().compute(at(0, 0, 0))` finite.

```java
class NoiseRouterTest {
    @Test void settingsAndRouter() {
        var data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings s = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        assertEquals(-64, s.minY()); assertEquals(384, s.height());
        assertEquals(63, s.seaLevel()); assertEquals(4, s.cellWidth()); assertEquals(8, s.cellHeight());
        NoiseRouter r = NoiseRouter.build(data, s, 0L);
        assertTrue(Double.isFinite(r.finalDensity().compute(new DensityFunction.SinglePointContext(0, 0, 0))));
    }
}
```

- [ ] **Step 2: 실패 확인.**

- [ ] **Step 3: 구현** — `NoiseSettings`(record: minY,height,cellWidth=4*size_h,cellHeight=4*size_v,seaLevel,defaultBlockStateId,defaultFluidStateId, lavaFloorY=-54). `NoiseRouter`(noise_settings.noise_router의 각 항목을 DensityFunctionParser로 파싱: `final_density` 필수, 나머지(`barrier/fluid_level_floodedness/.../vein_*/initial_density_without_jaggedness`)는 후속단계용이라 파싱만 보관). cellWidth/Height = `QuartPos`/noise size 환산(size_horizontal=1→4, size_vertical=2→8).

- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseRouter.java furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseSettings.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseRouterTest.java
git commit -m "feat(worldgen): NoiseRouter + NoiseSettings from extracted overworld"
```

---

## Task 12: NoiseChunk 셀 보간

**Files:**
- Create: `world/gen/NoiseChunk.java`
- Test: `world/gen/NoiseChunkTest.java`

- [ ] **Step 1: 실패 테스트** — 단일 청크(cx=0,cz=0,seed=0)서 `NoiseChunk`로 셀 보간된 final_density 부호(>0)를 컬럼별로 계산, fixture(Task 3 chunk_0_0_0.bin)의 STONE 위치와 일치 검증(보간 정확성). 전체 컬럼 비교는 Task 13(블록 채움)서, 여기선 density 부호=stone 마스크 일치까지.

```java
class NoiseChunkTest {
    @Test void cellInterpolationMatchesFixtureStoneMask() throws Exception {
        var data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings s = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter r = NoiseRouter.build(data, s, 0L);
        byte[] fx = FixtureIO.readChunk("/worldgen/fixtures/chunk_0_0_0.bin"); // 카테고리 배열
        NoiseChunk nc = new NoiseChunk(0, 0, r, s);
        for (int lx=0; lx<16; lx++) for (int lz=0; lz<16; lz++) for (int y=s.minY(); y<s.minY()+s.height(); y++) {
            boolean ourStone = nc.finalDensityAt(lx, y, lz) > 0;
            boolean vanillaStone = fx[FixtureFormat.blockIndex(lx, y, lz)] == FixtureFormat.STONE;
            assertEquals(vanillaStone, ourStone, "@"+lx+","+y+","+lz);
        }
    }
}
```

- [ ] **Step 2: 실패 확인.**

- [ ] **Step 3: 구현** — 바닐라 `world/level/levelgen/NoiseChunk.java` 포팅(핵심 경로만): 셀 격자(cellCountXZ=16/cellWidth=4, cellCountY=height/cellHeight=48), 셀 코너서 `finalDensity.compute` 후 `interpolated` 노드용 보간 슬라이스(`NoiseInterpolator` 역할), 블록단위 `finalDensityAt(x,y,z)`가 삼선형 보간 반환. 바닐라의 `Cache2D/FlatCache/CacheOnce` `ContextProvider` 연동(Task 9 캐시노드가 NoiseChunk 셀 컨텍스트 사용). `Blender.empty()` 경로. **셀 채움 순서(아래 y, 안쪽 x/z 루프)·보간 계수 바닐라 정확 복제.**

> 가장 까다로운 Task. 바닐라 `NoiseChunk` + `DensityFunctions.Interpolated`/`Marker` 상호작용 정독 필수. 실패 시 NoiseChunkTest가 정확히 어느 좌표서 갈리는지 알려줌 → 그 셀 보간 디버깅.

- [ ] **Step 4: 통과 + 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseChunk.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseChunkTest.java
git commit -m "feat(worldgen): NoiseChunk cell sampling + trilinear interpolation"
```

---

## Task 13: NoiseWorldSource + 통합 + 전 컬럼 parity

**Files:**
- Create: `world/gen/NoiseWorldSource.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`
- Test: `world/gen/NoiseWorldSourceParityTest.java`

- [ ] **Step 1: 실패 테스트 — 전 컬럼 비트일치(다중 시드·청크)**

```java
// NoiseWorldSource 생성 == 바닐라 노이즈단계 fixture, 전체 컬럼 stone/water/lava/air 비트일치
package org.domi.furnace.server.world.gen;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

class NoiseWorldSourceParityTest {
    private void assertChunkParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg);
        ChunkColumn col = src.generate(cx, cz);
        byte[] fx = FixtureIO.readChunk("/worldgen/fixtures/chunk_"+seed+"_"+cx+"_"+cz+".bin");
        for (int lx=0; lx<16; lx++) for (int lz=0; lz<16; lz++) for (int y=-64; y<320; y++) {
            byte want = fx[FixtureFormat.blockIndex(lx,y,lz)];
            byte got = category(reg, col.getBlock(lx, y, lz));
            assertEquals(want, got, "seed="+seed+" @"+(cx*16+lx)+","+y+","+(cz*16+lz));
        }
    }
    @Test void seed0_spawn() throws Exception { assertChunkParity(0L, 0, 0); }
    @Test void seed0_far() throws Exception { assertChunkParity(0L, 5, 5); }
    @Test void seedB() throws Exception { assertChunkParity(123456789L, 0, 0); }
    // category(): stateId → AIR/STONE/WATER/LAVA (reg.defaultStateId 비교)
}
```

- [ ] **Step 2: 실패 확인.**

- [ ] **Step 3: 구현 NoiseWorldSource**

```java
// 바닐라 비트일치 노이즈 지형 WorldSource. final_density>0=stone, else 글로벌 fluid picker(lava<-54/water<63/air)
package org.domi.furnace.server.world.gen;
// overworld(seed, reg): WorldgenData 로드, NoiseSettings/NoiseRouter 빌드 1회(불변, 공유)
// generate(cx,cz): new NoiseChunk(cx,cz,router,settings)[thread-local 또는 청크당]; ChunkColumn 채움:
//   for lx,lz,y: d = nc.finalDensityAt(lx,y,lz);
//     state = d > 0 ? STONE : (y < -54 ? LAVA : (y < seaLevel ? WATER : AIR));
//     col.setBlock(lx, y, lz, state);
//   return col;
```
fluid picker 정확: `y < lavaFloorY(-54) → lava, y < seaLevel(63) → water, else air`. STONE/WATER/LAVA/AIR state id = `reg.defaultStateId("minecraft:stone"/"minecraft:water"/"minecraft:lava")`, air = `ChunkSection.AIR`.

- [ ] **Step 4: FurnaceMain 배선**

`base = new SuperflatWorldSource(...)` 자리를 `NoiseWorldSource.overworld(seed, blockRegistry)`로 교체(AnvilWorldSource에 주입). seed 소스 = furnace.properties 또는 기존 월드 seed. SuperflatWorldSource 파일은 남김.

- [ ] **Step 5: 통과 + 빌드**

Run: `./gradlew :furnace-server:test --tests "*NoiseWorldSourceParityTest"` → PASS(전 시드·청크 비트일치).
Run: `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**
```bash
git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseWorldSourceParityTest.java
git commit -m "feat(worldgen): NoiseWorldSource bit-exact terrain + wire into world"
```

---

## Task 14: 수동 검증 (in-game)

- [ ] **Step 1:** 기존 `world/` 백업/삭제(새 생성 보려면). furnace.properties seed = 알려진 바닐라 시드.
- [ ] **Step 2:** `./gradlew :furnace-server:run` → 접속. 맨 stone 지형 + 바다(y<63 water) + 지하 lava(<-54) 생성 확인. 표층(잔디) 없음 정상.
- [ ] **Step 3:** 같은 시드 바닐라 싱글플레이와 지형 윤곽(산/계곡/해안선) 육안 일치 스폿체크.
- [ ] **Step 4:** 블록 설치/파괴(M4c-1)·영속(M4b)·청크전송 회귀 없음 확인.
- [ ] **Step 5: 문제 시 커밋**
```bash
git add furnace-server
git commit -m "fix(worldgen): M4g-a from manual verify"
```

---

## Self-Review 메모

- **spec 커버리지:** 비트일치 4요소 = Xoroshiro(T4)·노이즈 프리미티브(T5,6,7)·DF 그래프(T8,9,10,11)·NoiseChunk 셀보간(T12). 데이터 추출=T1. fill rule(stone/lava/water/air)=T13. Tier1 단위 fixture=T4~7. Tier2 전컬럼 parity=T12(stone마스크),T13(전블록). 오라클 하니스 재사용=T3. 통합=T13. 수동검증=T14. 전 항목 커버.
- **포팅 참조 정당성:** 대형 바닐라 클래스(ImprovedNoise/Perlin/Normal/Blended/DF노드/NoiseChunk)는 정확 경로 + 적응 델타 지정 — 수기 전사보다 비트일치에 안전(권위 소스 포팅). 작은 코어(Xoroshiro/RandomSupport/MathHelper/fill rule)는 inline 완전 코드.
- **타입 일관성:** `XoroshiroRandomSource`(T4: nextLong/nextInt/nextDouble/forkPositional/at) → 노이즈(T5~7)서 사용. `DensityFunction.compute(FunctionContext)`/`SinglePointContext`(T8) → 전 DF Task·NoiseChunk·NoiseWorldSource 일관. `NoiseSettings.fromJson`/`NoiseRouter.build`(T11) → NoiseChunk(T12)·NoiseWorldSource(T13). `FixtureFormat.blockIndex`/카테고리(T3) → 전 parity 테스트. `WorldgenData.load`(T10) → T11,13. `NoiseWorldSource.overworld(seed,reg)`(T13).
- **위험/순서:** T3(오라클)이 후속 전 검증의 토대 — 가장 먼저(T1,2 다음). 오라클 jar 의존 실패 시 폴백(Paper dev 환경 덤프) 명시. T12(NoiseChunk 보간)이 최난도 — fixture가 정확 좌표 갈림 지점 짚어줌. blend는 `Blender.empty()` 패스스루(오버월드 신규생성 동일). aquifer/표층/carver 제외는 오라클도 끔(aquifersEnabled/oreVeinsEnabled=false) → 모호함 0.
- **미해결:** 오라클 mojang-mapped jar 경로 해시는 머신별 — T3 Step2 ls로 확정. gson 의존 furnace-server에 있는지 T10서 확인(없으면 추가). `ChunkColumn.getBlock/setBlock`·`BlockRegistry.defaultStateId`는 기존(M2/M4). `NoiseChunk` thread-local 재사용은 정확성 통과 후 최적화 — T13은 청크당 생성으로 시작 가능.
```
