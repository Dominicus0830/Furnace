# 공유 region 스케줄러(B안) 검토 (2026-10-27)

질문: 지금 차원(World)마다 따로 있는 `Regionizer` 작업 스레드 풀을 없애고, 스레드를 코어 수로 고정한 **하나의 공유 데드라인 큐**에서
모든 차원의 region 틱을 `(차원, region)` 단위로 꺼내 돌리면(B안) 되는가. 커스텀 차원이 늘어날 때를 대비한 검토다.
코드 수정 없이 읽고 잰 결과다. 줄 번호는 `claude/quirky-wright-lra834`(a3bbeffd) 기준. 확인 못 한 것은 **가정**으로 적었다.

**한 줄 결론:** B안은 이 코드베이스에서 생각보다 훨씬 작은 작업이다. 지금 `Regionizer` 가 **월드 하나 안에서는 이미 B안 그대로**
(공유 데드라인 큐 + 작업 스레드 풀 + region 단일 실행 + 스레드가 아니라 region 이 소유자)로 돌기 때문이다. 바꿀 것은 "큐·작업 스레드를
월드 밖으로 빼는 것" 하나이고, 소유권 가드·ThreadLocal·플러그인 API 계약은 거의 그대로 산다. 측정상으로도 같은 부하에서 틱 지터
p95 가 11.7ms → 0.14ms 로 줄었다(5장). **권고: B안 채택, 단 4장의 순서로.**

---

## 1. 현재 구조

### 1.1 스레드와 스케줄러 (`server/sched/*`)
| 구성 | 위치 | 스레드 | 쓰임 |
|---|---|---|---|
| `GlobalThread` | `sched/GlobalThread.java:22-33` | 단일 스레드 executor 1개(`furnace-global`). `isOwnerThread()` = `Thread.currentThread() == thread` (37행) | 전역 작업. B안과 무관 |
| `RegionThread` | `sched/RegionThread.java` | 자기 루프 스레드 1개, 우선순위 `MAX_PRIORITY-1`(37행). 인박스 드레인 25ms 예산(119-127행) + `TickTimer` + `PreciseWait.parkUntil`(108-110행) | **월드가 없을 때만 쓰는 대체 경로**(`FurnaceSchedulerImpl.fallback()`, 71-84행). 서버 실행 경로에는 없다 — 오버월드가 `installRouter` 하면(`World.java:117`) `region(RegionPos)` 는 라우터로 간다(`FurnaceSchedulerImpl.java:46-51`) |
| `FurnaceSchedulerImpl` | 108행 | 위 둘의 파사드. `regionThreads` 는 설정값을 기억만 한다(36행) | 플러그인 `FurnaceScheduler` 구현 |
| `PreciseWait` | 52행 | — | 시작 때 `parkNanos(1ms)` 15회 중앙값이 3ms 넘으면(윈도 15.6ms 타이머) 마지막 `spinMargin`(최대 20ms) 을 돈다(25-33행). 리눅스에서는 보통 0 |
| `TickTimer` | 33행 | — | 드리프트 보정, 밀리면 재동기(`advance`, 27-32행) |

### 1.2 실제 region 틱: `world/Regionizer.java`
- **월드마다 하나**: `World` 생성자 `World.java:115` `new Regionizer(this, regionThreads, …)`. `regionThreads` 는 설정 `region-threads`,
  기본 `availableProcessors()`(`Config.java:39`). `FurnaceMain.java:196`(오버월드), `:277`(네더·엔드 루프) 모두 **같은 값**을 넘긴다.
- 작업 스레드: `Regionizer.java:77-86` — `threads` 개, 이름 `furnace-region-[차원-]i`, 데몬, 우선순위 `MAX_PRIORITY-1`.
- 큐: `PriorityQueue<Region> queue`, `nextTickNanos` 오름차순(47행) = **이미 데드라인 큐**. 잠금 하나 `ReentrantLock lock` + `Condition wake`(40-41행).
- `workerLoop`(351-383행): 잠금 안에서 `takeDue()` → `r.ticking = true; r.tickThread = currentThread` → 잠금 밖에서 `r.runTick(gameTicks)` →
  잠금 안에서 `ticking=false; tickThread=null; afterTick(r)`.
