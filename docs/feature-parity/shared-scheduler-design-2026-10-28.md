# 공유 region 스케줄러(B안) 확정 설계서 — 개정판 (2026-10-28)

## 변경 이력
| 날짜 | 문서 | 내용 |
|---|---|---|
| 2026-10-27 | `shared-scheduler-review-2026-10-27.md` | B안 검토. 7장 최종 재검증(R1~R7), 조건부 채택(조건 6개) |
| 2026-10-27 | `shared-scheduler-bench-overload-2026-10-27.md` | 과부하 벤치(R3 재현: 무거운 차원 region 수가 코어 3배면 순수 EDF 공유 큐에서 가벼운 차원이 완전히 굶음) |
| 2026-10-27 | `shared-scheduler-design-2026-10-27.md` | 1차 확정 설계. §9 에 1라운드 검토(D1~D9) 반영, §10 에 2라운드 발견(N1~N7) **미반영** 기록 |
| **2026-10-28** | **이 문서** | §10 의 N1~N7 을 본문에 반영해 확정. 그 사이 바뀐 코드(`/tick sprint` lockstep — `sprintDoneSummary`·`sprintJoinedSummary`·`sprintCompatible` 병합 규칙·`sprintHeld` 틱·`afterTick` 요약, `Region.chunkAt` 비차단화)에 맞춰 줄 번호·구조·단계를 다시 적음. 1차 문서는 그대로 남긴다(이 문서가 우선) |

줄 번호는 `claude/quirky-wright-lra834`(**5b15a9bb**) 기준으로 코드를 직접 읽고 적었다. 코드로 확인하지 못한 것은 **가정** 으로 표시했다. 이 문서는 설계만 담고 코드 변경은 없다.

**한 줄 요약:** 차원별 `Regionizer` 는 큐·구조 잠금·합치기·나누기·sprint 요약을 그대로 갖고, **작업 스레드만** 서버 하나의 `SharedScheduler` 로 뺀다.
워커는 잠금 없이 각 차원이 `volatile` 로 발행한 "큐 머리 마감"을 비교해 차원을 고르고, 꺼내기·끝내기만 그 차원 잠금 안에서 한다.
깨우기는 `LockSupport.unpark`(풀에 잠금 없음 → 교착 불가). 공정성은 EDF + "굶은 차원 우선(머리 지연 기준)" + "차원별 동시 점유 상한(잠금 안 재확인)".
`-Dfurnace.sharedRegionPool=false` 면 지금처럼 차원마다 풀 하나(같은 코드 경로, 풀 인스턴스만 차원 수만큼).

---

## 0. 용어

| 용어 | 뜻 |
|---|---|
| 차원 잠금 `L_d` | `Regionizer.lock`(`Regionizer.java:41`, `ReentrantLock`). 차원(World)마다 하나 |
| 큐 | `Regionizer.queue`(`:48`, `PriorityQueue<Region>`, `nextTickNanos` 오름차순) |
| 머리 마감 `headDue` | 큐 맨 앞 region 의 `nextTickNanos`. 새로 추가하는 `volatile long`(비면 `Long.MAX_VALUE`) |
| 워커 | `SharedScheduler` 의 작업 스레드. 수 = T(`region-threads`) |
| 꺼내기 | QUEUED → RUNNING(`queue.poll`, `queued=false`, `ticking=true`, `tickThread=…`) — 지금 `workerLoop` 378-381 + `takeDue` 440-442 |
| 끝내기 | RUNNING → IDLE/QUEUED(`ticking=false`, `tickThread=null`, `afterTick`) — 지금 397-406 |
| 틱 본문 | `Region.runTick(gameTicks)`(`Region.java:100-119`). 잠금 밖 |
| sprint 요약 | `afterTick` 이 잠금 안에서 남기는 `sprintSeen`·`sprintPending`·`sprintJoinedSummary`·`sprintDoneSummary`(`Regionizer.java:478-483`, `Region.java:793-801`) |
| held 틱 | `/tick sprint` 중 전역을 앞선 region 의 차례: 인박스만 비우고 본문을 건너뛴다(`Region.java:101-112`), 다음 마감 = now + 100µs(`SPRINT_HOLD_NANOS`, `Regionizer.java:461,500`) |

---

## 1. 현재 코드 구조 (5b15a9bb)

### 1.1 스레드와 큐
- **월드마다** `Regionizer` 하나(`World.java:115`), 그 안에 워커 T 개(`Regionizer.java:72-88`, 이름 `furnace-region-[차원-]i`, 데몬, `MAX_PRIORITY-1`), 잠금 1 + 조건 변수 1(`:41-42`), 데드라인 큐 1(`:48`).
- 세 월드 모두 같은 T = `config.regionThreads()`(`FurnaceMain.java:197-199`, `:279-280`) → 실제 틱 스레드 = 3T.
- `workerLoop`(`:372-408`): 잠금 안 `takeDue` → `ticking=true; tickThread=me` → 잠금 밖 `runTick` → `tickState().record`(게임 틱일 때) → `recordTick`(STATS) → 잠금 안 `ticking=false; tickThread=null; afterTick`.
- `takeDue`(`:411-444`): 머리 없음 → `await(50ms)`; 머리가 죽음/yielded/ticking/!queued → 지연 삭제; 때가 안 됨 → `awaitNanos`(spinMargin 이하면 잠금 놓고 `PreciseWait.parkUntil`); 때가 됨 → poll.
- `afterTick`(`:447-467`): `updateSprintSummary` → `resolveMerges` → (20틱마다) `split` → `updateSprintSummary`(병합으로 이어받은 상태) → 다음 마감(`sprintPending` 이면 now 또는 now+100µs, 아니면 `+= tickNanos`, 밀렸으면 now+tickNanos) → `schedule`(`:469-474`, `signalAll`).
- `Region` 상태 플래그: `alive`·`mergedInto`·`ticking`·`tickThread` 는 volatile, `yielded`·`queued`·`nextTickNanos` 는 잠금 안(`Region.java:56-62`). `isOwnerThread()` = `tickThread == currentThread`(`:93`).
- `GlobalThread`(`sched/GlobalThread.java`) 하나가 `ServerCore.scheduledTick()`(`ServerCore.java:416-434`)을 50ms 마다 부른다. `RegionThread`(`sched/RegionThread.java`)는 월드 없는 fallback 경로(`FurnaceSchedulerImpl.java:71-84`)만 — 실서버 경로에 없다.

### 1.2 `/tick` 상태와 sprint/freeze 훅 (2026-10-28 에 들어온 것)
- `TickState`(`world/TickState.java`)는 **차원 공용 객체 하나**(`ServerCore.java:770` `w.tickState(world.tickState())`, `World.java:906-911`). 필드는 volatile, `startSprint`/`endSprint`/`record` 는 `synchronized`(잎 잠금).
- freeze/step: region 틱 본문이 `tickState.frozen()`·세대·`requestedSteps` 를 읽는다(`Region.java:848-857`). 스케줄에는 영향 없음(마감은 평소대로).
- sprint lockstep:
  - 전역(`ServerCore.tickSprint`, `:443-462`): 40ms 예산 안에서 "모든 월드의 `regionizer().sprintMinDone(g)` 최솟값 ≥ 전역 진행" 일 때만 전역 틱을 돌리고, 막히면 `parkNanos(50µs)` 로 돈다. 전역 틱을 다 쓰면 모든 월드 `sprintDone(g)` 가 참일 때 `finishSprint`.
  - `sprintMinDone`·`sprintDone`(`Regionizer.java:672-699`)은 **`L_d` 를 잡고** `regions` 를 돌며 요약만 읽는다(월드마다 차례로, 중첩 없음).
  - region(`Region.runTick`, `:100-119`): `sprintHoldsNow()`(`:826-830`, 마친 틱 ≥ 전역 진행이면 참)면 **held 틱** — 인박스만 비우고 반환. `afterTick` 은 `sprintPending` 이면 마감을 now(held 였으면 now+100µs)로 잡는다(`:459-465`).
  - 병합: `sprintCompatible`(`:490-497`) — 둘 다 참여 중이고 마친 틱 수가 다르면 한 틱 미룬다(`resolveMerges` 519-523, 미룬 상대는 `pendingMerges` 에 되돌림 502-510). `absorb` 는 `adoptSprintFrom`(`:552`).
  - 분할: 조각은 부모 sprint 상태를 물려받고 sprint 중이면 곧바로 돈다(`:612-614`).
