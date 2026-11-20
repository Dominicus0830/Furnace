# NMS shim 2·3번 묶음 결정 (nms-shim-batch2) — 2026-11-20

설계 초안: 공유 폴더 `furnace/shim-coverage-plan-2026-11-20/batches-2-4-design-draft.md`(저장소에는 `docs/feature-parity/compat-nms-batches-2-4-2026-11-20.md`). 두 결정 모두 코디네이터가 추천대로 확정했다(11-20). 다르게 원하면 알려 주면 된다.

| # | 물음 | 이렇게 했고, 그래서 어떻게 되나 | Paper 와 다른 점 |
|---|---|---|---|
| D2-1 | **확정:** Axiom 같은 플러그인이 "지금 서버가 틱을 도는 스레드인가?"(`MinecraftServer#isSameThread`)를 물을 때, Furnace 의 region 스레드(지역마다 도는 스레드)에서도 "예"라고 답할까? | **예.** 전역 스레드, region 스레드, Bukkit 동기 작업·명령·이벤트 안이면 "예". 예: Axiom 은 "예"일 때만 건축 작업을 그 자리에서 돌리고 아니면 다음 틱으로 미루는데, Furnace 에서도 Paper 처럼 바로 돈다. | 없다. Paper 1.21.1 도 "틱을 도는 스레드면 참"이다(Folia 에서는 region 스레드도 참). Bukkit `isPrimaryThread` 보다 넓은 것도 Paper 와 같다. |
| D3-1 | **확정:** VeinMiner 가 광맥을 이어 부술 때, 부술 칸이 플레이어와 다른 region(다른 스레드가 맡은 지역)에 있으면 어떻게 할까? | 같은 region 칸은 그 자리에서 부수고 진짜 결과(부쉈는지)를 돌려준다. 다른 region 칸은 그 region 스레드로 "부숴 달라"고 넘기고, 기다리지 않고 "부쉈다"(true)로 답한다. 실제로 부수는 일(블록 부수기 이벤트·드롭·도구 닳기)은 그 region 에서 손으로 부술 때와 똑같이 일어난다. | 다른 region 칸에서만: Paper 는 그 자리에서 결과를 알려 주지만 Furnace 는 "부쉈다"로 먼저 답하므로, 보호 플러그인이 그 칸 부수기를 취소해도 VeinMiner 는 부순 것으로 보고 다음 칸으로 넘어간다(그 칸은 안 부서진다). 기다리게 하면 두 region 스레드가 서로 기다려 서버가 멈출 수 있어서 이렇게 했다. |
