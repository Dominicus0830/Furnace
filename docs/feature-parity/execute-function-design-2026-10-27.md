# 설계: 명령 실행 결과 모델 · `/execute` · 함수 시스템 (2026-10-27)

확정 설계서. 구현자는 이 문서의 결정을 따른다. 기준 HEAD `8a5e3ab0`. 경로는 `furnace-server/src/main/java/org/domi/furnace/server/` 기준(`S/` 로 줄임). 바닐라 근거는 1.21.1 전용 서버 디컴파일(난독화 이름 그대로, 저장소에 복사하지 않음)에서 읽었고 `바닐라 클래스명(난독화명:줄)` 로 적는다. 확인하지 못한 것은 **가정** 이라고 표시했다.

선행 조사: `survey-structures-commands-2026-10-27.md` B-4a(execute 5단계), B-5. 알려진 한계: `context-notes.md` 2026-10-27 "반환값 차이".

## 0. 요약(결정)

| # | 결정 |
|---|---|
| D1 | **결과는 반환값이 아니라 콜백으로 흐른다.** 바닐라 1.20.3+ 의 `ExecutionContext` 큐 모델(`hs`/`ho`/`hu`/`eq`)을 이식한다. `Command.run` 의 int 는 콜백으로 전달되는 값일 뿐, 호출자가 읽지 않는다(바닐라 `ib.java:21-26`: `ContextChain.runExecutable(..., ev.b_(), forked)` 의 반환값은 trace 에만 쓴다). |
| D2 | **명령 실행은 전역 스레드에서만 진행된다.** 지금도 채팅(`S/net/ConnectionHandler.java:318`), 콘솔(`S/ServerCore.java:326`), 표지판(`S/ServerCore.java:584`) 모두 `global().execute` 로 들어온다. region 스레드는 "대상 하나에 대한 연산을 수행하고 future 를 완성" 만 한다. 명령 큐가 region 결과를 기다려야 하면 **큐를 멈추고(suspend) future 완료 시 전역 스레드에서 재개(resume)** 한다. 블로킹 `get()` 은 어디서도 쓰지 않는다. |
| D3 | **점진 이행 어댑터 = "지연 결과(deferred)" 슬롯.** 기존 43+ 명령의 `Command<CommandSource>`(int) 서명은 바꾸지 않는다. 지금 "1 을 돌려주고 나중에 메시지" 로 처리하는 헬퍼 세 곳(`EntityArgument.await` `S/command/arguments/EntityArgument.java:82-100`, `EntityOps.await` `S/command/EntityOps.java:70-85`, `ScoreHolderArgument.await` `S/command/arguments/ScoreHolderArgument.java:133-157`)만 `source.defer(future)` 를 부르도록 바꾸면, 그 헬퍼를 쓰는 11개 명령 파일(33곳)이 한 번에 올바른 결과값을 낸다. |
| D4 | **같은 틱 안의 직렬화는 "한 번에 하나의 ExecutionContext" 로 보장한다.** 최상위 명령(채팅·콘솔·명령 블록·tick 함수·schedule)은 `CommandQueue`(전역) 에 FIFO 로 들어가고, 앞 컨텍스트가 끝나기 전에는 다음 컨텍스트를 시작하지 않는다. 바닐라도 메인 스레드가 한 컨텍스트를 끝까지 돌린다(`eu.java:211-229`). 함수 안 줄 순서는 큐 순서 그 자체다. |
| D5 | **예산**: `maxCommandChainLength` 는 컨텍스트당 실행 명령 수(`hs.java:74-99`, 수정자 적용·함수 호출·실행마다 1 감소 `hs.e()`), `maxCommandForkCount` 는 수정자 하나가 만든 소스 수의 상한(`hy.java:51`), 큐 상한 10,000,000(`hs.java:11,54-62`). 컨텍스트가 region 왕복으로 여러 틱에 걸치더라도 예산은 **컨텍스트 단위** 로 유지한다(틱마다 리셋하지 않음). |
| D6 | **포크 결과 합산**: `execute store`/명령 블록 SuccessCount/`if function` 은 모두 "포크(소스)마다 콜백 한 번" 이다. store 는 포크마다 저장(마지막 값이 남음), 명령 블록은 성공한 포크 수(`dbx.java:105-109`), 함수 태그는 성공 여부 OR + 결과 합(`amy.java:132-156`). 합산 규칙은 §2.4. |
| D7 | 함수 시스템은 바닐라 구조(`ServerFunctionLibrary`=`alf`, `ServerFunctionManager`=`alg`, `CommandFunction`=`ig`/`ik`, 매크로 `ij`) 를 그대로 이식하고, 함수 틱·`#load`·TimerQueue 는 **전역 틱(`ServerCore.tick`)** 에 건다(바닐라도 `MinecraftServer.tickChildren` 에서 레벨 틱보다 앞에 함수 틱 `MinecraftServer.java:835-836`). |
| D8 | region 스레드는 어떤 명령 future 도 기다리지 않는다(scheduler 규칙 6). 전역이 region 을 기다리는 future 에는 타임아웃(100 틱)을 건다. 종료 중 미완료 컨텍스트는 버린다. |

---

## 1. 명령 실행 결과 모델

### 1.1 바닐라 모델(근거)

- `CommandSourceStack`(`et.java`) 은 불변. 필드 `l: eq`(결과 콜백), `j`(silent), `k`(엔티티), `d`(위치), `n`(회전), `e`(레벨), `m`(anchor). 변형 메서드는 모두 새 객체를 돌려준다(`et.java:75-151`). `withCallback(eq, BinaryOperator)` 은 `eq.chain` 으로 콜백을 **겹친다**(`et.java:99-102`).
- `CommandResultCallback`(`eq.java`): `onResult(boolean success, int result)`, `onSuccess`, `onFailure`(=`onResult(false,0)`), `chain(a,b)` 는 둘 다 부른다(`eq.java:24-33`). 빈 콜백 `eq.a`.
- `ExecutionContext`(`hs.java`): `c` 체인 한도, `d` 포크 한도, `i` 실행 덱, `j` 새로 들어온 항목 버퍼(`a(ho)`), `g()` 가 버퍼를 **역순으로 덱 앞에** 넣는다(`hs.java:101-107`) — 즉 한 작업이 넣은 항목들은 그 작업 **직후에, 넣은 순서대로** 실행된다. 이것이 함수 줄 순서 보장의 전부다. `a()` 루프(`hs.java:74-99`)는 한도 0 이면 "Command execution stopped due to limit" 로그 후 중단.
- `Frame`(`hu.java`): `(depth, callback, discard)`. `returnSuccess(int)`/`returnFailure()` 는 콜백 호출, `discard()` 는 그 깊이 이상의 대기 항목 제거(`hs.java:64-68`).
- `ChainModifiers`(`hn.java`): bit1 forked, bit2 isReturn.
- `BuildContexts`(`hy.java:23-96`): 수정자 단계를 돌며 소스 목록을 만든다. 포크 수가 `maxCommandForkCount` 이상이면 `command.forkLimit` 오류(`hy.java:51-54`). 소스가 비면 isReturn 일 때만 `FallthroughTask`(실패+discard, `ic.java`). 실행 노드가 `CustomCommandExecutor`(`hp`) 면 소스마다 직접 호출, 아니면 `ExecuteCommand`(`ib`) 를 소스마다 `ContinuationTask`(`ia`) 로 큐에 넣는다. isReturn 이면 **첫 소스 하나** 만, 콜백을 프레임 콜백과 chain 해서 실행(`hy.java:86-90`).
- `ExecuteCommand`(`ib.java:18-30`): `runExecutable` 의 ResultConsumer 가 `source.callback().onResult(success,result)` 를 부른다(`ev.b_()` `ev.java:28-30`). 예외는 `source.handleError(ex, forked, trace)` — forked 가 아니면 사용자에게 빨간 메시지(`et.java:346-354`).
- `Commands.performCommand`(`eu.java:152-180`): 파싱 → `ContextChain.tryFlatten` → `executeCommandInContext(source, ctx -> ExecutionContext.queueInitialCommandExecution(...))`. `executeCommandInContext`(`eu.java:211-229`) 는 ThreadLocal 에 현재 컨텍스트가 있으면 **그 컨텍스트에 항목만 추가**(중첩 호출을 큐에 합침), 없으면 새 컨텍스트를 만들어 `runCommandQueue()` 까지 돌린다. 한도는 게임룰 `maxCommandChainLength`(최소 1)·`maxCommandForkCount`(`dcs.java:58-59`, 기본 65536).

