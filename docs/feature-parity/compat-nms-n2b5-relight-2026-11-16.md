# T-197(N2b-5) 편집 뒤 빛 다시 계산 — 설계 (2026-11-16)

상태: **설계안(독립 검토 전)**. 구현은 이 문서가 검토를 통과한 뒤.
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
  → World.relight: 받기(P1) — 로드된 청크만, 붙잡기 핀(내리기 막기), 받은 수 돌려줌
       타일(8×8 청크)로 나눠 world 대기열에 넣음. 동시에 도는 타일은 일꾼 수 × 2 까지.
타일 준비 — 타일 기준 청크의 주인 region 스레드(작업):
  ① 타일 청크 중 이 region 것이 아닌 것은 떼어 그 주인에게 새 타일로
  ② U = 타일 ∪ 둘레 1칸 중 이 region 이 가진·빛 있는 청크. U 의 거울 맞추기(대기 있으면)
  ③ 붙잡기 걸기: 타일 청크 C 마다 3×3 청크에 세계 단위 붙잡기 수 +1
  ④ U 의 구역을 얼린다(freezeForLight). U 목록(멤버십) 기억
  ⑤ 일꾼 풀에 계산 작업
일꾼: U 를 새 ChunkLight 로 처음부터 계산(LevelLight.lightChunk 를 U 전부에) → 타일 청크의 빛만 결과로(compact)
설치 — 타일 청크 C 의 지금 주인 region 스레드(작업, C 마다):
  ⑥ awaitLightIdle(빛 작업 모드), C 가 같은 컬럼·빛 있음 확인
  ⑦ 3×3(C) 에 U 에 없던 "이 region 이 가진·빛 있는" 청크가 생겼으면 → C 만 다시 준비(②부터, 붙잡기는 새로 건 뒤 옛 것을 푼다)
  ⑧ C 의 하늘빛·블록빛 층을 결과로 갈아 끼우고, 하늘빛 원천은 지금 블록에서 다시 센다(computeSources)
  ⑨ 저장 표시(markLightDirty → markDirty), 캐시 버림(lightTouched), C 를 가진 모든 플레이어에게 전 섹션 LightUpdate
  ⑩ 3×3(C) 붙잡기 수 −1, 핀 −1, 얼린 구역 녹이기(U 의 마지막 C 가 끝나면)
  ⑪ 콜백 cb(C). 받은 수를 다 세면 마지막 쪽이 done(n)
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

- **붙잡기 수**: `World` 의 `ConcurrentHashMap<청크 키, 수>` + 전체 수 `volatile int`. 준비 ③에서 올리고 설치 ⑩에서 내린다.
- **빛 검사 붙잡기**: `LevelLight` 가 붙잡기 판정(`LongPredicate`, 칸 → 그 청크의 수 > 0)과 붙잡힌 검사 통(`LightEngine` 검사 집합 둘)을 갖는다.
  `runUpdates` 시작과 빛 작업 넘기기(`moveWorkTo`) 직전에 붙잡힐 검사를 통으로 옮기고, 붙잡기가 풀린 검사는 통에서 다시 꺼낸다. 전체 수가 0 이면 아무것도 하지 않는다(0 비용 경로: volatile 읽기 하나).
  옮기는 것은 **검사만**이다. 하늘빛 원천 고치기(`updateSource`, 빛 작업 모드의 미룬 원천)는 지금처럼 바로 한다 — 원천은 블록에서 정해지는 값이고 빛을 쓰지 않는다.
- 판정 단위가 청크(3×3)라 실제로 닿는 15칸보다 넓다 — 넓게 잡는 쪽은 결과를 바꾸지 않고 그 칸의 빛을 몇 틱 늦출 뿐이다.

**끼운 뒤 상태가 맞는 이유**: 끼운 직후 C = 얼린 블록 B0 의 올바른 빛, C 의 원천 = 지금 블록 B1 에서 센 정확한 값(⑧), 둘레 N 의 빛 = 얼린 뒤 그 칸 검사를 붙잡았으므로
N 의 붙잡힌 칸 둘레는 아직 B0 쪽 빛. B0→B1 사이에 바뀐 칸은 모두 검사가 붙잡혀 있다. 이것은 바닐라에서 "블록을 바꾸고(원천은 그 자리에서 고침) 빛 검사는 아직 안 돈" 상태와 같은 꼴이라,
풀린 검사가 보통 증분 계산으로 B1 의 빛에 맞춘다. 3×3 밖에서 온 전파는 C 에 닿지 못한다(4.2) — 그래서 그 사이 C 의 빛은 붙잡히지 않은 일로 바뀌지 않고, 둘레의 바뀜은 N 의 빛에만 남는다.

