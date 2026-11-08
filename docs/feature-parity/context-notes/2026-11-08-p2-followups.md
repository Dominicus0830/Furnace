## 2026-11-08 비동기 스케줄러 후속 — 관리 스레드·getAsyncScheduler 풀·핫 리로드 대기 (P2 후속)

기준: Paper 1.21.1 패치 `Improved-Async-Task-Scheduler`(CraftAsyncScheduler), `Folia-scheduler-and-owned-region-API`(FoliaAsyncScheduler) — 원문 읽기, 코드는 옮기지 않음.

**1. 비동기 대기열 관리 스레드 (`LegacyScheduler`)**
- 비동기 작업은 동기와 따로 PriorityQueue(`asyncQueue`)를 가진다. 박동(전역 스레드)은 "이번 틱" 메시지를 단일 스레드 실행기 `Craft Async Scheduler Management Thread`(데몬, 첫 박동에서 뜬다)에 보내기만 하고, 대기열을 비우고 풀로 넘기는 일·반복 작업 재삽입은 그 스레드가 한다. 전역 스레드는 비동기 작업의 정리·스레드 만들기를 더 이상 안 한다.
- Paper 의 `mainThreadHeartbeat` 순서대로 비동기 메시지를 동기 대기열 비우기보다 먼저 보낸다. 그래서 같은 틱의 동기 작업이 건 비동기 작업(지연 0)은 **다음 틱**에 돈다(전에는 같은 틱 안에서 이어서 돌았다 — Paper 와 같아졌다).
- `close()` 는 관리 스레드도 `shutdown()`(이미 받은 틱은 마저 비우고, 닫힌 풀에 넘기려던 작업은 취소). 취소는 Paper 처럼 `cancelTask` 가 관리 스레드를 거치지 않고 표시를 바로 세운다(관찰 가능한 동작이 더 엄격할 뿐 — 취소 직후 `isCancelled()` 가 true).

**2. Paper API `getAsyncScheduler()` 풀 (`PaperSchedulers`)**
- 전에는 서버의 가상 스레드 실행기였다. 이제 Paper 1.21.1 FoliaAsyncScheduler 모양의 **별도** 플랫폼 풀이다: 코어 `max(4, 프로세서/2)`·최대 무제한·30초·`SynchronousQueue`, 이름 `Folia Async Scheduler Thread #N`(0부터), 우선순위 NORM-1, 시간 재는 스레드 `Folia Async Scheduler Thread Timer`(우선순위 NORM+1), 잡히지 않은 예외는 서버 로그 `Uncaught exception in thread: 이름`. 코어 스레드는 놀아도 안 정리하고 미리 띄우지 않는다(Paper 와 같다). 데몬은 Furnace 만 다르다(Paper 는 비데몬).
- Bukkit 쪽 풀(`Craft Scheduler Thread - N`)과 **같은 풀이 아니다** — Paper 도 둘이 별개라서 스레드 덤프에서 보이는 이름이 같아야 하기 때문. 옵션 `plugins.compat.scheduler.async-threads=virtual` 이면 둘 다 서버의 가상 스레드 실행기를 쓴다.
- `close()` 는 타이머를 닫고 풀을 `shutdown()`(인터럽트 없음). Paper 는 FoliaAsyncScheduler 를 종료 때 기다리지 않으므로 기다림도 넣지 않았다.

**3. 개발 모드 핫 리로드가 비동기 작업을 기다림**
- 새 `PluginFormat#awaitPluginTasks(Furnace, Collection<FurnacePlugin>, long)`(기본 아무것도 안 함). `PluginManagerImpl.reloadNow` 가 대상+의존 묶음을 끈 직후·다시 읽기 전에 이미 만든 어댑터에게 부른다. `CompatFormat` → `Compat.awaitPluginAsyncTasks` → `LegacyScheduler.awaitAsyncWorkers(플러그인들, 5000)`: 종료와 같은 규칙(100ms 마다, 최대 5초, 남으면 Nag, 인터럽트는 삼키고 표시를 다시 세우지 않음). 풀은 닫지 않는다. 일꾼이 없으면 기다리지 않는다.
- **한계**: 전역 스레드에서 도므로 그동안 틱이 멈춘다. 일꾼이 `callSyncMethod(...).get()` 으로 전역을 기다리면 최대 5초 서로 기다리다 풀린다(개발 모드 한정, 종료 때 Paper 도 같다). 대기 시간은 `PluginManagerImpl.asyncWaitMillis`(시험이 줄임).
- 시험 하네스: `FurnaceTestServer.Builder#devMode(boolean)` 추가.
