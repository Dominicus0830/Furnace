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

---

## 7. 최종 재검증 (2026-10-27)

앞 장의 결론을 믿지 않고 코드를 다시 읽어 반박을 시도한 결과다. 줄 번호는 `838a8fb4` 기준. 판정은 CONFIRMED(코드로 확인) / WRONG(틀림) / UNVERIFIED(코드로 판가름 못 함 — 설계·측정 필요).

### 7.1 문서 주장별 판정

| 장 | 주장 | 판정 | 근거 |
|---|---|---|---|
| 1.1 | `RegionThread` 는 월드 없는 대체 경로, 서버 실행에는 안 쓴다 | **CONFIRMED** | `FurnaceSchedulerImpl.java:46-51`(라우터 있으면 라우터), `World.java:117`(오버월드가 라우터 설치). `fallback()` 71-84 는 라우터 없을 때만 |
| 1.2 | 월드마다 Regionizer·작업 스레드 D×T, 같은 설정값 | **CONFIRMED** | `World.java:115`, `FurnaceMain.java:153,196,277` 모두 `config.regionThreads()` |
| 1.2 | 큐는 데드라인 큐, 잠금 하나, region 단일 실행은 `ticking/queued` 를 잠금 안에서만 바꿔 보장 | **CONFIRMED** | `Regionizer.java:40-47`, `workerLoop` 351-383, `takeDue` 393-397·415-417, `schedule` 437-442 |
| 1.2 | region 은 매 틱 다른 스레드에서 돌 수 있다(친화도 없음) | **CONFIRMED** | `takeDue` 는 아무 워커가 머리를 가져간다. 배정 코드 없음 |
| 1.3 | `isOwnerThread` = "지금 이 region 을 돌리는 스레드" | **CONFIRMED** | `Region.java:59,93`; 설정·해제는 잠금 안(`Regionizer.java:358-359, 374-375`) |
| 1.3 | `lightWorld` 캐시는 레벨 인스턴스 필드 | **CONFIRMED**(가정 유지) | 1.3 서술대로. 이번에 다시 열지 않음 |
| 1.4 | 합치기·나누기에 스레드 배정이 없다 | **CONFIRMED** | `absorb` 464-480, `split` 483-567 은 큐 항목만 바꾼다 |
| 1.5 | 차원 이동은 메시지 전달만 쓴다, 스레드 풀 수와 무관 | **CONFIRMED** | 플레이어: `Region.java:1323-1331` → `World.java:879-887` → `completeTravel` 905-921(IO 레인·`submitAtChunk`) → `finishTravel` 927-944(`runAtChunk`, 1틱 재예약) → `Player.changeDimension` 904-909(연결 스레드로) → `applyDimensionChange`(`ConnectionHandler.java:2257`). 엔티티: `Entity.java:422-453`(`travel.travel` 콜백은 목적 region 작업 안에서 돈다). 리스폰: `ConnectionHandler.java:2214`(`from.forEachRegion`) |
| 2-#6,7 | `MenuBlockContext`·`RedStoneWireBlock` ThreadLocal 은 작업 범위 set/restore | **CONFIRMED** | `MenuBlockContext.java:12-21`(finally 복원), `RedStoneWireBlock.java:161-169`(finally 복원 — 2장에서 "확인 안 함"이라 한 예외 경로도 안전) |
| 2-#12 | 스레드 이름으로 분기하는 코드 없음 | **CONFIRMED** | `Thread.currentThread()` 사용처: `GlobalThread.java:37`, `RegionThread.java:43`, `Regionizer.java:359`, `Region.java:93`, `World.java:691,704`(메시지), `RandomSource` 시드. 테스트 중 이름을 세는 것은 `MultiDimensionTickBench.java:40,50`(벤치, `furnace-region` 접두사만)과 `FurnaceSchedulerImplTest.java:57`(fallback 경로) |
| 2-#14 | 플러그인 `region(RegionPos)` 는 오버월드만, B안과 독립 | **CONFIRMED** | `World.java:97-98,117-118`. 네더·엔드는 `addCloseHook` 만 |
| 2-#15 | `.claude/rules/scheduler.md` 모델 문구가 코드와 다르다 | **CONFIRMED** | 규칙 "같은 RegionPos → 항상 같은 스레드" vs 동적 region |
| 3.4 | 잠금 잡는 곳 목록 | **CONFIRMED + 누락** | 목록(`want`147, `release`173, `ownerForTask`135, `merging`163, `forEachRegion`263, 틱 시작·끝)에 더해 **`merging()` 은 게임 틱 안에서 플레이어마다(`Region.java:710`)·엔티티마다(`:846`) 주인이 다를 때 불린다.** 차원별 잠금으로 남겨야 할 이유가 하나 더 |
| 3.6 | spinMargin > 0 이면 여러 워커가 같은 머리를 보고 함께 돈다 | **CONFIRMED** | `takeDue` 400-411: 도는 워커 표시가 없다. 돌고 나서 `return null` → 다른 워커도 같은 머리를 본다 |
| 3.6 | B안이면 도는 워커를 1개로 줄일 수 있다 | **UNVERIFIED** | 설계상 가능하나 코드에 없다. "머리 region 이 바뀌면(더 이른 region 이 `schedule` 로 들어오면) 도는 워커를 깨워 다시 고르게" 하는 처리가 필요 — 지금 `schedule` 의 `signalAll` 은 Condition 대기만 깨우고 스핀 중인 스레드는 못 깨운다. 스핀은 `PreciseWait.parkUntil`(`PreciseWait.java:45-47`) 안이라 중단 지점이 없다 |
| 3.7 | happens-before 는 workerLoop 의 잠금 사슬 | **CONFIRMED** | 앞 워커 `lock()`(372) → `unlock()`(380); 다음 워커 `lock()`(354) → `takeDue` 가 그 region 을 내준다. 같은 `ReentrantLock` 이라 성립. `tickThread` 도 volatile(`Region.java:59`) |
| 3.7 | ③에서는 차원 잠금 안에서 둘 다 하므로 자동 만족 | **CONFIRMED(조건부)** | "꺼내기(queued→ticking)" 와 "끝내기(ticking=false, afterTick)" 를 **같은 차원 잠금** 안에서 하는 한 성립. 전역 선택기가 차원 잠금 **밖**에서 머리를 미리 보고(peek) 꺼내기만 잠금 안에서 하면 역시 성립. 꺼내기를 잠금 밖 CAS 로 바꾸면 깨진다(3.2 가 "1단계 잠금 유지"라 한 것과 일치) |
| 5.1 | 유휴 깨어남 D×C×20/초 | **CONFIRMED** | `takeDue` 390 `await(50ms)` |
| 6.3 | 파일 6~9개, 커밋 4~6개 | **WRONG(과소)** — 7.4 참고 | `new World(...)` 호출이 테스트·도구에 **38 파일 48 곳**(`int regionThreads` 인자). 생성자 서명을 바꾸면 모두 따라간다. 서명을 유지하고 "int 를 주면 자기 풀을 만든다"(구현 1) 로 두면 테스트는 건드리지 않지만, 그러면 실서버 경로와 테스트 경로가 갈라져 4단계의 새 테스트가 반드시 공유 풀 경로를 직접 만들어야 한다 |

