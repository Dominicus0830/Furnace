# 2026-08-16 worldgen — 정답지 감사 체계 도입 + carver RNG 스트림 분리 누락 수정

## 작업 요약

순정 vanilla 서버 `.mca`를 독립 정답지로 도입해 oracle을 처음으로 감사했고, 즉시 Furnace·oracle이 **공유하던** carver 버그를 검출·수정했다. 실서버 대비 mismatch 1730 → 73 (−96%), (0,0)은 byte-exact 0 달성. 커밋 `7919644`·`85c7857`.

## 문제 / 목적

fixture 372개가 **전부 oracle 산출물**이었다. oracle이 옳은지 검증할 독립 정답지가 프로젝트에 존재한 적이 없었고, 그래서 알려진 oracle 버그 5건이 모두 "Furnace 쪽을 며칠 파다가" 뒤늦게 발견됐다.

구조적 결함이 하나 더 있었다. oracle은 Paper 기반이라 Bukkit 이벤트를 벗겨내려고 vanilla 로직을 손으로 미러링했다 — `createStructuresNoBukkitEvent`, `placeVanillaFeatures` 등. 즉 **정답지가 검증 대상과 같은 실패 모드를 공유**한다.

## 해결 방법 / 구현 내용

### 1. 실서버 감사관 도입

순정 1.21.1 서버로 월드를 생성해 `.mca` 블록 배열을 oracle featurespill fixture와 대조하는 테스트를 추가했다.

타당성 근거 — `ChunkStatusTasks`상 FEATURES 이후는 블록 배열을 바꾸지 않는다. `initializeLight`/`light`는 광원만, `generateSpawn`은 엔티티만, `full`은 `LevelChunk` 래핑만. 따라서 FULL 상태 `.mca`를 FEATURES fixture와 비교해도 된다.

### 2. 검출된 버그 — carver RNG 스트림 분리 누락

vanilla `NoiseBasedChunkGenerator.applyCarvers`는 carver 인덱스마다 시드를 분리한다.

```java
int i3 = 0;
for (Holder<ConfiguredWorldCarver<?>> holder : carvers) {
    worldgenRandom.setLargeFeatureSeed(seed + i3, chunkPos.x, chunkPos.z);
    ...
    i3++;
}
```

oracle(`OracleMain:1066,1941`)·Furnace(`NoiseWorldSource:364`) 둘 다 `+ i3`가 없었다. overworld biome 65개 중 54개가 `[cave, cave_extra_underground, canyon]`이므로 인덱스 0만 우연히 맞고 **1·2는 통째로 잘못된 위치에 생성**됐다.

양쪽이 같은 버그를 가져서 기존 parity 테스트는 전부 green이었다 — "서로 합의한 잘못된 답".

### 3. 측정 조건이 결과를 지배한다

같은 코드인데 측정 조건만으로 515 → 73으로 바뀌었다.

| 조건 누락 | 오탐 |
|---|---|
| `generate-structures=false` 없음 | mineshaft 팔레트(`oak_planks`/`rail`/`cobweb`) |
| `/tick freeze` 없음 | 유체틱 전파(`water;level=1~8`), `bubble_column;drag=true` |

`gamerule randomTickSpeed 0`은 **랜덤틱만** 막고 스케줄틱·유체틱은 못 막는다. 근거는 `ServerLevel.tick` — `blockTicks`/`fluidTicks`는 `runsNormally` 가드 안이고, `getChunkSource().tick`은 가드 밖이라 **동결 상태에서도 청크 생성은 계속된다**.

## 핵심 코드

```java
// NoiseWorldSource.applyCarvers — carver 인덱스만큼 시드 오프셋
for (int originX = chunkX - 8; originX <= chunkX + 8; originX++) {
    for (int originZ = chunkZ - 8; originZ <= chunkZ + 8; originZ++) {
        for (OverworldCarvers.Entry entry : overworldCarvers.entries()) {
            worldgenRandom.setLargeFeatureSeed(this.seed + carverIndex, originX, originZ);
```

감사 월드 생성 절차 (드라이버가 서버 stdin으로 주입).

```
tick freeze
gamerule randomTickSpeed 0
gamerule doMobSpawning false
gamerule doFireTick false
gamerule doWeatherCycle false
forceload add <x1> <z1> <x2> <z2>     # 대상 ±4청크, 1회 최대 256청크 제한
save-all flush
stop
```

감사 실행.

```powershell
./gradlew :furnace-server:cleanTest :furnace-server:test --tests "*OracleGroundtruthAuditTest*" "-Dfurnace.oracle.audit.worldDir=<world경로>"
```

## 결과

| 좌표 | 수정 전 | 수정 후 |
|---|---|---|
| (0,0) | 221 | **0** |
| (7,-2) | 536 | 61 |
| (-5,3) | 973 | 12 |
| 합계 | 1,730 | **73** (−96%) |

잔여 73건은 별개 원인 — ore blob 계열(`diorite→granite` 54 등)과 deepslate/stone 전이대 판정(`lapis_ore→deepslate_lapis_ore` 3).

## 관련 파일

- [[2026-07-10_worldgen_parity-verification-infra-테스트3종]] — 검증 인프라 선행 작업
- [[2026-07-10_io_anvil-palette-properties-왕복]] — `.mca` 읽기에 쓰는 codec
- [[2026-07-02_worldgen-oracle_태그미바인딩-3x3쓰기-fixture오염]] — 과거 oracle 버그 사례
- [[레이어-월드젠]]

## 태그

#Furnace #worldgen #BugFix #TestInfra
