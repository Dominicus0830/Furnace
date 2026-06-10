# M4g-a 기본 노이즈 지형 (바닐라 시드 비트일치) — Design Spec

> 부모: `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` §M4g.
> M4g 5단계(a 노이즈지형 → b 바이옴 → c 표층+carver+aquifer+광맥 → d features → e 구조물) 중 **첫 단계 a**.

## 목표

같은 바닐라 마인크래프트 1.21.1 시드 → **바닐라와 비트 단위로 동일한 지형 셰이프** 생성. vanilla-like 근사 아님. M4g-a는 그 토대 = 노이즈 단계 지형(stone/water/air)만. 모든 후속 단계(바이옴·표층·동굴·광석·식생·구조물)가 이 위에 쌓이므로 **비트일치를 모호함 없이 증명**하는 게 핵심.

## 범위

**In (이번 산출물).**
- 오버월드 한정. density function 그래프(`noise_router.final_density`) 평가 → `> 0` 이면 stone. `≤ 0` 이면 **disabled-aquifer 글로벌 fluid picker**대로: `y < -54` lava, `y < 63`(sea_level) water, 아니면 air. (바닐라 `aquifersEnabled=false` 동작과 비트동일 — aquifer 켠 지하 air 포켓/지역 수위는 M4g-c.)
- 비트일치 4요소: Xoroshiro RNG 시딩, 노이즈 프리미티브(Perlin/Normal/Blended), density function 인터프리터, NoiseChunk 셀 보간.
- 데이터 주도: 바닐라 jar서 worldgen JSON 추출, 인터프리터가 평가(손하드코딩 X).
- 멀티스레드 안전 generate(불변 그래프 공유 + thread-local 버퍼). 기존 비동기 청크로드 IO풀서 호출.
- `NoiseWorldSource`가 `AnvilWorldSource`의 base 폴백 자리서 `SuperflatWorldSource` 대체.

**Out (후속 단계).**
- 바이옴(climate noise·multi-noise 배치) = M4g-b.
- 표층 rule(grass/dirt/sand 등 표층블록), carver(동굴/협곡), aquifer(지하 fluid 정확), 광맥 = M4g-c.
- features(나무/식생/광석) = M4g-d. 구조물 = M4g-e.
- 네더/엔드 차원.

**비트분리 근거.** 바닐라 `NoiseChunk`는 `final_density > 0` 이면 default_block(stone) 배치 — **aquifer 무관**. `≤ 0` 인 공간만 aquifer가 air/water/lava 결정. aquifer를 끄면(`aquifersEnabled=false`) `≤ 0` 공간은 단순 글로벌 fluid picker(lava<-54, water<63, else air)로 채워짐. 따라서 **오라클을 aquifer 끈 오버월드 설정으로 돌리면 우리 M4g-a 규칙과 전 컬럼 비트동일** → 마스킹 없이 깔끔히 검증. aquifer 켠 정확 지하 fluid는 M4g-c.

## 비트일치 4요소 (전부 [[mc-vanilla-decompiled-source]] 정독)

1. **Xoroshiro RNG.** `XoroshiroRandomSource`(Xoroshiro128++) + `RandomSupport.upgradeSeedTo128bit`(시드 128비트 확장) + positional seeding. 노이즈 octave 시드가 여기서 갈림 — 한 비트 틀리면 지형 전체 어긋남.
2. **노이즈 프리미티브.** `ImprovedNoise`(단일 옥타브 perlin) → `PerlinNoise`(다중 옥타브, octave별 시드) → `NormalNoise`(2-perlin 합성). `BlendedNoise`(`old_blended_noise` density function이 쓰는 구식 3D 지형 노이즈). 전부 바닐라와 octave 시딩·permutation·샘플 보간이 비트동일.
3. **Density function 그래프.** 데이터 주도 인터프리터. 추출 JSON(`noise_router` 인라인 그래프 + 참조된 `density_function/**` + `noise/*` NormalNoise 파라미터)을 평가 트리로 파싱. 노드: `constant/add/mul/min/max/abs/square/cube/half_negative/quarter_negative/squeeze/clamp/spline/noise/shifted_noise/y_clamped_gradient/range_choice/old_blended_noise/interpolated/flat_cache/cache_2d/cache_once/blend_*` 등 바닐라 전 노드 타입.
4. **NoiseChunk 셀 보간.** 셀 = 4(가로)×8(세로)×4(깊이). 청크당 4×4 가로 셀 × 48 세로 셀(height 384). 셀 **코너서만** density 계산 후 삼선형 보간(블록단위 평가 회피 = 최대 성능 + 바닐라가 실제로 이렇게 함 → 비트일치 필수). 보간 순서·캐시(`interpolated`/`flat_cache`/`cache_2d`) 동작 바닐라 복제.

**파라미터(추출 확정값):** min_y=-64, height=384, sea_level=63, default_block=stone, default_fluid=water, cell 4×8, size_horizontal=1, size_vertical=2.

## 컴포넌트 구조

```
world/gen/
  NoiseWorldSource.java     # WorldSource 구현. seed·NoiseRouter·NoiseSettings·BlockRegistry 보유. generate(cx,cz)→ChunkColumn
  NoiseChunk.java           # 청크당 셀 코너 density 샘플 + 삼선형 보간 + 블록 채움. thread-local 재사용
  NoiseSettings.java        # noise_settings/overworld 파라미터 레코드
  noise/
    XoroshiroRandomSource.java  # Xoroshiro128++ + upgradeSeedTo128bit + positional fork
    ImprovedNoise.java          # 단일 옥타브 perlin
    PerlinNoise.java            # 다중 옥타브
    NormalNoise.java            # 2-perlin 합성
    BlendedNoise.java           # old_blended_noise
  df/
    DensityFunction.java        # 평가 인터페이스: double compute(ctx) + fillArray(셀 벌크)
    DensityFunctions.java       # 전 노드 타입 구현
    DensityFunctionParser.java  # 추출 JSON → DF 트리(id 상호참조 해석)
    NoiseRouter.java            # noise_settings.noise_router → final_density(+ 후속용 기타 항목)
```

