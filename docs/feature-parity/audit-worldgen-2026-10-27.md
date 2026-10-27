# 월드젠 최적화 감사 재검증 (2026-10-27)

대상: 외부 감사 보고서 "Furnace 최적화 감사 — 최종 통합 보고서"(옛 커밋 b138cd7 기준)의 월드젠 항목을 HEAD(`838a8fb4`)에서 하나씩 다시 확인했다.
줄 번호는 HEAD 기준이다. 상태: **해결됨**(HEAD 에 이미 있음/고쳐짐) · **아직 있음** · **해당없음**.

## 결과표

| 항목 | 상태 | 근거(파일:줄) | 비고 |
|---|---|---|---|
| I-1 (W-NEW-1) pristine 캐시가 공유하는 StructureStart/조각 가변 상태 | **해결됨** (30611653) | `gen/pipeline/ChunkPipeline.java:122` pristine 은 `starts` 를 `List.of()` 로 둔다 · `:154-170` 링 시작점은 요청마다 `structureStartsFresh` · `gen/NoiseWorldSource.java:608-646` 공유 캐시 `sharedStarts` 는 빈 결과와 `JigsawPiece` 만으로 된 결과만 넣는다(`shareable`) · `gen/structure/JigsawPiece.java:39-48` 조각 `move` 는 생성 중(`JigsawStructure.java:114`)에만, `postProcess` 는 상태를 바꾸지 않음 | 회귀 테스트 `StructurePieceStateIsolationTest` 추가: seed 1234·12345 의 사막 신전 시작 청크마다 이웃 선생성 5종 순서 → 시작 청크 블록 해시·상자 개수를 새 생성기 결과와 대조. HEAD 통과. **음성 대조**: `shareable` 을 항상 true(사막 신전 시작점 공유)로 바꾸면 `seed 1234 청크 -154,402 상자 4→0` 으로 실패 — 보고서 재현(4→0)과 같은 증상을 테스트가 잡는다. 서버 경로(`generateShared`)는 시작점을 월드당 한 번만 장식하는 설계(`decoStarts`, 단일 레인)라 해당없음 |
| W-01 collectNeighborBiomes 81회 | 해결됨(기처리) | `ChunkPipeline.java:212-218` grid `biomeNames` 합집합 | `decorateCenter`(`NoiseWorldSource.java:818-826`)는 아직 `collectNeighborBiomes` 를 쓰나 대조 도구 경로 |
| W-03 aquifer 재사용 | 해결됨(기처리) | `NoiseWorldSource.java:436-437` `Generated.aquifer` | |
| W-04 높이 캐시 락 밖 | 앞부분 해결됨(기처리) | — | 바이옴 사전 필터는 이번에 보지 않음 |
| W-05 / W-07 / W-12 | 해결됨(기처리) | — | |
| W-02 표면 `sampleQuart` depth-그룹 환원 | **아직 있음(미적용)** | `gen/BiomeSampler.java:172-188` 쿼트마다 기후 6개 직접 평가 + R-tree · `gen/surface/SurfaceSystem.java:668-690` 청크 안 쿼트는 `chunkBiomeIds`(sampleChunk 결과) 표, 밖은 HashMap 캐시 후 `sampleQuart` | 그룹 환원은 없다. 청크 안 쿼트에 sampleChunk 표를 쓰는 것은 보고서 A-04(a)("fillTarget 보간 때문에 비트 동일 보장 없음"으로 기각)와 같은 형태지만, 바닐라 표면 단계도 BiomeManager 로 청크에 저장된 바이옴(fillBiomesFromNoise 결과)을 읽는다고 **가정**(바닐라 소스로 미확인). 이웃 청크 쿼트를 직접 샘플하는 부분이 바닐라와 같은지는 별도 확인 과제. 정적 Y-불변 게이트가 필요해 이번엔 적용 안 함 |
| W-09 기후 Y-불변 게이트 → j=0 1회 평가 | **아직 있음(미적용)** | `gen/NoiseChunk.java:160-180` `buildClimateCache` 가 6차원×5×49×5 전부 평가 · `:208-217` `isClimateColumnYInvariant` 는 정의만 있고 호출처 없음(사후 값 비교라 정적 게이트도 아님) | DF 트리 정적 분석 게이트가 선행 조건. 보고서 추정 이득도 W-01·W-03 후 요청당 한 자릿수 % 라 이번엔 적용 안 함 |
| W-10 aquifer "항상 air" 판정 | **해결됨(블록 단위 형태)** (de6e8a86) | `gen/NoiseBasedAquifer.java:141-147` `AIR_SHORTCUT`: y ≥ 후보 12칸 최대 수위 + 5 이면 공기 · `:283-310` `maxFluidLevelAround` 묶음 캐시 | 셀 분류기(RS-2)가 아니라 블록 단위지만 판정식은 같다(마진 5 유지). 증명: 최근접 상태 `at(y)` 는 공기, 모든 쌍 압력 `d11 = (max−y−0.5)/2.5 < −2` 라 장벽 노이즈 없이 음수 → NO_CARVE 불가. 기존엔 fuzz 테스트가 없었다 → **`AquiferAirShortcutFuzzTest` 추가**: 무작위 seed 10개 × 무작위 청크 100개(1000청크)에서 빠른 길이 잡은 **모든 블록**에 대해 바닐라 식(`computeSubstanceSlow`)을 다시 계산, 0 이 아닌 경우 0건(적중 64,019,755 블록, 약 37 s). `shouldScheduleFluidUpdate` 는 공기 블록에서 바닐라가 읽지 않아 대조 제외. 검증 훅은 테스트 전용 static 플래그(`verifyShortcut`), 끈 상태에서 PristineHashBench 해시 동일(`e87df7d13bfad324`/`473dbcd89273f233`)·시간 차 잡음 범위(60.9/55.8 vs 61.8/56.3 ms/청크) |
| W-11 표면 규칙 노드 컴파일 | **해결됨** (4eb900ce) | `gen/surface/SurfaceSystem.java:84`, `:495-641` `CRule`/`CCond` 가상 메서드 노드, -1 센티널 · `:434-442` 해석기(`eval`)는 `interpret` 플래그일 때만 | |
| W-06 ring StructureStart 판정값 캐시 | **해결됨(판정값 형태)** | `NoiseWorldSource.java:608-646` `sharedStarts`(빈 결과·jigsaw 전용), `ChunkPipeline.java:163-169` 캐시에 있으면 작업을 띄우지 않음 | 가변 조각이 있는 결과는 공유하지 않아 I-1 전제 충족 |
| W-08 NoiseChunk `mapAll`+HashMap → DAG 템플릿 | 아직 있음(미적용) | `gen/NoiseChunk.java:41,130-140` | 보고서 측정 JFR 2.3~2.4%(W-01/W-03 전), 지금 이득 미측정. 템플릿 설계가 중간 규모라 이번엔 적용 안 함 |
| W-13 feature 상수항(C-05~C-13) | 아직 있음(미적용) | 예: `world/ChunkSection.java:59-63` `copy()` 가 scratch 가 열려 있어도 낡은 `blocks.copy()` 를 함께 복사 | decorate 본체 합계 상한이 수 %(보고서). C-08 1안(blocks 복사 생략)은 `closeScratch`(`ChunkSection.java:141-148`)가 `blocks.load` 로 덮어 쓰므로 독립 객체가 필요하고, 새 빈 저장소의 bits 설정을 맞춰야 해 이득 측정 없이 넣지 않음. C-05(Stream→푸시)는 RNG 소비 순서 위험 |
| W-14 미시 최적화 묶음 | 미적용 | — | 각 1% 미만~수 %, 연산 순서 보존 위험. 측정 기반 없음 |

## 이번에 바꾼 것
- `NoiseBasedAquifer.computeSubstance` 를 빠른 길 + `computeSubstanceSlow`(기존 본문 그대로) 로 나누고, 테스트 전용 검증 훅을 추가했다. 운영 출력·RNG 소비는 그대로(해시 동일).
- 테스트 2개: `world/gen/structure/StructurePieceStateIsolationTest`, `world/gen/AquiferAirShortcutFuzzTest`.

## 남은 일
- W-02/W-09 정적 Y-불변 게이트(RS-3 의 분석 단계만)와 표면 이웃 쿼트 바이옴 출처의 바닐라 대조.
- W-08/W-13 은 현재 JFR 프로파일을 다시 떠서 비중이 확인되면 진행.