### 1.2 후보 비교

| 후보 | 내용 | 평가 |
|---|---|---|
| (a) `CompletableFuture<Integer>` 체인 | 모든 명령이 future 를 돌려주고 `/execute` 가 `thenCompose` 로 잇는다 | 43개 명령 서명 변경이 필요하고, `store`(콜백) · `return`(프레임 discard) · 함수 태그 합산처럼 "결과가 여러 번 발생" 하는 바닐라 의미를 future 하나로 표현하기 어렵다. 포크 N 개를 `allOf` 로 모으면 **실행 순서**(포크 1 의 실행이 끝난 뒤 포크 2)가 깨진다(모두 동시에 region 으로 날아간다). |
| (b) 대상 region 스레드면 동기 지름길 | 실행 스레드가 이미 대상 소유자면 그 자리에서 수행 | 명령은 전역 스레드에서만 돌아 "대상 region 에 있는 실행 주체" 가 없다. 대신 **future 가 이미 완료됐으면 즉시 이어 가는** 지름길은 이미 있고(`EntityArgument.java:84`, `EntityOps.java:71-73`, `ScoreHolderArgument.java:134-143`) 유지한다. 플레이어·storage·점수판·보스바 대상은 늘 이 길을 탄다. |
| (c) 전역 스레드가 단계별로 진행하며 비동기 결과 대기 | 바닐라 큐를 전역 스레드에 두고, region 결과가 필요할 때만 큐를 멈추고 재개 | 바닐라 의미(순서·콜백·discard·예산)를 그대로 가져올 수 있고, 명령 서명을 바꾸지 않는다. 비용은 region 왕복마다 틱 하나(인박스 드레인) 가 걸릴 수 있다는 것. |

**선택: (c) + (b) 의 완료-즉시 지름길.** (a) 의 future 는 "큐를 멈추게 하는 신호" 로만 쓴다.

### 1.3 Furnace 클래스 설계(`S/command/exec/` 새 패키지)

```
ResultCallback            // = eq. onResult(boolean,int) → CompletableFuture<Void> (아래 §3.4 E4 때문에 비동기 허용). EMPTY, chain(a,b)
Frame(depth, callback, discard)                 // = hu
ChainModifiers(byte)                            // = hn
ExecutionContext                                 // = hs. 전역 스레드 전용. 덱 + 새 항목 버퍼 + 체인 예산 + 포크 한도 + 큐 상한
CommandQueueEntry(frame, task)                  // = ho
EntryAction { void execute(ExecutionContext, Frame) }                 // = hr
UnboundEntryAction { void execute(CommandSource, ExecutionContext, Frame) ; bind(source) }   // = hw
BuildContexts(input, ContextChain)              // = hy (+ TopLevel/Continuation/Unbound 세 변형)
ExecuteCommand(input, modifiers, CommandContext) // = ib
ContinuationTask                                 // = ia
CallFunction(InstantiatedFunction, callback, returnFrame)  // = hz
IsolatedCall                                      // = id (execute if function 용)
FallthroughTask                                   // = ic
CustomCommandExecutor / CustomModifierExecutor    // = hp / hq (function·return 전용 노드)
CommandQueue                                      // Furnace 전용. 최상위 컨텍스트 FIFO(§2.1)
```

brigadier 사본에는 `ContextChain`(`tryFlatten`, `getStage`, `nextStage`, `getTopContext`, `runModifier`, `runExecutable`) 을 추가한다. 지금 `CommandDispatcher.execute`(`S/command/brigadier/CommandDispatcher.java:44-114`) 가 하는 평탄화·실행은 `ContextChain` 으로 옮기고, `execute()` 는 테스트 호환용으로 남기되 **`Commands.performCommand` 는 더 이상 `dispatcher.execute` 를 부르지 않는다**(§1.5).

`CommandSource`(`S/command/CommandSource.java`) 에 추가하는 필드(불변, 복사 메서드):

| 필드 | 바닐라 | 메서드 |
|---|---|---|
| `ResultCallback callback` | `l` | `withCallback(cb)`, `withCallback(cb, chain)`, `clearCallbacks()`(= `a_()`), `callback()` |
| `Rider entity` (플레이어·일반 엔티티·null) | `k` | `withEntity(Rider)` — 이름·표시명도 엔티티 것으로(`et.java:79-83`). 지금은 `player` 필드만 있어 `getEntityOrException()` 이 플레이어만 돌려준다(`CommandSource.java:122-125`) |
| `World level` | `e` | `withLevel(World)` — 좌표를 `coordinateScale` 비율로 바꾼다(`et.java:122-130`: 네더 8). `level()` 은 지금 플레이어 차원으로 계산(`CommandSource.java:86-92`) → 필드로 바꾼다 |
| `EntityAnchorArgument.Anchor anchor` | `m` | `withAnchor`, `getAnchor()`; `facing(Vec3)` 은 anchor 적용 위치 기준(`et.java:136-145`) |
| `ExecutionContext ctx` (실행 중에만, 포크 복사에 그대로 전달) | 없음(바닐라는 ThreadLocal) | `defer(CompletableFuture<Integer>)` §1.4 |

`withSilent()`(=`a()`), `withPermission`, `withPosition`, `withRotation` 은 있는 것·추가하는 것 모두 `this` 와 같으면 같은 객체를 돌려준다(바닐라와 같이; `execute run execute ...` 가 소스를 복제하지 않게).

### 1.4 점진 이행 어댑터: 지연 결과(deferred)

