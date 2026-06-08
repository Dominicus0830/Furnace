# M3d — 엔티티 가시 거리 분리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 엔티티 가시 거리를 청크 렌더 거리(`view-distance`)에서 분리 — 새 설정 `entity-view-distance`(청크, 기본 8)가 가시 계산 range와 교차region 스냅샷 스캔 반경을 결정.

**Architecture:** `Config`에 `entityViewDistance` 추가 → `World`가 보유, `effectiveEntityChunks() = min(entityViewDistance, viewDistance)`로 클램프. `Region.updateVisibility` range와 `World.neighborRadius()`가 이 유효값을 사용. 청크 로드(`updateChunks`)는 `view-distance` 유지 — 두 거리 독립.

**Tech Stack:** Java 21, JUnit 5. M3c(`World`/`Region`/`EntityView`) 위에 쌓음. 브랜치 `m3c-region` 연속.

**spec:** `docs/superpowers/specs/2026-06-08-m3d-entity-view-distance-design.md`

**M3c와의 연결:** M3c `neighborRadius = ceil(viewDistance/regionSize)+1`, `range = viewDistance*16`. M3d는 둘 다 `effectiveEntityChunks` 기준으로 교체.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `config/Config.java` | (modify) record에 `entityViewDistance` 필드 + DEFAULT 8 |
| `config/ConfigLoader.java` | (modify) `entity-view-distance` 키 파싱 |
| `world/World.java` | (modify) 생성자 +1 인자, `entityViewDistance` 필드/게터, `effectiveEntityChunks()`, `neighborRadius()` 교체 |
| `world/Region.java` | (modify) `updateVisibility` range를 `effectiveEntityChunks()*16`으로 |
| `FurnaceMain.java` | (modify) World 생성에 `config.entityViewDistance()` 배선 |

**테스트 호출부 갱신(World 생성자 +1 인자):** `NetworkServerTest`, `StatusPingTest`, `RegionTickTest`, `SnapshotRegistryTest`, `CrossRegionVisibilityTest`.

---

## Task 1: Config + ConfigLoader — entity-view-distance 설정

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/Config.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/config/ConfigLoader.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/config/ConfigLoaderTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`ConfigLoaderTest.java` — `load_returnsDefaults_whenFileMissing`의 `regionSize` 단언 아래에 추가:
```java
        assertEquals(8, c.entityViewDistance());
```
`load_overridesFromFile`의 props 문자열에 `entity-view-distance=12` 추가 + 단언 추가. props 문자열 교체:
```java
        Files.writeString(f, "server-name=My Server\ntick-nanos=25000000\nport=25500\ncompression-threshold=-1\nview-distance=8\nentity-view-distance=12\nregion-size=8\nregion-threads=2\n");
```
`assertEquals(8, c.viewDistance());` 아래에 추가:
```java
        assertEquals(12, c.entityViewDistance());
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: 컴파일 실패 — `entityViewDistance()` 메서드 없음.

- [ ] **Step 3: `Config.java` 수정**

record 시그니처에 `viewDistance` 다음으로 `entityViewDistance` 삽입 + DEFAULT 갱신:
```java
// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos, int port, int compressionThreshold,
                     int viewDistance, int entityViewDistance, int regionSize, int regionThreads) {

    public static final Config DEFAULT =
            new Config("Furnace Server", 50_000_000L, 25565, 256, 5, 8, 16,
                    Runtime.getRuntime().availableProcessors());
}
```

- [ ] **Step 4: `ConfigLoader.java` 수정**

`viewDistance` 파싱 아래에 추가:
```java
        int entityViewDistance = Integer.parseInt(
                props.getProperty("entity-view-distance",
                        Integer.toString(Config.DEFAULT.entityViewDistance())));
