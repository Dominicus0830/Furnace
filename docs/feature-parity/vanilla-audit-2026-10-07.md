# 2026-10-07 worldgen-parity — 순정 1.21.1 실서버 .mca 대 Furnace 독립 대조 감사

## 작업 요약
과거 fixture에 의존하지 않고, 새 시드 4개(424242, 777, 2024, 31337)로 순정 1.21.1 서버를 직접 돌려 정답지를 새로 만든 뒤 Furnace(`generateWithFeatures`)와 블록 단위로 대조했다. 결론은 **같은 월드가 아니다**. 지형 뼈대는 거의 비트 단위로 같고, 장식(feature)·구조물·조명·저장 포맷이 다르다.

## 방법
- 순정 서버 `build/minecraft_server.1.21.1.jar`, `generate-structures=false`, `/tick freeze`, forceload 25×25청크. 같은 명령 2회(A1, A2), 범위·순서를 바꾼 월드(B)로 순정 자체의 노이즈 바닥을 측정.
- `.mca`는 프로젝트 코덱을 쓰지 않고 자체 NBT/팔레트 디코더로 읽어 `name;k=v` 문자열로 비교(코덱 버그 배제).
- 순정 데이터팩으로 모든 바이옴 features를 비운 월드(지형 단독 정답지)를 만들어 결정적 비교.
- 정의 — hard 불일치 = 순정 실행들이 모두 같은 값인데 Furnace만 다른 위치. 확정 편차.
- 도구는 `tools/parity-audit/`, 당시 원본 출력은 `tools/parity-audit/reports/*_report.txt`(사본은 LLM-Wiki `output/furnace-parity-audit/`).

## 결과 1 — 지형 뼈대(노이즈·대수층·카버·표면·광맥), 순정 features 제거 월드
순정 지형 단독 생성은 시드 424242에서 같은 명령 2회 289청크 0블록 차이였다(777·31337은 순정 월드를 1개만 만들어 재현성을 직접 확인하지 못했고, 두 시드의 hard는 furnaceMismatch와 같은 값이다).

| 시드 | 불일치 / 28,409,856칸 | 완전 일치 청크 |
|---|---|---|
| 424242 | 329 (0.0012%) | 269/289 |
| 777 | 144 (0.0005%) | 273/289 |
| 31337 | 2,729 (0.0096%) | 204/289 |

합계 3,202 / 85.2M = 0.0038%, 청크 746/867(86.0%) 비트 일치. 잔차는 광맥(granite·copper_ore·tuff·iron), 산봉우리 눈(`snow_block`/`powder_snow` 임계), 소수의 물/공기.

## 결과 2 — 전체 생성(features 포함), 4시드 1,092청크
| 시드 | Furnace 불일치 | 순정 A1 대 A2 노이즈 | hard |
|---|---|---|---|
| 424242 (다크 포레스트·라임 동굴) | 1.88% | 0.61% | 1.37% |
| 777 (자작·다크 포레스트) | 0.31% | 0.05% | 0.28% |
| 2024 (숲·점적석·정글) | 0.60% | 0.17% | 0.52% |
| 31337 (설산·그로브) | 1.18% | 0.28% | 1.02% |
| 합계 | 0.94% | | 0.76% (820,882 / 107,347,968칸) |

424242 행만 225청크(15x15, |chunk|≤7)·순정 3회(A1/A2/B, `r1_report.txt`) 기준이고 777·2024·31337은 289청크·순정 2회다. 순정 회차가 많을수록 hard가 작아져 424242의 hard 1.37%는 다른 행과 비교할 수 없는 하한이다(2회 기준 환산은 원자료가 없어 추정이며 최대 +0.31%p, 합계 hard 최대 약 0.83%). A1 대 A2 노이즈 열은 모든 행이 같은 기준이다.