- `takeDue`(386-419행): 머리가 죽었거나 `yielded`/`ticking`/`!queued` 면 버리고(지연 삭제), 때가 안 됐으면 `wake.awaitNanos`, 남은 시간이
  `spinMargin` 이하면 **잠금을 놓고** `PreciseWait.parkUntil(due)` 로 돌다가 null 을 돌려 다시 고른다(400-411행).
- `afterTick`(422-435행): 합치기(`resolveMerges`) → 20틱마다 나누기(`split`) → `nextTickNanos += tickNanos`(밀리면 재동기) → `schedule`.
- **region 단일 실행 보장**: `ticking` 플래그와 `queued` 플래그를 잠금 안에서만 바꾼다. 한 region 은 큐에 한 번만(`schedule` 438행) 들어가고,
  꺼낸 스레드만 돌린다. CAS 가 아니라 잠금으로 보장한다.
- **region 은 매 틱 다른 작업 스레드에서 돌 수 있다.** 스레드 친화도가 없다(`takeDue` 는 아무 스레드나 가장 이른 region 을 가져간다).

### 1.3 Region / RegionLevel
- `Region.java:56-61`: `volatile alive, mergedInto, ticking, tickThread`; `yielded`, `queued` 는 Regionizer 잠금 안에서만.
- `Region.isOwnerThread()` = `tickThread == Thread.currentThread()` (93행). **소유자는 스레드가 아니라 "지금 이 region 을 돌리는 스레드"**다.
- `runTick`(100-118행): 인박스 → 지연 작업 → 게임 틱. 인박스 작업도 25ms 예산, 자기 청크가 아니면 `rz().enqueue` 로 되돌린다(106-107행).
- `inbox` 는 `ConcurrentLinkedQueue<Task>`(69행) — 다른 스레드가 넣는다. `delayed` 는 region 소유.
- `RegionLevel`(230행)과 `ChunkLevel` 은 region 의 블록 레벨이다. 빛 캐시 `lightWorld`(`ChunkLevel.java:1326-`)는 **ThreadLocal 이 아니라
  레벨 인스턴스 필드**(4칸 직접 사상 캐시 + `depth`)라 region 을 따라다닌다.

### 1.4 합치기·나누기와 스레드 배정
- 합치기(`resolveMerges` 445-461행, `absorb` 464-480행): 틱 끝(잠금 안)에서 상대가 쉬면 흡수, 틱 중이면 자기가 `yielded` 로 빠지고 상대가
  끝날 때 흡수된다. 흡수된 쪽 인박스는 `reroute`.
- 나누기(`split` 483-567행): 잠금 안, r 이 쉬는 동안. 새 조각은 `nextTickNanos = r.nextTickNanos + tickNanos`(536행)로 큐에 넣는다.
- **스레드 배정은 없다.** 합치기·나누기 결과는 큐 항목이 늘거나 줄 뿐이고, 어느 스레드가 돌릴지는 다음 `takeDue` 가 정한다. 그래서 B안에서
  이 부분은 손댈 것이 없다(단, 이 작업들이 잡는 잠금 범위는 문제가 된다 → 3.4).

### 1.5 작업 보내기·차원 간 이동
- `World.runAtChunk`(189행) → `Regionizer.enqueue`(245-249행): 청크 주인 region 인박스에 넣는다. 주인은 **스레드가 아니라 region**.
  `submitAtChunk`(209행)는 그 결과판, 플러그인용 `ChunkScheduler`(225-266행)의 `isOwnerThread` 도 `regionizer.ownerOf(...).isOwnerThread()`.
- 차원 이동(`Region.sendAcross` 1323-1331행 → `World.travel`(879행) → 목적 월드 `completeTravel`(905-921행) → `finishTravel`(923-945행)):
  원천 region 이 플레이어를 떼고, 목적 월드에 **작업 메시지**로 청크 로드·`complete` 를 넘긴다. 실패하면 `src.runAt(...)` 로 되돌린다.
  목적 쪽이 "청크가 한 region 에 모일 때까지" 1틱씩 다시 거는 것도 `r.schedule(…, 1)`(934행)이다.
  → **차원 간 이동은 스레드가 아니라 메시지 전달에 의존**한다. 스레드 풀이 하나든 셋이든 코드 경로가 같다.
- 엔티티의 region 경계 이동·가시성 핸드오프(규칙 4)도 같은 `enqueue` 경로다.

## 2. "스레드 == 소유자" 가정 목록