- `ExecuteCommand.execute(source, ctx, frame)` 절차:
  1. `ctx.incrementCost()` (= `hs.e()`).
  2. `int r = command.run(context.copyFor(source))` 를 try.
  3. `Deferred d = ctx.takeDeferred()` — 명령 본문 안에서 `source.defer(future)` 가 불렸으면 비어 있지 않다.
  4. d 가 없으면: `source.callback().onResult(true, r)` (legacy 명령은 **r 이 그대로 결과**, 예외면 `onFailure` + 메시지). `onResult` 가 미완료 future 를 돌려주면 그 future 를 d 로 삼는다(E4 store).
  5. d 가 있으면: `ctx.suspend(d.future.orTimeout(100틱))`. 완료 시 전역 스레드에서 `onResult(true, v)` 또는 `CommandSyntaxException` 이면 `source.sendFailure` + `onFailure`, 그 밖 예외는 `EntityOps.reportFailure` + `onFailure`, 그리고 `ctx.resume()`.
- `source.defer(future)` 는 **한 실행에 한 번** 만 허용(두 번째 호출은 `IllegalStateException` — 중첩 await 는 헬퍼 안에서 future 를 합성해야 한다. `TeleportCommand.java:57-58, 63-64` 의 `withEntities(... withEntity(...))` 중첩은 바깥 await 가 안쪽의 반환값 1 을 받던 구조라, 안쪽을 `findRiders(ctx, name, true).thenCompose` 로 합성해 defer 한 번으로 바꾼다).
- 세 헬퍼의 변경: `if (future.isDone()) return action(...)` 은 그대로(지름길). 아니면 `source.defer(future.thenCompose(v -> 결과 future))` 후 `return 1` (이 1 은 무시된다). 기존 `whenComplete` 안의 `sendFailure`/`reportFailure` 는 지운다(컨텍스트가 한다).
- **반환값이 없는 기존 명령의 의미**: `Command.run` 이 돌려준 int 가 그대로 `result`, 예외 없이 끝나면 `success=true`. 바닐라 명령은 모두 int 를 돌려주므로 "없다" 는 경우는 없다. 지금 1 을 돌려주던 명령(`/tp` 등)은 헬퍼 변경으로 자동으로 바닐라 값(대상 수)이 된다. 플러그인 명령(`S/plugin/CommandRegistryImpl.java:135` 는 `> 0` 만 본다)은 변화 없다.
- `ExecutionContext` 가 없는 호출(테스트의 `dispatcher.execute` 직접 호출)에서는 `source.defer` 가 `IllegalStateException` 을 내지 않고 `takeDeferred` 를 호출자가 못 하므로, `CommandSource.defer` 는 ctx 가 null 이면 옛 동작(`whenComplete` 로 메시지)으로 폴백한다 — 이행 중 안전장치. 모든 진입점이 §1.5 로 바뀐 뒤 제거한다.

### 1.5 진입점

`Commands.performCommand(parse, command)`(`S/command/Commands.java:53-72`) 는:
1. `validateParseResults` (그대로).
2. `ContextChain.tryFlatten(parse.getContext().build(command))` 실패면 `dispatcherUnknownCommand` 메시지(`eu.java:183-209`).
3. `server.commandQueue().enqueue(source, chain, command)` — §2.1. 반환값은 **더 이상 결과를 뜻하지 않으므로 `void`** 로 바꾸되, 호출자 호환이 필요한 `CommandRegistryImpl.java:135`(플러그인 `performCommand` boolean) 는 콜백으로 성공 여부를 받아 `CompletableFuture<Boolean>` 을 돌려주는 `performCommandAsync` 를 따로 둔다(플러그인 API 는 `furnace-api` 하위 호환 규칙 9 — 기존 boolean 메서드는 "큐에 들어갔는가" 로 의미를 바꾸고 문서에 적는다).
4. 테스트가 쓰는 `performPrefixedCommand(CommandSource, String)` 은 전역 스레드에서 불리면 큐를 즉시 돌리고, 다른 스레드(JUnit 스레드)에서 불리면 `global().submit` 으로 넘긴 뒤 **컨텍스트 종료 future 를 돌려주는** `performPrefixedCommandAsync` 를 추가한다. 기존 테스트(`ConsoleCommandsTest.java:35-39` 등 콘솔 출력을 리스트로 모으는 것들)는 `join()` 한 줄만 추가하면 된다.

---

## 2. 줄 순서·예산·포크

### 2.1 CommandQueue(전역) 와 컨텍스트 수명

- 전역 스레드에 `CommandQueue` 하나. 상태: `current: ExecutionContext | null`, `pending: ArrayDeque<TopLevel>`.
- `enqueue(top)`: current 가 null 이면 즉시 `start(top)`, 아니면 pending 에 추가. 바닐라 `executeCommandInContext` 의 "이미 컨텍스트가 있으면 거기에 합친다"(`eu.java:214-228`) 는 **같은 컨텍스트 안에서 재진입할 때**(표지판 클릭·명령 블록이 함수 안에서 또 명령을 돌릴 때) 만 적용한다: 재진입은 `CommandQueue.current` 가 있고 호출 스택이 그 컨텍스트 안이면 `current.queueNext(...)`, 밖(다른 틱의 패킷 처리)에서 들어온 것은 pending. 구분은 `ExecutionContext.running` 플래그(`runCommandQueue` 진입/이탈에서 토글).
- `start(top)`: `new ExecutionContext(max(1, gameRule maxCommandChainLength), gameRule maxCommandForkCount)`; `queueInitialCommandExecution`; `run()`.
- `run()`: 바닐라 `hs.a()` 루프. 항목 실행 중 `suspend(future)` 가 불리면 루프를 빠져나오고 `suspended=true`. future 완료 시 `global().execute(() -> { 완료 처리; resume(); })`, `resume()` 은 `run()` 재진입. 큐가 비면 `finish()` → `onComplete` future 완성 → `CommandQueue` 가 pending 의 다음을 start.
- 결과: **한 틱 안에서 두 컨텍스트가 섞이지 않는다**(바닐라와 같음). 대가: region 왕복이 있는 함수가 돌고 있으면 뒤 채팅 명령이 그만큼(보통 1~2 틱) 늦게 시작한다. region 에 간 작업은 그 region 의 다음 인박스 드레인(`S/world/Region.java:100-111`, 예산 25ms)에서 돈다.
- **가정**: 바닐라 함수가 수천 줄을 같은 틱에 끝내는 데 반해 Furnace 는 region 왕복 줄마다 틱이 걸릴 수 있다. 관측 가능한 차이(한 함수가 여러 틱에 걸침)는 context-notes 에 "의도한 차이" 로 적는다. 왕복이 없는 줄(플레이어·storage·점수판·보스바·전역 상태) 은 같은 틱에 끝난다.

### 2.2 예산

| 한도 | 바닐라 | Furnace |
|---|---|---|
| `maxCommandChainLength` | 컨텍스트당 `g` 감소: 수정자 적용 1회(`hy.java:44`), 함수 호출(`hz.java:16`), 명령 실행(`ib.java:22`). 0 이면 로그 후 큐 중단(`hs.java:78-80`) | 같다. `GameRules.getInt("maxCommandChainLength")`(`S/world/GameRules.java:91`, 기본 65536 `:41`). 컨텍스트가 여러 틱에 걸쳐도 리셋 없음 |
| `maxCommandForkCount` | 수정자 하나의 결과 소스 누적이 한도 이상이면 `command.forkLimit` 오류(`hy.java:51-54`) | 같다 |
| 큐 상한 | 10,000,000 넘으면 전부 비우고 오류 로그(`hs.java:54-62, 90-93`) | 같다 |
| 지연 future 타임아웃 | 없음(동기) | 100 틱(5 s). 초과 시 `command.failed` + 로그 + `onFailure`, 컨텍스트 계속 |