- 순정과 블록 단위로 완전히 같은 청크 43/1,092(3.9%), hard 0인 청크 59(5.4%).
- 순정 자체도 같은 명령 2회에서 0.05~0.61% 달라진다. 이웃 feature가 최대 16블록 겹쳐 쓰고 생성 순서가 비결정적이기 때문. 따라서 "비트 동일"은 순정끼리도 성립하지 않는다.
- hard 분류(근사) — 나무·덩굴 46%, 미구현 feature 블록 37%, 광석/치환 블롭 8%, 기타 9%.
- 기존 repo sweep(seed 51532, 289청크) 재현 — 총 46,789로 baseline과 동일. 이 시드는 평균 0.16%로 가장 유리한 쪽이었다.

## 확정된 편차 목록
1. **미구현 feature(오버월드 151개 placed 중 34개가 미구현 타입 접촉)** — monster_room(던전)은 53개 전 바이옴·lake(용암 호수)는 52개 바이옴, 점적석(large_dripstone/dripstone_cluster/pointed_dripstone), lush caves(vegetation_patch·cave_vines), sculk_patch, huge mushroom, bamboo, sugar_cane/cactus(block_column), coral, iceberg 등.
2. **나무 위치 자체가 다름** — 다크 오크 통나무 위치 겹침이 순정 2회 사이 86.9%인데 순정 대 Furnace는 23.5%. 순정 통나무 34k·잎 89k, Furnace 통나무 24.7k·잎 123k. 가설 "huge mushroom Noop 때문에 RNG 어긋남"은 데이터팩 실험(순정 쪽 버섯을 no_op으로 교체, 적용 확인 완료)으로 **기각**. 원인 미확정.
3. **바이옴** — 4시드 합 537/1.68M셀(0.032%) 불일치. 모든 Y에서 쿼트 컬럼 단위로 이웃 바이옴으로 뒤집힘(beach↔ocean, dark_forest↔grove). 순정 실행끼리는 0 차이라 Furnace 쪽 확정 오차. 지형 단독 월드에서도 동일하게 재현(결정적).
4. **MOTION_BLOCKING heightmap** — features 포함 전체 생성에서 시드 31337은 72,186/73,984컬럼(97.6%)이 어긋난다. 순정은 얇은 `snow` 레이어를 세지 않는데 Furnace는 센다는 설명과 부합한다(Furnace 최상단이 `snow;layers=1`인 컬럼 98.3%, 검증자 프로브 값). 그러나 **지형 단독(features 제거) 월드에서도 어긋난다**: 31337 7,950/73,984컬럼(10.7%), 424242 89, 777 0. 31337의 블록 불일치는 2,729칸뿐이라 블록이 같은데 높이만 다른 컬럼이 최소 5,221개이고, 이 월드에는 snow 레이어가 없으므로 얇은 눈으로는 설명되지 않는다. 유력한 원인은 `powder_snow`다: `ChunkColumn.isNonMotionBlockingByName`에 없어 Furnace는 motion-blocking으로 세는데, 순정은 빈 콜리전 shape라 `blocksMotion=false`일 것으로 추정한다. Furnace 출력의 powder_snow 최상단 컬럼 수가 불일치와 일치한다(424242 89=89, 777 0=0, 31337 7,067 대 7,950; 잔여 883컬럼은 귀속 미검증 — 검증자 프로브 값이며 이 문서 작성자가 재실행하지 않았다). 순정이 powder_snow를 빼는지는 순정 jar 없이 확인하지 못한 **가정**이다. features 포함 값 97.6%는 얇은 눈과 powder_snow가 섞인 것이며 분리 측정하지 않았다. WORLD_SURFACE는 지형 단독에서 424242·777이 0/73,984이지만 31337은 1,067/73,984(1.44%)다(산봉우리 `snow_block`/`powder_snow`→`air` 잔차와 같은 원인으로 추정, 컬럼 단위 미검증).
5. **구조물** — 순정 1.21.1 구조물 34종(네더·엔드 포함, structure_set 20개) 중 Furnace는 desert_pyramid만 조각 생성(shipwreck은 placement만). 순정 625청크 샘플에 광산 6·한랭 해저유적 1·폐허 포탈 1이 있었으나 Furnace는 하나도 못 만든다. 구현된 desert_pyramid는 실서버 2건 모두 6,615블록 중 6,614 일치(차이는 suspicious_sand→sand/sandstone 1블록).
6. **스폰** — 3시드 중 2개 일치. 424242는 순정 (0,112,0), Furnace (0,105,0). 원인 `getSpawnPosInChunk`가 장식 전 `generate()` 지형으로 높이를 계산.
7. **조명** — `ChunkSerializer`가 전 섹션 풀 skylight, block light 0. 동굴이 밝게 보임. 코드 확인, 렌더 미확인.
8. **엔티티** — 청크 생성 시 몹 스폰 코드 없음(코드 확인).
9. **저장 포맷** — Furnace가 쓴 `.mca` 섹션은 `block_states`·`Y`뿐. `biomes`·SkyLight·BlockLight·structures·isLightOn 없음, heightmap은 MOTION_BLOCKING만. 재로딩 시 `AnvilChunkCodec`가 바이옴을 plains 단일값으로 강제. 청크는 생성 직후 dirty가 해제되고 플레이어가 편집(설치·파괴·상자)한 청크만 저장되므로, **편집된 청크만** 재시작 후 바이옴 전체가 plains가 된다(편집 안 한 청크는 재생성이라 정상). 종단 간 재현 2종 — (1) 생성→저장(World.saveChunkAsync 경로)→재시작→재로딩: 5청크 모두 1,536/1,536셀 변경(birch_forest·dark_forest·lush_caves → plains). (2) 실제 World/Region(autosave·퇴출·종료 flush 전부 실행): 편집 없이 25청크 로드 → 디스크 저장 0개, 1블록 편집 → 저장 1개, 재시작 후 편집 청크만 바이옴 전체 plains(블록 변경은 편집한 1개뿐)이고 나머지 24청크는 바이옴·블록 모두 불변. 원인은 코덱의 "단일 바이옴(superflat/근사)" 주석이 M4b(6/8)에 들어가고 노이즈 지형(6/10) 이후 갱신되지 않은 것(git 이력 확인). 상자 id도 `furnace:chest`. (2026-10-07 재검토에서 "모든 청크"와 "수천 청크 중 8청크" 표현을 정정 — 후자는 생성 청크 수를 확인한 적 없어 근거에서 제외)
- 가시 영향 범위(바이옴 JSON 확인) — water_color는 plains·forest·birch_forest·dark_forest·lush_caves·grove 모두 동일(4159204), 달라지는 건 snowy_taiga·swamp뿐. 풀/잎 색은 기후(temp·downfall)로 결정되어 plains와 숲 계열은 미세한 차이, dark_forest는 grass modifier로 뚜렷, grove·snowy는 눈→비 전환, desert·savanna는 강수 없음→있음, jungle·swamp는 색 차이 큼. 렌더는 직접 안 봄.

