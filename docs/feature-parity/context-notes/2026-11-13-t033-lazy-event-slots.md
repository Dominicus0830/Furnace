# T-033 이벤트 칸 스냅샷: 패치 API 모양 캐시 + 게으른 칸 변환 (2026-11-13)

TASK: `docs/roadmap/optimization-tasks.md` T-033 (O09-01, O09 답변 (a)(b), 반론 C 3.6). 플러그인 API 경로만 바뀐다(바닐라 동작 무관, 장면 금 파일 그대로).
설계 전제는 T-032 노트(`2026-11-13-t032-event-bridge-slots.md`)의 판단 그대로: **불변 원본만 게으르게** — 칸 참조 배열을 이벤트 시점에 복사하고(모드 ①), 아이템 변환만 미룬다.

## 바꾼 것

- **(a) `ComponentPatch.apiForm()`**: 패치의 API 모양(이름 → SNBT, 지운 이름 — 둘 다 이름순 읽기 전용)을 처음 요구할 때 한 번 만들어 들고 있다.
  `ApiConvert.item(서버 → API)` 은 그 모양으로 API 아이템을 만든다. 계산은 옛 `ApiConvert.item` 과 같은 순서·같은 값(값 → 코덱 NBT → `Snbt.write`).
  변환이 예외를 내면 저장하지 않고 그대로 던진다. 필드는 final 필드뿐인 불변 레코드라 `String.hash` 처럼 경합 공개해도 안전하다.
- **(b) `furnace-api` `LazyList<T>`**: 원소 원본 배열(받을 때 복사)과 변환 함수. `List.copyOf` 결과와 관측 결과가 같은 불변 목록이다.
  - 범위 안 `get(i)`·`size()`·`isEmpty()` 만 게으르다(그 칸만 변환, CAS 로 고정).
  - 그 밖의 모든 메서드(반복·`equals`·`hashCode`·`toString`·`contains`·`subList`·`stream`·바꾸기 시도·범위 밖 `get` …)는 칸을 모두 변환한 `List.of(…)` 사본에 맡긴다 → 예외 종류·메시지(`contains(null)` 의 NPE, 바꾸기의 UOE, 범위 밖 예외 꼴)까지 옛 `List.copyOf` 와 같다.
  - `get(i)` 로 받은 객체와 반복으로 받은 객체는 같은 객체다(사본도 칸마다 처음 고정한 값을 쓴다). 옛 목록도 같은 칸은 늘 같은 객체였다.
  - 변환 결과가 null 이면 NPE(`List.copyOf` 의 null 거부와 같은 계약, 다만 읽을 때).
- `InventorySnapshot` 은 `LazyList` 면 그대로 받고, 그 밖의 목록은 예전처럼 `List.copyOf`.
- 서버: `ApiConvert.snapshot(menu)`(창 이벤트 8종), 깔때기 `inventoryMove` 출발·도착 용기, 양조 `BrewEvent` 칸이 모두 "칸 참조 배열을 지금 복사 + 게으른 변환". 칸을 읽는 순서·시점(`slot.getItem()`, `inv.get(i)`)은 옛 코드와 같다(이벤트 시점).
- 호환 계층 `SnapshotInventory`: 받은 목록이 `LazyList` 면 복사하지 않고 들고 있다가 처음 쓸 때(쓸 수 있는 칸에 `setItem`) 사본을 만든다. Bukkit 처리기가 읽은 칸만 변환된다. 그 밖의 목록은 예전처럼 받을 때 복사.

## 측정 (4코어 컨테이너, O09ConvertBench 와 같은 틀: 예열 뒤 5회 중 최소)

| 경로 | 옛 | 새 |
|---|---|---|
| 아이템 1개 서버→API, 인챈트·이름 검(같은 스택 반복 = 캐시 찬 뒤) | 1438 ns | 63 ns |
| 같은 것, 매번 새 패치(캐시 빔, 청크에서 막 읽은 스택) | 1285 ns | 1398 ns |
| 평범한 stone | 15 ns | 19 ns |
| 54칸 혼합 상자 스냅샷(평범·인챈트·빈 각 1/3), 처리기가 칸을 안 읽음 | 26.2 µs | 34 ns |
| 같은 것, 칸 하나 읽음 | 26.2 µs | 114 ns |
| 같은 것, 모든 칸 읽음 | 26.2 µs | 1.98 µs |
| 54칸 전부 평범, 안 읽음 | 1.33 µs | 30 ns |

