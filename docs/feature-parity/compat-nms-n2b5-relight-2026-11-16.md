# T-197(N2b-5) 편집 뒤 빛 다시 계산 — 설계 (2026-11-16)

상태: **독립 검토 조건부 승인(`furnace/reviews/t197-relight-design-review-2026-11-16.md`) → 필수 R1~R6 반영, 구현됨, 코드 검토(조건부 승인) F1~F5 반영**. 반영 내역은 11장, 바뀐 절은 4.1·4.3·4.4·4.6·4.8·5·7·9.
관련: `compat-nms-n2b-design-2026-11-14.md` 3.8·6장(N2b-5), 결정 D2, `compat-real-plugin-gaps-2026-11-16.md` 3장(호출 모양), `compat-nms-n2b6-2026-11-16.md`(실측),
`optimization-tasks.md` 12절 T-197, 빛 작업 모드 `context-notes/2026-11-13-t064-light-pool.md`, 저장 빛 T-012.

## 0. 결론

- **선행 조건은 충족됐다.** relight 가 기대는 것은 ① 플러그인이 쓴 섹션을 주인 region 이 들여오기(N2b-4, #396), ② 청크 다시 보내기 패킷 shim·`ChunkMap`·`ChunkHolder`·티켓(N2b-6, #412),
  ③ `getChunkAtAsync`(T-141)다. 모두 병합됐다. N3a 의 나머지(엔티티 추적기·연결 파이프라인·패킷 15종)는 relight 경로에 나오지 않는다(2장 3).
- 설계 요지: `starlight$serverRelightChunks` 를 **일반 서버 기능 "청크 빛 다시 계산"**(`ChunkInternals.relightChunks`)으로 받는다.
  주인 region 이 거울을 맞추고 블록을 얼린 뒤(T-064 와 같은 얼리기), **일꾼 스레드가 그 청크와 둘레 1칸을 새 빛으로 처음부터 계산**하고,
  주인 region 이 결과를 그 청크에만 끼운다(Paper 와 같이 둘레 청크의 빛은 그대로). 계산하는 동안 그 둘레의 빛 검사는 **붙잡아 두었다가** 끼운 뒤 풀어
  잃어버리는 갱신이 없게 한다. region 스레드가 하는 일은 얼리기·끼우기뿐이라 틱 시간에 거의 영향이 없다.
- FAWE 전용 코드는 없다. 다른 플러그인이 같은 Paper 메서드를 불러도 같은 길이다.

## 1. 선행 조건 확인

| 필요한 것 | 어디서 | 상태 |
|---|---|---|
| FAWE 가 쓴 섹션을 서버 청크로 들여오기(다른 스레드 쓰기 → 주인 맞추기) | N2b-4 `ChunkColumn.importSection`, `Region.syncMirror` | 병합(#396) |
| `ThreadedLevelLightEngine` shim·`getLightEngine()` | N2b-6 | 병합(#412) — 지금은 콜백만 부름 |
| 청크 통째 다시 보내기(`ClientboundLevelChunkWithLightPacket` → `World#refreshChunk`), `ChunkMap.getPlayers` | N2b-6 | 병합 |
| FAWE 티켓(`FAWE_TICKET`, `addTicketAtLevel`) | N2b-6 `NmsTickets` | 병합 |
| `CraftWorld#getChunkAtAsync` | T-141 | 병합 |
| 서버 빛 엔진: 새 청크 빛(`LevelLight.lightChunk`), 하늘빛 원천(`SkyLightEngine.computeSources`), 얼리기(`ChunkColumn.freezeForLight`) | T-012·T-064 | 있음 |

N2b 설계 6장은 "N2b-5 의 **패킷 부분**은 N3a 와 함께"였다. 그 패킷 부분(청크 다시 보내기)은 N2b-6 에서 이미 했다. relight 에 새로 필요한 패킷은 빛 갱신(`LightUpdate`)뿐이고 서버에 있다.
그래서 이 작업을 막는 선행 작업은 없다.

## 2. Paper 가 하는 일 (원문 확인)

Paper 1.21.1 의 빛은 Moonrise(Starlight) 패치다. 같은 코드를 `Tuinity/Moonrise` `mc/1.21.1` 에서 읽었다
(`ThreadedLevelLightEngineMixin#starlight$serverRelightChunks`, `StarLightInterface#relightChunks`, `StarLightEngine#relightChunks`).
관측 가능한 계약:

- **P1 받기**: 들어온 모음을 순서 유지 집합으로 바꾼다. 청크마다 relight 티켓을 걸고, 그 청크가 로드돼 있지 않거나 빛이 맞지 않으면(`isLightCorrect`·`LIGHT` 이상) 빼고 티켓을 거둔다.
  **돌려주는 값 = 받아들인 청크 수**. 부른 스레드에서 바로 돌아온다.
- **P2 비동기**: 계산은 청크 시스템의 "무한 반경 작업"으로 다른 스레드에서 한다. 부른 스레드는 기다리지 않는다.
- **P3 값**: 받아들인 청크 C 마다, C 와 둘레 8청크(로드되고 빛이 맞는 것만)를 **빈 빛에서 처음부터** 계산한 세계를 만들고(앞서 계산한 청크는 다시 쓴다), 그 결과 중 **C 의 빛만** C 에 넣는다.
  둘레 청크의 실제 빛은 건드리지 않는다. 빛은 15칸 안에서만 퍼지므로 C 의 값은 "지금 블록에 대한 올바른 빛"이다.
  하늘빛 엔진이 모든 청크를 먼저 하고, 블록빛 엔진이 이어서 한다.
- **P4 알림**: 청크마다 빛 섹션 전부를 `onLightUpdate` 로 알리고(저장 대상), 청크 콜백을 부른 뒤, 그 청크를 가진 **모든** 플레이어에게 전 섹션 `ClientboundLightUpdatePacket` 을 보낸다(청크 작업으로).
- **P5 끝**: 모두 끝나면 `onComplete(다시 계산한 수)` 를 부르고 relight 티켓을 거둔다.
- **P6 동시성**: 계산하는 동안 주 스레드는 계속 블록을 바꾼다. 그 블록 변경의 빛 갱신은 빛 대기열에 쌓였다가 relight 가 끝난 뒤 다시 계산된 빛 위에서 처리된다.

## 3. FAWE 호출 모양 (`javap`, FAWE 2.16.2 `v1_21_R1`)

- `StarlightRelighter#fixLighting` → `TaskManager.task` 안에서 청크마다 `CraftWorld#getChunkAtAsync`(완료 때 `FAWE_TICKET` 을 레벨 `33+LIGHT 반경` 으로) →
  `CompletableFuture.allOf(...).thenAccept(...)` 에서 `invokeRelight(set, 빈 콜백, postProcessCallback)`.
  그래서 **relight 는 아무 스레드에서나 불린다**(로드 미래를 끝낸 스레드 — region 스레드일 수도 있다). 막지 말아야 한다.
- 청크 콜백은 FAWE 에서 빈 람다다. `onComplete(n)` 은 `n != set.size()` 면 경고를 남기고, `TaskManager.task(postProcessChunks)`(청크 다시 보내기·티켓 풀기)와 `TaskManager.async(다음 묶음)` 을 건다.
  FAWE 는 편집한 청크를 32×32 청크 구역별로 모아 한 번에 최대 1024청크를 넘긴다.
- 지금 Furnace shim 은 빛을 그대로 두고 부른 스레드에서 콜백을 곧바로 부른다(경고 `compat.nms-relight-unsupported`).

## 4. 설계

### 4.1 흐름

```
아무 스레드: starlight$serverRelightChunks(chunks, cb, done)
  → ChunkInternals.relightChunks(world, keys[], cb', done')     (shim 은 변환만)
  → World.relight: 받기(P1) — 핀 먼저(내리기 막기), 그다음 로드됐고 빛이 켜진 청크만 받음, 받은 수 돌려줌
       타일(8×8 청크)로 나눠 world 대기열에 넣음. 동시에 도는 타일은 일꾼 수 × 2 까지.
타일 준비 — 타일 기준 청크의 주인 region 스레드(작업):
  ① 타일 청크 중 이 region 것이 아닌 것은 떼어 그 주인에게 새 타일로
  ② 곧 붙잡을 3×3 합집합 안에 밀린 빛 검사(앞 relight 가 풀어 둔 것 포함)가 있을 때만 먼저 돌린다(R4). 빛 작업 끔은 그 자리 계산(틱 끝 일을 당겨 할 뿐),
     켬은 빛 작업 한 묶음으로 넘긴다(도는 묶음이 있으면 그것만 기다림 — region 스레드가 밀린 일 전부를 계산하지 않게, 코드 검토 F2). 넘긴 묶음은 ⑥ 에서 게시된다
  ③ 붙잡기 걸기: 타일 청크 C 마다 3×3 청크에 세계 단위 붙잡기 수 +1. 그 3×3 에 **다른 요청**의 붙잡기가 있으면 걸지 않고 타일을 미룬 줄에 넣는다
     (막던 붙잡기가 풀려 더는 막히지 않을 때만, 미룬 차례대로 대기열 **맨 앞**으로 돌아온다 — R4, 코드 검토 F3). 같은 요청의 타일끼리는 겹쳐도 된다
  ③ 뒤 ④⑤ 에서 예외가 나면(거울 맞추기·얼리기·일꾼 풀 거절) 얼린 것을 녹이고 붙잡기·핀을 풀어 끝으로 센다(코드 검토 F1). 설치 ⑧⑨ 예외도 같이 풀고 C 를 stale 표시
  ④ U = 요청 청크마다 3×3 의 합집합 중 이 region 이 가진·빛 있는 청크(S4). U 의 거울 맞추기(대기 있으면), 구역 얼리기(freezeForLight),
     U 의 멤버십을 **키 → 이번 로드의 컬럼 객체**로 기억(R3)
  ⑤ 일꾼 풀에 계산 작업
  (준비·설치는 region 마다 한 틱에 0.25 ms 까지 — 넘으면 다음 틱. 빛 작업 켬에서 묶음이 아직 도는 중이면 기다리지 않고 다음 틱 — 6장 M3)
일꾼: U 를 새 ChunkLight 로 처음부터 계산(LevelLight.lightChunk 를 U 전부에) → 타일 청크의 빛만 결과로(compact)
설치 — 타일 청크 C 의 지금 주인 region 스레드(작업, C 마다):
  ⑥ awaitLightIdle(빛 작업 모드), C 가 로드·빛 있음 확인(종료됐으면 끼우지 않음)
  ⑦ 3×3(C)(C 자신 포함) 에 "이 region 이 가진·빛 있는" 컬럼 중 U 에 기억한 컬럼과 **다른 객체**가 있으면(새로 로드·빛 켜짐·합치기, 내렸다 다시 올림·재생성 — R3)
     → C 만 다시 준비(③의 붙잡기는 그대로 넘긴다)
  ⑧ C 의 하늘빛·블록빛 층을 결과로 갈아 끼우고, 하늘빛 원천은 지금 블록에서 다시 센다(computeSources)
  ⑨ 저장 표시(markLightDirty → markDirty), 캐시 버림(lightTouched), C 를 가진 모든 플레이어에게 전 섹션 LightUpdate
  ⑩ 3×3(C) 붙잡기 수 −1(0 이 된 칸에 닿고 더는 막히지 않는 미룬 타일을 대기열 앞으로), 핀 −1. 얼린 구역은 일꾼이 계산을 끝낸 자리에서 녹인다(원자 수)
  ⑪ 콜백: C 를 요청의 콜백 줄에 넣는다 — 요청마다 한 콜백 스레드에서 설치 순서대로 cb(C), 받은 수를 다 세면 그 줄 끝에서 done(n)(R5)
붙잡힌 빛 검사는 붙잡기 수가 0 이 된 뒤 그 region 의 다음 runUpdates 에서 풀려 계산된다.
```

### 4.2 빛 값이 맞는 이유 (V4)

- 일꾼은 U 의 **얼린 블록**만 읽는다. 타일 청크 C 의 결과는 "U 만 있는 세계를 빈 빛에서 계산한 고정점"이다. Furnace 엔진의 청크 켜기(원천 전파 + 이웃 가장자리 밀기 + 증가 전파)는
  켜는 순서와 무관하게 같은 고정점에 닿는다(보통 로드 때와 같은 과정이다).
- C 의 칸에 닿는 빛은 15칸 안의 광원·하늘 열에서만 온다. 3×3(C) 밖의 칸은 C 에서 수평으로 17칸 이상 떨어져 있고, 3×3 밖으로 나갔다 들어오는 길도 17칸을 넘는다.
  그래서 C 의 값은 둘레 1칸만 있으면 "그 블록에 대한 올바른 빛"이고, Paper P3 과 같은 정의다. 둘레 청크의 실제 빛은 Paper 처럼 바꾸지 않는다.
- 둘레 중 다른 region 소유이거나 안 올라온 청크는 없는 것으로 본다 — Furnace 빛이 원래 그렇게 본다(`RegionLevel` 주석: 합쳐지기 전 1~2틱). Paper 와의 차이는 그 짧은 틈에서만 생긴다(5장).
- 얼리기 전에 거울을 맞추므로 FAWE 가 쓴 블록이 계산에 들어간다(Paper 는 쓴 섹션을 직접 읽는다).

### 4.3 동시성 — 왜 붙잡기가 필요한가

계산하는 몇 틱 동안 region 은 계속 블록을 바꾼다. 결과를 그냥 끼우면 그 사이의 빛 갱신을 잃는다. 예: 계산 중에 둘레 청크 N 의 경계 옆 횃불을 부순다 →
region 은 그 틱 끝에 N 과 C 의 빛을 줄인다 → 끼우기가 C 에 "횃불이 있던 때"의 빛을 덮어쓴다 → 다시 그 칸을 검사해도 N 쪽은 이미 줄어 있어 C 의 남은 빛이 지워지지 않는다.

Paper 는 P6 처럼 그 갱신을 relight 뒤로 미룬다. 같은 일을 한다:

- **붙잡기 수**: `World.relight()` 의 잠금(드문 일 — 타일 준비·청크 설치 때만) 안 `HashMap<청크 키, 수>`·`HashMap<청크 키, 요청>`. 바뀔 때마다 **박싱 없는 읽기 사본**
  (열린 주소법 `long[]`)을 `volatile` 로 새로 내고 **세대 수**를 올린다(S6). 준비 ③에서 올리고 설치 ⑩에서 내린다.
- **빛 검사 붙잡기**: `LevelLight` 가 붙잡기 판정(`Hold`: active·generation·held(칸))과 붙잡힌 검사 통(`LightEngine` 검사 집합 둘 — 넣은 순서 유지·중복 없는 `LongLinkedSet`)을 갖는다.
  `runUpdates`·빛 작업 넘기기(`moveWorkTo`·`dispatchLight`)·일 있음 판정(`hasWork`) 전에 `applyHold` 가 붙잡힐 검사를 통으로 옮기고, 세대가 바뀌었을 때만 통을 훑어 풀린 검사를
  다시 꺼낸다(R2 b — 다른 region 의 설치가 수를 0 으로 내려도 세대로 안다). 붙잡기가 없으면 읽기 몇 번이다(0 비용 경로).
  옮기는 것은 **검사만**이다. 하늘빛 원천 고치기(`updateSource`, 빛 작업 모드의 미룬 원천)는 지금처럼 바로 한다 — 원천은 블록에서 정해지는 값이고 빛을 쓰지 않는다.
- 판정 단위가 청크(3×3)라 실제로 닿는 15칸보다 넓다 — 넓게 잡는 쪽은 결과를 바꾸지 않고 그 칸의 빛을 몇 틱 늦출 뿐이다.

**끼운 뒤 상태가 맞는 이유**: 끼운 직후 C = 얼린 블록 B0 의 올바른 빛, C 의 원천 = 지금 블록 B1 에서 센 정확한 값(⑧), 둘레 N 의 빛 = 얼린 뒤 그 칸 검사를 붙잡았으므로
N 의 붙잡힌 칸 둘레는 아직 B0 쪽 빛. B0→B1 사이에 바뀐 칸은 모두 검사가 붙잡혀 있다. 이것은 바닐라에서 "블록을 바꾸고(원천은 그 자리에서 고침) 빛 검사는 아직 안 돈" 상태와 같은 꼴이라,
풀린 검사가 보통 증분 계산으로 B1 의 빛에 맞춘다. 3×3 밖에서 온 전파는 C 에 닿지 못한다(4.2) — 그래서 그 사이 C 의 빛은 붙잡히지 않은 일로 바뀌지 않고, 둘레의 바뀜은 N 의 빛에만 남는다.

빠지는 경우와 처리:
- **블록 검사를 안 거치는 쓰기**: 거울 들여오기(N2b-4)는 빛·원천을 고치지 않는다(D2). 계산 중 C 에 들어온 들여오기는 끼운 빛에도 반영되지 않는다 — FAWE 는 그 청크를 또 relight 한다. 원천은 ⑧이 B1 로 센다.
- **새로 보이게 된 이웃**(3×3(C) 안에서 계산 중 로드·빛 켜짐, 또는 region 합치기로 보이게 됨): 그 이웃의 `lightNewChunk` 가 C 로 민 빛은 붙잡기 대상(검사)이 아니라 전파라
  끼우기에 덮인다. 그래서 ⑦에서 멤버십이 늘었으면 C 를 다시 준비한다. 내려간 이웃은 다시 하지 않는다(바닐라도 이웃이 내려가도 빛을 남긴다 — 결과와 같다).
- **같은 자리를 겹쳐 relight**(R4): 다른 요청의 붙잡기와 3×3 이 겹치는 타일은 그 설치 뒤로 미루고(③), 준비는 붙잡기 전에 밀린 검사를 먼저 계산한다(②).
  그래서 플러그인이 매 틱 같은 청크를 relight 해도 붙잡힌 검사는 relight 한 번 사이에 반드시 계산된다(시험 `repeatedRelightDoesNotStarveNearbyLight`).
  같은 요청 안 이웃 타일은 겹쳐 붙잡아도 된다 — 요청이 유한하니 굶지 않고, 큰 요청의 타일들이 나란히 계산된다.
- **가짜 광원(플러그인 빛 API)**: 일꾼은 `VirtualLights`(스레드 안전 읽기)를 읽고, 계산 중 바뀌면 `emissionChanged` 검사가 붙잡힌다.

### 4.4 region 규칙

- 준비·설치는 **각 청크의 주인 region 스레드**에서만 한다. 작업은 청크 키로 보내므로(`enqueueIfOwned`) 그때의 주인에게 간다. 주인이 없으면(내려감) 그 청크는 건너뛰고 수에 넣는다.
- **합치기·나누기**: 진행 중인 relight 를 기다리지 않는다(나누기 잠금 안에서 일꾼을 기다리면 다른 region 이 멈춘다). 붙잡기 수는 `World` 에 있어 모든 region 이 같이 본다.
  붙잡힌 검사 통은 보통 검사처럼 옮긴다: 합치기는 다른 쪽 통을 통째로, 나누기는 청크 조건으로(`moveChecksTo` 와 같은 자리). 통 옮기기는 `hasWork()` 가드 **밖**에서
  늘 한다(R2 a — 통만 있는 쪽의 검사가 사라지지 않게). `hasWork()` 는 붙잡기를 먼저 맞춘 뒤 "지금 돌릴 일"만 센다(통만 있으면 빈 계산·빈 묶음을 넘기지 않는다).
  옮겨 받은 통은 세대 기록을 지워 다음에 다시 훑는다.
- **얼린 구역 녹이기**: T-064 의 `ChunkSection.lightFrozen`(boolean, 주인 전용)을 **원자 수(int, VarHandle)**로 바꾼다. T-064 묶음과 relight 가 같은 구역을 같이 얼릴 수 있고,
  나누기 뒤에는 녹이는 스레드가 그 구역의 새 주인이 아닐 수 있기 때문이다. 쓰는 쪽(`ownSection`)은 0 이 아니면 복사한다 — 낡은 0 아닌 값을 보면 복사 한 번이 더 생길 뿐이다.
  녹이기는 일꾼이 계산을 끝낸 자리에서 `getAndAdd(-1)`(volatile)로 하고, 주인의 0 판정은 volatile 읽기(acquire 이상)라 "일꾼 읽기 → 녹이기 → 주인이 0 을 보고 그 자리 쓰기"가
  happens-before 로 이어진다(R2 d). T-064 의 동작은 그대로다.
- 다른 스레드에서 블록을 읽는 길(공개 스냅샷 N2b-3)은 바뀌지 않는다.

### 4.5 빛 작업 모드(T-064, `light.async`)와 함께

- 설치 ⑥은 `awaitLightIdle()` 뒤에 끼운다(보이는 빛 배열은 묶음이 도는 동안 아무도 고치지 않는다는 T-064 규칙).
- 묶음을 넘길 때(`dispatchLight`) 붙잡힐 검사는 먼저 통으로 뺀다. 미룬 원천 고치기는 그대로 넘긴다.
- 준비 때 이미 도는 묶음은 얼린 B0 기준으로 C·N 에 결과를 게시할 수 있다 — C 는 끼우기가 덮고, N 은 B0 와 맞는 갱신이라 그대로 둔다.

### 4.6 저장·내리기·종료

T-012 는 저장 빛을 믿으므로 "새 블록 + 옛 빛"을 디스크에 남기면 영구로 굳는다(PR #304 R2). 붙잡힌 검사가 있는 청크를 저장하면 그런 상태가 된다.
- `settleLightForSave(cx, cz)`(저장 스냅샷 전, 내리기 포함)에서 **그 청크의 체비셰프 1 안에 붙잡힌 검사가 있으면 `lightStale().mark(cx, cz)`**(R1 a — 붙잡힌 검사는 이웃 청크의 빛도
  바꾼다: N 의 경계 횃불을 부순 검사가 붙잡힌 동안 그 옆 K 를 저장하면 K 는 아직 횃불 빛을 든다). 메서드의 조기 반환보다 먼저 본다. 통에는 방금 풀렸지만 아직 안 돈 검사도 있으므로 통에서 꺼내기(`applyHold`) **전**과 후에 모두 본다(코드 검토 F4). 다음에 올릴 때 다시 계산한다(T-012 안전장치 그대로).
  청크가 계속 로드돼 있으면 풀린 검사가 빛을 고치고 다시 dirty 가 되어 다음 저장에 맞는 값이 써진다(표시는 그 다음 올릴 때 다시 계산하며 지워진다 — 한 번 더 계산할 뿐 결과는 같다).
- relight 대상 청크는 끝날 때까지 내리지 않는다(핀, 4.8). 둘레 청크는 내릴 수 있다 — 위 표시로 덮인다.
- 종료·월드 내리기(R1 b): 저장 전에 `Relight.shutdown()` — 새 요청을 받지 않고(받은 수 0, done(0)), **붙잡기를 모두 풀어** 마지막 저장의 빛 계산(`saveDirtyChunks` 의
  `runLightUpdates`)이 붙잡혔던 검사까지 끝까지 계산하게 하고, 아직 끼우지 못한 대상 청크(핀이 남은 것)는 "들여온 블록 + 옛 빛"이라 `lightStale` 로 표시한다.
  그 뒤 끝난 계산은 끼우지 않고 콜백도 부르지 않는다. 부르는 곳: `FurnaceMain` 종료(저장 전), `WorldManager` 추가 월드 내리기(틱 멈춘 뒤), `World.closeForUnload`.

### 4.7 알림·패킷·저장 표시

- ⑨ C 의 섹션 전부를 저장 대상으로(`markLightDirty` → `markDirty`, 바닐라 `sectionLightChanged → setUnsaved`), 청크 본문 캐시를 버린다(`lightTouched`).
- Paper P4 처럼 **그 청크를 받은 모든 플레이어**에게 전 섹션 `LightUpdate` 를 바로 보낸다. 지금의 `broadcastLightUpdate` 는 바닐라 증분 규칙(시야 가장자리만)이라
  `World.broadcastFullLightUpdate`(받은 플레이어 전부, 아직 청크를 못 받은 구독자는 건너뜀 — 그 빛은 청크 패킷에 들어간다)를 따로 둔다.
- FAWE 는 이어서 청크를 통째로 다시 보낸다(이미 있는 길).

### 4.8 받기·핀·콜백

- **받기(P1, S2)**: 부른 스레드에서 Paper 처럼 **핀을 먼저** 걸고(`World.ticketed` 가 핀을 티켓처럼 본다 — 내리기 막기) 그다음 로드됐고 빛이 켜진 청크만(순서 유지·중복 제거) 받는다.
  못 받은 청크는 핀을 거둔다. 돌려주는 값 = 받은 수(Paper 와 같은 정의).
- 준비 때 빛이 꺼져 있는 청크(재생성 중)는 계산하지 않고 끝난 것으로 센다 — 곧 그 자리에서 지금 블록으로 빛이 켜진다(결과가 같다).
- **콜백(R5)**: region 스레드는 설치만 하고 청크를 그 요청의 콜백 줄에 넣는다. 요청마다 한 줄(콜백 전용 데몬 풀 `furnace-relight-callback-N` 위 직렬 실행)에서 설치 순서대로
  `cb(C)` 를 부르고, 받은 수를 다 세면 그 줄 끝에서 `done(n)` — Paper 처럼 한 스레드에서 차례로 불리고, 콜백 안에서 기다려도 region 틱·계산 일꾼을 막지 않는다.
  받은 것이 0 개면 `done(0)` 을 그 풀에서 비동기로 부른다(Paper 도 작업으로 부른다). 플러그인 예외는 잡아서 경고로 남긴다.

### 4.9 일꾼 풀·타일

- 전용 풀 `furnace-relight`(데몬, 스레드 수 = `clamp(코어 / 4, 1, 4)`, 처음 쓸 때 만든다). 생성 풀(ForkJoin)과 T-064 빛 풀은 쓰지 않는다 — 수백 ms 짜리 계산이 청크 생성·틱마다의 빛을 막지 않게.
- 타일 = 8×8 청크(U 는 많아야 10×10). 둘레 중복 계산은 1024청크 요청에서 약 +27%, 41청크(실측 시나리오)에서 약 +100%(Paper 도 둘레를 같이 계산한다).
- 동시에 준비된(얼린) 타일은 일꾼 수 × 2 까지 — 남은 타일은 대기열에서 기다리고, 앞 타일이 끝나면 준비된다. 얼린 구역이 오래·많이 남지 않게 한다.

## 5. Paper 와 다른 점 (알고 둔 것)

1. 둘레가 다른 region 소유인 짧은 틈(합쳐지기 전 1~2틱)에는 그 청크를 없는 것으로 계산한다(Furnace 빛 전체의 규칙).
2. 엔진 자체의 값 차이(Starlight 대 Furnace 엔진, 대조군에도 있음 — 블록빛 2,668칸·하늘빛 110칸)는 이 작업 범위 밖이다(T-202). T-197 의 합격은 "편집한 청크의 빛 = Furnace 가 그 블록을 새로 올려 계산한 빛"(V4)과
   "Paper 대조에서 남는 차이가 대조군과 같은 꼴"(V6)이다.
3. 콜백 스레드: Paper 는 빛 일꾼 스레드, Furnace 는 콜백 전용 스레드(요청마다 한 줄, 차례 보장). 둘 다 주 스레드가 아니고 한 요청의 콜백이 동시에 불리지 않는다.
4. 붙잡힌 검사의 체비셰프 1 안 청크를 저장하면 stale 표시를 남겨 다음 로드 때 다시 계산한다 — Paper 는 그때의 빛을 그대로 저장한다(Furnace 쪽이 더 엄격, 결과는 같은 "올바른 빛").
5. **둘레는 그대로**(Paper 동작): C 의 가장자리에 광원을 넣거나 빼면 relight 하지 않은 둘레 N 에 빛 모자람·유령 빛이 남는다. Paper 도 edge check 를 하지 않으므로 같다 —
   M5·V6 대조에서 차이로 보지 않는다. 고치는 경계 검사는 Paper 와 달라지므로 넣지 않는다(기본 끔 옵션 후보로만, S8).
6. Paper 는 relight 중 새 청크 빛 켜기도 멈추지만 Furnace 는 계속한다. 계산 중 3×3 안에서 켜진 새 이웃 X 는 그때의 C 빛을 받아 든다 — ⑦ 이 C 는 다시 계산하지만 X 쪽에 남은
   옛 빛은 5 와 같은 종류로 남는다(S7, 드묾). 고친다면 설치 뒤 C–X 경계 칸 검사(Starlight `checkChunkEdges` 꼴) — T-205 후보.
7. R4 의 "준비 전 밀린 검사 먼저 계산"은 준비하는 region 의 통만 계산한다. 3×3 이 다른 region 청크에 걸치는 짧은 틈(합쳐지기 전 1~2틱)에는 그쪽의 풀린 검사가
   다음 relight 붙잡기 전에 계산된다는 보장이 없다 — 틈이 짧고 다음 틱 끝에 계산되므로 받아들인다(재검토 N2).

## 6. 성능

### 비용 모델 (이 컨테이너, 4코어, 소음 있음 — 구현 때 데스크탑에서 다시 잰다)
- 새 빛 계산: 실제 지형(시드 20261115) 10×10 청크를 빈 빛에서 켜는 데 **약 4 ms/청크**(첫 회 JIT 7.6). 일꾼에서 돈다.
- 섹션 복사로 스냅샷을 뜨면 약 0.14 ms/청크(10×10 = 14 ms)라 region 스레드에 무겁다 → **얼리기**(구역마다 원자 증가 하나, 10×10 ≈ 0.05 ms)로 정했다.
- region 스레드 몫(타일당): 거울 맞추기(대기 있을 때만, 원래 틱 끝에 할 일), 얼리기, 설치(청크마다 원천 다시 세기 + 층 참조 갈아 끼우기 + 패킷 하나).

### 0 비용 경로
relight 가 없을 때 바뀌는 것은 `runUpdates`·`dispatchLight`·`hasWork` 마다 volatile 읽기 두 번(세대·사본 크기)과, 쓰기 경로의 `lightFrozen` 판정이 boolean 에서 int(volatile)로 바뀐 것뿐이다.

### 결과 (구현 뒤, 이 컨테이너 4코어 — 일꾼 1스레드, `:furnace-server:bench --tests '*RelightBench*'`, 3판 중 둘째·셋째 판)
실제 지형(시드 20261115) 34×34 청크를 한 region 에 올리고 정사각형을 relight 했다. 플레이어는 없다(패킷 보내기 비용 없음, 만들기는 따로 쟀다).

| 변 | 청크 | 끝까지 벽시계 | 일꾼 ms/컬럼(둘레 포함 배수) | 준비 ms/타일 | 설치 ms/청크 |
|---|---|---|---|---|---|
| 1 | 1 | 100 ms | 1.3~2.2 (9배) | 0.12~0.14 | 0.013~0.021 |
| 4 | 16 | 149 ms | 1.8~2.3 (2.25배) | 0.27~0.31 | 0.009~0.012 |
| 8 | 64 | 200~251 ms | 1.2~1.9 (1.56배) | 0.71~0.85 | 0.011~0.012 |
| 16 | 256 | 451~499 ms | 1.0~1.1 (1.56배) | 0.67~0.77 | 0.008 |
| 32 | 1024 | 1.60~1.70 s | 0.95~1.00 (1.56배) | 0.65~2.70 | 0.008~0.009 |

- 빛 패킷(전 섹션) 만들기 0.009~0.011 ms/청크, 하늘빛 원천 다시 세기 0.093~0.113 ms/청크.
- 처음 구현은 설치 때 원천을 늘 다시 세어 설치가 0.09~0.15 ms/청크(8×8 타일이면 한 틱에 약 8 ms)였다. 얼릴 때의 내용 판 번호(`modCount`)가 설치 때 그대로면 블록이 계산 때와
  같으므로 일꾼이 센 원천을 그대로 쓰게 바꿔 0.01 ms/청크가 됐다(바뀌었으면 지금처럼 다시 센다 — 빛 작업 게시도 판 번호를 올려 다시 세는 쪽으로 간다).
- 판정: **M1** 41청크급(16청크 149 ms, 64청크 200~251 ms) ≤ 0.5 s, 1024청크 1스레드 1.6~1.7 s ≤ 4 s — 합격. 작은 요청의 벽시계는 대부분 region 틱 두 번(준비·설치 작업이 다음 틱에
  돈다)이다. **M2** 준비는 타일당 0.65~0.85 ms(한 판 2.7 ms — GC 로 보임), 설치는 8×8 타일당 약 0.6 ms — 합격(≤ 2 ms). 큰 요청에서 설치는 타일마다 다른 틱에 흩어진다.
- 위 표는 틱 예산(아래 M3) 전 값이다. 예산 0.25 ms 를 넣은 뒤 같은 벤치: 1·4청크 그대로(100·150 ms), 64청크 350 ms, 256청크 800 ms, **1024청크 2.8 s**
  (설치가 틱마다 약 20청크로 나뉜다) — M1 합격선(≤ 4 s, **편집이 겹치지 않을 때**) 안. 같은 region 에서 편집이 계속되면(아래 M3 장면, 틱마다 20칸) 설치가
  원천을 다시 세어 0.08 ms/청크라 **1024청크 한 번에 14~17 s** 걸린다 — FAWE 는 done 을 받아야 청크를 다시 보내므로 그동안 빛이 늦게 보인다(T-206).

### M3 — region 틱 (`RelightBench#relightUnderEdits`, 이 컨테이너 4코어, 일꾼 1, 34×34 실제 지형 한 region, 게임 틱 켬)
장면마다 8초(셋째·넷째 판 = 둘째·셋째 회). 편집 = 틱마다 32×32 청크 안 무작위 20칸 발광석 켜고 끄기. relight = 32×32(1024청크)를 끝나는 대로 다시.
"일꾼 흉내"는 relight 없이 다른 스레드가 빛 배열만큼 메모리를 쉬지 않고 쓴다 — CPU·메모리 다툼만으로는 틱이 늘지 않음을 확인(p95 0.42→0.42, 0.57→0.54).

| 빛 작업 | 장면 | 처음 구현(예산 없음) p95 | 예산 0.25 ms p95 | 기준(같은 판) |
|---|---|---|---|---|
| 끔 | relight 만 | 2.7~3.0 ms | 1.49~1.51 ms | 쉼 0.44~0.55 → **+0.94~1.07** |
| 끔 | 편집 + relight | 13.1~16.2 ms | 4.91~7.07 ms | 편집만 4.90~7.59 → **−0.5~+0.01** |
| 켬 | relight 만 | 2.5~2.8 ms | 1.28~1.47 ms | 쉼 0.51~0.59 → **+0.69~0.96** |
| 켬 | 편집 + relight | 10.0~10.4 ms | 3.57~3.62 ms | 편집만 3.13~4.60 → **−1.0~+0.44** |

- 처음 구현은 편집과 겹치면 p95 가 +7 ms 였다. 타일 하나(64청크)의 설치가 한 틱에 몰리고(편집으로 블록이 바뀌면 원천을 다시 세어 청크당 0.08 ms), 빛 작업 켬에서는
  준비·설치가 도는 묶음을 기다렸다(묶음이 오래 걸리면 그만큼).
- 고친 것: **region 마다 한 틱에 relight 준비·설치에 0.25 ms**(`Relight.TICK_BUDGET`) — 넘으면 다음 틱으로(`Region.schedule`, 첫 일은 늘 한다). 틱 끝에 할 빛 일을
  당겨 한 몫(준비 전 밀린 검사 계산·끝난 묶음 게시)은 그 틱 끝이 그만큼 덜 하므로 예산에 넣지 않는다. 빛 작업 켬에서 묶음이 아직 계산 중이면 준비·설치는 기다리지 않고
  다음 틱에(20틱 넘게 밀리면 기다린다 — 빛 풀이 막혀도 끝나게).
- 예산 값 근거: 1 ms 에서는 예산 + 준비 하나(약 0.5 ms)가 한 틱에 얹혀 relight 만 돌 때 p95 +2.2~2.5 ms 였다. 0.25 ms 에서 +0.7~1.1 ms — 합격선(+1 ms) 경계이며 남는
  몫은 준비 하나(타일 100컬럼 얼리기·계산 세계 만들기)다. 대가는 큰 요청의 벽시계(1024청크 1.7 → 2.8 s, 편집이 겹치면 설치가 0.08 ms/청크라 더 길다 — 이 장면에서
  1024청크 한 번에 14~17 s). 41청크급(FAWE 보통 편집)은 몇 틱이다. 빛 작업 끔·relight 만 장면 한 판이 +1.07 ms 로 선을 살짝 넘어, M5 데스크탑 실측 때 이
  장면도 같이 잰다. p95 를 더 낮추려면 예산보다 준비 하나를 쪼개는 쪽이 효과가 크다(T-207).
- 시험 `RelightRegionTest#tinyTickBudgetStillCompletes`(끔·켬): 예산 1 ns(틱마다 첫 일 하나만)로도 25청크 요청이 끝나고 3×3 = 새로 켠 값.

### M4 — 회귀 (`AsyncLightRegionTest#lamps400Bench`, relight 없음)
기준 `b23cf1b5e`(구현 전)와 이 브랜치를 번갈아 두 판씩(판마다 3회, 해시 같음 `b4d8ed881e55d4e7`). 빛 작업 켬 평균 기준 1.233/1.085/1.005·1.232/1.092/1.025 ms,
이 브랜치 1.216/1.124/1.051·1.173/0.990/0.981 ms, p95 기준 1.67~2.11 · 이 브랜치 1.65~2.05 ms. 끔 평균 13.66~14.67 · 11.94~13.85 ms — 소음 안, 합격.

- **M5**(데스크탑 실측, 2026-11-16, Ryzen 7 3700X): 보고서 `furnace/reviews/m5-relight-2026-11-16.md`.
  - 빛: N2b-6 FAWE 장면에서 편집 41청크·둘레 61청크의 하늘빛·블록빛 칸 차이 **0**(예전 126,006·3,151칸).
  - MSPT(3판, 1·2회째 6개 값): relight 만 p95 쉼 대비 빛 작업 켬 **+0.70~0.98**(합격), 끔 **+0.83~1.07**(평균 +0.96, 6판 중 2판이 선을 0.02~0.07 ms 넘음 — 경계). 편집 중 relight 끔 +0.45~1.06·켬 +0.53~0.79(편집만 대비). 판정은 코디네이터.

  - 판정: 끔 장면은 평균 합격, 2개 값이 선을 조금 넘음(측정 흔들림 안), T-207 로 개선. 켬 장면과 빛 차이 0 은 합격.
### 측정 계획과 합격선
| 측정 | 방법 | 합격선 |
|---|---|---|
| M1 계산 | 벤치 시험(`-Dfurnace.bench=true`): 1·4·8·16·32 변 정사각형 relight, 일꾼 시간/청크, 끝까지 걸린 벽시계 | 기록(편집이 겹치지 않을 때 41청크 ≤ 0.5 s, 1024청크 ≤ 4 s 목표. 편집 중 1024청크는 14~17 s — 6장 M3) |
| M2 region 몫 | 같은 벤치에서 준비·설치의 region 스레드 시간 | 8×8 타일당 ≤ 2 ms |
| M3 틱 | 헤드리스 서버 장면: 플레이어 근처 41청크·1024청크 relight 중 region MSPT p50/p95(구현: 벤치 `relightUnderEdits` — 위 결과) | relight 없는 같은 장면 대비 p95 +1 ms 이내 |
| M4 회귀 | 기존 `lamps400Bench`(T-064)·`light-batch-scene`, relight 없음 | 이전과 소음 안 |
| M5 실측 | 데스크탑 또는 노트북: N2b-6 시나리오(FAWE 2.16.2) 다시, `tools/compat/anvil-diff.py` | 편집 41청크의 빛 차이 = 대조군 꼴(T-202)만 — **결과: 0칸(둘레 한 줄도 0)**, 위 M5 |

## 7. 시험 계획

구현된 시험(`furnace-server` `world/RelightTest` 16개·`world/RelightRegionTest` 10개, `furnace-compat-paper` `NmsFaweSurfaceTest#relightRequestRecomputesAndRunsCallbacks`):

| 시험 | 본 것 |
|---|---|
| `relightMatchesIncrementalAndFreshLightAndLeavesNeighboursAlone`(빛 작업 끔·켬) | 원시 쓰기(빛 없음) 뒤 relight → C 의 층 = 같은 블록을 보통 쓰기로 놓은 세계 = 7×7 을 새로 켠 값(R6 c), 원천 높이 = 보통 쓰기 세계, 둘레 8청크 빛 그대로, 저장 대상, 받은 수(중복·안 올라온 청크 제외), 콜백은 콜백 줄 스레드, 붙잡기·핀 0 |
| `emptyRequestCompletesWithZero` | 받은 것 없음 → 0, done(0) 비동기 |
| `concurrentChangesDuringComputeAreNotLost`(시드 4개 × 빛 작업 끔·켬) | R6 a: 빛이 맞는 5×5 에서 가운데 relight, 일꾼을 잡은 동안 C·둘레·거리 2 에 무작위 블록·광원 30번(사이사이 빛 계산) → 5×5 전체 = 새로 켠 값 |
| `withoutHoldsTheFuzzFails` | R6 b: 붙잡기를 끄면 위 퍼즈가 틀린다(변이 확인) |
| `savingWhileChecksAreHeldMarksNearbyLightStale` | R1 a: 붙잡힌 검사의 체비셰프 1 안은 stale, 2 떨어진 청크는 아님, 풀린 뒤 빛 반영 |
| `shutdownReleasesHoldsAndMarksUninstalledTargets` | R1 b: 종료가 붙잡기를 풀어 저장 전 빛 계산이 마저 함, 끼우지 못한 대상 stale, 늦게 끝난 계산은 안 끼움·콜백 없음, 새 요청 0 |
| `replacedNeighbourColumnTriggersRecompute` | R3: 계산 중 둘레 재생성(같은 키, 다른 컬럼) → C = 새로 켠 값 |
| `newlyLoadedNeighbourTriggersRecompute` | ⑦: 계산 중 새 이웃 로드(지붕 밑으로 하늘빛을 민다) → 다시 계산, 대상 = 새로 켠 값. ⑦ 을 끄면 실패(변이 확인) |
| `mergeAndSplitWhileChecksAreHeld` | 재검토 N1: 붙잡힌 검사를 든 채 region 합치기, 다음 relight 동안 나누기 → 통이 옮겨져 풀린 뒤 계산, 대상 = 새로 켠 값 |
| `repeatedRelightDoesNotStarveNearbyLight` | R4: 같은 청크를 쉬지 않고 relight 해도 옆 발광석 빛이 5초 안에 반영. 미루기·먼저 계산을 빼면 실패(변이 확인) |
| `RelightRegionTest#mergeDuringCompute`·`splitDuringCompute`(끔·켬) | 코드 검토 재현에서 옮김: 일꾼을 잡은 동안 region 합치기·나누기 → 통이 따라가 끼운 뒤 풀림, 3×3 = 5×5 새로 켠 값 |
| `RelightRegionTest#tileAcrossTwoRegions`(끔·켬) | 구역 2청크·반경 1 에서 한 8×8 타일이 두 region 에 걸침 → region 마다 따로 준비(2번), 받은 수·콜백·빛 15·붙잡기·핀 0 |
| `RelightRegionTest#prepareFailureReleasesHolds`(끔·켬) | 코드 검토 F1 회귀: 일꾼 풀이 거절해도 done·콜백이 오고 붙잡기·핀·얼림 0, 옆 발광석 빛 15. 정리를 빼면 실패(변이 확인) |
| `RelightRegionTest#installFailureReleasesHolds`(끔·켬) | 설치가 던져도 done·콜백, 붙잡기·핀 0, 그 청크 stale, 옆 발광석 빛 15 |
| `overlappingRequestWaitsForHoldsThenRuns` | F3: 겹치는 다른 요청은 미뤄졌다가 앞 설치로 풀리면 깨어 끝남, 겹치지 않는 요청은 안 기다림. 깨우기를 빼면 실패(변이 확인) |
| `savingRightAfterReleaseStillMarksStale` | F4: 붙잡기가 풀렸지만 통에서 아직 안 꺼낸 검사 옆 청크를 저장 → stale. 꺼낸 뒤에만 보면 실패(변이 확인) |

기존 시험: 빛 패키지 전부·`AsyncLightTest`·`AsyncLightRegionTest`(합치기·나누기 포함)·`RandomTickMaskTest`·`ChunkFieldCensusTest`(얼림 수 손잡이 분류) 등 140개 통과.
아래는 처음 계획(참고).

- **RelightOracleTest(V4)**: 빛 켜진 레벨에서 섹션을 빛 없이 갈아 끼운 뒤(들여오기 길) relight → 그 청크들의 층·원천 = 같은 블록으로 새 레벨을 만들어 다 켠 값.
  둘레 청크의 빛은 relight 전과 같다(P3). 지형 무작위·광원·물·잎·반블록(모양 가림) 섞기, 하늘 없는 차원.
- **동시 바뀜**: 일꾼을 잡아 두는 시험 이음매로 계산 중에 C 와 둘레 N 에 횃불 놓기·부수기(4.3 예) → 끝난 뒤 값 = 새로 켠 값. 붙잡기 전후 검사 수.
- **새 이웃**: 계산 중 3×3 안 청크 로드 → C 다시 준비, 값 = 새로 켠 값.
- **region**: 계산 중 합치기·나누기(붙잡힌 검사 옮김, 다른 스레드 녹이기), 타일이 두 region 에 걸침.
- **저장·내리기**: 계산 중 둘레 청크 저장·내리기 → stale 표시, 다시 올렸을 때 값.
- **T-064 모드**: 위 주요 시험을 `light.async` 켬으로 한 번 더.
- **콜백·수**: 청크 콜백 → done 순서, 받은 수, 안 올라온 청크 제외, 빈 요청 done(0) 비동기, 플러그인 예외.
- **호환**: `NmsFaweSurfaceTest` 에서 relight 뒤 빛이 바뀌고 경고가 없다. 시험은 바꾼 영역만 돌리고 전체는 CI.

## 8. 이 작업에 넣지 않는 것 — 빛 표면 묶음 B

`compat-real-plugin-gaps` 2장의 묶음 B(`getLayerListener(...).getDataLayerData`·`getLightValue`, `ServerLevel#getRawBrightness`, `LevelLightEngine#checkBlock`·`queueSectionData`)는
FAWE 의 `//fixlighting` 이외 빛 명령(`//removelighting`·`//getlighting`·`//setlight`)과 NativeAccess 한 칸 쓰기에서만 쓰인다(기본 편집은 안 탄다).
다른 스레드 읽기에는 공개 스냅샷에 빛 사본을 넣어야 하고, `queueSectionData` 는 거울 일지 쓰기라 설계가 따로 필요하다. **새 TASK(가칭 T-205, Opus high, 선행 T-197)로 나눌 것을 제안**한다.
이 문서의 붙잡기·설치 규칙을 `checkBlock`(주인에게 보내 검사 걸기)에 그대로 쓸 수 있다.

## 9. 고른 것과 버린 것

| 안 | 내용 | 버린 이유 |
|---|---|---|
| A 주인 스레드에서 동기 계산(틱당 예산) | 경쟁이 없어 가장 단순 | 4 ms/청크 × (타일+둘레) — 1024청크 요청이면 예산 5 ms/틱으로 30초 넘게 걸리고 그동안 MSPT +5 ms |
| B 일꾼 계산 + 바뀌면 무효화·다시 | 붙잡기 없음 | 물 흐름·레드스톤처럼 계속 바뀌는 곳에서 끝나지 않을 수 있다 — 결국 동기 대체 길이 필요(두 길) |
| C 일꾼 계산 + 바뀐 칸 다시 검사 | 붙잡기 없음 | 4.3 예처럼 이미 처리된 이웃 쪽 검사는 C 의 남은 빛을 못 지운다 — 틀린 값 |
| D 스냅샷을 섹션 복사로 | T-064 를 안 건드림 | region 스레드 14 ms/100청크 |
| H 들여오기가 바뀐 칸마다 검사 | 바닐라 의미 그대로(Starlight `blocksChangedInChunk` 꼴) | FAWE `//set` 1024청크면 수백만 칸 검사·전파가 region 스레드에 몰린다. D2(들여오기는 빛을 안 고침)와 어긋남 |
| I Paper 처럼 계산 동안 세계 전체 빛 갱신 멈춤 | 가장 단순, Paper 와 같은 관측 | 큰 relight 동안 모든 region 의 빛(플레이어 횃불 포함)이 수 초 멈춘다 — 3×3 붙잡기가 낫다 |
| **E(고름)** 일꾼 계산 + 3×3 검사 붙잡기 + 얼리기 수 | Paper P6 과 같은 꼴, 한 번에 끝남 | — |

## 10. 구현 단계·파일

1. `ChunkSection.lightFrozen` → 원자 수, T-064 얼리기·녹이기 맞춤(동작 그대로, 시험 `AsyncLightTest` 등 통과).
2. `LevelLight`/`LightEngine`: 붙잡기 판정·통·풀기, `ChunkLight.adoptLayers`(층 참조 갈아 끼우기), 일꾼 계산 `RelightCompute`(빈 빛 세계, `light` 패키지).
3. `World`/`Region`/`ChunkLevel`: relight 대기열·핀·붙잡기 수, 준비·설치 작업, 합치기·나누기 통 옮기기, `settleLightForSave` 표시, `broadcastFullLightUpdate`, 일꾼 풀.
4. `furnace-api` `ChunkInternals.relightChunks(world, long[] keys, LongConsumer chunkDone, IntConsumer allDone) → int`(불안정 내부 다리, `docs/api/internals.md`), `ChunkInternalsImpl`.
5. shim `ThreadedLevelLightEngine#starlight$serverRelightChunks` → 다리, 경고 키 `compat.nms-relight-unsupported` 제거(언어 파일 둘).
6. 시험(7장), 벤치(6장 M1·M2), 문서(이 문서 결과 절, `context-notes/2026-11-16-t197-relight.md`, 12절 T-197 행).
7. M5 실측은 코디네이터에게 데스크탑 사용을 요청한다(콘솔 보이는 창 + UTF-8).

## 11. 독립 검토 반영 (2026-11-16, 판정 조건부 승인)

| 항목 | 반영 | 어디 |
|---|---|---|
| R1 a stale 범위 체비셰프 1 | `LevelLight.heldNear`, `settleLightForSave` 맨 앞 | 4.6, `ChunkLevel.markHeldLightStale` |
| R1 b 종료: 붙잡기 풀고 계산 뒤 저장, 못 끼운 대상 stale | `Relight.shutdown()` — `FurnaceMain` 종료·`WorldManager` 내리기·`World.closeForUnload` | 4.6 |
| R2 a hasWork 둘로 | `hasWork()` 는 붙잡기를 맞춘 뒤 돌릴 일만, 통 옮기기는 가드 밖에서 늘 | 4.4 |
| R2 b 풀림 세대 | `Hold.generation()` — 세대가 바뀔 때만 통을 훑는다 | 4.3 |
| R2 c 통 = 순서 유지·중복 없음 | 이미 `LongLinkedSet` | 4.3 |
| R2 d 얼림 수 판정 acquire | volatile 읽기(acquire 이상), 감소 `getAndAdd` | 4.4 |
| R3 멤버십 = 컬럼 정체성 | `Job.members` 키 → 컬럼 객체, ⑦ 에서 `!=` 비교(C 자신 포함) | 4.1 ⑦ |
| R4 붙잡기 기아 | (b) 안: 다른 요청과 겹치는 타일은 미룸 + 준비 전 밀린 검사 먼저 계산 | 4.1 ②③, 4.3 |
| R5 콜백 한 줄 | 요청마다 콜백 전용 풀 위 직렬 실행, done 은 줄 끝 | 4.8 |
| R6 시험 | 7장 표(퍼즈·변이·5×5/7×7 참값·R1·R3·R4) | 7장 |
| R6 e M5 둘레 한 줄 | M5 실측 때 `anvil-diff` 범위에 편집 안 한 둘레 한 줄을 넣는다 | 6장 M5 |
| S2 핀 먼저·빛 없는 청크는 안 받음 | `Relight.relight` | 4.8 |
| S3 H·I 안 | 9장 | 9장 |
| S4 U 정의 | "요청 청크마다 3×3 합집합 ∩ 이 region 소유·빛 있음" | 4.1 ④ |
| S5 M2 나눠 재기 | 설치·패킷 만들기·원천 다시 세기를 따로 쟀다. 원천을 일꾼 값으로 재사용해 설치 0.01 ms/청크 | 6장 결과 |
| S6 박싱 없는 판정 | 열린 주소법 `long[]` 사본 + 세대 | 4.3 |
| S7·S8 | 5장 5·6 에 적고 넘김(경계 검사는 T-205 후보, 기본 끔 옵션 후보) | 5장 |
| S9 얼림 중 `-ea` 단언 | 시험이 모두 `-ea` 로 돈다. 퍼즈 시험이 얼림이 걸린 채 C·둘레를 쓴다 | 7장 |

코드 검토(`furnace/reviews/pr439-t197-relight-code-review.md`, 조건부 승인):

| 항목 | 반영 | 어디 |
|---|---|---|
| F1 준비·설치 예외 때 붙잡기·핀·얼림·done 정리 | 붙잡기 뒤 준비를 try/catch — 녹이고 `skip(held)`, `dispatched` 는 `execute` 가 돌아온 뒤. 일꾼과 정리 중 먼저 집은 쪽이 끝을 맡는다(`Job.claimed`). 설치는 try/finally + stale 표시. 언어 키 `relight.prepare-failed`·`install-failed` | 4.1 ③, `RelightRegionTest#prepareFailureReleasesHolds` |
| F2 준비마다 region 밀린 빛 전부 계산 | 곧 붙잡을 3×3 합집합 안에 밀린 검사가 있을 때만. 빛 작업 켬은 묶음으로 넘김(`ChunkLevel.settleChecksBeforeHold`) | 4.1 ② |
| F3 설치마다 미룬 타일 전부 재큐 | 풀린 칸에 닿고 더는 막히지 않는 타일만 깨워 대기열 맨 앞에(미룬 차례 유지). 다시 준비로 돌리는 설치 청크도 앞에 | 4.1 ③⑩ |
| F4 저장 직전 풀린 검사 stale 누락 | `applyHold` 전후 모두 `heldNear` | 4.6 |
| F5 콜백 스레드 javadoc | `ChunkInternals.relightChunks`, `ThreadedLevelLightEngine` | — |
| 재확인 뒤 사소한 것 | 계산 뒤 설치 넘기기(`result`·`enqueueIfOwned`) 예외 보호, 설치 실패 시험 `RelightRegionTest#installFailureReleasesHolds` | — |
| M3 첫 측정 p95 +7 ms | 틱 예산 0.25 ms + 빛 작업 묶음 기다리지 않기 | 6장 M3 |
| 시험 보강 | 합치기·나누기·두 region 타일·F1 회귀를 `RelightRegionTest` 로 옮김 | 7장 |

M3·M4 는 6장 결과, M5(데스크탑 실측 + 둘레 한 줄)는 6장 M4 아래 M5 줄과 `furnace/reviews/m5-relight-2026-11-16.md`.