- 결론: **sprint/freeze 는 모두 `L_d` 안 요약 + 공용 `TickState` volatile 로만 동작하고 "어느 스레드가 돌리는가"에 의존하지 않는다.** B안이 바꿀 것은 없다. 단 공유 풀에서는 held 틱의 100µs 재예약과 lockstep 의 공정성이 새 상호작용을 만든다(§4.4).

### 1.3 수명·종료
- `World` 생성자(`:100-120`)가 `Regionizer` 를 만들고 오버월드는 `installRouter(…, regionizer::close)`, 다른 차원은 `addCloseHook(regionizer::close)`(`:116-119`). `FurnaceSchedulerImpl.close()`(`:96-107`)가 훅을 등록 순서로 돈 뒤 fallback·global 을 닫는다.
- 종료 순서(`FurnaceMain.java:319-367`): 플러그인 끄기 → 네트워크 → 모든 월드 `flushAll`(30초 상한) → 플레이어 IO → io/gen 풀 → 저장소 닫기 → `chunkPool.shutdown` → **`scheduler.close()`**(363) → 플러그인 close.
- `Regionizer.close()`(`:740-755`): `running=false`, `signalAll`, 워커 `join(1000)`.

### 1.4 선행 조건 상태
| # | 내용 | 상태 |
|---|---|---|
| ① | `Region.chunkAt` 동기 생성 제거(검토서 R2) | **완료.** `chunkAt`(`Region.java:228-234`)은 로드된 청크만 돌려주고 없으면 비동기 로드 요청. 동기 경로는 `chunkAtBlocking`(`:249`, 시험·도구 전용)으로 분리. 운영 호출처 조사 `chunkat-callers-2026-10-27.md` |
| ② | 벤치 과부하 확장 | **완료.** `MultiDimensionTickBench.overloadScenarios`(`:101-136`), 결과 `shared-scheduler-bench-overload-2026-10-27.md` |
| ③ | 설계서 확정 | 이 문서(사용자 승인 대기 — `docs/user-todo.md`) |

---

## 2. 목표 구조와 변경 목록 (파일·메서드 단위)

### 2.1 새 클래스 `SharedScheduler` (`server/world/SharedScheduler.java`, 신규)
`Region`/`Regionizer` 의 패키지 전용 필드를 만지므로 `world` 패키지. `sched` 패키지(`furnace-api` 공개 표면과 가까움)에 두지 않는다.
```java
public final class SharedScheduler implements AutoCloseable {
    SharedScheduler(int threads, long tickNanos, String name);   // 워커 T개 생성·시작(데몬, MAX_PRIORITY-1). name: 스레드 이름 접두("furnace-region" / 호환 경로 "furnace-region-the_nether")
    void register(Regionizer rz);        // dims 에 추가(COW). 잠금 없음
    void unregister(Regionizer rz);      // dims 에서 제거. 풀은 멈추지 않는다
    void wake(long due);                 // 차원이 머리를 앞당겼을 때(§3.3). 잠금 없음
    int threadCount();
    long wakeups();                      // 계측(§7.1): 워커가 park 에서 돌아온 횟수
    @Override public void close();       // running=false → 전원 unpark → join(1000)
}
```
내부 상태(모두 잠금 없는 원시형):
- `CopyOnWriteArrayList<Regionizer> dims`, `Thread[] workers`, `volatile boolean running`.
- `ConcurrentLinkedQueue<Parked> parked`; `static final class Parked { final Thread t; volatile long until; }`(**N2**: `until` 은 volatile, 갱신 가능).
- `AtomicBoolean spinning`, `volatile Thread spinner`, `AtomicLong spinDeadline`(§5).
- `AtomicInteger rr`(동률 순환), `AtomicLong wakeups`.
- 상수: `STARVE_NANOS = 2·tickNanos`(프로퍼티 `furnace.sched.starveTicks`, 벤치용), `perDimCap = max(1, T−1)`(`furnace.sched.dimCap`), `TIE_NANOS = 1µs`, `PARK_MAX = 50ms`, `SPIN_HANDOFF = 1ms`.

워커 루프(§3·§4·§5 가 각 단계를 정의):
```
WORKER.set(true)                        // Regionizer.WORKER ThreadLocal(:368) 을 SharedScheduler 로 옮긴다(isRegionWorkerThread 는 Regionizer 정적 메서드로 남겨 호출처 불변)
while (running):
    now = nanoTime()
    (best, bestDue, maxRunning) = select(now)            // §4.1 — 잠금 없음
    if best == null: sleep(until = now + PARK_MAX); continue
    if bestDue - now > 0: wait(bestDue) (§5); continue   // 잔다/돈다 → 다시 고른다
    r = best.tryTake(now, maxRunning)                    // L_d 안
    if r == null: continue                               // 낡은 머리/상한/closed → 다시 고른다
    runOne(best, r)                                      // 잠금 밖: runTick, record, recordTick → best.finishTick(r) (L_d 안)
```

### 2.2 `Regionizer` 변경
| 구분 | 메서드·필드 | 지금(줄) | 변경 |
|---|---|---|---|
| 제거 | `Condition wake` | 42 | 삭제 |
| 제거 | `Thread[] workers`, `running`, 생성자의 스레드 생성 | 49-50, 78-87 | 삭제. 생성자는 `pool.register(this)` |
| 제거 | `workerLoop` | 372-408 | `SharedScheduler.runOne` 으로 이동(틱 본문·`tickState().record`·`recordTick` 호출 순서 그대로) |
| 제거 | `takeDue` | 411-444 | 기다리는 부분(415-416, 423-438)은 워커 §5 로, 지연 삭제·꺼내기(418-422, 440-442)는 `tryTake` 로 |
| 제거 | `close()` 의 join | 748-754 | `close()` = 잠금 안 `closed=true; headDue=MAX` → 잠금 놓고 `pool.unregister(this)` → `ownsPool` 이면 `pool.close()` |
| 추가 | `final SharedScheduler pool; final boolean ownsPool` | — | 두 생성자(§2.4) |
| 추가 | `volatile long headDue = Long.MAX_VALUE` | — | §3.1 발행 규칙 |
| 추가 | `volatile boolean closed` | — | `unregister` 뒤 참. `tryTake` null, `schedule` 거절 |
| 추가 | `final AtomicInteger running` | — | RUNNING 인 region 수. `tryTake` +1 / `finishTick` −1 |
| 추가 | `Region tryTake(long now, int maxRunning)` | — | 잠금 안: 머리 지연 삭제 반복 → 머리가 때가 안 됐으면 null → `running.get() >= maxRunning` 이면 null → poll, `queued=false`, `ticking=true`, `tickThread=me`, `running++`, `publishHead()`. **N3**: `maxRunning` 의 뜻은 §4.1 |
| 추가 | `void finishTick(Region r)` | — | 잠금 안: `ticking=false; tickThread=null; running--; afterTick(r)`(예외는 지금처럼 로그) |
| 추가 | `private void publishHead()` | — | `headDue = queue.isEmpty() ? MAX : queue.peek().nextTickNanos` — 큐가 바뀌는 모든 자리의 끝(§3.1) |
| 수정 | `schedule` | 469-474 | `closed` 거절, `queue.add` 뒤 `prev=headDue; publishHead(); if (headDue < prev) pool.wake(headDue)` |
| 수정 | `recordTick` | 327-365 | 라벨 `world.dimension()+"#"+id`, 맵을 `Region.tickStat` 인스턴스 필드로, `INSTALL_STATS`/`lastInstall` 인스턴스화(§8) |
| 수정 | `threadCount()` | 94 | `pool.threadCount()` |
| 유지 | `lock`·`queue`·`sections`·`regions`·`want`·`release`·`merging`·`ownerForTask`·`enqueue`·`forEachRegion`·`afterTick`·`updateSprintSummary`·`sprintCompatible`·`resolveMerges`·`absorb`·`split`·`kill`·`sprintMinDone`·`sprintDone`·`regionCount`·`regionsSnapshot`·`debugState`·`setTickNanos`·`gameTicks` | — | 그대로. `afterTick` 안의 `schedule` 호출이 `pool.wake` 로 이어지는 것만 다르다 |