### 7.2 문서가 놓친 위험 (B안이 새로 만들거나 넓히는 것)

**R1. 전역 깨우기 ↔ 차원 잠금의 순서(③ 설계의 교착 가능성).** `schedule()` 은 차원 잠금 안에서 불린다 — `newRegion`(238, `want`/`ownerForTask` 경로 135-141·147-160), `split`(565), `afterTick`(434). ③에서 깨우기가 "전역 Condition" 이면 그 Condition 의 전역 잠금을 **차원 잠금을 쥔 채** 잡는다(차원 → 전역). 반면 워커가 "각 차원 머리 중 가장 이른 것"을 고르려면 전역 잠금을 쥐고 차원 잠금을 잡거나(전역 → 차원) 차원 머리를 잠금 없이 읽어야 한다. 두 순서가 공존하면 교착. 해결은 셋 중 하나이고 모두 설계에 추가돼야 한다: (a) 깨우기를 잠금 없는 원시(`LockSupport.unpark`/`Semaphore`)로, (b) 차원 쪽이 `volatile long headDue` 를 잠금 안에서 갱신하고 선택기는 그것만 읽은 뒤 꺼내기만 차원 잠금으로(꺼내기 실패하면 다시 고른다), (c) 깨우기를 차원 잠금을 놓은 뒤로 미룬다(`schedule` 호출자가 여러 곳이라 누락 위험). 문서 3.3 ③ 의 "워커는 전역 Condition 하나에서 잔다, 차원 schedule 은 자기 큐에 넣은 뒤 풀을 깨운다" 는 이 순서를 정하지 않았다. **지금 코드에는 이 문제가 없다**(잠금 하나).

