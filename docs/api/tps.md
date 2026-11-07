# TPS API — `Furnace.tps()`·region TPS (2026-11-07, 묶음 Q3)

Paper 의 `Bukkit.getTPS()` 에 해당한다. Furnace 는 전역 스레드 하나와 region 여러 개가 **각자 박자로** 틱하므로 "서버 TPS" 한 값이 없다. 도미니코 결정(2026-11-07)에 따라
**활성 region 평균**을 기본값으로 주고, 특정 region 의 값도 따로 읽게 했다. 명령 `/tps` 도 같은 값을 쓴다.

## 정의

| 값 | 무엇 | 비고 |
|---|---|---|
| **활성 region 평균** (`active*`) | 플레이어가 있는 region 들의 최근 TPS 를 **플레이어 수로 가중한 평균** | "플레이어 한 명이 겪는 평균 틱 속도". 플레이어 20명이 사는 region 이 12 TPS 이고 1명짜리가 20 TPS 면 (20×12+20)/21 ≈ 12.4. 활성 region 평균이 **높은 값 하나(예: 빈 region)** 에 끌려 올라가지 않게 플레이어 없는 region 은 뺀다 |
| 가장 느린 region (`slowestActive`, `slowestRegion`) | 활성 region 중 / 전체 region 중 최저 5초 값 | 평균이 가리는 "누가 느린가"를 본다 |
| 전역 박동 (`global*`) | 시간·날씨·명령 큐·보더를 돌리는 전역 스레드의 초당 박동 | 늘 20 이 목표(`/tick rate` 와 무관) |

- **창**: 5초는 지금 값, 1분·5분·15분은 전역 스레드가 **1초마다** 적은 "그 순간 활성 평균(최근 1초 창)" 표본의 단순 평균이다. region 은 합쳐지고 나뉘어 오래 이어지는 기록을 가질 수 없어(region 별 기록은 6.4초) 이렇게 따로 쌓는다.
- **활성 region 이 없으면**(접속자 없음) 활성 평균 자리에 **전역 박동 값**이 들어간다(Paper 가 빈 서버에서 20 을 주는 것과 같은 결과). 기록이 1초 미만인 region(막 생김·분할 직후)은 잰 값에서 뺀다. 아무것도 잴 수 없으면 NaN.
- 목표를 넘는 값(`/tick sprint`)은 그대로 준다(API 는 자르지 않는다 — 명령 화면만 목표로 자르고 `*` 를 붙인다).
- 플레이어 수는 region 의 마지막 틱 끝(과 편입·합치기·나누기 때) 값이라 이동·접속 직후 한 틱쯤 낡을 수 있다.

## 플러그인 API

```java
ServerTps t = Furnace.server().tps();   // 아무 스레드, 값 복사본
t.current();            // 활성 region 평균 최근 5초
t.oneFiveFifteen();     // Paper getTPS() 모양 {1분, 5분, 15분} (활성 평균)
t.activeRegions(); t.activePlayers(); t.slowestActive(); t.slowestRegion();
t.global5s(); t.global1m(); ...

// 특정 region
World w = Furnace.server().world("minecraft:overworld");
w.tpsAt(chunkX, chunkZ)         // OptionalDouble — 그 청크 region 의 최근 5초 TPS(없거나 잴 수 없으면 empty)
w.tpsAt(blockPos)
w.regionAt(chunkX, chunkZ)      // RegionInfo.tps()/playerCount()/active()/lastTickNanos() …
w.region(id)                    // 번호로
w.regions()                     // 모든 region (id 순)
Furnace.server().regionTps(location)   // 위치로
player.regionTps()                      // 그 플레이어가 겪는 region TPS
```
- 서버가 값을 주지 않는 구현(시험용 대역)에서는 `tps()` 가 모든 값 NaN 인 `ServerTps.UNKNOWN`, `tpsAt` 은 empty.
- 모든 타입은 버전 중립 값 객체(레코드·`OptionalDouble`)다. 내부 클래스(`Regionizer` 등)는 노출하지 않는다.

## 명령 (`/tps`, 권한 3)

- `/tps` — 전역 TPS 줄, **활성 region 평균 줄**(5초·1분·5분·15분, 활성 region 수·플레이어 수·그중 가장 느린 값), 차원별 가장 느린 region.
- `/tps regions [개수]` — 모든 차원의 region 을 느린 순으로(기본 10개, 최대 100). 각 줄: 차원·번호·TPS·플레이어 수·청크 수·범위·직전/최장 틱.
- `/tps here` — 명령을 실행한 위치(플레이어면 자기 위치)의 region.
- `/tps player <대상>` — 그 플레이어가 있는 region.
- `/tps region <차원> <번호>` — region 번호로(`/tps regions` 로 번호를 본다). 합쳐졌거나 나뉘어 사라졌으면 "없다".
- 색: 목표 대비 90% 이상 초록, 80% 이상 노랑, 아래 빨강, 잴 수 없으면 회색 `-`.

## 구현 위치
`api/world/ServerTps`·`RegionInfo.playerCount`·`World.tpsAt/region` · `server/sched/ActiveTpsHistory`(1초 표본 기록) · `Regionizer.activeSummary`(잠금 안에서 region 별 시작 시각 고리·플레이어 수만 읽음) · `ServerCore.serverTps`/`sampleActiveTps` · `TpsCommands`.
