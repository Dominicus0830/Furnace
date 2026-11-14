## 2026-11-14 하늘빛 원천 처음 계산: 공기뿐인 구역 건너뛰기 (PR #329 검토 S1)

- 바닐라 1.21.1 `ChunkSkyLightSources.findLowestSourceY`(`fillFrom`)는 위에서 아래로 내려가다 **공기뿐인 구역(`hasOnlyAir`, air·cave_air·void_air)** 을 만나면 위 칸 상태를 AIR 로 되돌리고 구역을 통째로 건너뛴다. 그래서 공기뿐인 구역 바로 위 구역의 **맨 아래 줄(y&15==0) 블록의 아래 면**은 막힘 검사를 받지 않는다. 바닥 반 블록 같은 블록이 거기 있으면 원천이 그 아래로 계속 내려간다.
- 이 계산은 생성 때와 **청크를 올릴 때마다**(`ChunkStatusTasks.initializeLight` → `initializeLightSources`) 돈다. 블록이 바뀔 때의 `update`·`findLowestSourceBelow` 는 칸마다 검사해서 이 특이점이 없다(Furnace `updateSource`·`scanDown` 도 그대로).
- Furnace `SkyLightEngine.computeSources` 가 이제 같은 규칙을 따른다. 공기뿐인 판정은 힌트가 아니라 결과를 정하므로 `LightWorld.sectionHasOnlyAir`·`StateReader.sectionHasOnlyAir` 를 새로 두었다. 실제 세계는 구역의 비공기 수(scratch 가 열려 있으면 칸을 훑음, `ChunkSection.onlyAirExact`)로 답하고, 기본 구현은 4096칸을 훑는다.

### 바닐라 서버 jar 대조 (2026-11-14, 평지 생성 설정·시드 12345·정오, 술어 light.min 1·15)
- 기반암 1·공기 47·매끄러운 돌 반 블록(y=-16, 구역 -1 맨 아래 줄): 5개 열 × 15개 높이 = 75곳. 바닐라·Furnace 모두 y=-5..-63 이 15. 고치기 전 Furnace 는 y=-17 이하 0.
- 대조로 공기 46(반 블록 y=-17, 구역 -2 맨 위 줄): 75곳. 둘 다 y=-18 이하 0.
- 위 반 블록 월드를 다시 열고(Furnace 는 저장 빛 + 원천만 다시 셈) (8,-10,8)에 돌을 놓았다 지운 뒤 묻기: 24곳 모두 같다. 지운 뒤에는 `findLowestSourceBelow` 가 칸마다 검사해서 반 블록에서 멈추므로, 그 열의 반 블록 아래는 둘 다 1..14(옆 열에서 들어온 빛)다.
- 자연 지형: 바닐라로 만든 월드(시드 12345, 16×16 청크 강제 로드)를 `VanillaLightTest` 로 Furnace 엔진에 넣어 비교했다. 안쪽 256청크, 829만 칸에서 고치기 전·뒤 모두 차이 0이다. 이 지역에는 이 꼴이 없었다. PR #329 문서의 "다른 5곳"은 블록 차이 쪽일 가능성이 크다(확인 안 함).

### 성능
- 같은 400청크로 원천 계산만 반복(`computeStoredSources`): 청크당 약 50µs → 약 30µs. 블록이 섞인 팔레트라 한 상태 힌트가 없던 공기 구역도 비공기 수로 바로 건너뛰기 때문이다.

### 시험
- `SkySourceAirSectionTest`: 위 평지 장면 4가지(맨 아래 줄·맨 위 줄·아래 구역에 돌 한 칸·cave_air 구역)와, 바닐라 `findLowestSourceY` 를 그대로 옮긴 모형과 무작위 400열을 비교한다(힌트 있음·없음 둘 다). 특이점 분기를 끄면 3개가 실패한다.