**R2. 블로킹이 차원 격리를 잃는다.** 지금 한 차원의 워커가 막혀도 다른 차원의 T 개는 산다. B안은 공유 T 개라 막힌 워커 수만큼 모든 차원이 느려진다. 막힐 수 있는 현존 경로: `Region.chunkAt`(`Region.java:221-238`) 의 **동기 생성·디스크 읽기** — 주석은 "테스트·도구용"이지만 `getBlock`(402-405), `chestAt`(418-421), `setChestItem`(424-433), `markBlockEntityChanged`(436-443), `markChestChanged`(446-453), `breakBlock`(369-378), `placeBlock`(472-483) 이 모두 `chunkAt` 을 부르고 이들은 운영 경로(상자·블록 상호작용)에서 불린다. 규칙 6("region 스레드에서 블로킹 IO 금지") 위반이 이미 있고, B안은 그 피해 범위를 서버 전체로 넓힌다. `/save-all` 의 `f.join()`(`FurnaceMain.java:305`)은 글로벌 스레드에서 돈다(`ConnectionHandler.java:293` 명령은 `global().execute`)라 region 워커를 막지는 않는다.

**R3. EDF 의 "밀린 차원 우선"은 양날이다.** `afterTick` 433 은 밀린 region 을 `now + tickNanos` 로 재동기한다. 차원 A 의 region 이 틱마다 100ms 걸리면 매번 데드라인을 지나 있고, 재동기 뒤에도 50ms 뒤가 데드라인이라 다음 선택 때 다른 차원의 "지금 막 때가 된" region 과 동률이 된다. 결과: A 가 워커 T 개 중 (A 의 region 수) 개를 계속 점유하고, 나머지 차원은 남은 워커로 돈다. A 의 region 수 ≥ T 이면 **다른 차원은 A 의 틱이 끝나는 틈에만** 돈다. 지금 구조에서는 A 의 과부하가 다른 차원 스레드를 뺏지 못한다(CPU 경쟁만). 3.5 의 "최소 보장" 정책이 1단계에서 빠진 채 4단계로 가면 이 회귀가 생긴다 — **4단계와 5단계 1항을 분리하면 안 된다.**

**R4. `region-threads` 의미 변경은 운영 회귀다.** 기본값(코어 수)으로 지금은 region 스레드가 3C, B안은 C. 코어 4 기계에서 네더·엔드에 플레이어가 흩어져 region 이 5개 이상이면 지금은 12 스레드가 다 돌고 B안은 4개가 번갈아 돈다. 5.2 측정은 "CPU 수요 60%" 조건이었다. 수요 > 100% 구간(지금은 OS 가 시간 조각으로 공평하게 나눔, B안은 EDF 가 늦은 것부터)에서의 비교가 없다. 설정 주석만 바꾸면 안 되고 **릴리스 노트에 "기존 설정값 × 차원 수" 로 환산하라고 적거나 기본값을 올려야** 한다.

**R5. 종료·`close()` 경로.** 지금은 월드마다 `regionizer::close` 가 `FurnaceSchedulerImpl.closeHooks` 로 등록되고(`World.java:117-118`) `scheduler.close()`(`FurnaceMain.java:354`)가 차례로 부른다. 각 `close()` 는 `running=false` 뒤 워커를 1초 join(`Regionizer.java:625-640`). 공유 풀이면 **첫 월드의 close 가 모든 차원의 워커를 멈춘다** — 나머지 월드의 flush 는 이미 `FurnaceMain.java:326-335` 에서 끝난 뒤라 실서버는 괜찮지만, 테스트 55곳이 `regionizer().close()`/`sched.close()` 를 월드 단위로 부른다. 풀을 서버 단위로 올리면 "마지막 월드가 닫힐 때" 또는 스케줄러 close 때만 멈추게 참조 계수가 필요하다.

**R6. 틱 계측(`tickstats`) 이동.** `recordTick` 의 `tickStats` 맵은 Regionizer 별이고 키가 `r.id`(`Regionizer.java:307,312`)라 공유 풀로 옮기면 차원끼리 id 가 겹친다(3.1 이 지적). 추가로 `lastInstall`(309)·`Region.INSTALL_STATS`(`Region.java:285`, static 배열, 비원자 `++`/`+=`) 은 지금도 여러 워커·여러 차원이 잠금 없이 쓰는 경합이다. B안과 무관한 기존 부채지만 계측을 풀로 옮기는 1단계에서 같이 고쳐야 한다.

**R7. 차원 간 직접 접근(B안과 무관, 기존 경합).** `Region.adoptAfterDimensionChange`(`Region.java:1360-1368`) 는 목적 region 스레드에서 **다른 월드의** `updateSleepingPlayerList()` 를 직접 부른다. `World.java:548-551` 은 `playersHere()`(volatile 배열 읽기, 안전) → `sleepStatus.update(...)`. `SleepStatus` 의 일부 메서드는 `synchronized`(`SleepStatus.java:14,17,24`)이지만 `update` 가 그런지는 확인하지 않았다(**가정**). 여기 외에 Regionizer 잠금을 쥔 채 다른 월드를 부르는 경로는 없다(`Regionizer` 는 다른 월드를 참조하지 않는다) — **차원 간 잠금 교착은 현재 없다**(B안 ③의 R1 은 새로 생기는 것).

