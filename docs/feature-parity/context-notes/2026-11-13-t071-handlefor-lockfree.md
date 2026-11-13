# T-071 PluginHost.handleFor 잠금 제거 (2026-11-13)

근거: 공유 폴더 `furnace/reviews/opt-review/O13-compat.md` P-O13-01, `wave2-rebuttal-compat.md`. 어댑터(Bukkit 호환 계층)의 `Compat.world(dim)` → `world(dim)` → `handleFor` 는
`getWorld`·`getLocation`·`Block.getType` 등 거의 모든 호출이 지나는 길인데, 예전 `ConcurrentHashMap.compute` 는 값이 있어도 bin 을 잠가 스레드를 늘릴수록 처리량이 줄었다(1→4 스레드 17→2.8 M/s).

## 바뀐 것
`handleFor(w)` 가 `worldHandles.get(dim)` 을 먼저 읽고 `cur != null && cur.handle() == w` 면 그대로 돌려준다. 없거나 다른(내린 뒤 같은 키로 다시 올린) 월드의 낡은 손잡이일 때만 예전과 똑같은 `compute`.

## 결과가 같은 이유
- 빠른 길은 `compute` 가 "이미 같은 World 의 손잡이가 있으면 그것을 돌려준다" 고 하던 분기를 잠금 없이 한 것뿐이다. 낡은 손잡이를 돌려줄 길은 없다(`handle() == w` 를 검사).
- 새 손잡이를 만들 때는 여전히 `compute` 안(원자적)이라 동시에 처음 조회해도 하나로 수렴한다. `worldDetached`(`computeIfPresent`)와의 순서도 그대로.
- 시험: `MultiWorldTest.handleFor*`(동일성·내렸다 다시 올림 = 새 손잡이·detach 전에 새 월드가 올라온 경우·동시 첫 조회 20회×8스레드).

## 측정 (`HandleForBench`, 4코어 컨테이너, 옛 compute 대 새 get, 최고 5회)
| 스레드 | 옛 | 새 |
|---|---|---|
| 1 | 30 M/s | 232 M/s |
| 2 | 12 M/s | 383 M/s |
| 4 | 6.5 M/s | 703 M/s |

옛 방식은 스레드가 늘수록 줄고 새 방식은 스레드 수에 맞춰 는다. 실행: `./gradlew -PskipOracle=true :furnace-server:bench --tests '*HandleForBench*'`.

## 안 한 것
서버 `World` 에 `WorldHandle` 붙임 칸을 두는 안(O13 보고서의 "더 나아가")은 하지 않았다 — 지도 읽기가 이미 호출당 수 ns 이고 World 파일이 다른 TASK(T-011·T-016)와 겹친다.