**리소스(바닐라 jar `data/minecraft/worldgen/`서 distill).**
```
resources/worldgen/1.21.1/
  noise_settings/overworld.json
  density_function/**.json
  noise/*.json
```
추출 = Gradle task 또는 스크립트로 `build/versions/1.21.1/server-1.21.1.jar`서 복사(M2 블록/레지스트리 distill과 동일 철학: 알고리즘=재구현, 데이터=추출).

**통합.** FurnaceMain: `base = new NoiseWorldSource(seed, blockRegistry, router, settings)` → AnvilWorldSource 주입. SuperflatWorldSource 파일은 남김(테스트·폴백 옵션). generate는 스테이트리스(불변 그래프 + thread-local NoiseChunk 버퍼) → 병렬 호출 안전.

## 검증

**Tier 1 — 노이즈 프리미티브 단위테스트(비트 단위).** 바닐라 소스서 추출한 test vector로 assert.
- `XoroshiroRandomSource`: 고정 시드 → nextLong/nextInt 시퀀스 일치. `upgradeSeedTo128bit` 결과 일치.
- `ImprovedNoise`/`NormalNoise`/`PerlinNoise`/`BlendedNoise`: 고정 좌표·시드 샘플값 일치(double 비트 동일 또는 ulp 0).
- 목적: RNG/노이즈 1비트 오류를 지형 전체 검증 전에 격리.

**Tier 2 — 청크 parity (지형 셰이프 비트일치, gold 오라클 = A).**
- **오라클 하니스(1회 작성, M4g 전 단계 재사용):** mojang-mapped 1.21.1 서버 jar로 `NoiseBasedChunkGenerator.fillFromNoise`를 **노이즈 스텝만** 실행(carver/surface/feature 스킵). **오버월드 설정 복사본에 `aquifersEnabled=false`, `oreVeinsEnabled=false`** 적용 → `≤0` 공간이 글로벌 fluid picker로 채워져 우리 규칙과 동일. 알려진 시드 + N개 청크 좌표 → 블록배열을 바이너리 test fixture로 덤프, 커밋. 부트스트랩 글루(Bootstrap.bootStrap, worldgen 레지스트리 접근) ~100줄.
- **parity 테스트:** `NoiseWorldSource`로 같은 시드·좌표 생성 → fixture와 **전체 컬럼(y −64..319) stone/water/lava/air 비트비교**. aquifer/표층/광석 다 꺼서 순수 노이즈끼리 비교 → 마스킹·허용오차 0. 1블록이라도 다르면 실패.
- **시드 셋:** 최소 2개(예: 0, 그리고 비자명 시드 1개) × 다중 청크(스폰 근처 + 원거리), 지형 다양성(평지/산/바다) 커버.

**재사용:** 같은 오라클 하니스가 M4g-b(바이옴 덤프)·c(surface/carver)·d(feature)·e(structure) 검증에 그대로 확장 → 지금 만드는 ~100줄이 M4g 전체 검증 인프라.

## 성능 / 멀티스레드 (정체성)

M4g-a는 **정확성 우선, 측정가능 최적화 동시**.
- 셀 보간(블록단위 평가 회피) — 정확성 + 성능 둘 다(필수).
- thread-local NoiseChunk 버퍼 재사용(할당 최소).
- DF 캐시 노드(`flat_cache`/`cache_2d`/`interpolated`) 바닐라대로 구현 = 정확성 + 재계산 회피.
- **이번 범위 밖(후속 최적화 패스):** Vector API SIMD 노이즈, density function 컴파일/플래튼(트리→플랫 바이트코드), ForkJoin work-stealing 튜닝. M4g-a는 트리 인터프리터로 비트확정 먼저, SIMD/컴파일은 parity 통과 후 별도.

## 성공 기준

1. Tier 1 단위테스트 전부 통과(노이즈 프리미티브 비트일치).
2. Tier 2 parity: 2+ 시드 × 다중 청크서 우리 생성 == 바닐라 노이즈단계(aquifer/oreVein 끈) fixture, 전체 컬럼 stone/water/lava/air 비트일치.
3. `NoiseWorldSource`가 base 폴백 자리서 동작, 서버 실행 시 바닐라스러운(실은 바닐라 동일) 지형 로드, 기존 영속·청크전송·블록설치/파괴와 회귀 없음.
4. `./gradlew build` green.

## 리스크 / 미해결

- **부트스트랩 글루 무게:** vanilla worldgen 헤드리스 실행이 레지스트리 의존성으로 예상보다 클 수 있음. 너무 무거우면 fixture를 외부(소형 Fabric mod 등)서 1회 덤프해 커밋하는 폴백 — 단 정석은 jar 직접 호출.
- **DF 노드 커버리지:** 오버월드 그래프가 쓰는 노드 타입 전부 구현 필요. 누락 시 파서가 명시적 실패(추측 평가 금지).
- **double 재현성:** 바닐라와 동일 부동소수 연산 순서 유지(StrictMath vs Math, 곱셈 결합 순서). 비트일치 깨지면 여기 의심.
- **NoiseChunk 보간 세부:** 바닐라 `NoiseChunk`의 셀 채움 순서·`Blender`(blend 비활성=평탄) 처리 정확 복제.
- 검증 시드용 바닐라 레퍼런스는 사용자가 1회 생성(또는 하니스가 자동).
```
