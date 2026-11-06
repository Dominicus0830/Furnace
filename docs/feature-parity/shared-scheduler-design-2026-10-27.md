# 공유 region 스케줄러(B안) 확정 설계서 (2026-10-27)

검토서 `shared-scheduler-review-2026-10-27.md`(이하 "검토서")의 7장 최종 재검증(R1~R7, 채택 조건 6개)을 받아, 구현자가 그대로 따라 짤 수 있도록
구조·잠금 순서·공정성·대기·설정·계측·단계·테스트를 **확정**한 문서다. 줄 번호는 `claude/quirky-wright-lra834`(f25f8042) 기준으로 코드를 직접 읽고 적었다.
코드로 확인하지 못한 것은 **가정**으로 표시했다. 이 문서는 설계만 담고 코드 변경은 없다.

**한 줄 요약:** 차원별 `Regionizer` 는 큐·구조 잠금·합치기·나누기를 그대로 갖고, 작업 스레드만 서버 하나의 `SharedScheduler` 로 뺀다.
워커는 **잠금 없이** 각 차원이 `volatile` 로 발행한 "큐 맨 앞 마감시간"을 비교해 차원을 고르고, 꺼내기·끝내기만 그 차원 잠금 안에서 한다.
깨우기는 `LockSupport.unpark` 라 어느 스레드도 잠금 두 개를 동시에 쥐지 않는다(교착 불가). 공정성은 EDF + "굶은 차원 우선" + "차원별 동시 점유 상한".

---

## 0. 용어

| 용어 | 뜻 |
|---|---|
| 차원 잠금 `L_d` | `Regionizer.lock`(`Regionizer.java:40`, `ReentrantLock`). 차원(World)마다 하나 |
| 큐 | `Regionizer.queue`(`:47`, `PriorityQueue<Region>`, `nextTickNanos` 오름차순) |
| 머리 마감 `headDue` | 큐 맨 앞 region 의 `nextTickNanos`. 새로 추가하는 `volatile long` 필드(비면 `Long.MAX_VALUE`) |
| 워커 | `SharedScheduler` 의 작업 스레드. 수 = `region-threads`(T) |
| 꺼내기 | QUEUED → RUNNING(`queue.poll`, `ticking=true`, `tickThread=…`) — 지금 `workerLoop` 356-359행 |
| 끝내기 | RUNNING → IDLE/QUEUED(`ticking=false`, `afterTick`) — 지금 372-381행 |

---

## 1. 클래스·인터페이스 구조

### 1.1 새 클래스 `SharedScheduler` (서버 하나, 워커 풀)
위치: `furnace-server/src/main/java/org/domi/furnace/server/world/SharedScheduler.java`. `Region`/`Regionizer` 의 패키지 전용 필드(`queued`, `ticking`, `tickThread`,
`Region.java:56-61`)를 만지므로 `sched` 가 아니라 `world` 패키지에 둔다(`Regionizer` 와 같은 이유).

```java
public final class SharedScheduler implements AutoCloseable {
    SharedScheduler(int threads, long tickNanos);                 // 워커 T개 생성·시작(데몬, MAX_PRIORITY-1 — Regionizer.java:82-83 그대로)
    void register(Regionizer rz);                                  // 차원 큐 등록(CopyOnWriteArrayList 추가)
    void unregister(Regionizer rz);                                // 차원 큐 분리(목록 제거) — 월드 close 가 부른다. 풀은 멈추지 않는다
    void wake(long due);                                           // 차원이 큐에 더 이른 항목을 넣었을 때 — 잠금 없음(1.4, 2.3)
    int threadCount();
    @Override void close();                                        // 서버 종료 때만. running=false → 모든 워커 unpark → join(1s)
}
```
내부 상태(모두 잠금 없이 다루는 원시형만):
- `CopyOnWriteArrayList<Regionizer> dims` — 선택기가 순회. 등록·해제는 드물다.
- `Thread[] workers`, `volatile boolean running`.
- `ConcurrentLinkedQueue<Parked> parked` — 자는 워커 목록(2.3 깨우기). `Parked { Thread t; volatile long until; }` — 언제까지 자는지 함께 적어 둔다(헛깨우기 방지, 2.3).
- `AtomicBoolean spinning`, `volatile Thread spinner`, `AtomicLong spinDeadline` — 돌며 기다리는 워커는 1개(4장). 앞당김은 `accumulateAndGet(due, Math::min)`.
- `AtomicInteger rr` — 동률 때 차원 순환(3.2).

### 1.2 `Regionizer` 가 유지하는 것 / 내놓는 것
유지(코드 그대로): `lock`·`queue`·`sections`·`regions`·`want`(147)·`release`(173)·`merging`(163)·`ownerForTask`(132)·`forEachRegion`(261)·`enqueue`(245)·
`afterTick`(422)·`resolveMerges`(445)·`absorb`(464)·`split`(483)·`kill`(578)·`schedule`(437).

제거: `workers`(48, 77-86), `workerLoop`(351-383), `takeDue`(386-419), `Condition wake`(41), `close()` 의 join(633-639).

추가(모두 패키지 전용, `SharedScheduler` 가 부른다):
```java
volatile long headDue = Long.MAX_VALUE;          // 1.3 발행 규칙
volatile boolean closed;                          // unregister 뒤 참. 새 틱을 꺼내 주지 않는다
/** 차원 잠금 안: 머리가 때가 됐고 살아 있으면 꺼내 RUNNING 으로 바꿔 돌려준다. 아니면 null(머리를 다시 발행했으니 워커는 다시 고른다). */
Region tryTake(long now);
/** 차원 잠금 안: ticking=false, tickThread=null, afterTick(r). */
void finishTick(Region r);
/** 차원별 공정성(3장): 지금 RUNNING 인 region 수. 선택기는 잠금 없이 힌트로 읽고, tryTake 가 잠금 안에서 상한을 다시 확인한다 */
final AtomicInteger running = new AtomicInteger();
```
`tryTake(now, maxRunning)` 는 지금 `takeDue` 의 "지연 삭제 + 꺼내기" 부분(393-397, 415-417)에 "`running.get() >= maxRunning` 이면 null"(3.1 cap 재확인) 을 더한 것이다. 기다리는 부분(390, 398-413)은 워커로 간다.

### 1.3 `headDue` 발행 규칙
- **큐가 바뀌는 모든 자리의 끝에서, 차원 잠금 안에서** `publishHead()` 를 부른다: `headDue = queue.isEmpty() ? Long.MAX_VALUE : queue.peek().nextTickNanos`.
  자리: `schedule`(437-442 — 추가), `tryTake`(poll — 지연 삭제·꺼내기), `absorb`/`kill`(alive=false — 머리가 죽은 항목이면 선택기는 꺼내 보고 null 을 받아 다시 고르므로 발행을 빠뜨려도 안전하지만, 그래도 부른다), `unregister`(MAX_VALUE).
- `tryTake(now, maxRunning)` 는 `running.get() >= maxRunning` 이면(차원별 상한, 3.1) 꺼내지 않고 null 을 돌려준다 — 선택기가 잠금 없이 본 `running` 은 힌트이고 이 재확인이 보장이다.
- 머리가 "살아 있지 않거나 yielded/ticking" 인 낡은 항목이어도 그대로 발행한다(지금 `takeDue` 393-397 의 지연 삭제와 같은 정신). 워커가 `tryTake` 로 치우고 다시 발행한다. 낡은 머리는 **시간을 앞당길 뿐 늦추지 않으므로**(죽은 항목의 due ≤ 실제 머리 due) 늦게 돌리는 일은 없다.
- `volatile` 쓰기는 잠금 안에서만, 읽기는 어디서나(선택기). 발행 값은 "힌트"다 — 결정은 항상 `tryTake` 가 잠금 안에서 다시 한다.

### 1.4 `schedule` 과 깨우기
```java
private void schedule(Region r) {                 // 차원 잠금 안(지금과 같음)
    if (r.queued || !r.alive || r.yielded || closed) return;
    r.queued = true;
    queue.add(r);
    long prev = headDue;
    publishHead();
    if (headDue < prev) pool.wake(headDue);       // 머리가 앞당겨졌을 때만 깨운다. pool.wake 는 잠금을 잡지 않는다(2.3)
}
```
`wake.signalAll()`(441) 은 없어진다. 조건 변수가 없으므로 `Condition` 을 위해 전역 잠금을 잡을 일도 없다 — 이것이 R1 의 해법 (a)+(b) 다.