### 2.3 `Region` 변경
- `INSTALL_STATS`(`:322`, `:361-364`) → 인스턴스 `installStats`; `tickStat` 인스턴스 필드 추가(§8). 그 외 없음. `isOwnerThread`(`:93`)·`runTick`·sprint 필드 전부 불변.

### 2.4 `World` 변경
- 기존 세 생성자 + `int regionThreads` 서명 **유지**(`:82-94, 100-120, 122-127`). `:115` → `new Regionizer(this, regionThreads, sectionShift, mergeRadius, tickNanos)`(호환 생성자: 안에서 `pool = new SharedScheduler(threads, tickNanos, 차원 이름); ownsPool=true; pool.register(this)`). → 테스트·도구의 `new World(` **61곳**(이 워크트리 grep, 주석 포함) 무변경.
- 새 생성자 하나 추가: `World(DimensionType, WorldSource, ChunkWorkerPool, FurnaceScheduler, SharedScheduler pool, int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance, int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool)` → `new Regionizer(this, pool, sectionShift, mergeRadius, tickNanos)`(공유 생성자, `ownsPool=false`).
- 종료 훅 등록(`:116-119`)은 그대로(`regionizer::close` = 자기 큐 분리).

### 2.5 `FurnaceMain` 변경
- `:155` 뒤에 `SharedScheduler pool = sharedPool ? new SharedScheduler(config.regionThreads(), config.tickNanos(), "furnace-region") : null;` (`sharedPool = Boolean.parseBoolean(System.getProperty("furnace.sharedRegionPool", "true"))`).
- `:197-199`, `:279-280`: `pool != null` 이면 새 생성자(풀 주입), 아니면 지금 int 생성자. 두 자리만.
- `:294`(`startTicking`) 뒤: `if (pool != null) scheduler.addCloseHook(pool::close);` — **월드 훅들 뒤**에 등록해 "세 월드 분리 → 풀 종료 → fallback → global" 순.
- 기동 로그 한 줄: `[SCHED] region 워커 {}개({}, 차원 {}개)` — "서버 전체 공유" / "차원별".

### 2.6 `FurnaceSchedulerImpl`·`sched/*`
- `FurnaceSchedulerImpl`: 변경 없음(`regionThreads` 는 fallback 수에만 쓰임 `:76`; 훅 순서는 등록 순서 그대로).
- `PreciseWait`: `parkUntilInterruptible(long deadline, LongSupplier currentDeadline)` 추가(§5, **N6** 재park 루프). 기존 `parkUntil` 은 `RegionThread` 가 쓰므로 유지.
- `GlobalThread`·`RegionThread`·`TickTimer`: 변경 없음.
- `ThreadOwner` 주석(`furnace-api`): "현재 스레드가 **지금 이 owner 를 실행 중인** 스레드인가"(하위 호환, 문구만).

### 2.7 설정·문서
- `Config.java:7` 주석, `ConfigLoader.java:69` 뒤 경고(`> 2·C` 면 "차원 수를 곱해 넣었나"), 릴리스 노트(§6), `.claude/rules/scheduler.md` 모델 1-2항("같은 region 은 동시에 한 스레드에서만 돈다. 매 틱 다른 워커일 수 있다"), `multi-dimension-2026-10-26.md` §1 "region 스레드 풀: 월드마다 따로" 줄 갱신.

### 2.8 변경 규모(**가정**, 코드 읽기 기준)
파일 **12~15개**: 신규 1(`SharedScheduler`), 수정 `Regionizer`(약 150줄 이동/삭제 + 80줄 추가), `Region`(10줄), `World`(생성자 1개 + 1줄), `FurnaceMain`(약 15줄), `PreciseWait`(30줄), `Config`/`ConfigLoader`(주석·경고), `ThreadOwner`(주석), `RegionTickBench`(`INSTALL_STATS` 참조 1곳), `MultiDimensionTickBench`(공유 풀 모드), 새 테스트 4~5개, 문서 3(규칙·다차원·릴리스 노트). 커밋 **8~10개**(§9).

---

## 3. 잠금·깨우기 (N1·N2·N4 반영)

### 3.1 `headDue` 발행 규칙
- 큐가 바뀌는 모든 자리의 끝에서, `L_d` 안에서 `publishHead()`: `schedule`(추가), `tryTake`(지연 삭제·꺼내기), `absorb`/`kill`(alive=false — 머리가 죽은 항목이면 선택기가 `tryTake` 에서 null 을 받아 다시 고르므로 빠뜨려도 안전하지만 부른다), `close`(MAX).
- 낡은 머리(죽음·yielded·ticking)는 그대로 발행해도 된다: 죽은 항목의 due ≤ 실제 머리 due 이므로 **앞당길 뿐 늦추지 않는다.** 워커가 `tryTake` 로 치우고 다시 발행한다.
- 쓰기는 잠금 안에서만, 읽기는 어디서나. 발행 값은 **힌트**, 결정은 `tryTake` 가 잠금 안에서 다시 한다.
- **N4**: `headDue == Long.MAX_VALUE` 는 "후보 없음" 표지로 먼저 걸러낸다. 그 뒤의 모든 시간 비교는 **차이**(`due - now`, `now - due`)로만 한다. `MAX − now` 나 `now − MAX` 를 계산하는 코드를 두지 않는다(nanoTime 은 음수일 수 있어 오버플로).

### 3.2 R1 규칙(잠금 순서)
> **`L_d` 를 쥔 채 다른 차원 잠금 `L_d'` 나 "`L_d` 를 쥔 스레드를 기다릴 수 있는 잠금"을 잡지 않는다. 풀에는 잠금이 없다.**

잠금 안에서 잡히는 것은 잎 잠금만: 로거 appender(`log.debug` `:644`), `ConcurrentHashMap` 내부(`sections`), `TickState` 의 `synchronized`(`afterTick` → `updateSprintSummary` 가 `tickState().sprintTicks()` 등 volatile 만 읽는다 — `synchronized` 메서드는 부르지 않는다. **구현 규칙**: `L_d` 안에서 `TickState.startSprint/endSprint/record/sampleCopy` 를 부르지 않는다), `RegionLevel` 지연 생성. 이들은 `L_d` 를 기다리지 않으므로 사이클이 없다.

| 경로 | 부르는 곳 | 잡는 잠금 | 잠금 안 외부 호출 |
|---|---|---|---|
| 선택기 `select` | 워커 | 없음 | `rz.headDue`·`rz.running.get()` 읽기 |
| `tryTake` | 워커 | `L_d` | `publishHead` |
| 틱 본문 `runTick` | 워커 | 없음 | `rz().enqueue`→`ownerForTask`→`L_d`(비중첩); 다른 월드 `runAtChunk`→`L_d'`(비중첩) |
| `finishTick` | 워커 | `L_d` | `afterTick` → `split`/`absorb`/`reroute`(재진입 같은 `L_d`) → `schedule` → `pool.wake`(unpark/volatile 만) |
| `sprintMinDone`/`sprintDone` | 전역 스레드 | `L_d`(월드마다 차례로) | 없음 |
| `register`/`unregister` | World 생성자 / `Regionizer.close` | `L_d` → **놓고** → COW 목록 | 없음 |
| `pool.close` | `FurnaceSchedulerImpl.close` 훅 | 없음 | 워커 join 은 잠금 밖 |
| `adoptAfterDimensionChange` → 다른 월드 `updateSleepingPlayerList` | 목적 region 틱 | 없음(잠금 밖) | `SleepStatus` synchronized — **잠금 안으로 옮기지 않는다**(R7) |

