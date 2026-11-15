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
- 검토 R1(11-15): 실제 보내는 거리를 필드로 두면 `client_information`(네티 스레드)과 플러그인 상한(잠금 안)이 각자 계산해 쓰다 갱신이 사라졌다(20만 판 중 2,281판).
  이제 `Player#viewDistance()` 가 읽을 때 max(2, min(바탕, 상한)) 을 계산하고 두 setter 는 자기 값만 쓴다(`PlayerDistanceRaceTest`). 플러그인 값 잠금은 Player 모니터와 나누지 않게 `distanceLock`.
- 검토 R2: `Player#getSimulationDistance` 도 World 와 같게 min(시뮬레이션, 시야 + 1).
- Furnace 자체 API `Player#viewDistance()` 도 Paper 와 같은 값(load − 2)으로 둔다. Paper 공식에서는 load 가 tick + 1 에도 묶여 어느 정의든 set → get 이 늘 같은 값으로 돌아오지 않으므로,
  Bukkit 과 Furnace API 가 같은 숫자를 주는 쪽이 덜 헷갈린다고 판단했다(검토 S4 — 코디네이터에 알림).