빠지는 경우와 처리:
- **블록 검사를 안 거치는 쓰기**: 거울 들여오기(N2b-4)는 빛·원천을 고치지 않는다(D2). 계산 중 C 에 들어온 들여오기는 끼운 빛에도 반영되지 않는다 — FAWE 는 그 청크를 또 relight 한다. 원천은 ⑧이 B1 로 센다.
- **새로 보이게 된 이웃**(3×3(C) 안에서 계산 중 로드·빛 켜짐, 또는 region 합치기로 보이게 됨): 그 이웃의 `lightNewChunk` 가 C 로 민 빛은 붙잡기 대상(검사)이 아니라 전파라
  끼우기에 덮인다. 그래서 ⑦에서 멤버십이 늘었으면 C 를 다시 준비한다. 내려간 이웃은 다시 하지 않는다(바닐라도 이웃이 내려가도 빛을 남긴다 — 결과와 같다).
- **같은 청크를 두 요청이 겹쳐 relight**: 둘 다 붙잡고, 끼우는 순서가 바뀌어도 더 이른 얼리기 이후의 바뀜은 모두 붙잡혀 있다가 마지막 풀림 뒤 계산된다.
- **가짜 광원(플러그인 빛 API)**: 일꾼은 `VirtualLights`(스레드 안전 읽기)를 읽고, 계산 중 바뀌면 `emissionChanged` 검사가 붙잡힌다.

### 4.4 region 규칙

- 준비·설치는 **각 청크의 주인 region 스레드**에서만 한다. 작업은 청크 키로 보내므로(`enqueueIfOwned`) 그때의 주인에게 간다. 주인이 없으면(내려감) 그 청크는 건너뛰고 수에 넣는다.
- **합치기·나누기**: 진행 중인 relight 를 기다리지 않는다(나누기 잠금 안에서 일꾼을 기다리면 다른 region 이 멈춘다). 붙잡기 수는 `World` 에 있어 모든 region 이 같이 본다.
  붙잡힌 검사 통은 보통 검사처럼 옮긴다: 합치기는 다른 쪽 통을 통째로, 나누기는 청크 조건으로(`moveChecksTo` 와 같은 자리).
- **얼린 구역 녹이기**: T-064 의 `ChunkSection.lightFrozen`(boolean, 주인 전용)을 **원자 수(int, VarHandle)**로 바꾼다. T-064 묶음과 relight 가 같은 구역을 같이 얼릴 수 있고,
  나누기 뒤에는 녹이는 스레드가 그 구역의 새 주인이 아닐 수 있기 때문이다. 쓰는 쪽(`ownSection`)은 0 이 아니면 복사한다 — 낡은 0 아닌 값을 보면 복사 한 번이 더 생길 뿐이다.
  녹이기는 일꾼이 끝난 뒤(작업 완료 → 설치 작업의 순서 보장)에만 한다. T-064 의 동작은 그대로다.
- 다른 스레드에서 블록을 읽는 길(공개 스냅샷 N2b-3)은 바뀌지 않는다.

### 4.5 빛 작업 모드(T-064, `light.async`)와 함께

- 설치 ⑥은 `awaitLightIdle()` 뒤에 끼운다(보이는 빛 배열은 묶음이 도는 동안 아무도 고치지 않는다는 T-064 규칙).
- 묶음을 넘길 때(`dispatchLight`) 붙잡힐 검사는 먼저 통으로 뺀다. 미룬 원천 고치기는 그대로 넘긴다.
- 준비 때 이미 도는 묶음은 얼린 B0 기준으로 C·N 에 결과를 게시할 수 있다 — C 는 끼우기가 덮고, N 은 B0 와 맞는 갱신이라 그대로 둔다.

### 4.6 저장·내리기·종료