| # | 가정 | 위치 | 지금 | B안에서 |
|---|---|---|---|---|
| 1 | `Owned<T>.get()` 은 `owner.isOwnerThread()` | `furnace-api/.../Owned.java:18-23` | owner 가 무엇이냐에 달림 | **변경 없음**. 판단을 `ThreadOwner` 에 맡긴다 |
| 2 | `ThreadOwner.isOwnerThread()` 계약 "현재 스레드가 소유 스레드인가" | `ThreadOwner.java:4-8` | Region 은 이미 "지금 돌리는 스레드"로 해석(`Region.java:93`) | **변경 없음**. 문서 문구만 "지금 이 owner 를 실행 중인 스레드" 로 고치면 정확하다(하위 호환) |
| 3 | `GlobalThread`/`RegionThread` 의 `currentThread()==thread` | `GlobalThread.java:37`, `RegionThread.java:43` | 고정 스레드 | Global 은 그대로. `RegionThread` 는 월드 없는 테스트용이라 남겨도 됨 |
| 4 | `World.regionAt`/`ownsChunkOnCurrentThread`/`assertOwns` | `World.java:688-705` | `Region.isOwnerThread` 경유 | 변경 없음 |
| 5 | `ChunkScheduler.isOwnerThread` | `World.java:258-262` | 동적 | 변경 없음 |
| 6 | ThreadLocal `MenuBlockContext.CURRENT` | `MenuBlockContext.java:7-21` | `runIn` 이 set/restore — 작업 하나 범위 | 안전. 단 작업이 중간에 양보(continuation)하지 않는다는 가정 위. 지금 그런 코드는 없다 |
| 7 | ThreadLocal `RedStoneWireBlock.NO_SIGNAL` | `RedStoneWireBlock.java:29` | 계산 중 잠깐 켜고 끈다(Folia 와 같은 처리) | 안전(같은 가정). **가정**: 예외 경로에서도 끄는지 확인 안 함 — 지금도 같은 위험 |
| 8 | ThreadLocal `RandomTrace.CURRENT` | `world/gen/carver/RandomTrace.java:11` | 생성 풀 전용 진단 | 무관 |
| 9 | ThreadLocal 날짜 포맷 | `BanListEntry.java:13`, `PlayerLists.java:85` | 순수 캐시 | 무관 |
| 10 | `ThreadLocalRandom` | `FurnaceBlockEntity.java:191`, `PlayerVitals.java:137,177,813`, `Player.java:56`, `ServerCore.java:106` | 시드·바닐라에서도 비결정인 곳 | 무관(스레드가 바뀌어도 결정성은 원래 없음) |
| 11 | 스레드 ID 를 시드에 | `block/RandomSource.java:25` | `nanoTime ^ … * threadId` | 무관 |
| 12 | 스레드 이름 | `Regionizer.java:80-81` (`furnace-region-the_nether-0`) | 로그·진단용, 이름으로 분기하는 코드는 **없다**(`Thread.currentThread()` 사용처 grep 결과 위 3·4·11 뿐) | 이름이 차원을 뜻하지 않게 된다. 로그에서 차원은 region 쪽에서 찍어야 한다 |
| 13 | 락 소유자 | `Regionizer.lock`(40행) — 재진입 잠금, 소유자는 잡은 스레드 | 월드별 | 큐 잠금을 전역으로 올리면 3.4 의 경합 |
| 14 | 플러그인 `FurnaceScheduler.region(RegionPos)` | `FurnaceSchedulerImpl.java:46`, 오버월드만(`World.java:117`) | RegionPos 에 차원 없음 | B안과 독립. 차원 인자는 별도 API 작업 |
| 15 | 규칙 문서 `.claude/rules/scheduler.md` "같은 RegionPos 는 같은 스레드" | 규칙 모델 1-2항 | **이미 코드와 다르다**(동적 region 은 매 틱 다른 스레드) | 문서를 "같은 region 은 동시에 한 스레드" 로 고쳐야 한다(B안과 무관한 기존 부채) |

정리: 스레드에 매인 가정은 사실상 **"작업 하나가 끝날 때까지 한 스레드에서 돈다"** 뿐이고, 이것은 B안에서도 그대로 성립한다.

## 3. 설계안 상세

### 3.1 작업 단위
- 단위는 지금처럼 `Region` 객체 하나의 `runTick`. 키 `(World, Region)` 는 따로 만들 필요 없이 `Region.world` 가 이미 있다(`new Region(id, world)`, 235·535행).
  단 `Region.id` 는 월드별 `++nextId`(235행)라 전역 진단(`tickStats` 307행, 로그)에는 `dimension#id` 를 써야 한다.