### 7.3 질문 항목별 결론
1. **불변식**: `isOwnerThread`·`tickThread` 설정/해제는 잠금 안이라 통합 워커가 차원을 번갈아 돌아도 깨지지 않는다(CONFIRMED). 구조 락 순서는 지금 문제 없고(잠금 하나) ③에서 R1 이 새로 생긴다. 차원 간 이동 메시지는 큐 순서에 의존하지 않는다(매 단계 "지금 주인"을 다시 찾고 안 모였으면 1틱 뒤 재시도).
2. **메모리 모델**: 잠금 사슬로 충분(CONFIRMED). 비동기 완료(`loadChunk` 의 `whenComplete` → `runAtChunk`, `Region.java:280`)는 모두 인박스(`ConcurrentLinkedQueue`)를 거쳐 region 틱 안에서 돈다 — 콜백이 region 상태를 직접 만지는 곳은 못 찾았다. ③는 "꺼내기·끝내기를 같은 차원 잠금에서" 를 지키면 된다.
3. **기아**: R3·R2 가 핵심. 종료는 R5. `region-threads` 는 R4.
4. **PreciseWait**: 스핀 워커 1개 축소는 가능하지만 깨우기 설계가 추가로 필요(UNVERIFIED). 리눅스에서는 spinMargin=0 이라 영향 없음.
5. **놓친 영역**: 플러그인 라우팅은 오버월드만이라 무관(CONFIRMED). 글로벌 스레드는 명령·시계·날씨·잠(`ServerCore.java:311-316`)만 돌리고 region 풀과 독립 — B안 무관. 테스트는 스레드 이름·수에 의존하지 않지만 **생성자 호출 48곳·close 호출 55곳**이 월드 단위 수명에 의존한다. `want` 경로의 잠금은 차원별로 남기면 된다(③ 그대로, `merging` 추가).

### 7.4 수정된 작업 규모 (**가정**, 코드 읽기 기준)
- 커밋 **7~10개**, 파일 **10~14개 + (서명을 바꾸면) 테스트 38개**:
  `Regionizer.java`(워커 분리, `headDue` 발행, 계측 키), 새 `RegionTickPool.java`(선택기·깨우기 원시·참조 계수 close·스핀 워커 1개), `Region.java`(`INSTALL_STATS` 정리), `World.java`(풀 주입 + int 호환 생성자), `FurnaceMain.java`, `FurnaceSchedulerImpl.java`(close 순서), `Config`/`ConfigLoader`(의미·기본값), 벤치 1, 새 테스트 3(차원 3개 공정성·합치기 중 다른 차원 진행·R3 과부하), 규칙 문서, `ThreadOwner` 주석.
- 6.3 의 "4~6 커밋·6~9 파일"은 R1(깨우기 설계)·R3(공정성 정책을 1단계에 포함)·R5(close 참조 계수)·테스트 호출 수를 빼고 센 것이다.

### 7.5 최종 권고: **조건부 채택**
B안의 기본 논거(소유자 = 돌리는 스레드, 큐 밖으로 빼기만 하면 된다, 메시지 기반 차원 이동)는 모두 코드로 확인됐고 반박되지 않았다. 그러나 "작은 작업" 이라는 규모 평가와 "1단계는 EDF 만" 이라는 순서는 틀렸다. 채택 조건:
1. ③ 설계에 **깨우기/잠금 순서(R1)** 를 명시하고 선택지 (a) 또는 (b) 로 정한다.
2. **차원별 최소 보장(3.5-1항) 을 4단계 통합과 같은 커밋**에 넣고, R3 시나리오(한 차원 region 수 ≥ T, 틱 100ms) 벤치를 선행한다 — 현행보다 다른 차원 p95 가 나빠지면 채택 보류.
3. `region-threads` 기본값·의미 변경을 릴리스 노트에 적는다(R4).
4. `Region.chunkAt` 동기 생성 경로를 운영 경로에서 떼어 내는 작업(규칙 6 위반)을 **B안보다 먼저** 한다. 안 하면 B안이 그 피해를 차원 전체로 넓힌다(R2).
5. 풀 수명을 참조 계수로 하고 월드 단위 `close()` 호환을 유지한다(R5).
6. `MultiDimensionTickBench` 를 R3 조건으로 확장해 4단계 전후를 같은 조건으로 잰다.

5·6 조건이 만족되지 않으면 **보류**가 맞다. 지금 측정(5.2)은 B안이 좋아지는 조건만 재고 나빠질 수 있는 조건(과부하·블로킹)은 재지 않았다.