깔때기 이벤트 구독자(보호·로그 플러그인)는 대부분 `item`·위치만 본다 → 이벤트당 상자 두 개 변환 비용이 사실상 0. 모든 칸을 읽어도 패치 캐시로 1/13.
같은 패치를 처음 변환할 때는 캐시를 채우느라 약간 더 든다(+~9%, 한 번뿐).

## 결과 동일 증명 (`LazySnapshotIdentityTest`, IdentityKit 차분)

- 옛 구현 사본 `identity/legacy/plugin/LegacyApiConvert`(since 2b28b2ff: `ApiConvert#item`·`#snapshot`).
- `t033-item-api-form` 3000번: 무작위 스택(아이템 12종, 컴포넌트 0~6개 — 텍스트·로어·인챈트·custom_data·물약·책·장식·폭죽·속성·컨테이너·저장 안 하는 종류, 지움 표시, null·EMPTY, 레지스트리 없음)에서 API 아이템과 `toString`(맵 순서)이 같다. 같은 패치를 다른 아이템·개수로 다시 변환(캐시 찬 뒤)해도 같다. 변이 점검: 캐시가 아이템 이름·개수까지 붙잡으면 잡는다.
- `t033-lazy-snapshot` 3000번: 칸 0~63개 스냅샷에 무작위 조작열 30종(범위 안·밖 get, 반복, contains(null), indexOf, subList, toArray, 바꾸기 시도 10종, equals 양방향·hashCode·toString, stream, getFirst/getLast·reversed, listIterator)을 옛·새에 돌려 값·예외·"어느 조작이 같은 객체를 받는가"가 같다. 부른 쪽이 배열을 나중에 고쳐도 같다. 변이 점검: 배열을 복사하지 않으면 잡는다.
- 동시성: 8 스레드가 같은 스냅샷 27칸을 서로 다른 순서로 처음 읽어도 칸마다 한 객체, 다 변환한 사본도 같은 객체(`@RepeatedTest(20)` × 50라운드).
- 실서버 경로(`PluginApiBlockEventsTest.hopperMoveSnapshotIsLazyAndEventTime`): 깔때기 이벤트 처리기가 칸을 안 읽으면 변환 0칸. 이벤트가 끝나 상자가 바뀐 뒤에 읽어도 이벤트 시점 내용 = 옛 즉시 변환(처리기 안에서 서버 배열로 만든 값).
- 호환 계층(`InventoryResultEventsTest.lazySnapshotsStayLazyThroughBukkitContainers`): Bukkit 처리기가 `getItem(0)` 만 읽으면 1칸만 변환, 쓸 수 있는 칸에 쓰면 사본만 바뀌고 이벤트 스냅샷은 그대로.

## 달라지는 것 (오류 경로만)

칸 변환이 예외를 내는 아이템(정상 아이템에서는 없다 — 코덱 `toNbt` 실패)이 있으면, 예전엔 이벤트를 만들다 게임 코드 쪽에서 예외가 났고 지금은 플러그인이 그 칸을 읽을 때 난다. 정상 값은 모두 같다.

## 후속 후보

- 창 클릭 이벤트의 Bukkit 뷰(`CompatInventories.view`)는 `subList` 를 `ArrayList` 로 복사해 전체를 변환한다 — 클릭은 플레이어 동작당 한 번이라 두었다. 필요하면 `LazyList` 의 `subList` 를 게으른 보기로(예외 계약 맞춰서).
- T-032 노트의 후속 둘(Bukkit `BlockPhysicsEvent` 의 게으른 `CompatBlockData`, `Mappings.FORMAT_BASE` 를 붙임 칸으로)은 그대로 남아 있다.
- O09-04(패치 인터닝)가 들어오면 같은 내용의 패치가 한 객체가 되어 (a) 캐시 적중이 늘어난다.