코드 리뷰 체크리스트: `Regionizer` 안의 `pool.` 호출은 `wake`·`register`·`unregister`·`threadCount` 만; `register/unregister` 는 잠금을 놓은 뒤; `SharedScheduler` 는 `rz.lock` 을 직접 잡지 않는다; `afterTick` 계열이 부르는 `Region` 메서드(`absorbData`·`splitData`·`players()`·`isIdleEmpty`·`adoptSprintFrom`·`inheritSprint`)는 월드 밖을 부르지 않는다; 잠금 안 `log` 는 debug 이하.

### 3.3 깨우기·대기 원시 (N1·N2 확정)
```java
// SharedScheduler — 이것이 유일한 깨우기 정의다
void wake(long due) {
    long prev = spinDeadline.getAndAccumulate(due, Math::min);         // 스피너 목표 앞당김(원자적 min)
    if (due < prev) { Thread s = spinner; if (s != null) LockSupport.unpark(s); }
    for (Parked p : parked) {                                           // "due 보다 늦게 깰" 자는 워커 하나만
        if (p.until - due > 0 && parked.remove(p)) { LockSupport.unpark(p.t); return; }
    }
}
private void passBaton() { Parked p = parked.poll(); if (p != null) LockSupport.unpark(p.t); }
```
워커가 자는 절차 `sleep(until)`:
```java
Parked me = new Parked(self, until);
parked.add(me);                                                   // ① 먼저 "잔다"고 등록
long d = earliestHeadDue();                                       // ② 그 다음 머리를 다시 본다(모든 차원 headDue 의 min; MAX 면 없음)
if (d != MAX) {
    long target = d - spinMargin;
    if (target - now() <= 0) {                                    //    이미 때가 됨 → 안 잔다
        if (!parked.remove(me)) passBaton();                      //    (N1-②) 누가 나를 뽑았으면 무조건 바통 — 그 wake 는 다른 머리를 위한 것이었을 수 있다
        return;                                                   //    → 다시 고른다
    }
    if (target - me.until < 0) me.until = target;                 //    (N2) 더 이른 머리를 알았으면 until 을 **갱신**(volatile 쓰기, 노드 재생성 아님)
}
LockSupport.parkNanos(me.until - now());                          // ③ 잔다(상한 PARK_MAX=50ms 는 안전망)
wakeups.increment();
boolean picked = !parked.remove(me);                              //    remove 실패 = wake/passBaton 이 나를 뽑았다
if (picked && now() - me.until >= 0) passBaton();                 //    (N1-③) 뽑혔는데 **타임아웃과 겹친** 경우만 전달. 제때 뽑혀 깬 워커는 자기가 그 일을 하므로 전달하지 않는다
```
- **N1 해결(바통 순환 제거):** 1차 문서는 ③에서 `remove` 실패 시 무조건 전달했다. 유휴 워커가 둘 이상이면 A→B→A 로 바통이 돌며 재등록·전달이 끝나지 않는다(라이브락). 확정 규칙: **② 는 무조건 전달, ③ 은 "뽑혔으나 타임아웃으로도 깼을" 때만 전달.** 근거: ② 의 워커는 자기가 본 머리를 돌리러 가므로 wake 가 겨눈 머리(다른 차원일 수 있음)를 놓칠 수 있다 → 전달 필요. ③ 에서 제때 뽑힌 워커는 wake 의 의도대로 고르러 가므로 전달이 불필요하다. 타임아웃과 겹친 경우는 그 워커가 "낡은 until 로 깬 뒤 뽑힘"일 수 있어(permit 소비) 한 번 전달한다. **전달 사슬 길이 ≤ 1**: 바통을 받은 워커는 until 전에 깨므로(③ 의 두 번째 조건 거짓) 다시 전달하지 않는다. 종료 논증은 "전달이 parked 를 줄인다"가 아니라 **"전달은 ②(틱을 돌리러 가는 워커) 또는 ③ 타임아웃(50ms 마다 한 번) 에서만 일어난다"** 로 바꾼다 — 두 사건 모두 유휴 상태에서 무한히 빠르게 반복되지 않는다. (대안으로 `Parked.picked` 플래그를 두고 `wake` 가 세팅하는 방식도 같은 효과 — 구현자가 택해도 된다. 둘 다 "③ 제때 뽑힘 → 전달 없음" 을 지켜야 한다.)
- **N2 해결(옛 until 로 자는 회귀):** 1차 문서는 `until` 을 `parked.add` 전 값으로 고정하고 ② 는 "지금 때가 됐나"만 봤다. 생산자 `wake` 의 순회가 내 `add` 보다 먼저 끝나면 나는 더 이른 머리를 알면서도 옛 until(≤50ms)까지 잔다. 확정: ② 에서 `me.until = min(me.until, d − spinMargin)` 로 **갱신한 뒤** 그 값으로 park. `Parked.until` 은 volatile 이라 `wake` 의 `p.until - due > 0` 비교가 새 값을 본다. 노드 재생성(remove→add)은 하지 않는다 — 재생성 사이 틈에 `wake` 가 빈 목록을 볼 수 있다.
- Dekker 식 교차 논증(불변): `schedule` 은 `queue.add → headDue 쓰기 → wake(parked 순회)`, 워커는 `parked.add → headDue 읽기`. 둘 다 volatile/CAS 순서가 보장돼 "워커가 옛 머리를 읽고 잠" 과 "wake 가 빈 parked 를 봄" 은 동시에 못 일어난다. `unpark` 가 `park` 보다 먼저 와도 permit 이 남아 즉시 돌아온다.
- 헛깨우기(D2 유지): 유휴 차원이 매 틱 `MAX→now+50ms` 로 앞당기므로 매 틱 `wake` 가 불리지만 `p.until - due > 0` 비교로 "어차피 먼저 깰 워커"는 깨우지 않는다. 기대: 유휴 때 깨어남 ≤ T×20/초(§7.1 `wakeups` 로 검증).

### 3.4 메모리 가시성(불변)
앞 틱 `finishTick`(`L_d` 안 `ticking=false`)과 다음 틱 `tryTake`(같은 `L_d` 안 `ticking=true`)가 같은 `ReentrantLock` 을 지나므로 앞 워커의 모든 쓰기가 다음 워커에 보인다. 선택기의 `headDue` 읽기는 힌트라 가시성 사슬에 끼지 않는다. 꺼내기를 잠금 밖 CAS 로 바꾸지 않는다.

---

## 4. 공정성·과부하 정책 (N3·N5 반영)

### 4.1 선택 키와 `maxRunning` 의 뜻 (N3)
워커의 `select(now)` 는 등록된 차원마다 잠금 없이:
```
due_d     = rz.headDue;  if due_d == MAX → 후보 아님                       (N4: MAX 먼저)
late_d    = now - due_d                                                     (음수면 아직 때 아님)
anyOtherDue = (다른 차원 d' 중 due_d' != MAX && now - due_d' >= 0 인 것이 있다)
capped_d  = rz.running.get() >= perDimCap && anyOtherDue
starved_d = late_d >= STARVE_NANOS
key_d     = (capped_d ? 2 : starved_d ? 0 : 1, due_d)                       사전식; 같은 클래스에서 |due 차| ≤ TIE_NANOS 면 rr 순환
best      = min key.  maxRunning = anyOtherDue ? perDimCap : Integer.MAX_VALUE
r = best.tryTake(now, maxRunning)
```
- **N3 확정 문구:** `tryTake` 의 두 번째 인자는 "선택기가 capped 로 판정했는가"가 **아니다.** 뜻은 **"지금 다른 차원에 때가 된 머리가 있는가 → 있으면 이 차원은 `perDimCap` 까지만, 없으면 상한 없음"** 이다. 선택기 둘이 동시에 같은 차원을 고르고 둘 다 `running = T−2` 를 봤어도, `tryTake` 가 잠금 안에서 `running >= perDimCap` 을 재확인하므로 둘째는 null 을 받아 다시 고른다. 상한은 **이 재확인이 보장**한다(선택기의 `capped` 는 정렬용 힌트).
- "다른 차원에 때가 된 머리가 없으면 상한 없음" 이라 유휴 서버에서 한 차원이 워커를 다 써도 된다(그때는 지킬 상대가 없다).
- `perDimCap = max(1, T−1)`; T=1 이면 상한 없음. `starved` 지표는 머리 지연 `now − headDue`(1차 D4 유지 — `lastTickStart` 는 느림을 굶음으로 오판).
- `running` 은 `tryTake` 성공 +1 / `finishTick` −1. `tryTake` 가 `running++` 한 뒤 예외 없이 region 을 돌려주고, `runOne` 은 `finishTick` 을 `finally` 로 보장한다(틱 예외는 지금처럼 로그 후 계속).

