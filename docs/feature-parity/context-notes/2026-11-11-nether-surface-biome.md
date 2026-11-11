## 2026-11-11 네더 표면 규칙 바이옴 조회·구조물 바닥 열 — 결과 같은 최적화의 함정 (nether-surface-biome)

상세·숫자는 `docs/feature-parity/perf-nether-surface-2026-11-11.md`.

- 표면 규칙 바이옴 조회(바닐라 `BiomeManager.getBiome`)는 블록마다 꼭짓점 8개의 지터 거리를 계산한다. 꼭짓점 8개의 바이옴이 모두 같으면 어느 꼭짓점이 이기든 답이 같다 —
  이 판정에 쓰는 꼭짓점 바이옴은 **예전 경로가 그 quart 에 돌려주던 값과 같은 출처**여야 한다. 청크 안쪽 높이 범위는 `sampleChunk`(R-트리 직전 leaf 힌트) 결과,
  나머지(이웃 청크·높이 밖 한 칸)는 `sampleQuart`(힌트 없음)다. 둘은 거리 동률에서 다를 수 있으니 같은 quart 에 두 출처를 섞지 않는다.
- 지터(fiddle x·y·z)는 시드·quart 좌표만의 함수라 청크 안에서 메모해도 된다. 거리 식의 덧셈 순서는 `z + y + x` 그대로(바닐라 `getFiddledDistance`).
- 예전 경로는 `-Dfurnace.surface.zoomTable=false` 로 켤 수 있다(대조용, 생성기마다 만들 때 읽는다).
- **구조물 바닥 열 `TerrainAccess.baseBlock` = 바닐라 `getBaseColumn` = `iterateNoiseColumn`(노이즈·대수층·광맥만, 표면 규칙 없음).** `javap` 로 `dzb.a(IILdcy;Ldzm;)Lddi;` 가
  `iterateNoiseColumn` 만 부르는 것을 확인했다. 예전 Furnace 는 표면 규칙까지 한 청크(`generate`)를 만들어 읽었다 — 네더 화석이 시작점 후보마다 이웃 청크 표면을 새로 만들어
  새 청크 하나에 표면 규칙이 약 4번 돌았다. 높이 질의(`computeSurfaceHeights`)는 이미 노이즈만 쓰고 있었다.
- 네더에서 표면 규칙은 기본 블록(네더랙)만 다른 단단한 블록으로 바꾸고 공기·용암은 건드리지 않아서, 네더 화석·네더 폐허 포털 판정은 두 열에서 같다(영혼 모래는 이름으로 따로 본다).
  오버월드는 빙산(`frozenOceanExtension`)·악지 기둥(`erodedBadlandsExtension`)이 공기·물에 블록을 놓아 바다 폐허 포털 높이가 다를 수 있었다 — 이제 바닐라와 같다.
  표본(12설정, 120×120 청크, 시작점 2419개) 안에서는 바뀐 시작점이 없었다.
- 바닥 열 밖(생성 높이 밖) y 는 공기(바닐라 `NoiseColumn.getBlock`). 예전 `ChunkColumn.getBlock` 은 차원 밖에서 예외였다.
- 측정 함정: 네더 `pristine`·`area` 측정에 구조물 시작점(화석)이 포함된다. 표면 규칙 비용만 보려면 화석 수정 전후를 나눠 잰다.