### 3.2 상태 전이
지금의 `queued/ticking/yielded/alive` 4개 플래그(잠금 안)가 곧 IDLE/QUEUED/RUNNING(+YIELDED/DEAD) 이다. 처음에는 **그대로 잠금으로** 지킨다.
CAS 로 바꾸는 것(예: `AtomicInteger state`, QUEUED→RUNNING 을 `compareAndSet`)은 큐가 잠금 없는 구조가 될 때만 의미가 있고, 합치기·나누기가
"둘 다 쉬는 중"을 원자적으로 봐야 해서(`resolveMerges` 451행) 상태 하나의 CAS 로는 부족하다. → 1단계에서는 잠금 유지, CAS 는 측정 후 선택.

### 3.3 데드라인 큐 구조
| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| ① 전역 큐 하나 | 지금 `PriorityQueue` + 잠금을 서버 단위로 | 코드 변경 최소, 데드라인 순서가 전역으로 정확 | 잠금 경합(3.4) |
| ② 워커별 큐 + 훔치기 | region 을 워커에 배정, 비면 남의 큐에서 훔침 | 캐시 지역성, 잠금 분산 | 데드라인 순서가 근사가 된다, 합치기·나누기 때 재배정, 코드 큼 |
| ③ 차원별 큐 + 전역 선택기 | 차원마다 지금 큐를 두고, 워커는 "각 차원 큐 머리 중 가장 이른 것"을 고른다 | 구조 잠금(합치기·나누기)은 차원별로 남는다, 공정성 정책을 선택기에 둘 수 있다 | 머리 비교가 O(차원 수) — 12 이하면 무시 가능 |

**권고: ③.** 측정(5장)상 지금 월드 하나에 24 region·워커 4 개를 넣어도 잠금이 병목이 아니었다(p95 0.14ms). 그러나 `split` 은 구역 수에 비례해
잠금 안에서 BFS 를 돈다(509-529행) — 전역 잠금이면 한 차원의 나누기가 모든 차원 스케줄을 멈춘다. ③은 "구조 잠금 = 차원별, 큐 선택 = 전역"으로 이 문제를 피한다.
구현: `Regionizer` 에서 `workers`/`workerLoop`/`takeDue`/`wake` 를 새 `RegionTickPool`(서버 단위)로 옮기고, 각 Regionizer 는 `peekDue()`/`take()`/`afterTick()`
만 남긴다. 워커는 전역 `Condition` 하나에서 잔다. 차원 쪽 `schedule()` 은 자기 큐에 넣은 뒤 풀을 깨운다.

### 3.4 잠금 경합
- 지금 잠금을 잡는 곳: `want`(147행, 청크가 "있음"이 될 때마다), `release`, `ownerForTask`(구역이 없을 때), `merging`, `forEachRegion`, 틱 시작·끝.
  `want` 는 청크 로드마다 불리므로 차원별 잠금으로 남겨야 한다(③). 전역으로 올리면 한 차원의 대량 로드(포털 도착 시 반경 청크)가 다른 차원 틱 시작을 막는다.
- **가정**: 차원 수 12·region 수백까지 전역 선택 잠금(틱 시작·끝만)은 초당 수천 회라 문제 없다. 측정 필요(4장 1단계).

### 3.5 차원 공정성
지금은 "공정성"이 OS 스케줄러에 맡겨져 있다: 차원마다 코어 수만큼 스레드가 같은 우선순위로 경쟁한다(5장 측정이 그 결과). 데드라인 큐 하나면
**EDF(가장 이른 데드라인 먼저)** 자체가 기본 공정성이다 — 모든 region 이 같은 20TPS 주기라 밀린 region 이 자연히 먼저 뽑힌다. 추가 정책은 과부하 때만 필요:
1. 차원별 최소 보장: 한 차원이 계속 늦으면(예: `now - nextTickNanos > 1틱`) 그 차원 머리를 먼저. ③의 선택기에서 비교 키를 `(늦음 > 임계 ? 0 : 1, nextTickNanos)` 로.
2. 차원별 예산(가중): 플레이어 없는 차원·로드 청크만 있는 차원을 낮게. **가정**: 바닐라는 단일 스레드로 모든 차원을 같은 틱에 돌리므로 "차원 간 우선순위"가
   관측 결과(틱 수)를 바꾸면 안 된다 — 가중은 늦을 때 순서만 바꾸고 틱을 건너뛰지 않아야 한다(`TickTimer.advance` 의 재동기는 지금도 틱을 건너뛰지 않고 간격만 늘린다).
