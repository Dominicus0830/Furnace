# 네더 표면 규칙 바이옴 조회·구조물 바닥 열 성능 (2026-11-11)

`perf-worldgen-noise-2026-11-11.md`(PR #222) 5장 후보 1의 후속이다. 네더 새 청크 CPU 의 약 60% 가 표면 규칙의 블록별 바이옴 조회였다.
생성 결과(블록·바이옴·유체 표시·구조물 시작점)는 그대로 두고 계산만 줄였다.

## 1. 결과 요약

4코어 컨테이너, 시드 12345, `-Xmx3g`. 기준 = 기준 브랜치 dafcc842(PR #222 병합). 기준·새 빌드를 번갈아 두 번 돌렸다(값은 두 번 모두의 범위).

| 장면 | 기준 | 새 빌드 | 변화 |
|---|---|---|---|
| 네더 이어진 땅 24×24, 3 스레드 — 청크당 CPU | 12.0~15.0 ms | 4.0~5.2 ms | **약 -63%** |
| 같은 장면 벽시계 | 2.5~3.2 초 | 0.8~1.3 초 | **약 -60%** |
| 네더 흩어진 원시 청크 300개, 1 스레드(`pristine`) | 4.0~4.5 초 | 1.4 초 | **약 -66%** |
| 오버월드 이어진 땅 24×24, 3 스레드 — 청크당 CPU | 15.5~18.8 ms | 14.0~17.0 ms | 약 -9% |
| 구조물 시작점 12설정 × 120×120 청크(`starts`) | 2분 44초 | 1분 11초 | -57% |

나눠 보면(같은 날 따로 잰 값): 바이옴 표(2-1)만 넣은 빌드는 네더 이어진 땅 7.3~8.4 ms/청크, `pristine` 2.4~2.5 초였다. 나머지는 바닥 열(2-2)이다.

JFR(1ms 표본, 네더 `pristine`): 기준은 표본 2323개 중 1449개(62%)가 `biomeAtBlock` 이었다. 바이옴 표 뒤에는 1716개 중 347개(20%)이고, 그중 대부분은
이웃 청크 쪽 꼭짓점 바이옴을 아직 몰라 거리 계산으로 가는 경우다(아래 2-1 통계).

## 2. 바꾼 곳

### 2-1. 표면 규칙 바이옴 조회 꼭짓점 표 (`SurfaceSystem.biomeAtBlockTable`)
바닐라 `BiomeManager.getBiome` 은 블록(x−2, y−2, z−2)을 둘러싼 quart 꼭짓점 8개마다 LCG 9번으로 지터를 만들고 거리를 비교한다. 표면 규칙은 거의 모든 돌 블록에서
바이옴을 묻는다(네더는 청크당 약 2만 번). 청크마다 6×6×높이 꼭짓점 표를 두고

1. 꼭짓점 8개의 바이옴이 모두 같으면(이미 아는 값으로) 거리 계산 없이 그 바이옴을 돌려준다. 어느 꼭짓점이 이기든 답이 같다.
2. 아니면 지터 3개를 꼭짓점마다 한 번만 계산해 표에 두고, 바닐라와 같은 식·같은 덧셈 순서(z, y, x)·같은 비교(`bestDistance > distance`, 0..7 순서)로 고른다.

꼭짓점 바이옴의 출처는 예전과 같다: 청크 안쪽 높이 범위는 `sampleChunk` 결과, 나머지는 처음 필요할 때 `sampleQuart`.
네더 표본 통계(140청크): 생략 55%, 이웃 꼭짓점을 아직 몰라 거리 계산 43%, 바이옴이 섞여 거리 계산 1.7%. 지터 계산은 조회 22번에 한 번꼴.
`-Dfurnace.surface.zoomTable=false` 로 예전 경로를 쓴다(대조용).

### 2-2. 구조물 바닥 열 = 노이즈 단계만 (`NoiseWorldSource.baseBlock`)
바닐라 `NoiseBasedChunkGenerator.getBaseColumn` 은 `iterateNoiseColumn`(노이즈·대수층·광맥, 표면 규칙 없음)이다(1.21.1 서버 jar `dzb` 바이트코드로 확인).
Furnace 는 표면 규칙·바이옴까지 한 청크(`generate`)를 만들어 읽었다. 네더 화석은 청크마다 시작점 후보에서 바닥 열을 읽어, 새 청크 하나에 표면 규칙이 약 4.2번
돌았다(표본 140청크에 `buildSurface` 590번). 이제 높이 질의와 같은 `noiseBlockIds` 를 캐시해 읽는다.

- 네더: 표면 규칙은 네더랙을 다른 단단한 블록으로만 바꾸므로 화석·폐허 포털 판정 결과가 같다.
- 오버월드: 빙산·악지 기둥처럼 표면 단계가 공기·물에 놓은 블록을 예전에는 바닥으로 보았다 — 이제 바닐라처럼 보지 않는다. 표본 안에서 바뀐 시작점은 없었다.
- 생성 높이 밖 y 는 공기를 돌려준다(바닐라 `NoiseColumn.getBlock`).

## 3. 동일성 확인

- `NoiseIdentityBench hash`: PR #222 의 26설정에 네더 시드 6개(1, −1, 8675309, Long.MAX_VALUE, 20261111, −4172144997902289642)·월드 경계 위치
  (청크 1874999·−1875000·1875000~1875003)를 더하고, 네더 설정은 모두 3 스레드 동시 5×5 를 만든다. 원시 청크(지형·표면·카버·바이옴·유체 표시) 1131개.
  기준 dafcc842 `TOTAL 17b110d07e943668 chunks=1131` = 바이옴 표만 넣은 빌드 = 두 수정을 모두 넣은 빌드(파일 `cmp` 동일). 네더 화석은 beard_thin 이라 지형 해시가 배치도 덮는다.
- `NoiseIdentityBench starts`: 오버월드 시드 5개·amplified·large_biomes·네더 시드 5개, 각 120×120 청크의 구조물 시작점(종류·상자·조각 상자) 2419개
  (네더 화석 1524, 폐허 포털 오버월드 계열 58·네더 38 포함)가 기준과 같다.
- 시험: `SurfaceZoomTableTest`(6설정 × 33청크의 모든 블록 바이옴 = 블록마다 지터 계산 경로, 바이옴 경계 청크 20개 이상, 표 끔 빌드와 원시 청크 블록 대조를
  4 스레드 동시 생성으로), `NoiseWorldSourceBaseColumnTest`(바닥 열은 노이즈 단계 블록만, 표면이 바꾼 블록만 다름, 생성 높이 밖은 공기).
  기존 `SurfaceParityTest`·`CompiledSurfaceRuleTest`·`NetherFossilStructureTest`·`StructureStartSharingTest` 와 전체 시험 통과.

## 4. 측정 방법

```
java -cp <cp> org.domi.furnace.server.world.gen.NoiseIdentityBench hash out.txt                 # 기준·새 빌드 파일을 cmp
java -cp <cp> org.domi.furnace.server.world.gen.NoiseIdentityBench starts out.txt 120          # 구조물 시작점 목록(diff)
java -Dfurnace.bench.dim=nether -cp <cp> ... pristine 1 300                                    # 흩어진 원시 청크, 1 스레드
java -Dfurnace.bench.rounds=3 -cp <cp> ... area nether 3 24                                    # 이어진 땅, 3 스레드
java -Dfurnace.bench.dim=nether -Dfurnace.bench.jfr=out.jfr -cp <cp> ... pristine 1 300        # JFR
```
기준 빌드는 기준 커밋 클래스에 같은 측정 파일만 얹어(javac) 돌렸다.

## 5. 남은 후보

1. ~~이웃 청크 쪽 꼭짓점 바이옴 출처·y 자르기~~ → 후속 PR 에서 바닐라처럼 고쳤다(`context-notes/2026-11-11-surface-stored-biome.md`).
2. 표면 규칙 노드 평가(`SurfaceSystem$2.apply`) 자체가 이제 네더 표본의 약 15%.
