# 2026-07-10 worldgen — parity 검증 인프라 테스트 3종 (육안 검증 대체)

## 작업 요약

worldgen 검증 구조 갭 3개(광역 스윕·NoopFeature 조용한 skip·저장 왕복)를 gradle 테스트로 봉인 — fixture green인데 실서버 육안에서 birch 나무·연못·버섯 부재가 발견되던 상황 제거.

## 문제 / 목적

기존 parity 테스트는 청크 ~10개만 검증. fixture에 없는 좌표의 회귀는 사용자 육안(실서버 접속)이 먼저 발견했다. 갭 3종:
1. 광역 커버리지 없음 — 수백 청크 자동 대조 수단 부재
2. 미구현 feature가 NoopFeature로 조용히 폴백 — 새 미구현 타입이 생겨도 아무 테스트도 안 깨짐
3. 생성은 맞는데 저장(.mca)에서 깨지는 회귀를 잡는 게이트 없음

## 해결 방법 / 구현 내용

전부 소넷 워커 구현, Fable 검증 (task: parity-verification-infra).

1. **ChunkSweepParityTest** (opt-in, `-Dfurnace.sweep=true`): seed·중심·반경 `-D` 파라미터로 N×N 청크를 oracle featurefull fixture와 일괄 대조. 텍스트 히트맵 + 상위 mismatch 샘플 출력. seed 51532 반경 8(289청크) 실측치를 `SWEEP_BASELINE_51532_0_0` 상한으로 고정 — baseline은 정답(0)이 아니라 회귀 감지선 (featurefull은 center-only decorate라 spill 잔차 항상 포함, 총 516471 mismatch가 정상 상태).
2. **NoopFeatureAuditTest**: configured_feature JSON 전수 스캔 → `parseConfigured` 폴백 타입이 허용목록(`KNOWN_UNIMPLEMENTED_TYPES`) 밖이면 fail. 프로덕션엔 `BiomeFeatureData.HANDLED_CONFIGURED_FEATURE_TYPES` 계측 상수만 추가(if-chain과 수동 동기화 필요). 신규 미구현 2종 발견: `minecraft:vines`, `minecraft:void_start_platform` (survey 17종 목록에 없었음).
3. **ChunkPipelinePersistenceParityTest**: `ChunkPipeline.generateFull` → `AnvilChunkCodec.toNbt/fromNbt` 왕복 후 블록을 oracle featurespill fixture와 대조. **부산물로 왕복 air 소실 버그 최초 발견** — (0,0) 22개, (-5,3) 31개, (7,-2) 73개 블록이 왕복 후 air로 소실, 원인 미상(y분포에 섹션 경계 규칙 없음). 테스트는 실측 상한만 고정. 후속 디버깅 = codex 권고.

커밋: `9876a71`(감사) · `da59dd6`(스윕+fixture 289개) · `7aef8cc`(영속 게이트). full suite green 10m 15s.

## 핵심 코드

실행법 (PowerShell은 `-D` 인자 따옴표 필수 — 미인용 시 `=` 파싱 깨져 build fail):

```powershell
# 스윕 (opt-in — 기본 suite에서는 skip)
./gradlew :furnace-server:cleanTest :furnace-server:test --tests "*ChunkSweepParityTest*" "-Dfurnace.sweep=true"
# 다른 파라미터: "-Dfurnace.sweep.seed=<seed>" "-Dfurnace.sweep.cx=<cx>" "-Dfurnace.sweep.cz=<cz>" "-Dfurnace.sweep.radius=<r>"
# (기본 파라미터가 아니면 baseline 비교 없이 리포트만)

# fixture 신규 생성 시 oracle 배치 선행 (289개 기준 4~5분)
./gradlew :worldgen-oracle:run --args="51532 featurefull:-8:-8 ... featurefull:8:8"

# 감사·영속 게이트 (기본 suite 포함)
./gradlew :furnace-server:test --tests "*NoopFeatureAuditTest*"
./gradlew :furnace-server:test --tests "*ChunkPipelinePersistenceParityTest*"
```

`-Dfurnace.*` 전달은 `furnace-server/build.gradle.kts:35`가 test JVM으로 포워딩:

```kotlin
if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
```

## 관련 파일

- [[2026-07-05_worldgen_milestone-gap-survey]] — 미구현 17종 survey (이번에 +2종)
- [[2026-07-06_worldgen_tree-overlap-residual-parity]] — 직전 parity 작업
- [[2026-07-02_worldgen-feature_featurefull-0mismatch]] — featurefull fixture 의미(center-only decorate)
- [[레이어-월드젠]]

## 태그

#Furnace #worldgen #Feature #TestInfra
