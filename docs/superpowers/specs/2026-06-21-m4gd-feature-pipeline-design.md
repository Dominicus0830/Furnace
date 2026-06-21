# M4g-d 다중청크 Feature 파이프라인 아키텍처 설계

**부모:** `2026-06-06-furnace-multithreaded-mc-server-design.md` §6 M4g-d (line 149·152)
**선행:** `2026-06-20-m4gd-feature-placement-design.md` (single-chunk decoration — 부분 구현됨)
**작성:** 2026-06-21 (멀티에이전트 claude-main 설계 → Orchestrator 검토·저장)

---

## 1. 목표

같은 시드 → 바닐라 1.21.1 오버월드와 **비트일치 월드**. 이 마일스톤은 "feature 알고리즘"이 아니라
**feature가 도는 인프라(staged chunk pipeline + cross-chunk write + thread-safety)**를 byte-exact로
구축한다. 나무·lake·geode·구조물(M4g-e)이 이웃 청크에 흘러드는 블록까지 정확히 재현하는 것이 수락 조건.

비목표: 개별 ConfiguredFeature 알고리즘 신규 구현(별도). 조명(M5). 구조물 jigsaw(M4g-e, 훅 자리만).

## 2. 현황 (코드 실측)

| 컴포넌트 | 현재 | 이 마일스톤서 |
|---|---|---|
| `NoiseWorldSource.generateWithFeatures` | `generate→carvers→decorate(center만)` 단일청크 | status 파이프라인 진입점으로 재구성 |
| `FeatureWorldView` | **center 청크 전용**. setBlock이 inCenterChunk 가드로 이웃 쓰기 무음 드롭 | center→3×3 span 확장 |
| `FeaturePlacer.decorate` | step·setDecorationSeed/setFeatureSeed·identity index — **이미 byte-exact 골격** | 그대로. view만 multi-chunk |
| status 개념 | 없음. 모놀리식 풀청크 | 도입(§3.1) |
| oracle | feature fixture 미구현 | WorldGenRegion 기반 실좌표 덤프 도입(§3.4) |

**핵심 차이:** 2026-06-20 spec은 multi-chunk FeatureWorldView를 *설계*했으나 **실제 코드는 center 전용**으로
들어감(이웃 쓰기 미구현). 현재 decoration은 "이웃 feature가 X로 흘러듦"도, "X feature가 이웃으로 나감"도
빠진 **근사**. 이를 정밀화하는 게 본 마일스톤.

## 3. 아키텍처

### 3.0 바닐라 권위 (ChunkPyramid.GENERATION_PYRAMID 실측)

```
status      직접의존(radius)         writeRadius
NOISE       BIOMES(1)                0
SURFACE     BIOMES(1)                0
CARVERS     STRUCTURE_STARTS(8)      0
FEATURES    CARVERS(1)               1   ← 핵심
```

- FEATURES는 CARVERS를 radius 1 요구 → 청크 X features 하려면 **3×3 이웃 모두 ≥CARVERS**.
- FEATURES writeRadius=1 → X feature는 **3×3 이웃에 기록 가능**(WorldGenRegion.ensureCanWrite: section Chebyshev ≤1). radius 밖 무음 false(바닐라도 드롭).
- 청크 X 최종 = **X 자신 feature + 8 이웃 각각이 X로 흘려보낸 것**. X 확정엔 9청크 feature 패스 완료 필요.
- 결정성: feature 시드 = `setDecorationSeed(seed, cx*16, cz*16)` → `setFeatureSeed(decorSeed, idx, step)` = **순수 위치 함수**(청크 독립·순서무관). 좌표 충돌은 "마지막 쓰기 승", 바닐라 청크 처리순서 결정적이라 결과 결정적. **byte-exact 검증은 oracle(동일 WorldGenRegion 경로)과 맞춤** — 바닐라 순서를 우리가 추측하지 않음.

### 3.1 청크 status 파이프라인

ChunkColumn에 status 필드 직접 X(런타임 chunk와 생성중 proto 혼재 방지). 대신:

```java
// world/gen/pipeline/GenStatus.java
enum GenStatus { EMPTY, BIOMES, NOISE, SURFACE, CARVERS, FEATURES, FULL }  // light는 M5

// world/gen/pipeline/ProtoColumn.java — 생성 중 청크 래퍼
final class ProtoColumn {
    final int cx, cz;
    volatile GenStatus status;   // 소유 스레드만 승격
    ChunkColumn col;             // 블록/바이옴 본체(기존 타입 재사용)
    int[] biomeIds;              // collectNeighborBiomes 재사용
    NoiseChunk noiseCache;       // surface/feature 공유(재생성 제거)
}
```