3. 1단계에서는 EDF 만 두고, 정책은 5단계에서 계측 후.

### 3.6 틱 지터·PreciseWait 와의 상호작용
- 지금 `takeDue` 는 머리 region 의 남은 시간이 `spinMargin` 이하면 **잠금을 놓고 돈다**(400-411행). 그 사이 다른 워커가 잠금을 잡으면 같은 머리를 보고
  **역시 돈다**. 그래서 거친 타이머 플랫폼(윈도)에서는 한 월드에서 최대 `threads` 개, 서버 전체로 **차원 수 × 코어 수** 개의 스레드가 동시에 돌 수 있다.
  **가정**: 윈도에서 실측하지 않았다(이 환경은 리눅스라 `spinMargin` 이 0 으로 보정된다).
- B안(③)이면 "머리를 기다리며 도는 워커는 1개, 나머지는 `Condition` 대기"로 만들 수 있다(도는 워커를 표시하는 필드 하나). 돌기 비용이 차원 수와 무관해진다.
  이것은 B안의 장점이자, 현행 구조의 숨은 비용이다.

### 3.7 메모리 가시성
- region 상태는 지금도 틱마다 다른 워커에서 이어 돈다. happens-before 는 `workerLoop` 의 잠금이 만든다: 앞 워커가 `afterTick` 전에 잠금을 잡고(372행)
  다음 워커가 `takeDue` 에서 같은 잠금을 잡는다(354행) → 앞 틱의 모든 쓰기가 다음 틱에 보인다.
- B안에서 큐 잠금이 차원 잠금과 다른 객체가 되면 이 사슬이 **반드시 같은 잠금(또는 volatile 쓰기→읽기)** 을 지나야 한다. 규칙: 틱 끝 `ticking=false` 기록과
  다음 틱 시작의 `queued→ticking` 확인이 같은 동기화 객체를 거칠 것. ③에서는 차원 잠금 안에서 둘 다 하므로 자동으로 만족한다.
- 인박스(`ConcurrentLinkedQueue`)는 원래 스레드 안전. `delayed` 는 region 소유라 위 사슬로 충분.

### 3.8 캐시 지역성
- 지금도 친화도가 없다(1.2). B안이 나빠지게 하지는 않는다. 오히려 지금은 코어보다 스레드가 많아(5장, 48 스레드/4 코어) OS 가 스레드를 옮겨 다닌다.
- 개선 여지: 워커가 직전에 돌린 region 을 기억하고, 같은 데드라인 창(예: 1ms) 안이면 그 region 을 먼저 고르는 약한 친화도. 측정 후(5단계).

### 3.9 락·ThreadLocal 캐시의 안전성
- `lightWorld` 캐시(`ChunkLevel.java:1326-`)는 레벨 필드 → region 소유 → 안전.
- 노이즈 생성 thread-local 버퍼는 생성 풀(`ChunkPipeline`) 쪽이라 무관.
- 2장 6·7의 ThreadLocal 은 작업 범위 안에서 set/restore → 안전.
- **가정**: `Region`·`ChunkLevel` 에 static 가변 캐시가 숨어 있지 않다(`private static` 비final 필드를 Region/RegionLevel/ChunkLevel 에서 grep 했을 때 없음). 블록 타입 클래스 전체는 보지 않았다 —
  그러나 static 가변 캐시는 지금도 여러 차원 풀 사이에서 공유되므로 B안이 새 위험을 만들지는 않는다.

