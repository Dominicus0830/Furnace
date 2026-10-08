# 월드젠 측정 방법과 현재 수치 (2026-10-08)

## 핵심 교훈 — "어디까지가 진짜 불일치인가"
순정(vanilla)은 한 청크를 만들 때 주변 origin을 **그때그때 다른 순서로** decorate하고, 그 순서가 결과(특히 나무·덩굴·식생 패치처럼 서로 겹치는 feature)에 영향을 준다. 그래서 측정이 잘못되면 "고칠 수 없는 차이"를 버그로 착각한다.

1. **스폰 영역 오염.** 서버가 시작할 때 스폰 주변(origin −4..4)을 먼저 decorate한다. 중심이 0, ±6인 격리 청크는 이웃 origin이 스폰 영역과 겹쳐 순정 쪽에 Furnace가 모르는 선행 쓰기가 들어간다. → **링 청크(max(|x|,|z|)=12)만 '깨끗한' 비교 대상**으로 쓴다(`iso_clean.txt`).
2. **origin 개별 생성은 다른 문맥.** `SegDump`처럼 origin마다 따로 생성하면 이웃 8 origin이 달라져 순정의 "중심 한 번 생성"과 다르다. → `tools/parity-audit/SegBox`가 중심 청크를 **한 번** 생성하면서 3×3 origin 전부의 RNG·feature 추적을 한꺼번에 덤프한다.
3. **순정은 중심 주변 더 먼 origin도 선행 decorate한다**(예: 중심 (−12,6)에서 origin (−12,3),(−12,4)가 (−12,5)보다 먼저). Furnace의 청크 파이프라인은 3×3 origin만 쓴다(설계). 이 선행분이 만드는 차이는 구조상 남는다 — 순정 자체도 실행마다 순서가 달라지는 영역.
4. RNG 줄(draw) 수준 대조는 **feature 단위 세그먼트** + 첫 불일치(순정 순위 기준)로 한다. `freeze_top_layer`의 Furnace nextDouble은 추적 기록 아티팩트(별도 노이즈 난수)라 무시.

## 이번 라운드에서 찾아 고친 실제 버그
| 증상 | 원인 | 수정 |
|---|---|---|
| 나무 사이 덩굴이 순정에는 없고 Furnace에만 남음 | `StructureTemplate.updateShapeAtEdge` 미구현 — 나무 shape 가장자리마다 양쪽 블록의 `updateShape`가 불려, 새 잎에 덮여 지지를 잃은 이웃 vine이 사라짐 | `TreeFeature.updateShapeAtEdge` + `VineSupport`(VineBlock.updateShape) |
| 대수층 물/공기·광맥 경계 불일치(시드 128 960칸) | 대수층의 deep-dark 판정이 `NoiseChunk`로 wrap되지 않은 erosion/depth를 써서 청크 가장자리에서 쿼트 정렬 flat_cache 값을 못 봄 | `NoiseBasedAquifer`에 `NoiseChunk.wrappedForAquifer` 적용 |
| 광맥(철/구리)이 장벽 셀에서 빠짐(시드 49 121칸) | material rule 체인 `[aquifer, oreVeinifier]`에서 aquifer가 null(장벽)이면 광맥 규칙을 돌려야 하는데 곧바로 stone | `NoiseChunk.generateBlockIds`가 NO_CARVE일 때 `oreVeinifier` 실행 |
| 정글 나무/풀 패치 RNG 어긋남(시드 80) | 위 vine 문제 + 측정 문맥 오염 | 위 수정 + SegBox |

## 깨끗한 링 청크 기준 hard 불일치 (11시드 × 링 16청크, 위치 약 1.57억)
| 시드 | 이전(전체 청크) | 이전(링) | 지금(링) | 남은 이유 |
|---|---:|---:|---:|---|
| 105 | 7 | 5 | 1 | |
| 10 | 6 | 6 | 6 | |
| 125 | 13 | 12 | 1 | |
| 18 | 2 | 1 | 1 | |
| 11 | 1 | 1 | 1 | |
| 157 | 2 | 0 | 0 | |
| 4 | 94 | 31 | 31 | 순정이 더 멀리서 선행 decorate한 얼음 가시·용암 호수 문맥 |
| 49 | 427 | 126 | 6 | |
| 80 | 495 | 14 | 13 | |
| 424242 | 1725 | 789 | 789 | 거의 전부 sculk(딥다크) 미구현 |
| 128 | 2291 | 1596 | 645 | 거의 전부 sculk 미구현 |

## 도구 (`$S` = 세션 스크래치패드, 저장소에는 `tools/parity-audit`)
- `SegBox <seed> <중심목록> <출력>` — 중심 한 번 생성 + 3×3 origin 추적.
- `-Dfurnace.watch=x,y,z` — 해당 칸의 `setBlock` 호출자·직전 placed feature origin 출력. `-Dfurnace.hmwatch=x,z`, `-Dfurnace.aqwatch=…`, `-Dfurnace.aqcenters=1`, `-Dfurnace.logall=1`.
- 순정 계측은 난독화 클래스를 디컴파일해 패치(`edq`=TreeFeature, `edl`=SimpleBlockFeature, `eij`=BlockPredicateFilter, `dyk`=NoiseBasedAquifer …)한 뒤 classpath 앞에 놓는다. 디컴파일 소스는 오버로드 해석이 달라질 수 있어(`jd$a.a(kh,…)` vs 정적 `a(jd,…)`) 패치 후 `javap`로 호출 집합을 원본과 대조해야 한다.