### 4.2 과부하 정책(총 수요 > 용량)
벤치(2026-10-27)가 보인 두 열화 방식: 현행은 차원 수×T 스레드의 OS 경쟁(차원 격리는 좋음), 순수 EDF 공유 큐는 균등 붕괴 + 비대칭 때 가벼운 차원 완전 기아. 확정 정책:
1. **틱을 건너뛰지 않는다.** 순서만 바꾼다. `afterTick` 재동기(밀리면 now — T-171 로 now+tickNanos 에서 바뀜, 아래 4.3 의 "지연 0 에서 다시 시작"은 이제 "지연 0(마감 = 끝난 때)"이며 순서는 EDF 그대로)·`TickTimer` 의미 불변 → 바닐라 관측(틱 수) 불변.
2. 평시(아무 차원도 starved 아님): EDF. 한 차원은 다른 차원에 때가 된 머리가 있는 한 `T−1` 워커까지만.
3. 어느 차원의 머리가 2틱(STARVE) 넘게 밀리면 클래스 0 — 모든 클래스 1·2 차원보다 먼저. 클래스 0 차원이 여럿이면 due 순.
4. 총 수요 > 용량이면 어떤 정책도 20TPS 를 못 지킨다. 그때의 목표는 **"어느 차원도 STARVE + 유계 시간 넘게 멈추지 않는다"(최대 지연 예측 가능)** 이고 평균 개선은 보장하지 않는다(§7.2-C 가 측정).
5. 자기 보호: 차원 수 n 이 커도(≤12 가정) 선택기는 O(n) volatile 읽기. 12 를 넘으면 `headDue` 힙이 필요하지만 그러면 전역 잠금이 생기므로 그때 따로 설계한다.

### 4.3 차원 최소 보장 — 상한 수식 (N5)
1차 문서는 "각 선택이 한 차원의 starved 를 해제한다"고 썼는데 틀렸다: 한 차원에 밀린 region 이 k 개면 k 번 뽑혀야 그 차원 머리의 지연이 0 으로 돌아간다. 확정 논증:

전제(**가정**): 워커 T ≥ 2, 모든 틱 길이 ≤ `Tmax`, **총 수요 < 용량**(Σ 틱 길이/틱 주기 < T). 총 수요 ≥ 용량이면 밀린 region 수가 유계가 아니므로 아래 상한은 성립하지 않고, 그때는 §4.2-4 의 "EDF + 굶은 차원 먼저" 순서 보장만 남는다.

- 차원 B 의 머리 region 이 때가 됐는데 `STARVE` 동안 꺼내지지 않았다 → `starved_B` → 클래스 0.
- B 는 capped 가 아니다(capped 는 자기 차원이 이미 `T−1` 워커를 쓰고 있다는 뜻 — 굶는 차원은 `running_B < perDimCap`).
- 시각 t0(B 가 클래스 0 이 된 순간)에 **클래스 0 인 region 전체 수**를 K 라 하자(B 의 머리 포함; 차원별로 머리만 큐 앞에 있지만 꺼내 돌린 뒤 그 차원의 다음 region 이 머리가 되어 역시 클래스 0 일 수 있으므로 K 는 "지연 ≥ STARVE 인 모든 region 수"로 센다). 총 수요 < 용량이면 K ≤ R(전체 region 수)이고 **t0 이후 새로 클래스 0 이 되는 region 은 유계**(밀림이 누적되지 않는다 — 가정).
- 워커는 각각 `Tmax` 안에 선택기로 돌아오고, 클래스 0 중 due 가 가장 이른 것을 고른다. B 의 머리보다 due 가 이른 클래스 0 region 은 최대 K−1 개. 워커 T 개가 병렬로 처리하되 한 차원은 `T−1` 까지만 쓰므로 최악 처리율은 `(T−1)/Tmax`.
- **B 의 첫 틱 시작 지연 상한 ≈ `STARVE_NANOS + Tmax + ⌈(K−1)/(T−1)⌉ · Tmax`.** K 는 밀린 region 수(차원 수 n 이 아니다).
- 꺼내진 region 은 `afterTick` 재동기로 지연 0 에서 다시 시작하므로 클래스 0 에서 빠진다. 따라서 B 의 머리는 유한 단계 안에 뽑힌다 = **최소 보장.**
- cap 덕에 평시에도 한 차원이 T 워커를 전부 쥐고 다른 차원을 `Tmax` 단위로 막는 일이 없다(R3 의 "A 틱이 끝나는 틈에만" 상황 제거).

### 4.4 sprint lockstep 과의 상호작용 (새 항목)
현재 구조(§1.2)를 공유 풀에 얹으면 다음이 생긴다. 모두 **설계로 처리**하며 `TickState`·`Region` sprint 코드는 바꾸지 않는다.

| 상황 | 현행(차원별 풀) | 공유 풀 | 처리 |
|---|---|---|---|
| 전역이 `sprintMinDone` 으로 모든 월드의 `L_d` 를 차례로 잡음 | 같음 | 같음 — 중첩 없음, 워커는 `L_d` 안에서 잠금 없는 `pool.wake` 만 부름 | **교착 없음.** 전역 스레드는 풀의 어떤 자료구조도 잡지 않고, 워커는 전역을 기다리지 않는다(held 틱은 "기다림"이 아니라 빈 틱이다). R1 규칙 그대로 |
| held 틱 재예약(now+100µs) | 그 차원 워커들만 10kHz 로 돈다 | **모든 차원의 held region 이 한 풀에서 10kHz 로 돈다.** EDF 상 held region(due = now+100µs)과 진행 region(due = now)이 번갈아 뽑히므로 뒤진 region 이 밀리지는 않지만, 워커 시간의 상당 부분이 빈 틱에 쓰인다(인박스 드레인만, 수 µs) | S4 에서는 **그대로 둔다**(측정 전 추측으로 바꾸지 않음). S5 후보: `TickState.takeSprintGlobalTick` 뒤 전역이 `pool.wake(now)` 를 부르고 held 마감을 `now + 1ms` 로 늘리기(`SPRINT_HOLD_NANOS` 상수만 바뀜, lockstep 의미 불변). §7.2-D 벤치로 결정 |
| 공정성: sprint 중 모든 참여 region 이 `sprintPending` 으로 due = now → EDF 동률 → rr 순환 | 차원별로 독립 | 차원끼리 rr 로 번갈아. 가장 느린 region 이 전체 속도를 정하는 lockstep 성질은 같다. perDimCap 이 한 차원의 region 떼가 워커를 다 쥐는 것을 막아 다른 차원의 뒤진 region 이 먼저 뽑힌다 → lockstep 대기(전역의 50µs park)가 **줄어든다**(가정, 측정 필요) | 변경 없음 |
| `sprintCompatible` 병합 유예(마친 틱 다르면 한 틱 미룸) | — | `L_d` 안 요약 비교라 스레드와 무관 | 변경 없음 |
| `starved` 판정과 held 틱 | — | held region 은 due 가 now+100µs 로 늘 재설정되므로 지연이 쌓이지 않아 클래스 0 이 되지 않는다. 뒤진(진행해야 하는) region 은 due = now 라 지연 ≥ STARVE(100ms) 가 되면 클래스 0 → 먼저 뽑힘 — lockstep 이 바라는 방향 | 변경 없음 |
| `gameTicks=false` 월드 | `sprintMinDone` MAX | 같음 | 변경 없음 |
| sprint 끝(`endSprint` → `frozen` 복구) | region 은 다음 틱에서 세대 변화로 `sprintLeft=0`, `afterTick` 이 평소 마감으로 | 같음 | 변경 없음 |