## 4. 마이그레이션 계획
| 단계 | 내용 | 위험 | 회귀 테스트 | 롤백 |
|---|---|---|---|---|
| 1. 벤치·계측 | `MultiDimensionTickBench`(이번 커밋) + `furnace.tickstats` 에 차원 표시, 윈도에서 같은 벤치(돌기 스레드 수) | 없음 | — | — |
| 2. 큐 추상화 | `RegionTickPool` 인터페이스: `register(Regionizer)`, `signal()`. 구현 1 = 지금처럼 월드마다 풀(동작 동일) | 리팩터링 실수 | `RegionizerTest`, `RegionTickTest`, `RegionPendingOrderTest`, `EmptyRegionEvictionTest`, 차원 이동 테스트 전체 | 구현 1 유지 |
| 3. 소유권 가드 정리 | 코드 변경 거의 없음. `ThreadOwner` 주석과 `.claude/rules/scheduler.md` 모델 문구 수정, 스레드 이름 대신 region 에 차원 표시 | 문서만 | `OwnedTest` 에 "다른 워커에서 이어 돌아도 가드 참" 추가 | 문서 되돌림 |
| 4. 차원 풀 통합 | 구현 2 = 서버 하나의 풀, 3.3 ③(차원별 큐·잠금 + 전역 선택), 돌기 워커 1개(3.6). 설정 `region-threads` 는 서버 전체 수로 의미 변경 | 데드라인 선택 버그로 한 차원 굶음, 가시성 사슬 끊김 | 새 테스트: 차원 3개·region 여러 개에서 모든 region 의 틱 수가 같은 시간 동안 ±1 안, 차원 이동 왕복 스트레스, 합치기·나누기 중 다른 차원 틱 진행 | 설정 플래그 `furnace.sharedRegionPool=false` 로 구현 1 |
| 5. 공정성 튜닝 | 3.5 정책, 3.8 약한 친화도 — 측정이 필요를 보일 때만 | 과튜닝 | 벤치 비교 | 정책 끄기 |

## 5. 정량 검토

### 5.1 스레드 수 공식
- 지금: region 작업 스레드 = **D × T**(D = 차원 수, T = `region-threads`, 기본 코어 수 C). 여기에 global 1, 청크 워커 C(`FurnaceMain.java:162`),
  생성 대기 max(2,C)(188행), IO `io-threads`(기본 4), Netty. 틱 우선순위 스레드만 D×C 개.
- B안: region 작업 스레드 = **T**(차원 수와 무관).

| D(차원) | 지금 region 스레드(C=4) | 지금(C=16) | B안 |
|---|---|---|---|
| 3 | 12 | 48 | 4 / 16 |
| 6 | 24 | 96 | 4 / 16 |
| 12 | 48 | 192 | 4 / 16 |

- 유휴 때: 쉬는 워커는 `wake.await(50ms)`(390행)로 잠들어 비용이 작다. 다만 50ms 마다 깨어나므로 D×C×20 회/초의 깨어남이 있다(D=12, C=16 → 3840 회/초. **가정**: 무시할 만함).
  윈도에서는 3.6 의 돌기가 D 에 비례 — 유휴라도 머리 region 이 있는 차원마다 돈다.
- 부하 때: 모든 틱 스레드가 `MAX_PRIORITY-1` 이라 D×C 개가 C 코어를 두고 OS 시간 조각으로 경쟁 → 데드라인과 무관한 순서로 실행되어 지터가 커진다(아래 측정).