### 2.3 포크(수정자) 와 region 경계

- 수정자는 두 종류로 등록한다: 동기 `RedirectModifier`(기존, `S/command/brigadier/RedirectModifier.java`) 와 **비동기 `AsyncRedirectModifier { CompletableFuture<Collection<S>> applyAsync(CommandContext<S>) }`**. `BuildContexts` 의 수정자 루프(`hy.java:31-67`) 는 소스 하나씩 수정자를 적용하다 미완료 future 를 만나면 **그 자리에서 suspend** 하고, 완료 후 같은 소스 인덱스에서 이어 간다(소스 순서 유지). 포크 한도 검사는 소스가 추가될 때마다.
- `as @e`: 선택자 해석 `EntitySelector.findRiders`(`S/command/arguments/EntitySelector.java:111-162`) 는 모든 region 에서 후보를 모아 합치는 future 다 — 그대로 비동기 수정자의 재료. 소스 변형(`withEntity(e)`, `at` 의 `withPosition/withRotation/withLevel`) 은 **전역 스레드에서 엔티티 위치·회전·차원을 읽는다**(소유 스레드 아님, 읽기뿐). `/tp`·`/spreadplayers` 가 이미 그렇게 하고 있고 context-notes 171 에 "의도한 차이" 로 기록돼 있다. 같은 결정을 `/execute` 에도 적용한다. 순서는 플레이어(접속 순) → 엔티티 id 순(바닐라는 레벨 조회 순; context-notes 163).
- 포크 뒤 실행: 소스마다 `ExecuteCommand` 항목을 `ContinuationTask` 로 **하나씩** 큐에 넣는다(`ia.java:21-27`: 두 개 이하면 바로, 셋 이상이면 하나 실행 후 자신을 다시 넣음). 포크 1 의 명령이 defer 로 멈추면 포크 2 는 그 뒤에 시작한다 — 바닐라 순서와 같다. 포크 N 개가 모두 다른 region 에 있으면 왕복 N 번 = 최악 N 틱. **성능 최적화는 하지 않는다**(순서 보장이 우선; 필요해지면 "연속한 같은-region 포크 묶기" 를 나중에).

### 2.4 결과 합산(`store`/`if`/명령 블록)의 정의

| 상황 | 바닐라 | Furnace |
|---|---|---|
| `execute store result|success X run cmd` 소스 1개 | `withCallback(storeCb, chain)`(`amt.java:268-300`) → 명령 결과 1번 저장 | 같다 |
| `execute as @e[3] store ... run cmd` | store 가 `as` 뒤에 있으므로 포크된 소스 3개가 각각 콜백을 가짐 → **3번 저장**, 마지막 값이 남음(저장 대상이 같다면) | 같다(포크 순서대로) |
| `execute store ... as @e[3] run cmd` | 콜백은 포크 전 소스에 붙고 포크 복사가 콜백을 물려받음(`et.java:79-83` 은 `l` 그대로) → 역시 **3번 저장**. 합계가 아니다 | 같다 |
| 명령 블록 `SuccessCount` | 콜백 `(ok,_) -> if ok count++`(`dbx.java:105-109`) → **성공한 포크 수** | 같다(명령 블록 구현 시) |
| `execute if entity @e` 종단 | 일치 수 반환, 0 이면 `conditional.fail`(`amt.java:541-559`) | 같다 |
| `execute if entity @e run cmd` | 수정자: 일치하면 소스 그대로(1개), 아니면 빈 목록(`amt.java:443, 592-594`) — 포크 수 아님 | 같다 |
| `execute if function f` | 소스마다 `IsolatedCall` 로 함수 실행, **함수 반환이 0 이 아니면**(unless 는 0) 그 소스 통과(`amt.java:769-783`, `amt.d:810`). 함수가 return 없이 끝나면 콜백이 안 불려 실패 취급 | 같다 |
| `/function #tag`(함수 N개) | 소스 콜백이 EMPTY 면 각자 실행; 아니면 누적기: 하나라도 성공하면 `onSuccess(합)`(`amy.java:116-157`) | 같다 |
| `/function f` 결과 메시지 | 소스가 silent 가 아니면 `commands.function.result`(이름, 값)(`amy.java:25-29, 95-100`) | 같다 |
| 함수 안 `/return v` | 프레임 콜백 `onSuccess(v)` + 그 깊이 이상 항목 discard(`anx.java:43-51`) | 같다 |
| 함수 안 `/return fail` | `onFailure` + discard(`anx.java:19-26`) | 같다 |
| `/return run cmd` | 프레임을 먼저 discard, isReturn 플래그로 다음 단계 진행; 소스가 비면 `FallthroughTask`(실패); 소스가 있으면 **첫 소스** 의 콜백에 프레임 콜백을 chain(`anx.java:28-41`, `hy.java:73-90`) | 같다 |
| `success` 와 `result` | `success` 는 콜백 `onResult(true, …)` 가 불렸는가(실패·예외는 false), `result` 는 명령이 돌려준 int(실패면 0). `store success` 는 1/0 (`amt.java:273, 281, 294`) | 같다 |

---

## 3. `/execute` 5단계와 실행 방식 배정

공통: `execute` 루트 노드는 `run` → `redirect(root)`, 나머지 수정자는 `redirect(executeNode, fn)`(단일) 또는 `fork(executeNode, fn)`(다중)(`amt.java:47-137`). Furnace brigadier 사본의 `ArgumentBuilder.redirect/fork/forward`(`S/command/brigadier/ArgumentBuilder.java:46-58`) 와 `CommandContextBuilder.withNode`(수정자·forks 기록) 는 이미 있다. 비동기 수정자는 `fork(node, AsyncRedirectModifier)`/`redirect(node, AsyncSingleRedirectModifier)` 오버로드로 받고 `CommandNode.getRedirectModifier()` 의 타입을 둘 다 담는 상위 인터페이스로 바꾼다. 트리 모양은 `VanillaCommandTreeTest` 로 전수 대조하므로 노드 이름·파서는 바닐라 덤프와 같아야 한다.

