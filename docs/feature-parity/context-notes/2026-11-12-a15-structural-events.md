# A15-04·A15-07 — 새 청크 표시, 같은 우선순위 처리기 순서 (2026-11-12)

## A15-04 `ChunkLoadEvent#isNewChunk`
- `ChunkColumn.newlyCreated`: 생성기가 이번에 만든 청크일 때만 참(저장소가 없는 월드 소스의 모든 청크, Anvil 에서 디스크에 없어 생성한 청크). 디스크에서 읽은 청크는 거짓. `Region.finishLoad` 가 설치 때 한 번 읽고 내린 뒤 서버 `ChunkLoadEvent.newChunk()` 로 알리고, 호환 `ChunkLoadEvent#isNewChunk` 가 그대로 돌려준다. 청크 재생성(W3)은 이벤트를 부르지 않으므로 영향 없다.
- 판정에 `freshlyGenerated`(평지 생성기는 서지 않는다)나 `dirty()`(디스크 청크도 설치 중 바뀔 수 있다)를 쓰지 않고 새 필드를 둔 까닭이 이것이다.
- **`BlockBreakEvent#getExpToDrop/setExpToDrop/setDropItems` 는 여전히 던진다.** 서버의 블록 파괴 이벤트가 드롭·경험치 파이프라인(`spawnAfterBreak`, 광석 경험치는 파괴 시점에 난수로 정해진다)을 노출하지 않는다 — 거짓 값을 돌려주는 것보다 `UnsupportedFeature` 가 낫다는 보고서 권고를 따랐다. 서버가 파괴 이벤트에 드롭·경험치를 싣는 작업(Paper 는 이벤트를 부르기 전에 경험치를 정한다)이 먼저다.

## A15-07 같은 우선순위 처리기 순서
- 옛 다리는 (플러그인, 우선순위) 마다 구독 하나를 두고 그 플러그인의 그 우선순위 처리기를 한꺼번에 불렀다. 그래서 플러그인 A 가 B 보다 먼저 켜진 뒤 처리기를 더 달면 Paper 는 A1→B1→A2, Furnace 는 A1→A2→B1 이었다.
- 이제 **처리기(RegisteredListener)마다 Furnace 버스 구독 하나**를 단다(`SimplePluginManager.registerEvent` 가 등록 직후 `EventBridge.ensure(…, registered)`). 버스는 (우선순위, 구독 순서)로 부르고 구독은 처리기를 등록하는 그 자리에서 하므로 Bukkit `HandlerList` 의 등록 순서와 같다. 처리기 하나만 부르므로 `pull`·`push`(값 되돌려 쓰기)도 처리기마다 일어난다 — 앞선 처리기가 바꾼 값을 다음 처리기가 보는 Bukkit 의미에 더 가깝다.
- 그 사이 `HandlerList.unregister` 로 해제된 처리기는 `HandlerList#isRegistered`(호환 계층 내부용으로 더한 메서드, Paper 에는 없다)로 걸러 부르지 않는다. 목록 훑기 없이 객체 동일성 집합을 본다.
- **비용**: 같은 이벤트에 처리기가 N 개면 구독도 N 개라 버스 호출·`bukkitFor`(동기 약한 맵 조회)가 N 번 일어난다(옛 방식은 플러그인 수만큼). 처리기가 아주 많은 핫 이벤트(PlayerMove·EntityDamage)에서 문제가 되면 "연속한 같은 플러그인 처리기 묶음" 단위 구독으로 줄일 수 있다 — 지금은 측정해 보지 않았다(가정).

## 독립 검토 반영 (PR #256 검토)
- **R1 누수**: 처리기 구독을 `SimplePluginManager.registerEvent` 가 만들고 버리던 것을 **`HandlerList` 가 맡는다**. `HandlerList#register` 가 `EventBridge.attach` 로 그 처리기의 버스 구독을 만들어 처리기별로(`IdentityHashMap`) 들고, `unregister(처리기|플러그인|리스너)`·정적 `unregisterAll()` 이 빼는 처리기마다 구독을 끊는다. 달았다 떼기 1000번 뒤 버스 구독 수가 처음 값으로 돌아오고 리스너가 회수된다(`A15HandlerSubscriptionTest`).
- **R2**: `HandlerList#register` 로 직접 단 처리기도 같은 길을 타므로 불린다(Paper 와 같다).
- **R3**: 뗐다 다시 달면 새 구독이라 우선순위 맨 뒤로 간다(`[B1, A1]`).
- HandlerList → 짝이 있는 Bukkit 이벤트 클래스 표(`EventBridge.owners`)는 처음 쓸 때 한 번 만든다(그 클래스가 `getHandlerList` 를 직접 가진 것만 — 부모 목록을 물려받는 하위 클래스는 부모 쪽에서 잡힌다). 서버가 알리지 않는 이벤트의 경고는 `warnIfNotFired` 가 맡는다. 이 호환 계층의 플러그인이 아니거나 서버가 없으면 이을 것이 없다.
- **R4**(전역 스레드 이벤트의 region 빌림이 처리기마다 열리고 닫힘)는 측정하지 않았다(가정). 전역 스레드 이벤트가 적어 영향은 작다고 본다 — 문제가 되면 같은 플러그인의 연속 처리기를 묶어서 빌린다.