freeze/step: 마감에 영향이 없으므로 공유 풀과 무관.

---

## 5. PreciseWait 스핀 대기 (N6 반영, 윈도 전용 경로)

리눅스는 `spinMargin=0`(`PreciseWait.java:33` 보정)이라 이 절의 스핀 경로가 없다. 거친 타이머 플랫폼(윈도)에서만 의미 있다.

```java
/** deadline 까지 기다린다. 거친 구간은 parkNanos 로 자되, 조기 복귀(wake 의 unpark)하면 currentDeadline 을 다시 읽고
 *  남은 시간이 여전히 spinMargin 보다 크면 **다시 잔다**(N6). 마지막 spinMargin 만 돈다. 돌려주는 값: 실제로 기다린 마감. */
public static long parkUntilInterruptible(long deadline, LongSupplier currentDeadline) {
    long d = deadline, spin = spinMargin();
    while (true) {                                            // (N6) 거친 구간 재park 루프
        long remain = d - System.nanoTime();
        if (remain <= spin) break;
        LockSupport.parkNanos(remain - spin);
        d = Math.min(d, currentDeadline.getAsLong());         // 앞당겨졌으면 새 목표로; 아니면 남은 거친 구간을 다시 잔다
    }
    while (System.nanoTime() - d < 0) {                       // 차이 비교(N4 와 같은 원칙)
        long c = currentDeadline.getAsLong();
        if (c < d) d = c;
        Thread.onSpinWait();
    }
    return d;
}
```
- **N6 해결:** 1차 문서는 거친 구간을 한 번만 자고 조기 복귀하면 남은 시간 전부(≤ spinMargin + SPIN_HANDOFF ≈ 18ms)를 돌았다. 루프로 바꿔 조기 복귀 뒤에도 `remain > spin` 이면 다시 잔다. 도는 시간은 항상 ≤ spinMargin.
- 워커 쪽: `wait − spin ≤ SPIN_HANDOFF` 이면 `spinning` CAS 로 스핀 자리(서버 전체 1개)를 잡는다. 잡은 워커: `spinner=self; spinDeadline.accumulateAndGet(bestDue, min); target=min(spinDeadline, earliestHeadDue()); parkUntilInterruptible(target, spinDeadline::get)` → `finally { spinner=null; spinDeadline.set(MAX); spinning.set(false) }` → 다시 고른다. 못 잡은 워커는 **반드시 잔다**(§3.3 `sleep(until = min(bestDue, now+50ms))`) — 바쁜 재시도 금지(D7).
- `spinDeadline` 쓰기는 항상 `min`(생산자·스피너 모두), 끝날 때만 `set(MAX)`.
- 테스트: `PreciseWaitTest` 에 `parkUntilInterruptible(now+10ms, 공급자가 2ms 뒤 now+3ms)` 가 ~3ms 에 돌아오고 반환값이 앞당긴 시각인지 + (N6) `now+30ms` 목표에서 5ms 마다 헛 unpark 를 넣어도 CPU 를 돌지 않고(`spin=17ms` 강제 시 스핀 구간 진입 시각이 ≥ 13ms) 끝나는지. margin 을 인자로 받는 오버로드로 리눅스에서도 돈다.

---

## 6. `region-threads` 의미 변경과 호환 (R4)

| | 지금 | B안(`furnace.sharedRegionPool=true`, 기본) | `=false` |
|---|---|---|---|
| 뜻 | 차원마다 워커 수 | **서버 전체** 워커 수 | 차원마다(지금과 같음) |
| 기본값 | `availableProcessors()`(`Config.java:39`) | 같음(**가정**: §7.2-A/C 결과로 확정) | 같음 |
| 실제 틱 스레드(차원 3, C=4) | 12 | 4 | 12 |
| 검증 | `>= 1`(`ConfigLoader.java:69`) | 같음 + `> 2·C` 경고 | 같음 |

- 키 이름은 바꾸지 않는다(설정 파일 호환). 바뀌는 것은 `Config.java:7` 주석·경고 문구·기동 로그 한 줄·릴리스 노트.
- **호환 규칙:** 한 릴리스 동안 `-Dfurnace.sharedRegionPool=false` 가 이전 의미(차원별 풀)를 **같은 코드**(`SharedScheduler` 인스턴스를 차원 수만큼)로 제공한다. 설정 파일에 새 키를 추가하지 않는다(JVM 프로퍼티만 — 롤백 스위치는 운영 설정이 아니다). 다음 릴리스에서 스위치와 `FurnaceMain` 의 int 경로를 제거하되 **테스트용 int 생성자는 유지**한다.
- 릴리스 노트(초안): "**region-threads 의미 변경.** 이전에는 차원(오버월드·네더·엔드)마다 이 수만큼 틱 스레드를 따로 만들었습니다(실제 = 값 × 차원 수). 이번 버전부터 모든 차원이 하나의 워커 풀을 공유하며 `region-threads` 는 그 풀의 크기입니다. 기본값(코어 수)이면 바꿀 필요가 없습니다. 값을 직접 정했다면 '이전 값 × 차원 수'가 이전과 같은 스레드 수이지만 코어 수를 넘는 값은 권하지 않습니다. 한 차원이 CPU 를 독점하지 못하도록 워커 하나는 항상 다른 차원 몫으로 남습니다(≥ 2 일 때). 되돌리기: `-Dfurnace.sharedRegionPool=false`."

---

## 7. 풀 수명·종료 (R5) — 참조 계수 대신 `ownsPool` + `unregister`

- **공유 풀은 서버 종료(`scheduler.close()` 훅) 때만 닫힌다. 월드 close 는 자기 큐를 분리(`unregister`)할 뿐이다.** 참조 계수로 "마지막 월드가 닫히면 풀 종료"를 하면 테스트의 "월드 하나 닫고 다른 월드 계속"이 깨지고, 등록 0 인 풀이 살아 있는 상태(워커가 50ms 주기로 잠)는 비용이 없으므로 허용한다.
- 호환 경로(`ownsPool=true`): `Regionizer.close()` 가 `pool.close()` 까지 하므로 테스트 **55곳**의 `regionizer().close()`/`sched.close()` 가 지금처럼 워커를 멈춘다(`join(1000)` 상한 동일).
- 훅 순서(`FurnaceSchedulerImpl.close`, 등록 순): 오버월드 `regionizer::close`(installRouter) → 네더·엔드 `regionizer::close` → **`pool::close`**(S4 에서 `startTicking` 뒤 등록) → fallback → global. `scheduler.close()` 자리(`FurnaceMain.java:363`)는 flush(331-340)·IO 종료(345-348) 뒤라 지금과 같다.
- `unregister` 와 RUNNING 의 경합: `closed=true` 는 `L_d` 안; `tryTake` 는 `closed` 면 null; `schedule` 은 `closed` 면 넣지 않는다. 돌고 있던 region 은 `finishTick` 까지 돌고(`afterTick→schedule` 거절 → IDLE) 워커는 다른 차원으로 간다. 공유 경로는 join 하지 않는다 — flush 가 먼저 끝나므로 이 시점의 틱은 저장 뒤의 빈 틱(**가정**: flush 30초 상한 안에 안 끝나는 틱은 지금도 종료를 막지 않는다).
- 기존 부채(변경 없음, 기록): `unregister` 뒤 그 월드에 `runAtChunk`/`flushAll` 을 부르면 future 가 영원히 완료되지 않는다(현행도 `close()` 뒤 같다). 종료 중 `onSaveAll` 의 `f.join()`(`FurnaceMain.java:309`, 글로벌 스레드)이 여기 걸릴 수 있는 것은 기존 부채.