| 단계 | 하위 명령 | 실행 방식 | 비고 |
|---|---|---|---|
| **E1** | `run` | redirect(root), 동기 | |
| | `as <targets>` | **비동기 포크**: `findRiders` → 소스마다 `withEntity`(`amt.java:57-65`) | 플레이어만 고르는 선택자·`@s` 는 완료 future 라 동기 지름길 |
| | `at <targets>` | 비동기 포크: `withLevel(e.level).withPosition(e.pos).withRotation(e.rot)`(`amt.java:66-74`) | 위치·회전 읽기는 전역 스레드(§2.3) |
| | `positioned <pos>` | 동기 redirect: `withPosition(pos).withAnchor(FEET)`(`amt.java:78`) | anchor 를 FEET 로 되돌리는 것 주의 |
| | `positioned as <targets>` | 비동기 포크: `withPosition(e.pos)` | |
| | `positioned over <heightmap>` | **비동기 redirect**: `world.submitAtChunk(cp, r -> 높이)`(`S/world/World.java:217-223`); 청크가 로드돼 있지 않으면 `argument.pos.unloaded`(`amt.java:88-99`) | region 읽기 |
| | `rotated <rot>` / `rotated as` | 동기 / 비동기 포크 | |
| | `facing <pos>` | 동기: `facing(vec)`(`et.java:136-145`, anchor 적용 위치에서) | `/tp facing` 의 FEET 고정(context-notes 171) 을 `source.getAnchor()` 로 바꾼다 |
| | `facing entity <targets> <anchor>` | 비동기 포크: `facing(anchor.apply(e))` | |
| | `align <axes>` | 동기: `BlockPos.floor` 후 축별 내림(`amt.java:130`) | |
| | `anchored <anchor>` | 동기 | |
| | `in <dimension>` | 동기: `withLevel` + 좌표 스케일(`et.java:122-130`) | `server.world(key)` 는 있음 |
| | `summon <entity>` | **비동기 redirect**: 그 위치 region 에서 `SummonCommand` 의 생성 경로를 호출해 엔티티를 만들고 `withEntity`(`amt.java:134, 735-738`) | `S/command/builtin/SummonCommand.java` 재사용 |
| | `on owner|leasher|target|attacker|vehicle|controller|origin|passengers` | **비동기 포크**: 소스 엔티티의 주인 region 에서 관계 엔티티를 읽어(`EntityOps.onOwner`) 소스로(`amt.java:689-733`); 제거된 엔티티 제외 | B-4a 는 E3 에 뒀지만 점수판 무관 → E1 로 옮김 |
| **E2** | `if|unless entity <sel>` | 비동기(선택자 future). 포크 수정자: 일치 시 소스 유지; 종단 `executes` 는 일치 수(`amt.java:441-445`) | |
| | `if block <pos> <block>` | **비동기**: 그 칸 region 에서 `BlockPredicate.test(BlockInWorld)`(`amt.java:310-321`) | `S/command/arguments/BlockPredicateArgument.java` 있음 |
| | `if blocks <start> <end> <dest> all|masked` | 비동기: 두 영역이 **같은 region 에 로드** 돼 있어야 함(`/clone` 과 같은 제약, context-notes 184). 32768 칸 초과 `blocks.toobig`(`amt.java:635-687`). 블록 엔티티 비교는 NBT 저장본(`e(ka)`) 동등성 | 종단은 일치 칸 수 |
| | `if biome <pos> <biome>` | 비동기(region 바이옴 읽기) | `/fillbiome` 경로 참고 |
| | `if loaded <pos>` | 비동기: `chunkAt` 이 FULL 이고 월드 안(`amt.java:302-306`) | |
| | `if dimension <dim>` | 동기(소스 level 비교) | |
| | `if predicate <loot predicate>` | 동기: `LootContext(origin=source.pos, this=source.entity)`(`amt.java:584-590`) | 엔티티 필드를 읽는 조건이 있으면 전역에서 읽는다(읽기뿐) |
| | `if items entity|block` | 비동기(엔티티 주인/칸 region) (`amt.java:449-485, 503-539`) | `S/command/ItemSlots.java` 재사용 |
| **E3** | `if score <target> <obj> (=,<,<=,>,>=) <source> <obj>` / `matches` | 동기: 점수판은 `synchronized`(`S/scores/ServerScoreboard.java`, context-notes 180). 점수 없으면 false(`amt.java:565-582`) | `ScoreHolderArgument` 가 `@e` 면 future → 비동기 |
| | `store result|success score <targets> <obj>` | 콜백 등록(동기): 홀더마다 `set`(`amt.java:268-277`) | |
| | `store ... bossbar <id> value|max` | 콜백(동기): `CustomBossEvents` 는 `synchronized`(`S/bossbar/CustomBossEvents.java:27-50`) (`amt.java:279-288`) | |
| **E4** | `store ... block <pos> <path> <type> <scale>` | **비동기 콜백**: 콜백이 그 칸 region 에서 `getData → path.set → setData` 를 수행하고 future 를 돌려준다. 바닐라는 콜백 안 `CommandSyntaxException` 을 **삼킨다**(`amt.java:290-300`) → 같이 삼킨다(실패 메시지 없음) | `DataCommands.blockProvider`(`S/command/builtin/DataCommands.java:159-185`) 재사용 |
| | `store ... entity <target> <path> ...` | 비동기 콜백(주인 region). 플레이어면 `commands.data.entity.invalid`(삼켜짐) | `DataCommands.onEntity`(`:120-131`) |
| | `store ... storage <id> <path> ...` | 동기 콜백 | |
| | `if data block|entity|storage <path>` | 비동기/동기: `path.countMatching(data) > 0`(`amt.java:488-498, 561-563`) | |
| **E5** | `if function <name>` | `CustomModifierExecutor`(`amt.d`, `amt.java:740-789`): 소스마다 `IsolatedCall` 로 함수 호출 후 반환값 판정 | 함수 시스템 선행 |
| | `run function …` | `/function` 노드로 redirect 되므로 별도 구현 없음 | |
| | `/function`, `/return`, `/schedule` | §4 | |

E4 콜백이 비동기라서 `ResultCallback.onResult` 가 `CompletableFuture<Void>` 를 돌려준다(§1.3). `chain(a,b)` 는 `a.onResult(...).thenCompose(_ -> b.onResult(...))` — 순서 유지(바닐라는 a 다음 b 호출). `ExecuteCommand` 는 콜백 future 가 미완료면 suspend(§1.4 단계 4).

실행 컨텍스트가 소유 경계를 넘는 규칙(요약): **소스·컨텍스트·프레임 객체는 전역 스레드에만 산다.** region 으로 넘기는 것은 "엔티티/블록 엔티티 하나에 대한 람다" 와 결과 future 뿐이다(`EntityOps.runOnOwner` `S/command/EntityOps.java:30-47` 의 재시도 5회 포함). region 쪽 람다는 `CommandSource` 의 메시지 메서드를 부르지 않는다(메시지는 future 결과를 받은 전역 쪽에서).

---

## 4. 함수 시스템

### 4.1 로드(`ServerFunctionLibrary` = `alf`)