| status | 작업 | 의존(radius) |
|---|---|---|
| BIOMES | biomeSampler.sampleChunk | self |
| NOISE | nc.generateBlockIds+loadGenerated | BIOMES(0) |
| SURFACE | surfaceSystem.buildSurface | NOISE(0) |
| CARVERS | applyCarvers | SURFACE(0) |
| FEATURES | featurePlacer.decorate | **CARVERS(1)** |
| FULL | compact+heightmap | FEATURES(0) |

**오케스트레이터** `ChunkPipeline`: "X를 FULL로" 요청 시 ① X+3×3 이웃을 ≥CARVERS로(self-dep 병렬) ② X에
FEATURES(3×3 CARVERS view) ③ X FULL은 X+8이웃 FEATURES가 모두 X에 반영돼야(§3.3 confluence).

### 3.2 다중청크 decoration 모델 (writeRadius=1, 3×3)

```java
final class FeatureWorldView {
    private final ProtoColumn[] grid;  // [9], idx=(dz+1)*3+(dx+1); center=4
    private final int cx, cz, writeRadius = 1;
    public void setBlock(int wx,int wy,int wz,int state){
        int nx=Math.floorDiv(wx,16), nz=Math.floorDiv(wz,16);
        if(Math.abs(nx-cx)>writeRadius||Math.abs(nz-cz)>writeRadius) return; // 바닐라 드롭과 동일
        grid[(nz-cz+1)*3+(nx-cx+1)].col.setBlock(wx-nx*16,wy,wz-nz*16,state);
    }
    // getBlock/getSurfaceY/getBiomeId도 3×3 span (이웃은 ≥CARVERS 상태 지형만 조회)
}
```

불변식: 청크 X 최종 = Σ(9청크가 X에 쓴 setBlock). getBlock이 읽는 이웃은 ≥CARVERS(feature-단계 블록 미참조 →
순서무관 병렬 가능, 바닐라 불변식). **FeaturePlacer.decorate 무변경** — view만 multi-chunk 주입.

### 3.3 region-thread 안전성 (최대 난제)

방침: **generation은 region 틱 스레드 밖, 전용 gen 도메인.** 생성 중 ProtoColumn은 region 편입 *전* → region
소유권 규약 밖(핵심 탈출구). ChunkPipeline은 genPool 위 동작. 한 FULL 요청의 9-grid는 단일 gen 태스크 소유
→ 그 안에선 lock 불필요. 9청크 CARVERS까지는 self-dep → fan-out(allOf).

**confluence 전략 (P3 결정):**
- **(A) 9-decorate per target (권장 1차, lock-free·정확):** X를 FULL로 만들 때 X+8이웃 각각 decorate를
  X-view에 누적(각 decorate는 자기 origin 시드, write는 X로 클램프). 결정적·lock-free(FULL 태스크가 9-grid 소유). 비용 ~9× 중복.
- **(B) 공유 ProtoColumn + 마샬링(최적화):** ProtoColumn LRU 캐시, 이웃 쓰기를 소유 gen-슬롯 큐로 마샬. 중복 0이나 복잡 → P6+.

region 편입: ProtoColumn FULL → ChunkColumn만 떼어 Region.chunks에 commit(소유 스레드 execute 마샬).
`Region.loadChunk`의 genPool 호출을 `ChunkPipeline.generateFull`로 교체, `thenApplyAsync(region executor)` 편입.
생성은 region 스레드 안 건드림 → assertOwns 위반 0, cross-region 락 불필요(방침 A).

### 3.4 byte-exact 검증 (oracle = ground truth)

손 stub 금지. 실제 `WorldGenRegion`(9-chunk StaticCache2D) 기반 `applyBiomeDecoration` → 실좌표 덤프.

```
worldgen-oracle: arg "featurefull:cx:cz"
  9청크 CARVERS까지 ProtoChunk → WorldGenRegion(writeRadius 1) features → center 최종 블록(이웃 흘러듦 포함) 덤프
  포맷: magic "FEAT", seed i64, cx i32, cz i32, blocks[384*16*16] uint16
```

