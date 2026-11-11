# 월드젠 노이즈 계산 성능 (2026-11-11)

청크 로딩 진단(`docs/audit/chunk-loading-diagnosis.md`, PR #211)의 후속이다. 새 땅에서는 CPU 의 약 87% 가 월드젠이고 그중 절반 넘게가
노이즈·밀도 함수 계산이었다. 생성 결과(블록·바이옴)는 비트까지 그대로 두고 계산만 줄였다.

## 1. 결과 요약

4코어 컨테이너, 시드 12345, `-Xmx3g`. 기준 = 기준 브랜치 af1d7be9. 같은 측정을 기준·새 빌드로 번갈아 두 번 돌렸다(아래 값은 두 번 모두).

| 장면 | 기준 | 새 빌드 | 변화 |
|---|---|---|---|
| 이어진 땅 24×24 오버월드, 3 스레드, 원시 청크(지형+표면+카버) — 청크당 CPU | 25.0~28.5 ms | 15.6~19.0 ms | **약 -32%** |
| 같은 장면 벽시계 | 4.9~5.7 초 | 3.1~3.9 초 | **약 -33%** |
| 이어진 땅 16×16 엔드 — 청크당 CPU | 6.7~8.0 ms | 4.1~6.4 ms | 약 -30% |
| 지형 채우기(`generateBlockIds`, 흩어진 청크, 1 스레드) 오버월드 | 15.0~15.3 ms | 10.4~10.8 ms | -30% |
| 같은 것, amplified | 16.6~18.4 ms | 11.0~11.2 ms | -35% |
| 같은 것, 네더 | 1.35 ms | 0.93~1.02 ms | -27% |
| 같은 것, 엔드 | 5.5~5.9 ms | 5.5~5.6 ms | 같음(엔드는 이웃 공유에서 이득) |
| 원시 청크 전체(흩어진 청크, 새 구조물 영역 포함) 오버월드 | 34.2 ms | 27.2~28.0 ms | -19% |

흩어진 청크 측정은 이웃 꼭짓점 공유(3-3)가 안 걸리는 장면이다. 서버는 플레이어 둘레로 이어진 땅을 만들므로 위쪽 "이어진 땅" 값이 실제에 가깝다.
컨테이너 속도가 날마다 달라 절대값은 다른 날과 바로 비교하지 않는다(11-11 진단 문서 참고).

## 2. 동일성 증명

`NoiseIdentityBench hash`(시험 소스, `-Dfurnace.bench=true` 일 때만 JUnit 으로 돈다)가 다음을 한 줄씩 해시해 파일로 쓴다. 기준 커밋과 새 커밋의 파일이 **바이트까지 같다**(`cmp`).

- 설정 26개: 오버월드 시드 10개(0, 12345, -1, 42, 3141592653589793, 8675309, Long.MIN_VALUE+7, 20261111, -4172144997902289642, 1),
  large_biomes·amplified 시드 3개씩, 네더·엔드 시드 4개씩.
- 설정마다 위치 20곳(엔드 24곳): 원점 근처 2×2 묶음, ±300 청크 안 10곳, ±10000 청크 안 3곳, (100000, -100000) 근처, (-1,-1), (0,0), 엔드 바깥 섬 4곳.
  세 설정마다 하나는 이어진 5×5 를 3 스레드가 동시에 만든다(이웃 꼭짓점 공유·동시 접근 경로).
- 청크마다 원시 청크(지형·표면·카버 뒤) 블록 상태 전부, 바이옴 전부, 유체 후처리 표시·생성 유체 틱(순서까지)의 해시. 모두 696 청크.
- 라우터 밀도 함수 15종(final_density, 기후 6종, 대수층 4종, 광맥 3종 등)을 NoiseChunk 없이 무작위 400점(±2만, ±2900만 블록)에서 바로 평가한 double 비트 해시.
- 결과: 기준 af1d7be9 `TOTAL 8ddffc9d67d1f534 chunks=696`(블록·바이옴) = 새 빌드 같은 값. 꼭짓점 공유 캐시를 끈 새 빌드(`-Dfurnace.worldgen.sliceCache=false`)도 같은 값.
  기준 브랜치에 대수층 유체 후처리 표시(A06-03)가 들어온 뒤 합친 빌드도 기준 6db55df7 과 `TOTAL 977bd3343f00dffc chunks=696`(유체 표시 포함)으로 같다.
- 단계마다(1~5) 같은 해시를 다시 돌려 확인했다.

시험으로도 남겼다.

- `NoiseFastPathParityTest`: Perlin 표본(옛 byte 표·int[][] 기울기 경로와 새 경로) 80만 점, `wrap` 200만 점·경계값, `noise()` 50만 점을 비트 대조.
- `SliceColumnCacheTest`: 오버월드·amplified·large_biomes·네더·엔드에서 이웃 8청크를 먼저 만든 뒤(경계 꼭짓점이 캐시에서 옴) 만든 청크 = 새 라우터에서 처음 만든 청크.
  광맥 없는 구성이 같은 라우터에 섞여도 서로의 값을 쓰지 않음. 4 스레드 동시 생성 = 따로 만든 결과.
- 기존 바닐라 대조 시험(NoiseChunkTest·NoiseWorldSourceParityTest·SurfaceParityTest·AquiferParityTest·CarverParityTest 등)과 전체 시험 통과.

## 3. 바꾼 곳

### 3-1. Perlin 원시 함수 (`ImprovedNoise`, `PerlinNoise.wrap`)
- 순열 표를 `int[]` 로 미리 풀고(바닐라 `p(i) = p[i & 0xFF] & 0xFF`), 마지막 해시 단계는 `(p & 15) * 3` 표로 기울기 번호를 바로 얻는다.
  기울기는 `double[48]` 한 배열에서 읽는다. `int * double` 곱은 정수 → double 변환이 정확해서 값이 같고, 더하는 순서(g0·x + g1·y + g2·z)도 같다.
- `wrap(v)`: |v| < 2^23 이면 바닐라 식 `v - floor(v/2^25 + 0.5)·2^25` 의 floor 가 0 이라 결과가 v 그대로다(-0.0 도 유지). 그 밖은 `/2^25` 를
  `*2^-25` 로(둘 다 같은 실수값을 한 번 반올림 — 같은 비트). 2^24 근처는 `+0.5` 반올림 때문에 floor 가 1 이 될 수 있어 경계를 2^23 으로 잡았다.

### 3-2. 지연 보간 (`NoiseChunk.NoiseInterpolator`)
바닐라 `updateForY/X/Z` 는 블록마다 모든 보간기(오버월드 8개: 본 지형, 국수 동굴 4개, 광맥 3개)의 중간값을 갱신한다. 이제는 t 와 세대 번호만 바꾸고,
보간기 값을 실제로 읽을 때 바뀐 단계만 같은 식·같은 순서(`lerp(t, a, b)`)로 계산한다. 갱신 순서가 늘 Y → X → Z 라 Y 세대가 바뀌면 X·Z 세대도 그 뒤에 바뀐다.
광맥 보간기는 돌 블록 중 광맥 높이에서만 읽히므로 대부분 계산하지 않는다. 보간기 목록도 배열로 돈다.

### 3-3. 이웃 청크 꼭짓점 공유 (`SliceColumnCache`, 라우터마다 4096칸)
청크마다 (4+1)² 개 꼭짓점 기둥 중 9개는 이웃 청크와 같은 월드 좌표다. 꼭짓점 값은 라우터와 월드 셀 좌표만의 함수다(보간기 안의 flat_cache 는 청크 쿼트 표를
같은 식으로 미리 채운 것이고, cache_2d·cache_once 는 같은 점의 메모다). 그래서 한 번 계산한 기둥을 이웃이 그대로 복사한다.
- 잠금 없는 직접 사상 표. 항목은 불변(좌표·구성·값 배열). 충돌하면 덮어쓰고, 좌표·구성이 다르면 없는 것으로 본다.
- 구성(layout) = 보간기 수·광맥 여부·셀 크기·셀 높이 수·최저 셀. 생성자 뒤에 보간기가 늘면(다른 wrap) 쓰지 않는다.
- 보간기 함수 안에 상태를 읽는 노드(다른 보간기를 compute 로, cache_all_in_cell)가 있으면 그 구성은 쓰지 않는다(지금 바닐라 데이터에는 없다 — 확인함).
- 적중률: 이어진 오버월드 약 30%(이론 최대 9/25 = 36%), 엔드 약 51%(셀 폭 8 이라 3×3 중 5개 공유). 구조물 높이 조회가 같은 청크를 미리 만든 경우도 적중한다.
- 메모리: 오버월드 칸당 8 × 49 double ≈ 3.2KB, 가득 차면 약 13MB(라우터=차원 하나당). 네더·엔드는 칸당 수백 바이트.
- `-Dfurnace.worldgen.sliceCache=false` 로 끈다.

### 3-4. 지표 위 공기 블록의 밀도 계산 생략 (`Aquifer.computeSubstanceNonPositive`)
셀의 밀도 상한(기존 셀 skip 과 같은 `cellBounds`)이 음수면 그 셀 블록은 모두 밀도 ≤ 0 이라 바닐라 경로는 늘 aquifer 로 간다. aquifer 는 용암 전역 수위·
"최대 수위 + 5 위면 공기" 갈래에서 밀도 값을 쓰지 않는다. 이 셀에서는 aquifer 가 밀도 값이 필요한 갈래(`computeSubstanceSlow`)에 갈 때만 블록 밀도를 계산한다.
갈래·부수효과(`shouldScheduleFluidUpdate`)는 `computeSubstance` 와 같다. Beardifier 가 닿는 셀, `-Dfurnace.aqwatch` 디버그 중에는 쓰지 않는다.
DisabledAquifer(네더 등)는 밀도를 부호로만 쓴다.

### 3-5. 작은 것
- `RangeChoice.minValue/maxValue` 를 만들 때 한 번 구한다(바닐라는 부를 때마다 두 가지를 다시 구한다. Ap2 MIN/MAX 가 블록마다 묻는다). 레코드 성분이라 구조 동등성(같은 보간기 공유)은 그대로다.
- 표면 규칙의 바이옴 조회에서 청크 바로 바깥 한 줄 quart 를 HashMap 대신 6×6×높이 배열에 메모한다(표본 CPU 의 약 8% 가 `HashMap.get` 이었다). 값은 같은 `sampleQuart` 결과다.

## 4. 측정 방법

```
# 클래스 경로는 :furnace-server 시험 런타임 클래스 경로(이 문서 작업에서는 gradle 초기화 스크립트로 뽑아 java 로 바로 실행했다)
java -cp <cp> org.domi.furnace.server.world.gen.NoiseIdentityBench hash out.txt          # 동일성 해시(기준·새 빌드 파일을 cmp)
java -cp <cp> org.domi.furnace.server.world.gen.NoiseIdentityBench time overworld,nether,end,amplified   # 1 스레드 단계별 ms/청크
java -cp <cp> org.domi.furnace.server.world.gen.NoiseIdentityBench area overworld 3 24   # 이어진 땅, 3 스레드
java -Dfurnace.bench.jfr=out.jfr -cp <cp> ... pristine 2 150                              # 예열 뒤 1ms 표본 JFR
# gradle 로: -Dfurnace.bench=true :furnace-server:test --tests '*NoiseIdentityBench'
```
기준 빌드는 기준 커밋의 클래스에 같은 측정 파일(과 `NoiseWorldSource` 의 측정용 접근자 3개)만 얹어 돌렸다.

## 5. 남은 후보 (이번 PR 밖)

1. **네더 새 청크가 오버월드보다 두 배 느리다**(원시 청크 약 65ms, 지형 채우기는 1ms): CPU 의 약 60% 가 표면 규칙의 블록별 바이옴 조회
   `SurfaceSystem.biomeAtBlock`(바이옴 확대 지터 계산) 자체다. 바닐라도 블록마다 조회하지만 같은 quart 결과를 기둥 단위로 나누면 크게 줄 것이다.
2. 바이옴 `ClimateRTree` 검색(오버월드 원시 청크 CPU 의 약 8%).
3. 남은 꼭짓점 계산은 거의 Perlin 표본 자체다(BlendedNoise 40 옥타브, NormalNoise). 더 줄이려면 벡터화나 기둥 단위 일괄 계산이 필요하다.
4. 블록별 밀도 트리(인터페이스 호출 10여 번)와 aquifer 느린 갈래.
