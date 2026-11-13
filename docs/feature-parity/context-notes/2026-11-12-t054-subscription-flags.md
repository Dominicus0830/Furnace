# T-054 이벤트 구독 확인을 필드 읽기로 (2026-11-12)

- **무엇**: `PluginGameEvents` 의 고정 이벤트 입구(64종)가 `bus().hasSubscribers(X.class)`(`baked` ConcurrentHashMap 조회) 대신 `EventBusImpl.Flag`(volatile boolean 하나)를 읽는다. 필드는 `PluginGameEvents` 생성 때 `bus.flag(X.class)` 로 한 번 받는다. 이벤트 종류가 변수인 입구(`has(type)`, 일부 `type` 인자 입구)와 `ConnectionHandler`·`ServerCore` 의 직접 `hasSubscribers` 호출은 그대로다(드문 경로).
- **값은 같다**: 필드 = `bake(type).length > 0` = `hasSubscribers(type)`(상위 클래스·인터페이스 구독 포함, 하위 타입 구독은 불포함). 등록·해제·`unregisterAll`(핫 리로드)이 `baked.clear()` 와 같은 자리에서 `refreshFlags()` 를 불러 다시 계산한다 — 그 호출이 돌아온 뒤의 첫 읽기부터 반영.
- **동시 변경**: 재계산은 `flagLock` 안에서 byType 을 새로 읽는다. 두 스레드가 동시에 등록해도 나중에 도는 재계산이 둘 다 보므로 낡은 값이 남지 않는다(`wantsCause` 도 같은 잠금으로 옮김 — 이전엔 잠금 없이 계산해 같은 경합이 이론상 있었다).
- **이웃 갱신**: `GameEvents.wantsBlockPhysics()`(기본 참 — `blockPhysics` 를 직접 구현한 시험 구현도 그대로 불린다, 서버 구현은 필드) 를 `NeighborUpdater.executeUpdate/executeShapeUpdate` 가 먼저 묻는다. 구독자가 없으면 `B.block(neighborState)`·`Pos` 전달·`level.dimension()` 호출까지 건너뛴다.
- **근거**: O12-06(`hasSubscribers` 3.2~6.1ns vs 필드 0.2~0.5ns), 반론 B(실서버 입구 모양으로 처리기 0개에서도 레드스톤 장면 평균 약 +5%, p95 약 +11%). 이 PR 은 실서버 입구로 다시 재지는 않았다(O15 RegionTickBench 정정 후 측정 TASK).
- **함정**: `PluginGameEvents` 의 필드는 생성자에서 `host.eventsImpl()` 로 받는다(필드 초기화식에서 `host` 는 아직 null). 새 고정 이벤트 입구를 추가할 때는 같은 방식으로 필드를 하나 더 만든다(#276 이 더한 `BlockPlace`·`PlayerDropItem` 등 입구는 병합 뒤 따라서 바꾸면 된다 — 지금은 `hasSubscribers` 를 그대로 써도 값이 같다).