```
`return` 문에 인자 삽입(viewDistance 다음):
```java
        return new Config(name, tick, port, compression, viewDistance, entityViewDistance, regionSize, regionThreads);
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(config): add entity-view-distance setting" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: World effectiveEntityChunks + neighborRadius + Region range

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/SnapshotRegistryTest.java`
- Test(호출부 갱신): `NetworkServerTest`, `StatusPingTest`, `RegionTickTest`, `CrossRegionVisibilityTest`

생성자 인자 추가가 main(FurnaceMain) + 5개 테스트 호출부를 깨므로 한 Task에서 원자적으로 갱신한다.

- [ ] **Step 1: SnapshotRegistryTest 갱신(실패 테스트)**

`SnapshotRegistryTest.java` 헬퍼에 `entityViewDistance` 인자 추가:
```java
    private static World world(FurnaceSchedulerImpl sched, int regionSize, int viewDistance, int entityViewDistance) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), regionSize, viewDistance, entityViewDistance, reg.directBits(), 7);
    }
```
`candidatesAround_collectsNeighbors`의 `world(sched, 16, 5)` → `world(sched, 16, 5, 5)`.
`neighborRadius_coversViewDistance` 메서드 전체를 두 테스트로 교체:
```java
    @Test
    void neighborRadius_basedOnEntityDistance() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = world(sched, 4, 20, 16); // entity 16, regionSize 4 → ceil(16/4)+1 = 5 (vd 20 무관)
        assertEquals(5, w.neighborRadius());
        sched.close();
    }

    @Test
    void neighborRadius_clampsEntityToViewDistance() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = world(sched, 4, 5, 20); // entity 20 > vd 5 → 유효 5 → ceil(5/4)+1 = 3
        assertEquals(3, w.neighborRadius());
        sched.close();
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*SnapshotRegistryTest"`
Expected: 컴파일 실패 — World 생성자 9-인자 없음.

- [ ] **Step 3: `World.java` 수정**

필드 추가(`viewDistance` 아래):
```java
    private final int entityViewDistance;
```
생성자 시그니처에 `viewDistance` 다음 `entityViewDistance` 삽입 + 대입:
```java
    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits) {
```
대입 추가(`this.viewDistance = viewDistance;` 아래):
```java
        this.entityViewDistance = entityViewDistance;
```
`neighborRadius()` 메서드 교체:
```java
    /** 엔티티 가시 거리(view-distance로 클램프)가 닿는 인접 region 반경(region 단위). */
    public int neighborRadius() {
        return (int) Math.ceil((double) effectiveEntityChunks() / regionSize) + 1;
    }

    /** 실제 엔티티 가시 거리(청크). view-distance를 넘으면 미렌더 청크 방지 위해 클램프. */
    public int effectiveEntityChunks() {
        return Math.min(entityViewDistance, viewDistance);
    }
```
게터 추가(`viewDistance()` 아래):
```java
    public int entityViewDistance() { return entityViewDistance; }
```

- [ ] **Step 4: `Region.java` 수정**

`updateVisibility`의 range 계산 교체:
```java
        double range = world.effectiveEntityChunks() * 16.0;
```
(기존 `double range = world.viewDistance() * 16.0;` 제거.)

- [ ] **Step 5: `FurnaceMain.java` 배선**

World 생성 인자에 `config.viewDistance()` 다음 `config.entityViewDistance()` 삽입:
```java
        World world = new World(new SuperflatWorldSource(blockRegistry, plainsBiome), chunkPool,
                scheduler, config.regionThreads(), config.regionSize(), config.viewDistance(),
                config.entityViewDistance(), blockRegistry.directBits(), biomeDirectBits);
```

- [ ] **Step 6: 나머지 테스트 호출부 +1 인자**

`viewDistance` 다음에 `entityViewDistance` 값 삽입(각 파일):

`NetworkServerTest.java:22` — `..., 16, 2, reg.directBits(), 7)` → `..., 16, 2, 2, reg.directBits(), 7)`
`StatusPingTest.java:27` — `..., 16, 2, reg.directBits(), 7)` → `..., 16, 2, 2, reg.directBits(), 7)`
`RegionTickTest.java:19` — `..., 16, 1, reg.directBits(), 7)` → `..., 16, 1, 1, reg.directBits(), 7)`
`CrossRegionVisibilityTest.java:21` — `..., 1, 5, reg.directBits(), 7)` → `..., 1, 5, 5, reg.directBits(), 7)`
(인자 순서: `regionThreadCount(), regionSize, viewDistance, entityViewDistance, directBits(), 7`)