---

## 8. 틱 계측 정리 (R6, S1 에서 먼저)

| 항목 | 지금 | 확정 처리 |
|---|---|---|
| `tickStats` 맵 | `Regionizer`(`:323`) 키 `r.id`(월드별 `++nextId` `:240,610`) → 풀로 옮기면 차원끼리 겹침 | `Region.tickStat = new long[2+200]`(인스턴스). 로그 라벨 `world.dimension()+"#"+id` |
| `Region.INSTALL_STATS` | `static long[4]`, 비원자(`:322,361-364`) | `Region.installStats` 인스턴스. `recordTick` 이 방금 돈 region 값을 읽어 찍고 0 으로 |
| `lastInstall` | `Regionizer:325` | 삭제 |
| `phaseSum/phaseMax` | 인스턴스(`Region.java:763`) | 그대로 |
| `recordTick` 호출 위치 | `workerLoop:396`(틱 뒤, 끝내기 잠금 앞) | **같은 자리**: `runTick` 뒤 · `finishTick` 앞(region 이 아직 RUNNING — 다른 워커가 못 꺼낸다, D5) |
| `tickState().record` | `:392` | 같은 자리(잠금 밖, `TickState.record` 는 synchronized 잎 잠금) |
| `RegionTickBench:131` | `INSTALL_STATS` 직접 참조 | `regionsSnapshot()` 으로 region 들의 `installStats` 합(D6) |
| 벤치 스레드 이름 세기 | `MultiDimensionTickBench:41,51` 접두 `furnace-region` | 유지. 공유 모드 출력 라벨 "shared-pool" |

---

## 9. 구현 단계 (커밋 단위, 매 단계 전체 테스트 통과, 플래그 전환 가능)

각 단계 끝에 `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test` 전체 통과. 순서 고정(공정성은 통합과 같은 커밋 — 검토서 조건 2).

| 단계 | 커밋 | 바뀌는 파일 | 회귀 시험(기존) | 새 시험·벤치 | 롤백 | 규모·모델(**가정**) |
|---|---|---|---|---|---|---|
| **S1 계측·문서** | §8 전부, `ThreadOwner` 주석, `.claude/rules/scheduler.md` 모델 문구, `multi-dimension` §1 줄 | `Region`, `Regionizer`(recordTick), `RegionTickBench`, `ThreadOwner`, 규칙·다차원 문서 | 전체(컴파일 포함) | 없음 | revert | 커밋 1, 반나절. 중간 등급 모델로 충분(기계적) |
| **S2 워커 분리(동작 동일)** | `SharedScheduler` 신설(§2.1, 선택기는 단일 차원만 — `dims.size()==1`), `Regionizer` 에서 워커·`takeDue`·`Condition` 제거, `headDue`·`tryTake`·`finishTick`·`publishHead`·`closed`·`running` 추가, 깨우기 unpark(§3.3 N1·N2 포함). **호환 생성자만**(월드 = 풀 하나) | `SharedScheduler.java`(신규), `Regionizer.java`, `World.java`(없음) | `RegionizerTest`, `RegionTickTest`, `RegionPendingOrderTest`, `EmptyRegionEvictionTest`, `ChunkEvictionTest`, `DimensionChangeTest`, `TickCommandTest`(sprint·freeze·step), `FurnaceSchedulerImplTest`, 전체 | `SharedSchedulerTest`: (a) 단일 실행 — region 1개, 워커 4, 1000틱, `inFlight` 최대 1; (b) 소유자 — 틱 안 `isOwnerThread()` 참·`Owned.get()` 통과, 밖에서 AssertionError(`OwnedTest` 확장); (c) 깨우기 — 워커 4 중 3 이 자는 중 **서로 다른 차원 둘(S2 에서는 월드 둘 = 풀 둘이 아니라 한 풀에 Regionizer 둘을 register 하는 패키지 전용 시험)에 1ms 간격으로 region 을 넣어 둘 다 5ms 안에 시작**(N2 확장 시험); (d) 바통 — 유휴 워커 4 로 10초 동안 `wakeups` ≤ 4×20×10×1.5 (N1 라이브락 검출); (e) sprint — 공유 풀에서 `/tick sprint 200` 이 끝나고 보고 틱 수 = 200, 각 region 마친 틱 = 200 | revert(공개 API 불변) | 커밋 2~3, 1~2일. **최상위 모델**(동시성 핵심) |
| **S3 스핀** | §5 `parkUntilInterruptible`(N6 루프), 스핀 자리 1개 | `PreciseWait.java`, `SharedScheduler.java` | `PreciseWaitTest`, 전체 | §5 테스트 2개 | revert | 커밋 1, 반나절. 중간 등급 |
| **S4 통합 + 공정성**(한 커밋) | `World` 새 생성자, `Regionizer` 공유 생성자, `FurnaceMain` 풀 1개 + 훅 순서 + `furnace.sharedRegionPool` 스위치, §4.1 선택 키(`starved`·`perDimCap`·`tryTake(now,maxRunning)`·rr·N4 차이 비교), 기동 로그 | `World.java`, `Regionizer.java`, `SharedScheduler.java`, `FurnaceMain.java` | 전체 | `SharedSchedulerFairnessTest`: 차원 3·region 각 2·워커 4·200틱 → 모든 region 틱 수 차 ≤ 1. `SharedSchedulerStarvationTest`(R3): 차원 A region 4 × 100ms 바쁜 대기, 차원 B region 1 → B 틱 간격 p95 ≤ 3·tick, STARVE 초과 공백 0. `DimensionTravelSharedPoolTest`: 공유 풀에서 플레이어 차원 왕복 200회 → 한 차원에만 있고 가드 위반 0(`DimensionChangeTest` 변형). `SharedPoolCloseTest`: 월드 A `regionizer().close()` 뒤 월드 B 틱 계속 + `flushAll` future 완료, `pool.close()` 1초 안. `SplitDuringOtherDimTest`: 차원 A 에서 구역 수백 개 로드·퇴출로 `split` 반복, 차원 B 틱 간격 max < 2·tick. `SprintSharedPoolTest`: 차원 3 공유 풀 `/tick sprint 500` 완료·보고, 병합 유예 중에도 끝남 | `-Dfurnace.sharedRegionPool=false`(코드 유지) | 커밋 1~2, 1~2일. **최상위 모델** |
| **S4b 설정·노트·벤치 모드** | `Config`/`ConfigLoader` 주석·경고, 릴리스 노트, `MultiDimensionTickBench` 공유 풀 모드(실제 `SharedScheduler` 주입 — 지금의 "한 월드 근사" 대체) + `wakeups`·STARVE 초과 횟수·held 틱 수 출력 | `Config.java`, `ConfigLoader.java`, 릴리스 노트, `MultiDimensionTickBench.java` | 전체 | — | revert | 커밋 1, 반나절. 중간 등급 |
| **S5 측정·튜닝** | §7.2 벤치로 `STARVE`·`perDimCap`·`SPRINT_HOLD` 확정, 벤치 프로퍼티 → 상수, 약한 친화도는 필요 보일 때만 | `SharedScheduler.java`, 벤치, 벤치 문서 갱신 | 전체 | 수치 문서화(반복 5회, 중앙값·최악) | 상수 되돌림 | 커밋 1~2, 1일(측정 시간 포함). 중간 등급 + 판단은 사용자 |
| **S6 지도 갱신** | 로드맵 단계 하나 완료 → `graphify update .` → `graph.html`·`GRAPH_REPORT.md` 커밋 | `graphify-out/` | — | — | — | 커밋 1 |

합계 커밋 **8~10개**, 작업일 **5~7일**(가정, 벤치 반복 포함). S2·S4 는 리뷰 1라운드(독립 검토자)를 권한다 — 이 설계서가 두 라운드를 거쳤듯 구현도 §3.2 체크리스트로 검토한다.

