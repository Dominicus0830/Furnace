# T-141 E4c — 플레이어별 거리 (2026-11-15)

기준: Paper 1.21.1(33ff3dff) Moonrise `RegionizedPlayerChunkLoader`(ViewDistanceHolder·PlayerChunkLoaderData.getTickDistance/getLoadViewDistance/getSendViewDistance),
`CraftPlayer`(MC-Utils·Moonrise 패치), `CraftWorld`(MC-Utils "View distance … is out of range of [2, 32]", Moonrise 시뮬레이션 범위 검사). 공식·API 는 `docs/api/view-distance.md`.

- **Paper 1.21.1 의 getViewDistance 는 load − 2 다.** `CraftPlayer#getViewDistance` = `ChunkSystem.getLoadViewDistance(player) - 1` 이고, Moonrise 의
  `ChunkSystem.getLoadViewDistance` 가 이미 `lastLoadDistance - 1` 을 돌려준다. 그래서 기본값은 서버 시야 거리 − 1, `setViewDistance(v)` 뒤에는 v 다.
  Furnace 도 그 값을 돌려준다(플러그인이 보는 값 우선). 월드 쪽 `CraftWorld#getViewDistance` 는 `getAPIViewDistance` = load − 1 이라 서버 값 그대로다.
- 범위(코디네이터 동의, 11-15): 서버 값 이하로 **낮추기**만 Paper 대로 반영한다. 넘는 값은 서버 값으로 자르고 종류마다 경고 한 번(올릴 설정 안내). 넘는 값과 플레이어별 틱 거리는 T-141v(T-170 뒤).
- 서버 구현: `Player` 에 플레이어 값 셋(-1 = 월드)과 보내는 거리 상한(`sendCap`). `Player#viewDistance(int)` 는 이제 바탕 거리(min(서버, 클라이언트))를 받고 실제 값은 상한과의 최솟값이다 —
  예전 호출(접속·client_information·서버 시야 변경)은 그대로다. 플레이어 값이 없으면 상한은 `Integer.MAX_VALUE` 라 결과가 예전과 같다.
  `ServerCore.applyPlayerDistances` 가 상한을 다시 잡고, 보내는 거리가 바뀌면 `set_chunk_cache_radius`(Paper 처럼 보내는 거리)를 그 플레이어에게만 보낸다.
  서버 시야·시뮬레이션 거리가 바뀌면 플레이어 값이 있는 사람만 다시 푼다.
- 클라이언트 몫은 바닐라(렌더 거리 그대로)다. Paper 기본 `auto-config-send-distance` 는 렌더 거리 + 1 까지 보낸다 — 원래부터 다른 점.
- 시험: `BukkitViewDistanceTest`(서버 시야 8·시뮬레이션 6: 기본 7/8/6, setViewDistance(4) → 4·보내기 5·다음 틱 추적 5, send 3, 서버 값 변경 뒤 유지, 풀기,
  20 은 잘림, 시뮬레이션은 서버 값, 월드 범위 예외 문구·주 월드 쓰기).