## 측정 한계
- 샘플은 스폰 근처 |chunk|≤8(424242 전체 생성만 ≤7), 시드 4개. 사막·악지·늪·맹그로브·깊은 바다는 이번에 안 봄(과거 fixture상 맹그로브 3,408/청크, 정글 1,566/청크로 나쁨).
- 네더·엔드 미검증, 청크 NBT의 엔티티·상자 loot·유체 틱 미비교(`/tick freeze` 상태).
- 카테고리 분류는 블록 이름 기반 근사.
- MOTION_BLOCKING 불일치의 원인(powder_snow vs 얇은 snow 레이어)은 순정 jar 없이 미확정.

## 후속 과제 — 버그별 코드 위치와 우선순위
위치는 2026-10-07 시점 HEAD 기준. "확정"은 이번 대조로 재현된 것, "추정"은 코드 읽기로만 본 원인.

| # | 버그 | 위치 | 상태 |
|---|---|---|---|
| 1 | 편집 청크 바이옴 유실 — 저장에 `biomes` 없음, 로드 시 plains 강제 | 쓰기: `AnvilChunkCodec.java:60` `toNbt(ChunkSnapshot)`(실서버 저장 경로: `Region.java` saveDirtyChunks·evictIdleChunks → `World.saveChunkAsync`)와 `:30` `toNbt(ChunkColumn,int,int)`(main 호출 0, 테스트 전용) **두 오버로드 모두** `biomes`를 안 씀. 읽기: `:165` `fromNbt`가 `new ChunkColumn(biomeId,…)`로 plains를 채우고 `biomes`를 읽지 않음. 배선: `FurnaceMain.java:40`에서 코덱에 바이옴 이름 목록(`registries.entries("minecraft:worldgen/biome")`) 전달 필요 | 확정(종단 간 재현). `:30`만 고치면 단위 테스트만 초록이고 운영 저장은 그대로다. 수정 시 `biomes` 없는 옛 청크·알 수 없는 이름은 plains 폴백 유지 필요 |
| 2 | MOTION_BLOCKING 불일치 — `powder_snow`(지형 단독, 유력)와 얇은 `snow` 레이어(features 실행) | `ChunkColumn.java:437` `isNonMotionBlockingByName`(`powder_snow` 없음, 주석에 "눈더미 높이별 콜리전" 미커버로 명시) | **추정**(순정 jar로 재측정 필요). 컬럼 수 일치는 확인됨. 레이어 규칙만 고치면 powder_snow 컬럼이 남는다. 검증: powder_snow를 non-MB로 바꾼 뒤 지형 단독 Harness 재실행 — 예측은 424242 89→0 근처, 777 0 유지, 31337 7,950→약 883 이하. 틀리면 가설 기각 |
| 3 | 스폰 Y — 장식 전 지형으로 높이 계산 | `NoiseWorldSource.java:201` `getSpawnPosInChunk`가 `generate()` 사용 | 불일치는 확정(424242: 순정 112, Furnace 105), 원인은 추정 |
| 4 | 나무 위치 불일치(hard의 46%) | TreeFeature·트렁크/폴리지 placer 전반 | 확정, 원인 불명(거대 버섯 Noop 가설은 실험으로 기각) |
| 5 | 바이옴 쿼트 컬럼 오차 0.03% | `BiomeSampler`/`NoiseChunk.fillTarget` 부근 | 확정·결정적, 원인 불명 |
| 6 | 지형 단독 잔차(광맥 granite·copper·tuff, 산봉우리 눈, 소수 물; 31337 WORLD_SURFACE 1,067컬럼) | `OreVeinifier`, `SurfaceSystem` | 확정·결정적. 순정 지형 월드(features 제거)로 0까지 닫을 수 있음 |
| 7 | 감사 테스트 사각지대 | `NoopFeatureAuditTest.java:61` 루트 `type`만 검사 | 확정. `no_bonemeal_flower`는 `forest_flowers.json` 안에 인라인으로만 있어 허용목록에 없는데도 통과. 중첩 Noop 자식은 `BiomeFeatureData.java:301`, `:310`에서 조용히 Noop이 된다. 또 `KNOWN_UNIMPLEMENTED_TYPES`에 이미 구현된 `minecraft:vines`·`minecraft:forest_rock`이 남아 있던 것을 정리하고 '구현됐는데 허용목록에 남은 타입이 없다'를 확인하는 단언을 추가했다 |

미구현(버그가 아니라 작업량) — 점적석·라임 동굴·sculk·거대 버섯·던전·용암 호수·`block_column`(사탕수수·선인장), 구조물 33/34종, 조명, 몹 스폰.

우선순위(판단 — 근거 데이터가 아니라 영향/난이도 추정)
1. #1 — 확정이고 작다(두 `toNbt` 오버로드 모두). #2 — 원인이 추정이므로 순정 jar 재측정으로 가설을 먼저 확인한 뒤 수정.
2. #6 — 결정적 정답지가 있어 0까지 닫기 쉽다.
3. #4 — 영향이 가장 크므로 원인 규명부터.
4. 점적석 → 라임 동굴 → sculk 포팅(시드별 hard 중 미구현 feature 비중 22~65%).
5. #7 — 중첩 Noop을 세는 테스트로 보강하면 이후 포팅 진척을 수치로 볼 수 있다.