- 리소스 위치 `data/<ns>/function/<path>.mcfunction`(`alf.java:21-22`: 레지스트리 키 `function`, 확장자 `.mcfunction`). 태그는 `data/<ns>/tags/function/*.json`(`awv` TagLoader, `alf.java:24`). Furnace: `S/data/ResourceManager`·`TagLoader` 를 쓰는 `FunctionLibrary` 를 `DataPackManager.loadFrom`(`S/data/DataPackManager.java:135-149`) 에 **레시피 뒤, 발전 과제 앞** 에 넣는다(주석의 바닐라 순서 "태그 → 레시피 → 함수 → 발전 과제" 그대로).
- 파싱 소스: 위치 `(0,0,0)`, 회전 0, 레벨 null, 권한 `functionCompilationLevel`(`alf.java:55`; 전용 서버 `function-permission-level` 기본 2 — **가정**: `server.properties` 키 이름은 바닐라 `DedicatedServerProperties` 를 다시 확인). Furnace `CommandSource.forProfile`(`CommandSource.java:64-67`) 비슷한 "컴파일 전용 소스" 를 만든다(level null 허용).
- 줄 처리(`ig.java:20-76`): trim; 끝이 `\` 면 다음 줄 이어붙이기(끝에서 끝나면 "Line continuation at end of file"); 빈 줄·`#` 무시; `/` 로 시작하면 오류("did you mean …? Do not use a preceding forwards slash."); `$` 로 시작하면 매크로 줄; 그 밖은 **로드 시점에 파싱**(`dispatcher.parse` + `validate` + `tryFlatten`, `ig.java:85-94`) 하고 실패하면 "Whilst parsing command on line N: …" 로 **함수 전체 로드 실패**(로그 `Failed to load function {}`, `alf.java:72-75`), 그 함수만 빠진다. 명령 길이 2,000,000 자 초과는 `IllegalStateException`(`ig.java:78-83`).
- 결과 타입: 매크로 줄이 없으면 `PlainTextFunction`(`ik`: 이름 + `UnboundEntryAction` 목록, 자기 자신이 `InstantiatedFunction`), 있으면 `MacroFunction`(`ij`) — **후순위**(§4.5).
- 맵은 `volatile` 불변 교체(`alf.java:23,25`): 리로드 중에도 실행 중 컨텍스트는 옛 `InstantiatedFunction` 을 쥔 채 끝난다.

### 4.2 매니저(`ServerFunctionManager` = `alg`)

- 필드: `ticking`(`#minecraft:tick` 함수 목록 스냅샷, `alg.java:72`), `postReload`(리로드 뒤 첫 틱에 `#minecraft:load` 실행, `alg.java:29-38`), 라이브러리 참조.
- `tick()`: `tickRateManager.runsNormally()` 일 때만(`alg.java:30` `d.aQ().i()`, **가정**: `/tick freeze` 중에는 함수 틱 없음, step 중에는 있음) → postReload 면 load 태그 먼저, 그 다음 tick 태그 함수들을 **각각 새 컨텍스트** 로(`alg.java:41-64`: `eu.a(source, ctx -> hs.a(ctx, fn, source, eq.a))`), 소스는 `server.createCommandSourceStack().withPermission(2).withSuppressedOutput()`(`alg.java:76-78`), 콜백 EMPTY. 함수 인스턴스화 실패(`ew` = 매크로 인자 오류)는 조용히, 다른 예외는 `Failed to execute function` 경고.
- Furnace 위치: `ServerCore.tick()`(`S/ServerCore.java:389-395`) **맨 앞**(`border.tick()` 전) 에 `functions.tick()` 을 넣는다 — 바닐라 `tickChildren` 이 레벨 틱(보더·날씨·시간) 전에 `commandFunctions` 를 돈다(`MinecraftServer.java:835-837`). `/tick freeze` 는 `scheduledTick`(`:360-373`) 이 `tick()` 자체를 건너뛰므로 자동으로 맞는다; step 때는 `tick()` 이 불려 함수도 돈다.
- 각 함수 호출은 `CommandQueue.enqueue` 로 **별개 최상위 컨텍스트** 다. 따라서 tick 함수 A 가 region 왕복으로 미완료면 B 는 같은 틱에 시작하지 못하고 A 뒤에 돈다(§2.1). 다음 틱의 `functions.tick()` 이 또 enqueue 하면 pending 이 쌓인다 → **상한**: pending 에 같은 함수가 이미 있으면 이번 틱 것은 건너뛰고 경고 1회(가정: 바닐라에는 없는 보호. 왕복 없는 함수는 쌓이지 않는다).
- 콘솔 소스: `CommandSource.console`(`CommandSource.java:43-46`) 에 `withPermission(2).withSilent()`.

### 4.3 `/function`(`FunctionCommand` = `amy`)

- 트리: `function <name:function>` [`<arguments:nbt_compound_tag>` | `with (block <pos>|entity <target>|storage <id>) [<path>]`](`amy.java:31-63`). `hb` = `FunctionArgument`(함수 또는 `#태그`, 자동완성 `amy.b` 는 태그에 `#` 접두).
- 실행은 `CustomCommandExecutor`(`amy.c`, `amy.java:165-187`): 비어 있으면 `scheduled.no_functions`; 메시지 `commands.function.scheduled.single|multiple`(성공 메시지, allowLogging=true) 를 **먼저** 보낸 뒤 호출; 호출 소스는 `source.withSilent().withMaximumPermission(2)`(`amy.java:74-76`: `b(2)` 는 "2 이상으로 올리기만" `et.java:114-116`).
- 호출 본문(`amy.java:78-159`): isReturn 이면 `a()`(함수마다 콜백 = chain(source.callback, frame.callback), `CallFunction(..., returnFromFunction=true)`, 마지막에 `FallthroughTask`), 아니면 `b()`(§2.4 합산 규칙). `CallFunction`(`hz.java:16-26`): 비용 1, 새 프레임 depth+1, discard 는 returnFromFunction 이면 바깥 프레임의 discard 를 물려받고 아니면 자기 깊이 discard, 줄들을 `ContinuationTask` 로 하나씩.
- `with … <path>` 는 그 데이터를 **동기** 로 읽어야 인자를 만든다 → entity/block 이면 비동기(DataProvider future) 후 함수 호출을 큐에 넣는다. `CustomCommandExecutor.execute` 자체가 defer 를 쓸 수 있도록 `ExecuteCommand` 와 같은 `takeDeferred` 처리를 `BuildContexts` 의 hp 분기(`hy.java:79-84`) 에도 둔다.

### 4.4 `/return`(`anx`) 과 `/schedule`(`aod`)

