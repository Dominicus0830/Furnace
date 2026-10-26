---
paths:
  - "furnace-server/src/main/java/org/domi/furnace/server/sched/**"
  - "furnace-api/**"
  - "furnace-server/src/test/java/org/domi/furnace/server/sched/**"
---
# 스케줄러·스레드 소유권 규칙

## 모델
- `GlobalThread` 1개 + `RegionThread` N개. 각 스레드가 20 TPS 틱 루프를 돌며 자기 inbox를 매 틱 드레인한다.
- `FurnaceScheduler.region(RegionPos)`는 같은 `RegionPos`를 항상 같은 스레드로 라우팅한다(결정적 매핑). 여러 `RegionPos`가 한 스레드를 공유할 수 있다. 절대 null이 아니다.
- region 상태(청크, 엔티티, 플레이어)는 **소유 region 스레드에서만** 읽고 쓴다. 다른 스레드에서는 `TaskScheduler`로 작업을 넘긴다.
- `Owned<T>.get()`은 소유 스레드가 아니면 AssertionError(assertion 활성 빌드)를 낸다. 운영 빌드에서는 가드가 사라지므로 **테스트로 위반을 잡는다.** 가드를 우회하려고 `Owned` 밖으로 값을 빼 두지 않는다.

## 규칙
1. 락을 늘리기 전에 소유권 이전(메시지 전달)으로 해결할 수 있는지 먼저 본다. 이 서버의 설계 원칙이다.
2. `RegionThread` 생성자에서 모든 필드를 `start()` **이전에** 할당한다. `Thread.start()`의 happens-before에 기대고 있어, 순서를 바꾸면 가시성 버그가 생긴다.
3. `delayed` 큐 등 region 스레드 전용 자료구조는 동기화하지 않는다. 다른 스레드가 만지게 되면 설계가 틀린 것이다.
4. 교차 region 이동·가시성은 핸드오프로 처리한다(지연이 길면 깜빡임, `docs/feature-parity/context-notes.md` F7). 로컬 유예 틱 방식을 되돌리지 않는다.
5. 틱 예산을 쓰는 곳(인박스 드레인, 이동 예산, 패킷 속도)은 한도를 유지한다. 한도를 없애 성능을 맞추지 않는다.
6. 청크 생성·IO는 별도 풀(`ChunkWorkerPool`, IO 풀 단일 경로)이다. region 스레드에서 블로킹 IO·`future.get()`을 하지 않는다(재접속 스폰청크 타임아웃 버그 전례).
7. 플레이어 저장은 IO 풀 단일 경로로 원자적·순서 보장이다. 새 저장 경로를 만들지 않는다.
8. 동시성 변경은 `SchedulerTickHookTest`류 테스트와 `OwnedTest`를 확장해 같이 검증한다.
9. `furnace-api`는 서버 구현에 의존하면 안 된다(플러그인이 쓰는 공개 표면). 변경은 하위 호환을 먼저 본다.