T-012 는 저장 빛을 믿으므로 "새 블록 + 옛 빛"을 디스크에 남기면 영구로 굳는다(PR #304 R2). 붙잡힌 검사가 있는 청크를 저장하면 그런 상태가 된다.
- `settleLightForSave(cx, cz)`(저장 스냅샷 전, 내리기 포함)에서 **그 청크에 붙잡힌 검사가 있으면 `lightStale().mark(cx, cz)`** — 다음에 올릴 때 다시 계산한다(T-012 안전장치 그대로).
  청크가 계속 로드돼 있으면 풀린 검사가 빛을 고치고 다시 dirty 가 되어 다음 저장에 맞는 값이 써진다(표시는 그 다음 올릴 때 다시 계산하며 지워진다 — 한 번 더 계산할 뿐 결과는 같다).
- relight 대상 청크는 끝날 때까지 내리지 않는다(핀, 4.8). 둘레 청크는 내릴 수 있다 — 위 표시로 덮인다.
- 종료·월드 내리기: 진행 중인 작업은 버린다(콜백 없음). 붙잡힌 검사가 있던 청크는 마지막 저장에서 표시된다.

### 4.7 알림·패킷·저장 표시

- ⑨ C 의 섹션 전부를 저장 대상으로(`markLightDirty` → `markDirty`, 바닐라 `sectionLightChanged → setUnsaved`), 청크 본문 캐시를 버린다(`lightTouched`).
- Paper P4 처럼 **그 청크를 받은 모든 플레이어**에게 전 섹션 `LightUpdate` 를 바로 보낸다. 지금의 `broadcastLightUpdate` 는 바닐라 증분 규칙(시야 가장자리만)이라
  `World.broadcastFullLightUpdate`(받은 플레이어 전부, 아직 청크를 못 받은 구독자는 건너뜀 — 그 빛은 청크 패킷에 들어간다)를 따로 둔다.
- FAWE 는 이어서 청크를 통째로 다시 보낸다(이미 있는 길).

### 4.8 받기·핀·콜백

- **받기(P1)**: 부른 스레드에서 `World.loadedChunkColumn` 이 있는 청크만(순서 유지·중복 제거) 받는다. 받은 청크는 `World` 의 relight 핀(`ConcurrentHashMap<키, 수>`)에 올리고,
  `World.ticketed` 가 핀을 티켓처럼 본다(내리기 막기). 설치 ⑩이나 건너뛸 때 내린다. 돌려주는 값 = 받은 수.
- 준비 때 빛이 아직 없는 청크(로드 직후 빛 켜기 전)는 계산하지 않고 끝난 것으로 센다 — 곧 그 자리에서 지금 블록으로 빛이 켜진다(결과가 같다).
- **콜백**: 청크 콜백은 설치한 region 스레드에서 끼운 직후 부른다. `done` 은 마지막 청크를 끝낸 쪽이 자기 콜백을 부른 뒤 부른다(원자 감소 — 다른 region 의 콜백이 모두 먼저 끝난다).
  받은 것이 0 개면 `done(0)` 을 일꾼 풀에서 비동기로 부른다(Paper 도 작업으로 부른다). 플러그인 예외는 잡아서 경고로 남긴다(틱을 깨지 않음). FAWE 의 두 콜백은 막지 않는다(작업을 걸기만 한다).

### 4.9 일꾼 풀·타일

- 전용 풀 `furnace-relight`(데몬, 스레드 수 = `clamp(코어 / 4, 1, 4)`, 처음 쓸 때 만든다). 생성 풀(ForkJoin)과 T-064 빛 풀은 쓰지 않는다 — 수백 ms 짜리 계산이 청크 생성·틱마다의 빛을 막지 않게.
- 타일 = 8×8 청크(U 는 많아야 10×10). 둘레 중복 계산은 1024청크 요청에서 약 +27%, 41청크(실측 시나리오)에서 약 +100%(Paper 도 둘레를 같이 계산한다).
- 동시에 준비된(얼린) 타일은 일꾼 수 × 2 까지 — 남은 타일은 대기열에서 기다리고, 앞 타일이 끝나면 준비된다. 얼린 구역이 오래·많이 남지 않게 한다.

## 5. Paper 와 다른 점 (알고 둔 것)

1. 둘레가 다른 region 소유인 짧은 틈(합쳐지기 전 1~2틱)에는 그 청크를 없는 것으로 계산한다(Furnace 빛 전체의 규칙).
2. 엔진 자체의 값 차이(Starlight 대 Furnace 엔진, 대조군에도 있음 — 블록빛 2,668칸·하늘빛 110칸)는 이 작업 범위 밖이다(T-202). T-197 의 합격은 "편집한 청크의 빛 = Furnace 가 그 블록을 새로 올려 계산한 빛"(V4)과
   "Paper 대조에서 남는 차이가 대조군과 같은 꼴"(V6)이다.
3. 콜백 스레드: Paper 는 빛 일꾼 스레드, Furnace 는 그 청크의 region 스레드. 두 콜백을 쓰는 플러그인은 스레드를 가정하지 않는다(FAWE 확인).
4. 붙잡힌 검사가 있는 청크를 저장하면 stale 표시를 남겨 다음 로드 때 다시 계산한다 — Paper 는 그때의 빛을 그대로 저장한다(Furnace 쪽이 더 엄격, 결과는 같은 "올바른 빛").

## 6. 성능

### 비용 모델 (이 컨테이너, 4코어, 소음 있음 — 구현 때 데스크탑에서 다시 잰다)
- 새 빛 계산: 실제 지형(시드 20261115) 10×10 청크를 빈 빛에서 켜는 데 **약 4 ms/청크**(첫 회 JIT 7.6). 일꾼에서 돈다.
- 섹션 복사로 스냅샷을 뜨면 약 0.14 ms/청크(10×10 = 14 ms)라 region 스레드에 무겁다 → **얼리기**(구역마다 원자 증가 하나, 10×10 ≈ 0.05 ms)로 정했다.
- region 스레드 몫(타일당): 거울 맞추기(대기 있을 때만, 원래 틱 끝에 할 일), 얼리기, 설치(청크마다 원천 다시 세기 + 층 참조 갈아 끼우기 + 패킷 하나).

### 0 비용 경로
relight 가 없을 때 바뀌는 것은 `runUpdates`·`dispatchLight` 마다 volatile 읽기 하나와, 쓰기 경로의 `lightFrozen` 판정이 boolean 에서 int 로 바뀐 것뿐이다.

### 측정 계획과 합격선
| 측정 | 방법 | 합격선 |
|---|---|---|
| M1 계산 | 벤치 시험(`-Dfurnace.bench=true`): 1·4·8·16·32 변 정사각형 relight, 일꾼 시간/청크, 끝까지 걸린 벽시계 | 기록(41청크 ≤ 0.5 s, 1024청크 2스레드 ≤ 4 s 목표) |
| M2 region 몫 | 같은 벤치에서 준비·설치의 region 스레드 시간 | 8×8 타일당 ≤ 2 ms |
| M3 틱 | 헤드리스 서버 장면: 플레이어 근처 41청크·1024청크 relight 중 region MSPT p50/p95 | relight 없는 같은 장면 대비 p95 +1 ms 이내 |
| M4 회귀 | 기존 `lamps400Bench`(T-064)·`light-batch-scene`, relight 없음 | 이전과 소음 안 |
| M5 실측 | 데스크탑 또는 노트북: N2b-6 시나리오(FAWE 2.16.2) 다시, `tools/compat/anvil-diff.py` | 편집 41청크의 빛 차이 = 대조군 꼴(T-202)만 |

## 7. 시험 계획

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
| **E(고름)** 일꾼 계산 + 3×3 검사 붙잡기 + 얼리기 수 | Paper P6 과 같은 꼴, 한 번에 끝남 | — |

## 10. 구현 단계·파일

1. `ChunkSection.lightFrozen` → 원자 수, T-064 얼리기·녹이기 맞춤(동작 그대로, 시험 `AsyncLightTest` 등 통과).
2. `LevelLight`/`LightEngine`: 붙잡기 판정·통·풀기, `ChunkLight.adoptLayers`(층 참조 갈아 끼우기), 일꾼 계산 `RelightCompute`(빈 빛 세계, `light` 패키지).
3. `World`/`Region`/`ChunkLevel`: relight 대기열·핀·붙잡기 수, 준비·설치 작업, 합치기·나누기 통 옮기기, `settleLightForSave` 표시, `broadcastFullLightUpdate`, 일꾼 풀.
4. `furnace-api` `ChunkInternals.relightChunks(world, long[] keys, LongConsumer chunkDone, IntConsumer allDone) → int`(불안정 내부 다리, `docs/api/internals.md`), `ChunkInternalsImpl`.
5. shim `ThreadedLevelLightEngine#starlight$serverRelightChunks` → 다리, 경고 키 `compat.nms-relight-unsupported` 제거(언어 파일 둘).
6. 시험(7장), 벤치(6장 M1·M2), 문서(이 문서 결과 절, `context-notes/2026-11-16-t197-relight.md`, 12절 T-197 행).
7. M5 실측은 코디네이터에게 데스크탑 사용을 요청한다(콘솔 보이는 창 + UTF-8).