- `/return <value>`/`fail`/`run …`: §2.4. `run` 은 `forward(root, CustomModifierExecutor, fork=false)`(`anx.java:15`) — brigadier 사본의 `forward` 로 가능.
- `/schedule function <f|#tag> <time> [append|replace]`(`aod.java:20-81`): time 0 → `commands.schedule.same_tick`; 트리거 시각 = **소스 레벨의 gameTime** + time(`aod.java:59` `$$0.e().Z()`; 모든 차원이 같은 gameTime 을 공유하므로 Furnace 는 `ServerCore.gameTime()`); 큐는 **오버월드 레벨 데이터의 TimerQueue**(`aod.java:61` `bb().I().s()`); replace(기본) 면 같은 이름(`"ns:path"` / 태그 `"#ns:path"`) 항목을 모두 지운 뒤 추가; **같은 이름·같은 시각이 이미 있으면 추가 안 함**(`ewp.java:61-68`); 반환값 `floorMod(triggerTime, Integer.MAX_VALUE)`. `schedule clear <name>` 은 지운 개수, 0 이면 `cleared.failure`.
- TimerQueue(`ewp`): 우선순위 큐(시각, 등록 순번 UnsignedLong), 이름×시각 테이블; `tick(server, gameTime)` 은 `time <= gameTime` 인 것을 순서대로 실행(`ewp.java:48-59`). 콜백 `FunctionCallback`(`ewl`: 함수 하나, 매니저 소스로 실행) / `FunctionTagCallback`(`ewm`: 태그의 함수들 각각). 저장: `ScheduledEvents` 리스트 `{Name, TriggerTime, Callback:{Type:"minecraft:function"|"minecraft:function_tag", Name}}`(`ewp.java:92-104`, `ewl.a:20-28`).
- 틱 위치: 바닐라 `ServerLevel.tickTime`(`aqu.java:255-260`): `gameTime+1` 설정 **직후**, dayTime 증가 **직전** 에 `timerQueue.tick(server, gameTime)`. Furnace: `WorldClock.tickTime`(`S/world/WorldClock.java`) 이 gameTime 을 올린 직후에 `scheduler.tick(gameTime)` 훅을 부른다(`ServerCore.tick`:394 의 `clock.tickTime(gameRules, all)` 에 콜백 인자 추가). 실행은 `CommandQueue.enqueue`(함수당 새 컨텍스트).
- 저장: `LevelData` 에 `scheduledEvents: NbtList`(`customBossEvents` 와 같은 방식 `S/world/storage/LevelData.java:55-59, 216`), `ServerCore.writeLevelData`(`:419-432`) 에서 `timerQueue.save()`.
- `/reload`: `DataPackManager.reload`(`DataPackManager.java:178-197`) → `apply` 에서 새 `FunctionLibrary` 로 교체 → `onReload` 리스너(`ServerCore.java:84-100`) 에서 `functions.replaceLibrary(lib)`(= `alg.a(alf)` `alg.java:66-74`: ticking 스냅샷 갱신 + postReload=true → 다음 틱 `#load`). TimerQueue 는 이름으로만 함수를 찾으므로(`ewl.java:12-13`) 리로드 영향 없음.

### 4.5 매크로(후순위, E5 다음 커밋)

`$` 줄은 `MacroFunction`(`ij`): 변수 목록(`il` 파서, `$(name)`), 인자 compound 로 치환해 **호출 때** 파싱, 치환 결과별 8개 LRU 캐시(`ij.java:20-22, 53-64`), 수 포맷(float/double 소수 15자리 `#` 포맷, byte/short/long 그대로, 그 밖 `s_()`=SNBT)(`ij.java:68-80`), 인자 없으면 `commands.function.error.missing_arguments`, 변수 빠지면 `missing_argument`, 파싱 실패 `commands.function.error.parse`. 인스턴스 이름은 `ns:path/<인자 해시>`(`ij.java:96`). `/function f with …` 의 인자 compound 아니면 `argument_not_compound`(`amy.java:65-72`).

---

## 5. 스레드 소유권·데드락

| 경로 | 규칙 |
|---|---|
| 전역 → region | `World.runAt/submitAtChunk/forEachRegion`(`S/world/World.java:197-228`), `EntityOps.runOnOwner`(재시도 5회, 못 찾으면 onFail). 전역은 future 를 **기다리지 않고** 컨텍스트를 suspend 한다(§2.1). |
| region → 전역 | future 완성만. region 람다는 `CommandSource` 를 만지지 않는다(§3 끝). |
| region → 다른 region | 없다. 두 region 상태가 필요한 연산(`if blocks`, `/clone`)은 같은 region 에 로드돼 있어야 하고 아니면 `argument.pos.unloaded`(context-notes 184 와 같은 결정). |
| region 이 명령을 시작 | 없다. 표지판·명령 블록·플러그인은 `global().execute` 로 넘긴다(지금 그대로). 명령 블록 구현 시 `CommandBlock.tick`(`S/block/types/CommandBlock.java:31-34`) 은 region 틱에서 "명령 문자열 + 콜백" 만 전역 큐에 넘기고 SuccessCount 갱신은 콜백 future 가 region 으로 되돌려 쓴다. 연쇄(chain) 명령 블록의 "같은 틱 안 순서" 는 큐 FIFO 로 보장되나 결과 반영은 다음 region 틱 — **의도한 차이** 로 기록. |
| 타임아웃 | 지연 future 100 틱. region 이 사라졌거나(언로드) 엔티티가 제거돼 `runOnOwner` 가 onFail 로 끝나면 즉시 실패. |
| 취소 | 없음(바닐라도 없음). `return` 의 discard 는 큐 항목 제거일 뿐, 이미 region 에 간 람다는 돌고 결과는 버려진다(콜백 미호출). |
| 서버 종료 | `GlobalThread.close`(`S/sched/GlobalThread.java:62-65`) 뒤 resume 이 돌지 않아 미완료 컨텍스트는 그대로 버려진다. `CommandQueue.close()` 에서 pending 을 비우고 경고 로그 한 줄. 바닐라도 종료 시 큐를 보존하지 않는다. |
| 테스트 가드 | `ExecutionContext` 의 모든 공개 메서드에 `assert global.isOwnerThread()`; `Owned` 와 같은 방식으로 테스트에서 위반을 잡는다(규칙 8). |

---

## 6. 구현 단계(커밋 단위) · 회귀 테스트 · 모델 배정

각 단계는 `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test` 통과 상태로 끝난다. `VanillaCommandTreeTest` 는 `execute`·`function`·`return`·`schedule` 을 등록하는 단계부터 그 트리를 전수 대조한다(부분 구현 중에는 `NOT_YET` 에 미구현 하위 노드 이름을 올린다 — `locate` 선례, `VanillaCommandTreeTest.java:93`).