### 1.5 World 생성자·close 호환 (`new World(` 호출 38파일 48곳 불변 — `furnace-server/src/test`+`tools` grep, 주석 포함; 검토자 집계 37/47 과의 ±1 은 집계 범위 차이)
- 기존 세 생성자(`World.java:82-87, 89-94, 100-120`)와 `int regionThreads` 서명은 **그대로 둔다.** 115행 `new Regionizer(this, regionThreads, …)` 는
  `Regionizer` 호환 생성자로 가고, 그 안에서 `this.pool = new SharedScheduler(threads, tickNanos); this.ownsPool = true; pool.register(this)` 한다.
  즉 **int 를 주면 자기 전용 풀** = 지금 동작과 같다(월드 하나 = 풀 하나). 테스트·`MultiDimensionTickBench`(`:45`)·`FurnaceSchedulerImplTest` 모두 무변경.
- 새 생성자 하나를 추가한다(실서버 경로):
  `World(DimensionType, WorldSource, ChunkWorkerPool, FurnaceScheduler, SharedScheduler pool, int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance, int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool)`.
  → `new Regionizer(this, pool, sectionShift, mergeRadius, tickNanos)`(공유 생성자, `ownsPool=false`, `pool.register(this)`).
- `Regionizer.close()` 는 **자기 큐 분리**만 한다: `lock` 안에서 `closed=true; headDue=MAX_VALUE;` → `pool.unregister(this)` → `ownsPool` 이면 `pool.close()`.
  지금 테스트 55곳의 `regionizer().close()`/`sched.close()`/`scheduler.close()`(월드 단위)는 호환 생성자라 `ownsPool=true` 여서 지금처럼 워커가 멈춘다.
- 종료 훅 등록(`World.java:116-119`)은 그대로(`regionizer::close`). `FurnaceMain` 은 풀을 하나 만들어 세 월드에 넘기고(`:195-197`, `:277-278`),
  **월드 훅들 뒤에** `scheduler.addCloseHook(pool::close)` 를 단다. `FurnaceSchedulerImpl.close()`(`:96-107`)는 훅을 등록 순서로 돌리므로
  "세 월드 분리 → 풀 종료 → fallback → global" 순이 된다. `scheduler.close()` 호출 자리(`FurnaceMain.java:354`)는 flush(326-335)·IO 종료(336-339) **뒤**라 지금과 같다.
- 풀 수명 정리(R5·조건 5): **공유 풀은 서버 종료(`scheduler.close()` 훅) 때만 닫힌다. 월드 close 는 자기 큐를 분리할 뿐이다.** 검토서는 "참조 계수"를 말했지만
  `ownsPool` 플래그 + `unregister` 로 같은 효과(마지막 월드가 닫혀도 풀은 서버가 닫는다, 호환 경로는 월드가 닫는다)를 더 단순하게 얻는다.
  등록 수가 0 인데 풀이 살아 있는 상태는 허용된다(워커는 50ms 주기로 자며 선택할 차원이 없다 — 2.3). 참조 계수로 자동 종료하면 테스트에서 "월드 하나 닫고 다른 월드 계속" 이 깨지므로 하지 않는다.

### 1.6 틱 중인 region 과 분리(unregister)의 경합
`unregister` 시점에 그 차원 region 이 RUNNING 일 수 있다. 처리:
1. `closed=true` 는 잠금 안에서 쓴다. `tryTake` 는 `closed` 면 항상 null. `schedule` 은 `closed` 면 넣지 않는다(1.4).
2. RUNNING 중인 region 은 `finishTick` 까지 돈다(`afterTick` → `schedule` 이 `closed` 로 거절 → IDLE 로 끝). 워커는 그 뒤 다른 차원으로 간다.
3. 호환 경로(`ownsPool`)는 `pool.close()` 가 워커를 `join(1000)` 한다(지금 `Regionizer.java:633-639` 와 같은 시간 상한). 공유 경로는 join 하지 않는다 — 실서버 종료는 `FurnaceMain.java:326-335` 가 먼저 flush 를 끝내므로 이 시점에 돌고 있는 틱은 "저장 뒤의 빈 틱"이다. **가정**: flush 뒤 30초 안에 끝나지 않는 틱은 지금도 종료를 막지 않는다(`:331` 타임아웃) — 변하지 않는다.
4. `unregister` 뒤 그 월드에 `runAtChunk`/`flushAll` 을 부르면 `newRegion → schedule` 이 `closed` 로 거절돼 그 future 는 **영원히 완료되지 않는다.** 현행도 `close()` 뒤 같다(워커가 없다). 종료 중 `onSaveAll` 의 `f.join()`(`FurnaceMain.java:305`, 글로벌 스레드)이 여기 걸리면 글로벌 스레드가 멈추는 것은 **기존 부채**로, 이 설계가 바꾸지 않는다(기록만).