### 9.1 S2 "동작 동일" 확인
S2 뒤 `MultiDimensionTickBench`(현행 모드)를 S2 전과 같은 조건(검토서 5.2 표)으로 돌려 p95 가 같은 자릿수인지 본다. 유휴 깨어남(`wakeups`) 기대치: 풀마다 ≤ T×20/초(합계 D×T×20/초 **이하**) — 넘으면 §3.3 D2 가 깨진 것. `TickCommandTest` 의 sprint 시험이 통과해야 한다(held 틱·병합 유예가 워커 분리 뒤에도 같음).

### 9.2 회귀 벤치와 합격선 (S4 merge 기준, C=4 샌드박스; 다른 기계면 비례 조정)
S4 를 **시작**하는 기준이 아니라 **합치는** 기준. 선행 ①은 끝났으므로(§1.4) 벤치만 남는다. `-Dfurnace.bench=true -Dfurnace.bench.overload=true`, 현행(`sharedRegionPool=false`)과 공유를 같은 조건·같은 세션에서 5회 반복, 중앙값과 최악 보고(1회 측정은 노이즈 — 벤치 문서 "측정 환경과 한계").

| 시나리오 | 조건 | 합격선(공유 풀) |
|---|---|---|
| A. 균등 부하 | N=6 × region 2, 5ms/틱 | p95 ≤ 현행 1/3(≤ 2.1ms), p99 ≤ 현행 1/2 |
| B. R3 비대칭 | asym heavy=60/80ms × 12(코어 3배), 가벼운 차원 region 2 × 1ms | **가벼운 차원 p95 ≤ 현행 × 1.1, 지연 틱(>10ms) ≤ 현행 + 5%p, maxRun ≤ 3, STARVE 초과 공백 0.** 무거운 차원 A 의 틱 수는 **기록만**(합격선 아님 — **N7**: cap 으로 A 워커가 4→3 이 되어 처리량이 최대 25% 줄 수 있고, A 를 희생해 다른 차원을 지키는 것이 설계 의도다). 다만 A 의 손실이 **25% 를 넘으면** cap 외의 원인(선택기 버그)을 의심해 조사한다 |
| C. 총수요 > 100% | N=6, 12ms/틱(288ms/틱 vs 200ms 가용) | 차원별 p95 최대/최소 비 ≤ 2, STARVE 초과 0. 평균 지연은 현행보다 나빠도 됨(물리적) |
| D. sprint(신규) | 차원 3 × region 4, 부하 2ms, `/tick sprint 1000` | 완료 시간 ≤ 현행 × 1.2, held 틱 수 기록(S5 의 `SPRINT_HOLD` 결정 근거) |

A·B·C 중 하나라도 미달이면 §4.1 상수를 바꿔 재측정, 두 번 바꿔도 미달이면 **보류**(검토서 7.5). 윈도(spinMargin>0) 는 S3 의 스핀 워커 1개 확인용으로 별도(도는 스레드 ≤ 1, `jstack`).

### 9.3 위험과 롤백
| 위험 | 징후 | 완화 | 롤백 |
|---|---|---|---|
| 깨우기 누락(워커가 50ms 상한까지 잠) | 유휴→부하 전환 때 지터 p95 가 수십 ms | S2 시험 (c)·(d), `wakeups` 계측 | S2 revert |
| 바통 라이브락(N1) | 유휴 CPU 사용·`wakeups` 급증 | §3.3 규칙 + 시험 (d) | 같음 |
| 차원 기아(R3) | 벤치 B 미달 | §4 정책, 시험 `StarvationTest` | `sharedRegionPool=false` |
| 가시성 사슬 끊김 | 간헐적 `Owned` AssertionError·NPE | 꺼내기·끝내기를 같은 `L_d` 안에서만(§3.4), CAS 로 바꾸지 않음 | S2 revert |
| 종료 지연·멈춤 | 종료 로그 뒤 1초 넘게 JVM 남음 | 훅 순서(§7), `SharedPoolCloseTest` | 같음 |
| sprint 중 워커 낭비(held 10kHz) | 벤치 D 완료 시간 초과 | §4.4 S5 후보(`pool.wake` + 1ms) | 상수 되돌림 |
| `region-threads` 운영 회귀(스레드 1/3) | 과부하 때 TPS 하락 보고 | 릴리스 노트, 경고, 기본값은 벤치로 확정 | `sharedRegionPool=false` |
| 교착 | 틱 정지 + `jstack` 에 `L_d` 대기 | §3.2 체크리스트 리뷰, 전역은 `L_d` 를 차례로만 | S2 revert |

---

## 10. 사용자가 결정할 항목 (→ `docs/user-todo.md`)
1. 설정/스위치 이름과 기본값: `region-threads`(이름 유지, 의미 = 서버 전체, 기본 코어 수) / `-Dfurnace.sharedRegionPool`(기본 `true`, 한 릴리스 유지) — 설계 제안대로 할지.
2. 활성화 시점: S4 를 merge 할 때 바로 기본 켬(`true`) vs 한 릴리스 동안 기본 끔(`false`)으로 두고 측정만 받을지.
3. 착수 시점: 지금 작업 리스트상 D(마지막). S1~S3 는 동작 동일 리팩터링이라 먼저 진행해도 되는지.

---

## 11. 가정 목록(이 문서에서 확인 못 한 것)
1. `region-threads` 기본값 C 유지 — §9.2-A/C 로 확정.
2. `STARVE_NANOS = 2·tick`, `perDimCap = T−1`, `SPRINT_HOLD_NANOS = 100µs` 유지 — §9.2-B/C/D 로 확정.
3. 윈도 `SPIN_HANDOFF = 1ms` — 윈도 측정 없이는 후보.
4. 등록 차원 ≤ 12(선택기 O(n) 무시 가능).
5. 공유 경로 `unregister` 가 틱 중 region 을 join 하지 않아도 종료 안전 — 실서버 flush 순서 의존.
6. §4.3 상한은 **총 수요 < 용량** 전제에서만(N5). 과부하에서는 순서 보장만.
7. 공유 풀에서 lockstep 대기가 줄어든다는 §4.4 추정 — 벤치 D 로 확인.
8. 작업 규모(§2.8·§9)는 코드 읽기 기준 추정.

## 12. 2라운드 발견(N1~N7) 반영 요약
| # | 1차 문서 | 이 문서 |
|---|---|---|
| N1 | ③ 무조건 passBaton → 라이브락 | §3.3: ② 무조건, ③ 은 "뽑혔으나 타임아웃과 겹친" 경우만. 사슬 ≤ 1. 시험 (d) |
| N2 | until 고정, ② 는 "지금 때가 됐나"만 | §3.3: ② 에서 `me.until` volatile 갱신 후 park. 시험 (c) 확장 |
| N3 | `tryTake` 둘째 인자 뜻 모호 | §4.1: "다른 차원에 때가 된 머리가 있으면 perDimCap, 없으면 MAX" |
| N4 | `now − MAX` 오버플로 | §3.1·§4.1·§5: MAX 선행 검사, 차이 비교만 |
| N5 | "각 선택이 한 차원 starved 해제" | §4.3: 밀린 region 수 K 기반 `STARVE + Tmax + ⌈(K−1)/(T−1)⌉·Tmax`, 총 수요 < 용량 전제 |
| N6 | 조기 복귀 뒤 남은 시간 전부 스핀 | §5: 거친 구간 재park 루프 |
| N7 | 8.2-B 합격선과 "cap 손실은 결함 아님" 충돌 | §9.2-B: A 는 기록만, 합격선은 가벼운 차원. 25% 초과 손실만 조사 대상 |

1차 문서 §10 의 반박 요청 답: (1) N1 규칙 "②: 전달, ③: 타임아웃과 겹칠 때만 전달" **동의**(플래그 방식도 허용). (2) N2 는 노드 재생성이 아니라 **갱신(volatile 쓰기)** 이 맞다 — 재생성 틈에 `wake` 가 빈 목록을 본다. (3) N5 상한을 "총 수요 < 용량" 전제로 한정하는 데 **동의**.