| 단계 | 내용 | 회귀 테스트(새로) | 규모 | 모델 |
|---|---|---|---|---|
| **S0** 실행 코어 | `exec/` 패키지(§1.3), `ContextChain`, `CommandSource` 콜백·entity·level·anchor 필드, `Commands.performCommand` → `CommandQueue`, 세 await 헬퍼의 `defer` 전환, `performPrefixedCommandAsync`. `/execute` 는 아직 없음 | `ExecutionContextTest`: 큐 순서(한 항목이 넣은 3개가 그 직후 순서대로), 체인 예산 소진 로그, 포크 한도, 큐 상한. `DeferredResultTest`: 소 2마리를 region 에 올리고 콘솔 `tag @e[type=cow] add a` → 콜백 결과 **2**(지금은 1), `tag @e[type=cow] remove zzz` → success=false; 다른 두 명령을 연달아 enqueue 하면 두 번째는 첫 번째 완료 뒤 시작. 기존 `TeleportCommandTest`·`TagCommandTest`·`DataCommandsTest` 는 `Async…join()` 으로 갱신 | 3일 | **Opus**(동시성 핵심·기존 11개 명령 파일 영향) |
| **S1** E1 | `execute run/as/at/positioned(as/over)/rotated(as)/facing(entity)/align/anchored/in/summon/on` + 비동기 수정자 | `ExecuteE1Test`: `execute as @e[type=cow] run tag @s add x` 결과 2 와 태그 실제 부여(region 에서 확인); `execute at @e[type=cow,limit=1] run setblock ~ ~-1 ~ stone` 위치; `in minecraft:the_nether positioned` 좌표 /8; `positioned over motion_blocking`; `as @a`(플레이어)는 같은 틱 완료 | 2일 | Sonnet(설계 §3 표를 따름) |
| **S2** E2 | `if/unless entity/block/blocks/biome/loaded/dimension/predicate/items` | `ExecuteE2Test`: `if entity` 종단 카운트·`unless` 실패 카운트 메시지 `conditional.fail_count`; `if block` 로드 안 된 칸 오류; `if blocks` 32768 초과; 다른 region 두 영역 → unloaded | 2일 | Sonnet |
| **S3** E3 | `if score`, `store score/bossbar` | `ExecuteStoreScoreTest`: `execute store result score #a obj run data get storage t x` → 실제 값(동기); `execute store result score #a obj run data get entity @e[type=cow,limit=1] Health` → **실제 체력**(지연 결과가 콜백으로 저장되는 것 — 핵심 회귀); `as @e[2] store` 가 두 번 저장 | 1일 | Sonnet |
| **S4** E4 | `store block/entity/storage`, `if data` | `ExecuteStoreDataTest`: `execute store result storage t v int 1 run data get entity cow Health` 뒤 **같은 컨텍스트 다음 줄 없이** 바로 `data get storage t v` 가 값을 보여 줌(콜백 future 완료까지 컨텍스트가 기다렸는지); `store block` 은 region 에서 블록 엔티티 NBT 확인; 콜백 안 오류는 메시지 없이 삼김 | 2일 | **Fable**(비동기 콜백 체인 순서가 미묘) |
| **S5** 함수 | `FunctionLibrary`, `FunctionManager`, `FunctionArgument`, `/function`, `/return`, `#load`/`#tick`, `/reload` 연결, `commands.function.result` | `FunctionTest`(테스트 데이터팩을 `@TempDir/datapacks` 에 쓴다): (1) 줄 순서 — `tag @e[type=cow] add a` / `execute if entity @e[tag=a] run scoreboard players set #n o 1` / `tag @e[type=cow] remove a` 가 들어간 함수 뒤 `#n o` = 1(앞 줄이 region 에서 끝난 뒤 다음 줄 실행); (2) `return 7` 뒤 줄은 실행 안 됨, `/function` 결과 7 과 메시지; (3) `return fail`; (4) `return run execute if entity @e[type=zombie]`(없음) → 실패; (5) 태그 2개 함수 합산; (6) 파싱 실패 함수는 로드 제외·로그; (7) `#load` 가 리로드 다음 틱에 1회, `#tick` 매 틱; (8) `if function` 포크 통과/탈락; (9) `maxCommandChainLength 3` 에서 4줄 함수가 중단 | 3일 | **Opus**(return/isReturn/discard 의미·리로드 수명) |
| **S6** schedule | `TimerQueue` + 저장 + `/schedule` | `ScheduleTest`: same_tick 오류; `schedule function f 2` 후 `clock` 2틱 진행 시 실행; 같은 이름·시각 중복 무시; `replace` vs `append`; `clear` 개수; level.dat 왕복(`ScheduledEvents`) | 1일 | Sonnet |
| **S7** 매크로 | `MacroFunction`, `with …` 인자, 캐시 8 | `MacroFunctionTest`: 치환·수 포맷·missing_argument·캐시 | 1일 | Sonnet |
| S8 문서 | context-notes 에 의도한 차이(§2.1 가정, 명령 블록 결과 지연), survey B-2 의 `execute/function/return/schedule` 행 갱신, graphify 갱신(로드맵 한 단계 완료 시점) | — | 0.5일 | Sonnet |

총 규모: 약 15.5 작업일. S0 이 끝나기 전에는 S1~S7 을 시작하지 않는다(모두 `exec/` 에 의존). S1·S2·S3 은 S0 뒤 병렬 가능하나 같은 `ExecuteCommand.java` 를 만지므로 한 사람씩 순서대로가 안전하다. S6 은 S5 뒤, S7 은 S5 뒤.

### 6.1 구현자가 자주 틀릴 지점(체크리스트)

1. `positioned <pos>` 는 anchor 를 FEET 로 되돌린다(`amt.java:78`). `positioned as` 는 되돌리지 않는다(`:83`).
2. `at` 은 레벨도 바꾼다(`:70`). `as` 는 레벨을 바꾸지 않는다.
3. `store` 콜백은 **포크 전/후 어디에 있어도 포크마다 한 번** 이다(§2.4). 합계로 구현하면 틀린다.
4. `if entity … run` 은 포크 수를 늘리지 않는다(통과/탈락만, `amt.java:592-594`).
5. `return run` 은 **먼저** discard 하고 첫 소스만 실행한다(`anx.java:35-38`, `hy.java:86-90`).
6. `/function` 의 호출 소스는 silent 지만 **결과 메시지는 원 소스** 에게 간다(`amy.java:95-100`, `$$0` = 호출한 소스).
7. 함수 로드 실패는 그 함수만 빠지고 서버는 뜬다(`alf.java:72-75`).
8. `schedule` 의 `replace` 는 같은 **이름** 전부(시각 무관)를 지운다(`aod.java:64-66`, `ewp.java:70-76`).
9. `ExecutionContext` 의 새 항목 버퍼는 **역순으로 덱 앞에** 들어간다(`hs.java:101-107`). 그래서 "현재 항목이 넣은 것들이 다음에, 넣은 순서대로" 가 성립한다. 뒤에 붙이면 함수 안에서 `execute as @e run function g` 의 g 줄들이 호출자 함수의 다음 줄 뒤로 밀려 순서가 틀린다.
10. 체인 예산 감소 위치 세 곳(`hy.java:44`, `hz.java:16`, `ib.java:22`) 외에서 줄이지 않는다.
11. `source.defer` 는 실행당 한 번. 중첩 await(`TeleportCommand.java:57-58, 63-64`) 는 future 합성으로 푼다.
12. `maxCommandChainLength` 는 `max(1, 값)`(`eu.java:216`).

## 7. 가정 목록(확인 필요)

- `alg.java:30` 의 `d.aQ().i()` 가 `TickRateManager.runsNormally()` 라는 것(이름 매핑 미확인). `/tick freeze` 중 함수 틱 중단으로 설계했다.
- 함수 컴파일 권한 2 의 출처가 `server.properties` `function-permission-level` 이라는 것(`alf.java:55` 의 `g` 생성자 인자만 확인).
- 전용 서버의 명령 블록 `SuccessCount` 콜백(`dbx.java:105-109`) 이 1.21.1 `BaseCommandBlock.performCommand` 라는 것(클래스 이름 매핑은 `SuccessCount` 문자열로 추정).
- §2.1 의 "컨텍스트가 여러 틱에 걸침" 과 §4.2 의 "tick 함수 pending 중복 건너뛰기" 는 바닐라에 없는 Furnace 의 의도한 차이다.
- `execute` 의 `as/at` 에서 엔티티 위치·회전을 전역 스레드에서 읽는 것은 읽기 경쟁(한 틱 늦은 값) 을 받아들인 결정이다(context-notes 171 과 같은 선택).