### 1.7 FurnaceSchedulerImpl
변경 없음에 가깝다. `regionThreads` 필드(`:23,36,67-69`)는 fallback `RegionThread[]` 수에만 쓰이므로 그대로. 플러그인 `region(RegionPos)` 라우팅(`:46-51`, 오버월드만 `World.java:117`)은 region 객체를 찾는 것이라 풀과 무관(검토서 2-#14 CONFIRMED).

### 1.8 소유권 모델 — 변경 없음
`Region.isOwnerThread()`(`Region.java:93`) = `tickThread == currentThread`, 설정·해제는 꺼내기·끝내기(차원 잠금 안). `World.regionAt/ownsChunkOnCurrentThread/assertOwns`(`World.java:688-705`),
`Owned`·`ThreadOwner`(`furnace-api/.../concurrent/ThreadOwner.java`) 모두 그대로. `ThreadOwner` 주석만 "현재 스레드가 **지금 이 owner 를 실행 중인** 스레드인가" 로 고친다(하위 호환, 검토서 6.4-4).
`.claude/rules/scheduler.md` 모델 1-2항은 코드와 이미 다르므로(검토서 2-#15) "같은 region 은 동시에 한 스레드에서만 돈다. 매 틱 다른 워커일 수 있다" 로 고친다(7장 S1).

---

## 2. 잠금 순서 규칙 (R1)

### 2.1 규칙
> **R1 규칙: 차원 잠금 `L_d` 를 쥔 채 다른 어떤 잠금(다른 차원의 `L_d'`, 풀의 잠금)도 잡지 않는다. 풀에는 잠금이 없다.**
> 선택기는 `headDue`(volatile)만 읽는다. 깨우기는 `LockSupport.unpark` 다. 따라서 어느 스레드도 동시에 잠금 두 개를 쥘 수 없다.

### 2.2 상태 전이 표 (region 하나)
플래그: `queued`, `ticking`, `yielded`, `alive`(`Region.java:56-61`). 모든 전이는 **그 region 이 속한 차원 잠금 안**에서만.

| 상태 | queued | ticking | yielded | alive | 들어오는 전이(어디서) | 나가는 전이 |
|---|---|---|---|---|---|---|
| IDLE | F | F | F | T | 끝내기 뒤 `schedule` 거절(closed) / `newRegion` 직전 | `schedule` → QUEUED |
| QUEUED | T | F | F | T | `schedule`(`newRegion` 238, `split` 565, `afterTick` 434) | `tryTake`(때가 됨) → RUNNING; 지연 삭제(alive=F·yielded) → DEAD/YIELDED |
| RUNNING | F | T | F | T | `tryTake` | `finishTick` → `afterTick`: `resolveMerges` → QUEUED(`schedule`) / YIELDED(상대 틱 중, 452) / DEAD(흡수됨·`split`→`kill` 505) |
| YIELDED | F/T* | F | T | T | `resolveMerges` 452 | 상대의 `finishTick` → `absorb` 479(`yielded=false`, alive=F) → DEAD |
| DEAD | 아무 값 | F | 아무 값 | F | `absorb` 465, `kill` 579 | 없음. 큐에 남아 있으면 `tryTake` 가 지연 삭제 |

\* yielded 로 바뀔 때 큐 항목은 그대로 남을 수 있다(지금도 그렇다, 393행 지연 삭제). `tryTake` 가 치운다.

워커 쪽 불변식: **한 region 에 대해 RUNNING 은 동시에 최대 1개 워커.** 근거: QUEUED→RUNNING 은 `queue.poll` 과 `ticking=true` 를 같은 `L_d` 임계 구역에서 하고, 큐에는 한 번만 들어가므로(`schedule` 438 `if (r.queued) return`) 두 워커가 같은 region 을 꺼낼 수 없다. 지금 코드와 같은 논증(검토서 1.2).

### 2.3 깨우기·대기 원시 (잠금 없음)
이 코드가 **유일한** 깨우기 정의다(4.2 는 이것을 부른다).
```java
// SharedScheduler
void wake(long due) {
    // 1) 돌며 기다리는 워커의 목표를 앞당긴다(원자적 min — 스피너가 자기 값을 덮어써 생산자의 앞당김을 지우지 못하게)
    long prev = spinDeadline.getAndAccumulate(due, Math::min);
    if (due < prev) { Thread s = spinner; if (s != null) LockSupport.unpark(s); }   // 스피너가 아직 parkNanos 구간이면 깨운다(4.2)
    // 2) 자는 워커 중 "due 보다 늦게 깰" 워커 하나를 깨운다. 어차피 due 전에 깰 워커는 깨울 필요가 없다(헛깨우기 방지 — D2)
    for (Parked p : parked) {
        if (p.until > due && parked.remove(p)) { LockSupport.unpark(p.t); return; }
    }
}
```
워커가 자는 절차(놓친 깨우기·바통 소실 방지):
```java
Parked me = new Parked(self, untilNanos);   // untilNanos = min(d - spinMargin, now + 50ms)
parked.add(me);                                       // ① 먼저 "잔다"고 등록
long d = earliestHead();                              // ② 그 다음 머리를 다시 본다
if (d <= System.nanoTime() + spinMargin) {            //    바뀌었으면 안 잔다
    if (!parked.remove(me)) passBaton();              //    누가 나를 뽑아 갔다면(permit 만 남음) 바통을 다른 자는 워커에게 넘긴다
    continue;
}
LockSupport.parkNanos(me.until - System.nanoTime());  // ③ 잔다(상한 50ms — 지금 390행과 같은 안전망)
if (!parked.remove(me)) passBaton();                  //    깨어남: 내가 뽑혀 깬 것이면 다른 자는 워커도 하나 깨운다(바통)
```
```java
/** 나를 깨운 unpark 가 실제로 자고 있는 워커에게 가지 못했을 수 있으므로 하나 더 깨운다. 깨어난 워커는 다시 고르고,
 *  할 일이 없으면 다시 잔다(헛깨우기 1회 비용 ≪ 50ms 지연). */
private void passBaton() { Parked p = parked.poll(); if (p != null) LockSupport.unpark(p.t); }
```
논증: `schedule` 은 `queue.add` → `headDue` 쓰기 → `wake` 순이다(1.4). 워커는 `parked.add` → `headDue` 읽기 순이다. 둘 다 volatile/CAS 로 순서가 보장되므로,
"워커가 옛 머리를 읽고 잠" 과 "schedule 이 빈 parked 를 봄" 은 동시에 일어날 수 없다(Dekker 식 교차 — 둘 중 하나는 상대의 쓰기를 본다). 설령 `unpark` 가 `park` 보다 먼저 와도 `LockSupport` 의 허가(permit) 가 남아 `park` 가 바로 돌아온다.

**바통 소실(D1)**: `wake` 가 뽑은 워커가 "자려다 안 자기로 한" 워커(② 경로)거나 타임아웃으로 막 깬 워커면 unpark 는 permit 으로만 남고 실제 자는 워커는 깨지 않는다.
그래서 `parked.remove(me) == false`(= 누가 나를 뽑았다)인 두 경로 모두 `passBaton()` 으로 자는 워커를 하나 더 깨운다. 바통을 받은 워커도 같은 규칙을 따르므로
"깨울 워커가 남아 있는데 아무도 안 깨는" 상태는 유한 단계 안에 끝난다(매 전달이 `parked` 를 하나 줄인다). 50ms 상한은 **안전망일 뿐 성능 목표(지터 ≪ 1ms)의 근거가 아니다** — S2 테스트 (c) "5ms 안에 시작"이 이를 검증한다.

**헛깨우기(D2)**: 유휴 차원은 region 이 쉬었다 다시 `schedule` 되면 `headDue` 가 `MAX → now+50ms` 로 "앞당겨"지므로 매 틱 `wake` 가 불린다. `Parked.until` 비교로
"due 전에 어차피 깰 워커"는 깨우지 않아, 유휴 때 깨어남은 **워커당 20/초(T×20/초)** 로 유지된다(지금 D×T×20/초 — 7.1 기대치).

### 2.4 모든 경로의 잠금 획득 순서 표

| 경로 | 부르는 곳 | 잡는 잠금(순서) | 잠금 안에서 부르는 외부 | 비고 |
|---|---|---|---|---|
| 선택기(`earliestHead`) | 워커 | **없음** | `rz.headDue` 읽기만 | O(차원 수) |
| 꺼내기 `tryTake` | 워커 | `L_d` | `publishHead` | 잠금 안에서 풀을 부르지 않는다 |
| 틱 `runTick` | 워커 | **없음**(region 소유 상태만) | `rz().enqueue` → `ownerForTask` → `L_d`(같은 차원, 비중첩) ; `World.runAtChunk`(다른 차원) → 그 차원 `L_d'`(비중첩) | `Region.java:100-116` |
| 끝내기 `finishTick` | 워커 | `L_d` | `afterTick` → `split`/`absorb`/`reroute`(→`enqueue`→`ownerForTask`, **재진입** 같은 `L_d`) → `schedule` → `pool.wake`(잠금 없음) | 지금 372-381 과 같은 범위 |
| `schedule` | `newRegion`·`split`·`afterTick`(모두 `L_d` 안) | `L_d`(이미 쥠) | `pool.wake` — unpark/volatile 만 | **차원→전역 잠금 순서가 생기지 않는다** |
| `want`/`release`/`merging`/`ownerForTask`/`forEachRegion` | region 틱·IO 콜백·글로벌 스레드 | `L_d` | 없음(풀 무관) | 지금 그대로. `merging` 은 플레이어·엔티티마다 불린다(`Region.java:710,846`) — 차원별로 남긴 이유 |
| `enqueue` | 어느 스레드나 | (`ownerForTask` 안에서) `L_d` | 없음 | 인박스는 `ConcurrentLinkedQueue` |
| `register`/`unregister` | World 생성자 / `Regionizer.close` | `L_d`(closed·headDue 쓰기) → **놓고** → 풀 COW 목록 갱신 | 없음 | 순서: 잠금을 놓은 뒤 풀을 만진다 |
| `pool.close` | `FurnaceSchedulerImpl.close` 훅(글로벌/종료 스레드) | **없음**(running=false, 전원 unpark, join) | 없음 | 워커 join 은 잠금 밖 |
| 차원 이동 `completeTravel`/`finishTravel` | 원천 region 틱 | 원천 `L_d` 안이 아님(인박스에 넣기만, `World.java:879-945`) | 목적 월드 `runAtChunk` → 목적 `L_d'` | 지금도 비중첩(검토서 R7) |
| `adoptAfterDimensionChange` → 다른 월드 `updateSleepingPlayerList` | 목적 region 틱 | 없음(틱 중, 잠금 밖) | `SleepStatus` 자체 synchronized | **규칙: 이 호출을 잠금 안으로 옮기지 않는다**(R7) |

### 2.5 교착 불가능 논증
명제(정확히): **`L_d` 를 쥔 채, 다른 차원 잠금 `L_d'` 나 "`L_d` 를 쥔 스레드를 기다릴 수 있는 잠금"을 잡지 않는다.**
("두 잠금을 동시에 보유하지 않는다"는 문자 그대로는 거짓이다 — `L_d` 안에서 `log.debug`(appender 잠금, `Regionizer.java:566`), `ConcurrentHashMap` 내부 잠금(`sections`), `RegionLevel` 지연 생성(`Region.java:357-360`) 등이 잠깐 잡힌다. 이들은 `L_d` 를 쥔 스레드를 기다리는 일이 없는 **잎(leaf) 잠금**이라 사이클을 만들 수 없다.)
1. 잠금 계층: `L_d`(차원 수만큼, 서로 순서 없음 — 동시에 두 개를 잡는 코드가 없다) → 잎 잠금(로거·CHM·JDK 내부). 풀은 잠금을 갖지 않는다(2.3 — `ConcurrentLinkedQueue`·`AtomicBoolean`·`AtomicLong`·volatile·`LockSupport`).
2. `Regionizer` 는 다른 월드를 참조하지 않는다(검토서 R7 확인). `L_d` 를 쥔 채 호출되는 메서드는 2.4 표의 `publishHead`·`afterTick` 계열(`resolveMerges`/`absorb`/`split`/`kill`/`absorbData`/`splitData`)·`pool.wake`·`reroute→enqueue`(재진입 같은 잠금)만이고 그 어느 것도 다른 `L_d'`·블로킹 대기·다른 월드 호출을 하지 않는다.
   → 보유-대기 그래프에 `L_d → L_d'` 간선이 없고 잎 잠금은 밖으로 나가는 간선이 없으므로 사이클이 없다 = 교착 불가.
3. `LockSupport.park` 는 잠금을 쥔 채 하지 않는다(워커 대기는 선택기 단계, 잠금 밖). `tryTake`/`finishTick` 임계 구역 안에는 대기·IO 가 없다(지금 `split` 의 BFS 509-529 가 가장 긴 구간이며 CPU 작업만).
4. 구현 시 지킬 검사 규칙(코드 리뷰 체크리스트):
   - `Regionizer` 안에서 `pool.` 호출은 `wake`·`register`·`unregister`·`threadCount` 만 허용. `wake` 는 잠금 안에서 불려도 되지만(잠금 없는 원시) `register/unregister` 는 잠금을 놓은 뒤에만 부른다.
   - `SharedScheduler` 는 `rz.lock` 을 직접 잡지 않는다 — `tryTake`/`finishTick` 를 부를 뿐이다.
   - **`afterTick` 계열이 부르는 `Region`/`RegionLevel` 메서드(`absorbData`·`splitData`·`players()`·`isIdleEmpty`)는 월드 밖(다른 World·Dimensions·SleepStatus·네트워크)을 부르지 않는다.** 새 호출을 추가할 때 이 조건을 리뷰에서 확인한다.
   - 잠금 안 `log.*` 는 `debug` 이하만(appender 잠금 보유 시간 최소화 — 지금 `:566` 그대로).

### 2.6 메모리 가시성 (검토서 3.7 조건 유지)
앞 틱의 끝내기(`finishTick`: `L_d` 안 `ticking=false`)와 다음 틱의 꺼내기(`tryTake`: 같은 `L_d` 안 `ticking=true`)가 **같은 `ReentrantLock`** 을 지나므로
앞 워커의 모든 쓰기가 다음 워커에 보인다. 선택기가 잠금 밖에서 `headDue` 를 보는 것은 결정이 아니라 힌트(1.3)라 가시성 사슬에 끼지 않는다. 꺼내기를 잠금 밖 CAS 로 바꾸지 않는다(검토서 3.2·7.1).

---

## 3. 공정성 (R3)

### 3.1 선택 키
워커의 `earliestHead()` 는 등록된 차원마다 다음을 잠금 없이 계산해 가장 작은 키를 고른다.
```
now        = System.nanoTime()
due_d      = rz.headDue                                  // MAX_VALUE 면 후보 아님
starved_d  = now - due_d >= STARVE_NANOS                 // 머리 region 이 마감을 STARVE 이상 지났다(이미 발행된 값만 쓴다)
capped_d   = rz.running.get() >= perDimCap && (다른 차원 중 due <= now 인 것이 있다)   // 힌트 — tryTake 가 잠금 안에서 재확인
key_d      = (capped_d ? 2 : starved_d ? 0 : 1 , due_d)  // 사전식: 굶은 차원 → 보통 → 상한 초과
동률(같은 클래스·같은 due ±1µs) → rr 순환(차원 index 를 (rr++ % n) 부터 돈다)
r = best.tryTake(now, capped 조건이 걸리는 상황이면 perDimCap, 아니면 MAX)   // 잠금 안: running < max 재확인, 아니면 null → 재선택
```
- `perDimCap = max(1, T - 1)` : 한 차원이 워커 전부를 점유하지 못하게 **항상 워커 하나는 다른 차원 몫**으로 남긴다. T=1 이면 상한 없음(모두 한 워커).
  선택기 둘이 동시에 `running = T−2` 를 보고 같은 차원을 고르는 경합은 `tryTake` 의 잠금 안 재확인이 막는다(둘 중 하나는 null 을 받아 다시 고른다). 재확인은 정수 비교 하나라 비용이 없다.
- `starved` 지표는 **머리의 지연(`now − headDue`)** 이다. 검토자 지적(D4)대로 "마지막 틱 시작 뒤 흐른 시간(`lastTickStart`)" 은 굶음이 아니라 **느림**을 잰다 — 100ms 걸리는 region 하나뿐인 차원은 매 틱 끝에 lag ≥ STARVE 가 되어 영구 클래스 0 이 되고, 무거운 차원이 가벼운 차원보다 항상 먼저 뽑히는 역전이 생긴다. 머리 지연은 그런 차원도 `afterTick` 재동기(`:433`) 뒤 due = now+50ms 라 지연 0 에서 다시 시작하므로 역전이 없다. `lastTickStart` 필드는 두지 않는다.
- `STARVE_NANOS` 후보: `2 × tickNanos`(100ms). 대안 `1 × tickNanos`. 벤치 ②(8장)에서 정한다. 뜻: "마감을 두 틱 넘긴 차원은 무조건 다음 차례".
- `running` 은 `tryTake` 성공 +1 / `finishTick` −1(`AtomicInteger`, 둘 다 잠금 안이지만 선택기가 잠금 없이 읽으므로 원자형).

### 3.2 왜 EDF 만으로 부족한가(R3 재서술)와 해법의 효과
`afterTick`(`Regionizer.java:431-433`)은 밀린 region 을 `now + tickNanos` 로 재동기한다. 차원 A 의 region 이 틱마다 100ms 걸리면 끝날 때마다 데드라인이 50ms 뒤로 잡혀,
"지금 막 때가 된" 다른 차원 region 과 EDF 동률이 된다. A 의 region 수 ≥ T 면 A 가 워커를 다 차지한다.
- `perDimCap` 가 이를 막는다: A 는 최대 T−1 워커(선택기 힌트 + `tryTake` 잠금 안 재확인으로 상한이 **보장**된다). 남은 1 워커가 다른 차원을 EDF 순으로 돈다.
- `starved` 는 cap 이 걸리지 않는 경우(A 의 region 수 < T 인데도 데드라인 운이 계속 A 쪽인 경우)의 안전망이다.
- 틱을 건너뛰지 않는다: 순서만 바꾸고 `afterTick` 의 재동기·`TickTimer` 의미는 그대로 → 바닐라 관측(틱 수)은 변하지 않는다(검토서 3.5-2 가정 유지).

### 3.3 차원별 최소 보장·기아 방지 논증
전제: 워커 T ≥ 2, 차원 n, 모든 틱 길이 ≤ `Tmax`(유계).
- 임의 차원 B 의 머리 region 이 때가 됐는데 `STARVE_NANOS` 동안 꺼내지지 않았다고 하자. 그러면 `now − headDue_B ≥ STARVE` 이므로 `starved_B = true`, 클래스 0 이다.
- 클래스 0 차원은 capped 가 아닌 한 모든 클래스 1·2 차원보다 먼저 뽑힌다. B 가 capped 라면 B 는 이미 T−1 개 워커를 쓰고 있는 것이므로 "굶는다"는 전제에 어긋난다 — 즉 **굶는 차원은 running < perDimCap** 이다(cap 은 그 차원 자신이 쓰는 워커 수에만 걸린다).
- 워커 중 적어도 하나는 `Tmax` 안에 선택기로 돌아온다(모든 틱이 유계). 그 워커는 클래스 0 중 가장 이른 due 를 고른다. 클래스 0 차원이 여럿이면 due 순 → 각 차원은 유한 시간 안에 뽑힌다(클래스 0 집합 크기 ≤ n, 꺼내진 머리는 `afterTick` 재동기로 지연이 0 이 되어 클래스 0 에서 빠진다).
- 따라서 B 의 첫 틱 시작 지연 상한 ≈ `STARVE_NANOS + Tmax + (n−1)·Tmax`. **최소 보장**: 어느 차원도 그 이상 굶지 않는다.
- cap 덕에 평시(아무 차원도 starved 아님)에도 한 차원이 워커 T 개를 전부 쥐고 다른 차원을 `Tmax` 단위로 막는 일이 없다 — R3 가 지적한 "A 의 틱이 끝나는 틈에만" 상황을 구조적으로 제거.
- 한계(**가정**): 총 CPU 수요가 코어 수를 넘으면 어떤 정책도 모든 차원을 20TPS 로 돌릴 수 없다. 그때 B안은 "늦은 것부터"(EDF) + "굶은 차원 먼저"로, 현행(OS 시간 조각)보다 최대 지연이 **예측 가능**해지는 것이 목표이지 평균이 좋아진다는 보장은 아니다. 8장 착수 기준이 이를 측정으로 확인한다.

### 3.4 파라미터 후보 요약
| 이름 | 후보 | 기본(가정) | 바꾸는 방법 |
|---|---|---|---|
| `starved` 지표 | (a) 머리 지연 `now − headDue`(**확정, 3.1**), (b) 마지막 틱 시작 뒤 경과 `now − lastTickStart`(기각 — 느림을 굶음으로 오판, 3.1) | (a) | 상수. S5 벤치(`shared-scheduler-bench-overload-2026-10-27.md`, 작성 예정)에서 (a) 가 8.2-B/C 를 통과하지 못할 때만 (b)+(a) 조합을 다시 본다 |
| `STARVE_NANOS` | 1·tick, 2·tick, 4·tick | 2·tick | 시스템 프로퍼티 `furnace.sched.starveTicks`(정수, 벤치용) |
| `perDimCap` | T−1, ⌈T·0.75⌉ | max(1, T−1) | `furnace.sched.dimCap`(벤치용) |
| 동률 폭 | 0, 1µs, 100µs | 1µs | 상수 |
| 약한 친화도(검토서 3.8) | 끔 / 1ms 창 | **끔** | 5단계 이후 측정 후 |

벤치 전용 프로퍼티는 운영 설정(`furnace.properties`)에는 넣지 않는다 — 측정이 끝나면 상수로 굳힌다.

---

## 4. PreciseWait 스핀 대기 (검토서 3.6 UNVERIFIED 해소)

### 4.1 문제
지금 `takeDue` 400-411: 머리 남은 시간이 `spinMargin` 이하면 잠금을 놓고 `PreciseWait.parkUntil(due)` 로 돈다. 도는 워커 표시가 없어 **여러 워커가 같은 머리를 보고 함께 돈다**(차원 수 × T 까지).
`parkUntil`(`PreciseWait.java:42-51`)은 중단 지점이 없다 — 더 이른 region 이 `schedule` 돼도 끝까지 돈다.

### 4.2 설계: 스핀 워커 1개 + 중단 가능한 스핀
`PreciseWait` 에 추가(기존 `parkUntil` 는 `RegionThread` 108-110행이 쓰므로 그대로 둔다):
```java
/** deadline 까지 기다린다. 매 회전마다 currentDeadline 을 다시 읽어, 더 이르면 그 시각으로 바꾼다.
 *  돌려주는 값: 실제로 기다린 마감(바뀌었으면 호출자는 다시 고른다). */
public static long parkUntilInterruptible(long deadline, java.util.function.LongSupplier currentDeadline) {
    long d = deadline;
    long remain = d - System.nanoTime();
    long spin = spinMargin();
    if (remain > spin) {
        LockSupport.parkNanos(remain - spin);           // 거친 구간 — wake() 의 unpark 로 조기 복귀 가능(4.3)
        d = Math.min(d, currentDeadline.getAsLong());
    }
    while (System.nanoTime() < d) {
        long c = currentDeadline.getAsLong();
        if (c < d) d = c;                                // 더 이른 region 이 들어왔다 → 목표를 앞당긴다
        Thread.onSpinWait();
    }
    return d;
}
```
`SharedScheduler` 워커의 대기 단계:
```java
long wait = bestDue - now;
if (wait > 0) {
    long spin = PreciseWait.spinMargin();
    if (spin > 0 && wait <= spin + SPIN_HANDOFF) {
        if (spinning.compareAndSet(false, true)) {                       // 스핀 자리는 1개
            spinner = self;
            spinDeadline.accumulateAndGet(bestDue, Math::min);           // 덮어쓰지 않고 min — 생산자가 먼저 앞당긴 값을 지우지 않는다
            long target = Math.min(spinDeadline.get(), earliestHead());  // CAS 뒤 들어온 더 이른 머리를 다시 본다
            try { PreciseWait.parkUntilInterruptible(target, spinDeadline::get); }
            finally { spinner = null; spinDeadline.set(Long.MAX_VALUE); spinning.set(false); }
            continue;                                                    // 다시 고른다(머리가 바뀌었을 수 있다)
        }
        // 누가 이미 돈다: 바쁜 재시도 금지 — 2.3 절차로 잔다(until = bestDue, 상한 50ms). 스피너가 꺼내면 wake/바통이 깨운다
        sleep(until = Math.min(bestDue, now + 50ms));
        continue;
    }
    sleep(until = Math.min(bestDue - spin, now + 50ms));   // 2.3 의 자는 절차(parked 등록 → 재확인 → parkNanos)
    continue;
}
```
- `spinning` CAS 에 실패한 워커(이미 누가 돈다)는 **반드시 잔다**(`parked`, until = bestDue). `wait − spin` 이 음수가 되어 즉시 `continue` 하는 바쁜 루프(D7-3)는 이 분기로 막는다. 돌던 워커가 꺼내기에 성공하면 그 region 을 돌리고, 다음 머리는 `wake`/바통이 깨우거나 until 로 깨어나 본다.
- `spinDeadline` 은 `AtomicLong`, 쓰기는 **항상 `accumulateAndGet(…, Math::min)`**(생산자 `wake` 와 스피너 모두). 스피너는 CAS 성공 뒤 `earliestHead()` 를 한 번 더 읽어 "CAS 직전 들어온 더 이른 머리"를 놓치지 않는다(D7-1). 끝날 때만 `set(MAX_VALUE)` — 이 시점에는 `spinning=true` 라 생산자의 min 이 사라질 수 있지만, 스피너는 바로 `continue` 로 다시 고르므로 그 머리를 본다.
- `wake(due)` 의 정의는 2.3 하나다: `due < 이전 spinDeadline` 이면 `spinner` 를 unpark 한다(스피너가 아직 `parkNanos` 구간이면 즉시 복귀해 `currentDeadline` 을 다시 읽는다). 스핀 구간에서는 매 회전 `currentDeadline` 을 읽어 즉시 반영한다.
- `SPIN_HANDOFF`(후보 1ms): 스핀 자리를 잡는 문턱을 `spinMargin` 보다 조금 넉넉히 두어 "자다 깨서 보니 이미 스핀 구간" 인 워커가 다시 자러 가는 왕복을 줄인다. **가정**: 윈도에서만 의미 있다(리눅스 `spinMargin=0` → 스핀 경로 자체가 없음, `PreciseWait.java:33`).
- 효과: 서버 전체에서 도는 스레드는 항상 ≤ 1 (차원 수·T 와 무관). 검토서 3.6 의 "숨은 비용"이 사라진다.

### 4.3 테스트
`PreciseWaitTest` 에 추가: `parkUntilInterruptible(now+10ms, 공급자가 2ms 뒤 now+3ms 를 돌려줌)` 가 ~3ms 에 돌아오고 반환값이 앞당긴 시각인지(`-Dfurnace.timer.spin=true` 로 스핀 강제, `decide("true")`=17ms 이용). 리눅스에서도 돈다(spin 을 인자로 받는 `parkUntil(deadline, spinMargin)` 식 오버로드를 하나 더 두어 테스트가 margin 을 직접 준다).

---

## 5. `region-threads` 의미 변경 (R4)

### 5.1 의미
| | 지금 | B안 |
|---|---|---|
| 뜻 | **차원마다** region 작업 스레드 수(`Regionizer.java:77`, 세 차원 모두 같은 값 `FurnaceMain.java:196,277`) | **서버 전체** region 워커 수(`SharedScheduler` 하나) |
| 기본값 | `availableProcessors()`(`Config.java:39`) | 같음(**가정**: 벤치 ②/8장 결과로 `C` 유지. C 코어에서 틱 워커가 C 개를 넘으면 OS 경쟁으로 돌아가므로 늘릴 이유가 약하다) |
| 실제 틱 스레드 수(차원 3, C=4) | 12 | 4 |
| 검증 | `>= 1`(`ConfigLoader.java:69`) | 같음. 추가로 `> 2·C` 면 경고(오해 방지: "차원 수를 곱해 넣었나") |

키 이름은 바꾸지 않는다(설정 파일 호환). `Config.java:7` 주석과 `ConfigLoader` 경고 문구, 기동 로그 한 줄(`"[SCHED] region 워커 {}개(서버 전체, 차원 {}개 공유)"`)을 바꾼다.

### 5.2 릴리스 노트 문구(초안)
> **region-threads 의미 변경.** 이전 버전은 차원(오버월드·네더·엔드)마다 이 수만큼 틱 스레드를 따로 만들었습니다(실제 틱 스레드 = 값 × 차원 수).
> 이번 버전부터 모든 차원이 **하나의 워커 풀**을 공유하며 `region-threads` 는 그 풀의 크기(서버 전체)입니다. 기본값(코어 수)을 쓰고 있었다면 바꿀 필요가 없습니다 —
> 틱 지터가 차원 수에 비례해 커지던 문제가 사라지고 스레드 수는 1/3 로 줍니다. 값을 직접 정해 두었다면 "이전 값 × 차원 수"가 이전과 같은 스레드 수이지만,
> 코어 수를 넘는 값은 권하지 않습니다(틱 스레드가 코어보다 많으면 OS 가 순서를 정해 지터가 커집니다). 한 차원이 CPU 를 독점하지 못하도록 워커 하나는 항상 다른 차원 몫으로 남겨 둡니다(`region-threads` ≥ 2 일 때).
> 되돌리기: `-Dfurnace.sharedRegionPool=false` 로 이전 방식(차원별 풀)을 한 버전 동안 유지합니다(7장 롤백).

---

## 6. 틱 계측 정리 (R6, 기존 부채)

| 항목 | 지금 | 문제 | 확정 처리 |
|---|---|---|---|
| `tickStats` 키 | `Regionizer.tickStats`(`:307`) 키 `r.id`(`:312`), id 는 월드별 `++nextId`(`:235,535`) | 풀로 옮기면 차원끼리 겹침. 지금도 로그 `region #{}`(`:328`) 에 차원이 없음 | 맵을 **region 인스턴스 필드**로 옮긴다: `Region.tickStat = new long[2+200]`(region 소유 — 틱을 돌린 워커만 만지고 꺼내기·끝내기 잠금 사슬로 가시). 로그 라벨은 `world.dimension() + "#" + id`(예 `minecraft:the_nether#3`). `Regionizer.tickStats` 맵 삭제 |
| `Region.INSTALL_STATS` | `static long[4]`, 비원자 `++`/`+=`(`Region.java:285,323-326`), 모든 차원·워커가 잠금 없이 | 경합 — 값이 틀릴 수 있다 | `static` 제거. `Region.installStats = new long[4]`(인스턴스, 그 region 틱 안에서만 더함 — `finishLoad` 는 region 틱 안 `:287`). `recordTick` 은 방금 돈 region 의 값을 읽어 100틱 합계로 찍고 0 으로 되돌린다 |
| `lastInstall` | `Regionizer` 필드(`:309`) — 워커 여럿이 읽고 씀 | 경합 | 삭제(위 인스턴스화로 불필요) |
| `phaseSum/phaseMax` | region 인스턴스(`Region.java:645`) | 이미 안전 | 그대로 |
| `recordTick` 호출 위치 | `workerLoop` 371(틱 뒤, 끝내기 잠금 **앞**) | — | 같은 자리를 지킨다: **`runTick` 뒤, `finishTick` 앞(region 이 아직 RUNNING, 잠금 밖)**. `finishTick` 뒤로 옮기면 다른 워커가 그 region 을 이미 꺼내 인스턴스 계측(`tickStat`·`installStats`·`phaseSum`)을 동시에 만질 수 있다(D5). 차원별 메서드로 남겨 라벨에 차원을 쓴다 |
| `RegionTickBench.java:131` | `Region.INSTALL_STATS` 를 직접 읽는다 | static 제거 시 **컴파일 깨짐**(D6) | S1 에 포함: 벤치가 돌린 region 들의 `installStats` 합을 `regionsSnapshot()` 으로 모아 출력(벤치는 틱이 멈춘 뒤 읽으므로 경합 없음) |
| 벤치 스레드 수 세기 | `MultiDimensionTickBench.java:40,50` 이 `furnace-region` 접두사 | B안에서는 풀 하나라 의미가 "서버 전체" | 그대로 둔다(워커 이름 `furnace-region-N`). per-dim 라벨 출력(`:85`)은 "공유 풀" 로 바꾼다 |

이 정리는 B안 1단계(7장 S1)에서 **먼저** 한다. 기존 테스트 중 `RegionTickBench`(위 행) 하나만 같이 고친다. 그 외 영향 없다(계측은 `-Dfurnace.tickstats` 때만).

---

## 7. 단계별 구현 계획

각 단계 = 커밋 1~2개, 매 단계 끝에 `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test` 전체 통과. 단계 순서는 바꾸지 않는다(조건 2: 공정성은 통합과 같은 커밋).

| 단계 | 커밋 내용 | 바뀌는 파일 | 회귀 테스트(기존) | 새 테스트 | 롤백 |
|---|---|---|---|---|---|
| **S0 선행** | ① `Region.chunkAt` 동기 생성을 운영 경로에서 떼기, ② 벤치 과부하 확장 — 별도 작업(8장) | — | — | — | — |
| **S1 계측·문서** | 6장 전부(`tickStat`·`installStats` 인스턴스화, 라벨에 차원), `RegionTickBench.java:131` 수정, `ThreadOwner` 주석, `.claude/rules/scheduler.md` 모델 문구 | `Region.java`, `Regionizer.java`(recordTick), `RegionTickBench.java`, `ThreadOwner.java`, 규칙 문서 | 전체(컴파일 포함 — 벤치는 `assumeTrue` 로 건너뛰지만 컴파일된다) | 없음(계측) | 커밋 되돌림 |
| **S2 워커 분리(동작 동일)** | `SharedScheduler` 신설, `Regionizer` 에서 워커·`takeDue`·`Condition` 제거, `headDue`·`tryTake`·`finishTick`·`publishHead`·`closed` 추가, 깨우기를 unpark 로. **호환 생성자만** 존재(월드 = 풀 하나, `ownsPool=true`) | `SharedScheduler.java`(신규), `Regionizer.java`, `Region.java`(없음 또는 주석) | `RegionizerTest`, `RegionTickTest`, `RegionPendingOrderTest`, `EmptyRegionEvictionTest`, `ChunkEvictionTest`, 차원 이동 테스트, 전체 | `SharedSchedulerTest`: (a) 단일 실행 — region 1개에 틱마다 `AtomicInteger inFlight` 를 올리고 내려 최대값이 1 인지(워커 4, 1000틱); (b) 소유자 판정 — 틱 안 `isOwnerThread()` 참·`Owned.get()` 통과, 다른 스레드에서 `Owned.get()` 은 AssertionError(`OwnedTest` 확장, 규칙 8); (c) 깨우기 — 빈 큐에서 자는 워커가 `schedule` 뒤 5ms 안에 돌기 시작(50ms 상한에 의존하지 않음을 확인) | 커밋 되돌림(공개 API 변화 없음) |
| **S3 스핀** | 4장: `PreciseWait.parkUntilInterruptible`, 스핀 자리 1개 | `PreciseWait.java`, `SharedScheduler.java` | `PreciseWaitTest`, 전체 | 4.3 | 커밋 되돌림 |
| **S4 통합 + 공정성** (한 커밋 — 검토서 조건 2) + **S4b 설정·노트**(분리 가능) | S4: `World` 새 생성자(풀 주입), `Regionizer` 공유 생성자(`ownsPool=false`), `FurnaceMain` 풀 1개 + 종료 훅 순서, 3장 선택 키(`starved`·`perDimCap`·`tryTake` 재확인·rr), `-Dfurnace.sharedRegionPool=false` 스위치(FurnaceMain 에서 int 경로로 분기). S4b(같은 PR, 별도 커밋 가능): `Config`/`ConfigLoader` 주석·경고·기동 로그, 릴리스 노트, 벤치 공유 풀 모드 | `World.java`, `Regionizer.java`, `SharedScheduler.java`, `FurnaceMain.java`, `Config.java`, `ConfigLoader.java`, `docs/release-notes`(또는 기존 노트 위치), `MultiDimensionTickBench.java`(공유 풀 모드 추가) | 전체 | `SharedSchedulerFairnessTest`: 차원 3개·region 각 2개를 공유 풀(워커 4)에서 200틱 — 모든 region 틱 수 차 ≤ 1; `SharedSchedulerStarvationTest`(R3 시나리오): 차원 A region 4개 틱 100ms(바쁜 대기), 차원 B region 1개 — B 의 틱 간격 p95 ≤ 3·tick 이고 B 가 `STARVE` 넘게 멈춘 횟수 0; `DimensionTravelSharedPoolTest`: 공유 풀에서 플레이어 차원 왕복 200회(기존 차원 이동 테스트의 공유 풀 변형) 후 플레이어가 한 차원에만 있고 `isOwnerThread` 가드 위반 0; `SharedPoolCloseTest`: 월드 A `regionizer().close()` 뒤 월드 B 가 계속 틱(틱 수 증가) + `flushAll()` future 들이 완료, 그 뒤 `pool.close()` 가 1초 안에 돌아옴; 합치기·나누기 중 다른 차원 진행 — 차원 A 에서 `split` 을 반복 유발(구역 수백 개 로드·퇴출)하며 차원 B 틱 간격 max < 2·tick | `-Dfurnace.sharedRegionPool=false`(코드 유지) 또는 `FurnaceMain` 두 줄 되돌림 |
| **S5 측정·튜닝** | 벤치 ②(현행 vs 공유) 결과로 `STARVE`·`perDimCap` 확정, 벤치 전용 프로퍼티 제거, 약한 친화도는 필요가 보일 때만 | `SharedScheduler.java`, 벤치, 검토서 5장 갱신 | 전체 | 벤치 수치 문서화 | 정책 상수 되돌림 |
| **S6 지도 갱신** | 로드맵 단계 하나가 끝났으므로 `graphify update .` 후 `graph.html`·`GRAPH_REPORT.md` 커밋(CLAUDE.md 규칙) | `graphify-out/` | — | — | — |

### 7.1 S2 의 "동작 동일" 확인 방법
S2 뒤 `MultiDimensionTickBench`(현행 모드)를 S2 전과 같은 조건(검토서 5.2 표)으로 돌려 p95 가 같은 자릿수인지 본다. 깨우기 원시가 바뀌었으므로(Condition → unpark) 유휴 때 깨어남 횟수를 잰다(워커 루프에 `AtomicLong wakeups` 카운터, 벤치 출력). 기대치: S2(차원별 풀, 풀당 T)에서는 풀마다 `T×20/초` 이하 → 합계 `D×T×20/초` **이하**(지금과 같거나 적다 — `Parked.until` 비교로 "어차피 곧 깰 워커"는 깨우지 않으므로, 2.3 D2). S4(공유 풀)에서는 `T×20/초` 이하. 이보다 많으면 2.3 의 헛깨우기 방지가 깨진 것이다.

### 7.2 롤백 전체
- S1~S3 는 공개 서명이 바뀌지 않아 커밋 단위 revert 로 끝난다.
- S4 는 한 릴리스 동안 `-Dfurnace.sharedRegionPool=false` 를 남긴다. 이 스위치는 `FurnaceMain` 에서 "풀을 만들어 넘기느냐, `config.regionThreads()` 를 int 로 넘기느냐"만 가른다 — 두 경로 모두 S2 의 같은 `SharedScheduler` 코드를 쓰므로(차원별 풀 vs 공유 풀) 스위치가 테스트되지 않은 코드를 깨우는 일은 없다. 다음 릴리스에서 스위치와 int 생성자의 실서버 사용을 제거하되 **테스트용 int 생성자는 유지**한다.

---

## 8. 선행 조건 상태와 착수 기준

### 8.1 선행 조건
| # | 내용 | 상태(2026-10-27, 이 브랜치 기준) | 완료 판정 |
|---|---|---|---|
| ① | `Region.chunkAt`(`Region.java:221-238`) 의 동기 생성·디스크 읽기(`anvil.generate(..., ioLane)` 228행)를 운영 경로에서 떼기. 호출처: `getBlock` 404, `chestAt` 420, `setChestItem` 427·430, `markBlockEntityChanged` 440, `markChestChanged` 449·451, `placeBlock` 475 (+ `breakBlock`, 검토서 R2) | **진행 중**(로드맵 5-12 작업 리스트 ①, `roadmap-2026-10-09.md:135`). 호출처 조사 문서 `chunkat-callers-2026-10-27.md` 는 이 브랜치에 아직 없다 — **가정**: 다른 세션에서 작성 중 | 운영 경로(상자·블록 상호작용·엔티티 틱)에서 `chunkAt` 이 로드되지 않은 청크를 만나면 **생성하지 않고** null/무시 또는 비동기 `loadChunk` 로 가고, `chunkAt` 의 동기 생성은 테스트·도구 호출만 남는 것. 규칙 6(region 스레드 블로킹 IO 금지) 위반이 운영 경로에서 0 |
| ② | `MultiDimensionTickBench` 과부하 확장: 차원별 p95 분리 출력, R3 시나리오(한 차원 region 수 ≥ T, 틱 100ms), 총 수요 > 100% 구간, 공유 풀 모드 스위치 | **진행 중**(작업 리스트 ②). 지금 벤치(`MultiDimensionTickBench.java:35-92`)는 전체 합산 p95 만 찍고 차원별 분리·R3 조건이 없다 | 아래 8.2 의 세 시나리오를 현행/공유 두 모드로 같은 조건에서 돌릴 수 있고, 차원별 p50/p95/p99/max 와 "STARVE 초과 횟수"를 출력 |
| ③ | 이 설계서 확정(잠금 순서·차원 최소 보장·`region-threads` 의미·풀 수명) | 이 문서 | 사용자 승인 |

### 8.2 착수 기준(벤치 ②, 코어 C=4 샌드박스 기준; 다른 기계면 N 과 수치를 비례 조정)
S4 를 **시작**하는 기준이 아니라 S4 를 **합치는(merge)** 기준이다. S1~S3 는 선행 조건과 무관하게 진행해도 된다(동작 동일 리팩터링·계측).
**합치기 조건 = 아래 벤치 세 시나리오 통과 AND 선행 조건 ①(`chunkAt` 동기 생성 제거) 완료.** ① 이 끝나지 않은 채 S4 를 합치면 region 워커의 블로킹 IO 피해가 모든 차원으로 넓어진다(검토서 R2·조건 4) — 벤치가 통과해도 합치지 않는다.

| 시나리오 | 조건 | 현행(차원별 풀, 측정됨) | 공유 풀 합격선 |
|---|---|---|---|
| A. 균등 부하 | N=6 차원 × region 2, 부하 5ms/틱(검토서 5.2) | p95 6.42ms, p99 10.0ms | **p95 ≤ 현행의 1/3(≤ 2.1ms)** 이고 p99 ≤ 현행의 1/2. 근거: 단일 월드 근사가 p95 0.116ms 였으니 선택기·잠금 분리 비용을 넉넉히 쳐도 1/3 은 넘어야 B안이 뜻이 있다 |
| B. R3 과부하 | N≥6 차원, 차원 A region 수 = T(4) 틱 100ms, 나머지 차원 region 1개 부하 0 | 측정 필요(②) — 현행은 A 가 다른 차원 스레드를 뺏지 못하므로 다른 차원 p95 는 CPU 경쟁분만. A 자체는 현행도 100ms 틱이라 10TPS(물리적 한계) | **다른 차원 p95 ≤ 현행 × 1.1**, 어느 차원도 `STARVE_NANOS` 초과 공백 0 회. 틱 수 정의: **다른 차원 region 틱 수 = 20TPS × 측정 시간(±1)**, **A 의 region 틱 수 = 현행 측정값 ± 10%**(cap = 3 으로 A 워커가 4→3 이 되어 A 의 처리량이 최대 25% 줄 수 있다 — 이것이 10% 를 넘으면 `perDimCap` 후보 ⌈T·0.75⌉ 로 재측정. A 를 희생해 다른 차원을 지키는 것이 설계 의도이므로 A 의 손실 자체는 결함이 아니다) |
| C. 총수요 > 100% | N=6, 부하 12ms/틱(24 region × 12ms = 288ms/틱 vs 200ms 가용) | 측정 필요(②) | 차원별 p95 의 **최대/최소 비 ≤ 2**(공정성 — 현행은 OS 가 공평하므로 비슷할 것, 가정), 어느 차원도 STARVE 초과 0 회. 평균 지연은 현행보다 나빠도 된다(물리적으로 불가피) |

세 시나리오 중 하나라도 미달이면 S4 를 합치지 않고 3.4 파라미터를 바꿔 다시 잰다. 두 번 바꿔도 미달이면 **보류**(검토서 7.5). 윈도(spinMargin>0) 측정은 S3 의 스핀 워커 1개 확인용으로 별도 — 합격선: 도는 스레드 수 ≤ 1(`jstack` 또는 `spinning` 카운터 로그).

### 8.3 가정 목록(이 문서에서 확인 못 한 것)
1. `region-threads` 기본값 C 유지가 맞다 — 8.2-A/C 결과로 확정.
2. `STARVE_NANOS = 2·tick`, `perDimCap = T−1` 이 적정 — 8.2-B/C 로 확정.
3. 윈도 `SPIN_HANDOFF = 1ms` — 윈도 측정 없이는 상수 후보일 뿐.
4. 호출처 조사 문서 ① 이 다른 세션에서 작성 중이고 그 결론이 "운영 경로에서 동기 생성 제거" 와 같다.
5. 풀에 등록된 차원이 12개를 넘지 않는다(선택기 O(n) 가 무시 가능하다는 검토서 3.3 가정). 넘으면 `headDue` 를 힙으로 모아야 하나, 그러면 전역 잠금이 다시 생기므로 그때 따로 설계한다.
6. 공유 경로 `unregister` 가 틱 중인 region 을 join 하지 않아도 종료가 안전하다(1.6) — 실서버 flush 순서(`FurnaceMain.java:326-335`)에 의존.

## 9. 검토 반영 기록 (2026-10-27, 독립 검토자 '수정 후 통과')
| # | 지적 | 처리 |
|---|---|---|
| D1 | 깨우기 바통 소실(`parked.poll` 이 "안 자기로 한" 워커를 뽑으면 실제 자는 워커가 50ms 까지 안 깬다) | **인정.** 2.3 에 `passBaton()`(remove 실패 = 뽑힌 것 → 하나 더 깨움) 을 ②·③ 두 경로에 추가. 50ms 상한은 안전망일 뿐 지터 목표의 근거가 아님을 명시 |
| D2 | 유휴 차원 헛깨우기(MAX→now+50ms 가 늘 앞당김) | **인정.** `Parked.until` 비교로 due 전에 깰 워커는 깨우지 않음(2.3). 7.1 기대치를 `T×20/초 이하` 로 |
| D3 | cap "구조적으로 막는다"는 과장 | **인정.** `tryTake(now, maxRunning)` 가 잠금 안에서 재확인(1.2·1.3·3.1). 비용은 정수 비교 하나 |
| D4 | `lastTickStart` 는 느림을 굶음으로 오판 | **인정.** 지표를 `now − headDue` 로 교체, 필드 제거, 3.3 논증 재서술, 3.4 표에 두 지표 행 |
| D5 | `recordTick` 을 `finishTick` 뒤에 두면 경합 | **인정.** "`runTick` 뒤·`finishTick` 앞" 으로 고정(6장) |
| D6 | `RegionTickBench.java:131` 이 `INSTALL_STATS` 직접 참조 → S1 컴파일 깨짐 | **인정.** S1 범위에 포함(6장·7장) |
| D7 | `spinDeadline` 비원자·스피너 unpark 불일치·CAS 실패 바쁜 루프 | **인정.** `AtomicLong`+min, `wake` 정의를 2.3 하나로 통일, CAS 실패 시 `parked` 로 잠(4.2) |
| D8 | "두 잠금 동시 보유 불가"는 문자 그대로 거짓 | **인정.** 2.5 를 "`L_d` 를 쥔 채 `L_d'` 나 `L_d` 를 기다릴 수 있는 잠금을 잡지 않는다"(잎 잠금 허용) 로 재서술, 체크리스트에 `afterTick` 계열의 월드 밖 호출 금지 추가 |
| D9 | 수치·기준 | close 호출 57→55 정정. `new World(` 는 이 워크트리 grep 으로 38/48(주석 포함) — 검토자 37/47 과 ±1 은 집계 범위 차이로 보고 그대로 둠. 8.2-B 틱 수 정의 추가, 8.2 에 ① 완료를 합치기 조건으로 추가, 1.6 에 unregister 뒤 future 미완료(기존 부채) 기록 |
| (선택) | S4 에서 설정·노트 분리 | 수용 — S4(통합+공정성, 조건 2)와 S4b(설정·노트·벤치 모드)로 나눔 |

반박 요청 답변: (1) `now−headDue` 를 쓰면 안 되는 이유 — **없다.** 검토자가 맞다(위 D4). (2) `tryTake` 재확인 — **넣는다**(D3). 처음 힌트로 둔 것은 "선택기 = 잠금 없음"을 단순히 지키려던 것이었으나 재확인은 잠금 안 정수 비교 하나라 R1 규칙에 어긋나지 않는다. (3) D2 — **`Parked.until` 비교를 넣고** 7.1 기대치도 "이하"로 정확히 고쳤다(둘 다). (4) 8.2 를 "벤치 세 시나리오 + ① 완료"로 — **동의**(반영). (5) D1 — **동의.** 50ms 는 정확성(놓친 깨우기)의 안전망이고, 설계 목표인 지터 p95 ≪ 1ms 는 바통 전달 없이는 달성되지 않는다.

## 10. 2라운드 독립 검토 결과 (미반영 — 구현 착수 전에 2.3 등을 반드시 고칠 것)

검토자(2라운드)는 "수정 후 통과"로 판정했다. 1라운드 수정이 새로 만든 구멍 2개가 있어 아래를 반영하기 전에는 구현에 들어가지 않는다. 반영 후 추가 라운드는 불필요하다는 의견이다.

| # | 심각도 | 내용 | 수정 |
|---|---|---|---|
| N1 | 상 | passBaton 을 2.3 ③에서 무조건 전달 → 유휴 워커 2개 이상이면 바통이 순환(라이브락), "매 전달이 parked 를 하나 줄인다"는 종료 논증이 재등록을 빼고 센 것이라 불성립 | ③ 은 타임아웃으로 깬 경우(`now - me.until >= 0`)만 전달, ② 는 무조건 전달. 전달 사슬 길이 ≤ 1 |
| N2 | 중 | `until` 을 `parked.add` 전 값으로 고정하고 ② 가 "지금 때가 됐나"만 확인 → 생산자 순회가 내 add 보다 먼저 끝난 경우 더 이른 머리를 알고도 옛 until 까지 잠(≤50ms). 1라운드 원안에는 없던 회귀 | ② 에서 `me.until = min(until, d - spinMargin)` (volatile 쓰기) 후 그 값으로 park. 시험 (c) 를 "워커 4 중 3이 자는 중 서로 다른 차원 둘에 1ms 간격으로 region 을 넣어 둘 다 5ms 안에 시작"으로 확장 |
| N3 | 저 | `tryTake` 두 번째 인자가 "capped 판정"으로 읽혀 재확인이 경합을 못 막음 | "다른 차원에 때가 된 머리가 있으면 perDimCap, 없으면 MAX" 로 명시 |
| N4 | 저 | `now - Long.MAX_VALUE` 오버플로(nanoTime 음수) | MAX 검사 선행, 차이 기반 비교만 사용 |
| N5 | 저 | 3.3 "각 선택이 한 차원의 starved 를 해제" 는 틀림(밀린 region k 개면 k 번 뽑혀야 빠짐) | 상한을 "총 수요 < 용량" 전제 아래 밀린 region 수 기반 수식으로 |
| N6 | 저(윈도) | 스피너 조기 복귀 뒤 남은 시간 전부 스핀(≤ spinMargin + SPIN_HANDOFF ≈ 18ms) | 거친 구간 재park 루프 |
| N7 | 정보 | 8.2-B 합격선("A 틱 수 = 현행 ±10%")과 "cap 손실은 결함 아님" 문구 충돌 | "A 는 기록만, 합격선은 다른 차원" 또는 "±10% 초과 시 cap 후보 재측정" 중 하나로 |

남은 반박 요청(작성자 답 대기): (1) N1 규칙 "②: 전달, ③: 자발적으로 깼을 때만 전달" 동의 여부(플래그 방식도 가능) (2) N2 는 노드 재생성이 아니라 갱신(volatile 쓰기) 방식이 맞는지 (3) N5 상한을 "총 수요 < 용량" 전제로 한정하는 데 동의 여부.