**oracle 자가검증 선행(필수):** fixture 박제 전, 동일 seed 바닐라 .mca 청크 1개 vs oracle 덤프 대조.
**parity:** `FeaturePipelineParityTest` — generateFull center 블록 vs oracle. **나무가 청크경계 넘는 fixture 1개 필수.**

### 3.5 멀티스레드 최적화 (설계서 line 152)

② staged+의존성 비동기(C2ME): ChunkPipeline 9-grid status별 CompletableFuture 합성, CARVERS까지 self-dep 병렬.
⑦ 위치기반 시드 순서무관. ⑥ NoiseChunk/biomeIds ProtoColumn 캐시(generateWithFeatures의 NoiseChunk 4회 재생성 제거).
④ 9-grid 공유 biome quart 캐시. **최적화는 byte-exact 안 깨는 선에서. 방침 A 중복비용은 측정 후 B 전환(P6+).**

## 4. 단계별 구현 플랜 (codex 구현 단위)

| Phase | 목표 | 검증 |
|---|---|---|
| **P1** status 파이프라인 골격 | GenStatus·ProtoColumn·ChunkPipeline.generateFull (현 generateWithFeatures를 status 단계로 분해, **center-only 유지**) | 기존 parity test 전부 green. generateFull==기존 출력 동일(무변경 증명) |
| **P2** multi-chunk FeatureWorldView | 9-grid span. setBlock writeRadius=1, get* 3×3 | 단위: 경계밖 write 드롭, 이웃 write 기록됨 |
| **P3** thread-safe cross-chunk | 9-grid CARVERS까지 genPool 병렬 + **방침 A** confluence. Region.loadChunk 배선 | 멀티스레드 스트레스: assertOwns 위반 0, race 0, 결정성(2회 byte 동일) |
| **P4** oracle 확장 | featurefull:cx:cz(실 WorldGenRegion). **oracle 자가검증 vs 바닐라 .mca** | oracle==.mca(경계 좌표 포함) |
| **P5** parity 봉인 | FeaturePipelineParityTest 3 fixture(경계넘는 feature 포함) 0 mismatch | 3 fixture 0 mismatch |
| **P6** M4g-e 훅 + 최적화 | FEATURES 앞 STRUCTURE_STARTS status 자리 예약(no-op). 병목 측정 후 방침 B 판단 | 구조물 status가 기존 parity 안 깸. 벤치 |

P1은 순수 리팩터(동작 무변경) → 안전 진입점. 각 phase 독립 커밋.

## 5. 핵심 인터페이스 (구현은 codex)

```java
// world/gen/pipeline/ChunkPipeline.java
public final class ChunkPipeline {
    public CompletableFuture<ChunkColumn> generateFull(int cx, int cz);
    private CompletableFuture<ProtoColumn> toCarvers(int cx, int cz); // self-dep 병렬
}
```
NoiseWorldSource에 status 진입점 노출(private 승격): bakeNoiseSurface/applyCarvers/decorateInto.
FeaturePlacer/PlacedFeature/ConfiguredFeature **시그니처 무변경**.

## 6. 리스크 · 미해결

- **(R1 高) 방침 A 중복비용** ~9×. → P6 측정 후 B. 정확성은 A 안전.
- **(R2 高) oracle 충실도** WorldGenRegion 9-chunk 셋업. P4 자가검증 게이트 필수. Paper internal(WorldGenRegion/StaticCache2D) 테스트 클래스패스 가용성 P4전 spike.
- **(R3 中) 좌표 충돌 결정성** "마지막 승". 방침 A decorate 순서를 oracle과 일치 → **oracle 구현이 순서 정의**.
- **(R4 中) NoiseChunk 캐시 정확성** 재생성=비트동일 보장 P1 검증.
- **(미해결) light status** M5. FEATURES→FULL 직결, enum 자리만 예약.

## 7. 가정
- NoiseChunk 순수(재생성=동일). 캐싱 안전 — P1 실측 전제.
- FeaturePlacer/시딩/identity-index byte-exact 검증됨 → view만 교체.
- 생성은 genPool 단일 도메인 → cross-region 락 회피(생성중 미편입).
- writeRadius 1(3×3) 충분(바닐라 FEATURES 고정). M4g-e 구조물(STRUCTURE_STARTS radius 8)은 status 자리만 예약.