- [ ] **Step 7: 표적 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*SnapshotRegistryTest" --tests "*CrossRegionVisibilityTest" --tests "*RegionTickTest"`
Expected: PASS. (CrossRegionVisibility: entity=5 → range 80블록, B at 16 → 여전히 보임.)

- [ ] **Step 8: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: 커밋**

```bash
git add furnace-server
git commit -m "feat(world): decouple entity view distance from view-distance" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 수동 검증 — 엔티티 가시 거리 + M3c 교차region (통합 verify)

M3d가 M3c의 "200블록서 보임" 문제를 해소하므로 M3c·M3d verify를 한 번에 한다.

- [ ] **Step 1: 설정** — `furnace-server/furnace.properties`:
```
view-distance=20
entity-view-distance=8
region-size=4
region-threads=4
```

- [ ] **Step 2: 서버 기동 + 2클라**

Run: `./gradlew :furnace-server:run`
두 클라 접속.

- [ ] **Step 3: 가시 거리 분리 확인**

두 플레이어를 **128블록(=8청크) 안**에 두면 서로 보임. **128블록 밖~320블록(옛 view 기준 가시범위)**에 두면 **안 보임**(M3d 전엔 320블록까지 보였음). 청크는 view-distance 20만큼 계속 렌더되는지 확인(엔티티만 사라지고 지형은 남음).

- [ ] **Step 4: 교차region 가시성(M3c)**

두 플레이어를 **다른 region(>64블록, region-size 4=64블록)이지만 128블록 안** → 서로 보임.

- [ ] **Step 5: 경계 왕복**

한 명이 region 경계를 왔다갔다 → 상대 화면서 **깜빡임 없이** 계속 보임.

- [ ] **Step 6: 멀어지기/퇴장**

엔티티 가시(128블록) 밖으로 멀어지면 despawn. 퇴장 시 사라짐.

- [ ] **문제 해결 가이드**
  - 128블록 밖에서도 보임 → `effectiveEntityChunks`/`updateVisibility` range 확인. entity-view-distance 로드됐는지(ConfigLoader) 확인.
  - 지형도 128블록서 잘림 → `updateChunks`가 잘못 `effectiveEntityChunks` 씀. `updateChunks`는 `viewDistance()` 유지여야 함.
  - 다른 region 플레이어 안 보임 → `neighborRadius` 부족. entity 거리/regionSize 대비 반경 확인.

- [ ] **Step 7: 완료 커밋(수정 있었다면)**

```bash
git add furnace-server
git commit -m "fix(world): M3d entity view distance from manual verify" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 메모

- **spec 커버리지:** §5 Config/ConfigLoader=T1, World/Region=T2, FurnaceMain=T2 Step5. §3 클램프=T2(effectiveEntityChunks+clamp 테스트). §4 동작(range/neighborRadius)=T2. §7 테스트 전부 T1/T2. §8 향후=문서만(코드 없음, 정상).
- **타입 일관성:** `Config.entityViewDistance()`(T1) · `World.entityViewDistance()`/`effectiveEntityChunks()`/`neighborRadius()`(T2) · `World` 9-인자 생성자(T2서 main+5 테스트 호출부 전부 갱신) · `Region.updateVisibility`가 `effectiveEntityChunks()` 사용(T2).
- **생성자 리플:** World 9-인자화 → FurnaceMain + 5 테스트 한 Task서 원자 갱신(컴파일 단위). 누락 시 `./gradlew build` 실패로 즉시 검출.
- **회귀:** `updateChunks`는 `viewDistance()` 유지(청크 렌더 무변경). CrossRegionVisibilityTest entity=5로 B 가시 유지. RegionTickTest entity=1(range 16블록) — 같은 셀 내 가시 무관, tick 경유라 통과.
- **위험:** entity-view-distance > view-distance 오설정 → 클램프로 안전. 0 설정 → range 0, 아무도 안 보임(의도된 동작, 검증 불필요).