### 5.2 측정 (이번 커밋의 `MultiDimensionTickBench`)
환경: 이 샌드박스(리눅스, 코어 4, `spinMargin` = 0). 월드 = 평지, 게임 틱 끔(작업·지연 작업만 — "빈 월드"), 월드마다 서로 먼 region 2개.
각 region 에 매 틱 다시 거는 지연 작업을 두고 그 실행 간격 − 50ms 를 잰다(틱당 100회, 첫 5회 제외). 부하 = 매 틱 바쁜 대기 5ms.
명령: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test --tests '*MultiDimensionTickBench*' -Dfurnace.bench=true [-Dfurnace.bench.dims=1 -Dfurnace.bench.regions=24]`

**현행(차원마다 풀, 풀당 4 스레드):**
| D | region 수 | 스레드 | 부하 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| 3 | 6 | 12 | 0 | 0.000ms | 0.045 | 0.071 | 0.298 |
| 3 | 6 | 12 | 5ms | 0.001 | 3.180 | 4.887 | 5.251 |
| 6 | 12 | 24 | 0 | -0.001 | 0.102 | 0.324 | 1.552 |
| 6 | 12 | 24 | 5ms | -0.003 | 6.422 | 10.021 | 12.514 |
| 12 | 24 | 48 | 0 | -0.001 | 0.153 | 0.308 | 0.823 |
| 12 | 24 | 48 | 5ms | 0.003 | **11.663** | 17.693 | 23.005 |

**B안 근사(같은 region 수를 월드 하나 = 공유 데드라인 큐 하나, 스레드 4):**
| region 수 | 스레드 | 부하 | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|
| 6 | 4 | 5ms | 0.002ms | 0.084 | 0.659 | 3.224 |
| 12 | 4 | 5ms | 0.002 | 0.116 | 1.447 | 4.964 |
| 24 | 4 | 5ms | 0.002 | **0.144** | 1.968 | 4.957 |

해석:
- 총 CPU 수요가 같은데(24 region × 5ms = 틱당 120ms / 코어 4 × 50ms = 60%) 현행은 p95 지연이 D 에 비례해 커지고(3.2 → 6.4 → 11.7ms), 공유 큐는 region 수가 늘어도 0.1ms 대에 머문다.
  원인은 5.1 의 과다 구독(48 스레드/4 코어)과 OS 스케줄러가 데드라인을 모른다는 것.
- 유휴 때는 둘 다 1ms 이하 — 커스텀 차원이 "비어 있으면" 현행도 문제가 거의 없다.
- 한계: "B안 근사"는 실제 B안이 아니라 지금 코드의 단일 월드 경로다. 차원 간 선택기(③)·잠금 분리 비용은 측정에 없다. 게임 틱을 끈 측정이라 실제 청크·엔티티 부하가 아니다.
  윈도 타이머(3.6)는 측정하지 못했다. 코어 16 이상 기계에서 측정하지 못했다.

## 6. 결론

### 6.1 비교
| | 현행 유지 | A안(region 그룹을 스레드에 이주·고정) | **B안(공유 데드라인 큐)** |
|---|---|---|---|
| 스레드 수 | D×C | C (그룹 고정) | C |
| 부하 시 지터(측정) | D=12 에서 p95 11.7ms | 측정 안 함. 그룹 안 불균형 시 커짐(**가정**) | 공유 큐 근사 p95 0.14ms |
| 소유권 모델 변경 | 없음 | 큼 — region 마다 "담당 스레드"를 도입해야 하고 합치기·나누기 때 이주 프로토콜이 필요(지금 코드에 없는 개념) | **없음** — 지금 모델(스레드≠소유자) 그대로 |
| 차원 공정성 | OS 맡김 | 이주 정책에 달림 | EDF 기본 + 선택 정책 |
| 윈도 돌기 비용 | D 에 비례 | C | 1 워커로 줄일 수 있음 |
| 작업 규모 | 0 | 큼(가정: 10+ 커밋) | 작음~중간 |

A안은 "스레드 == 소유자" 쪽으로 **되돌아가는** 설계라 이 코드베이스에는 맞지 않는다. 이미 region 이 스레드 사이를 옮겨 다니는 구조이기 때문이다.

### 6.2 권고
**B안 채택(3.3 ③: 차원별 큐·구조 잠금 + 전역 선택 워커 풀).** 현행 유지도 차원 3개·유휴 위주라면 당장 문제는 없지만, 커스텀 차원을 늘리거나 부하가 있으면
스레드 수와 지터가 차원 수에 비례해 나빠지는 것이 측정으로 보였다.

### 6.3 예상 작업 규모(**가정**, 코드 읽기 기준)
- 커밋 4~6개(4장 2~5단계 + 테스트), 파일 6~9개: `Regionizer.java`(워커·`takeDue` 분리, 대략 100~150줄 이동), 새 `RegionTickPool.java`, `World.java`(생성자 115-119행 — 풀 주입),
  `FurnaceMain.java`(196·277행 — 풀 하나 만들어 전달), `FurnaceSchedulerImpl.java`(close 훅), `Config`(의미 변경 주석), 테스트 2~3개, 규칙·설계 문서.
- 플러그인 API(`furnace-api`) 변경 없음(주석만).

### 6.4 선행 작업
1. 윈도에서 이 벤치와 `furnace.tickstats` 로 돌기 스레드 수·지터 측정(3.6 가정 확인).
2. 게임 틱을 켠 실제 부하(`RegionTickBench` 노이즈 지형 + 차원 여러 개)로 같은 비교.
3. `.claude/rules/scheduler.md` 모델 문구를 지금 코드에 맞게 고치기(2장 15 — B안과 무관하게 이미 틀림).
4. `ThreadOwner` 계약 주석을 "지금 이 owner 를 실행 중인 스레드" 로 명확히(하위 호환).
