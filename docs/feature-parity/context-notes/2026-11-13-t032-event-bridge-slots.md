# T-032 Bukkit 이벤트 다리 전역 잠금 제거 + 게으른 이벤트 내용 도우미 (2026-11-13)

TASK: `docs/roadmap/optimization-tasks.md` T-032 (O12-01, 반론 C 3.6). 바닐라 동작과는 무관한 플러그인 API 경로다(장면 금 파일은 그대로).

## 바꾼 것

- `furnace-api` `Event` 에 붙임 칸 배열(`volatile Object[] slots`, VarHandle CAS)과 열쇠 클래스 `EventSlot<T>`.
- `EventBridge.Mapping.live`: `synchronizedMap(WeakHashMap)` → 짝(Mapping)마다 `EventSlot` 하나. 칸이 짝마다인 까닭: 한 Furnace 이벤트가 여러 짝으로 간다(`InventoryClickEvent` → Bukkit 클릭·드래그, 한쪽은 SKIP).
  - 옛 잠금은 짝마다 하나였다(그 종류 이벤트 전부가 여러 region 스레드에서 한 줄로 섰다). Bukkit 객체 만들기(`create`, Compat.block 두 번 등)도 그 잠금 **안에서** 했다. 이제 잠금 없이 만들고 CAS 로 붙인다. 같은 이벤트 객체를 두 스레드가 동시에 처음 부르면 만들기는 두 번 돌 수 있지만 붙는 것은 하나다(실제로는 이벤트가 한 스레드에서 처리되므로 생기지 않는다).
  - 수명: 값이 이벤트 객체에 붙어 함께 사라진다. 같은 Furnace 이벤트를 두 번 `call` 하면 같은 Bukkit 객체(옛 동작과 같음).
  - **옛 다리의 채팅 누수가 고쳐진다(PR #306 검토 R1)**: 채팅 짝의 `create` 가 `new MappedPlayers(f::recipients)` 로 Furnace 이벤트를 붙잡는 Bukkit 객체를 만들어, 옛 `WeakHashMap` 은 값→키 강참조 때문에 항목을 영영 못 지웠다. Bukkit `AsyncPlayerChatEvent` 처리기가 하나라도 있으면 채팅마다 Furnace·Bukkit 이벤트·받는 사람 집합이 남았다(검토자 실측: 채팅 200번 뒤 GC 에서 옛 200개 남음, 새 0개). 플러그인이 보는 값은 같고 메모리만 다르다. 회귀 시험 `EventBridgeSlotTest.bridgedChatEventsAreCollectable`.
  - 옛 `WeakHashMap` 은 키를 `equals` 로 찾았다. `equals` 를 덮어쓴 Furnace 이벤트는 없다(확인함) — 정체 기준 칸과 결과가 같다.
- `furnace-api` `LazyValue<T>`: 불변 원본 + 변환 함수, 처음 `get()` 때 변환하고 CAS 로 고정(동시에 읽어도 같은 객체).
- 첫 적용(O08-07 의 Furnace 쪽): `BlockPhysicsEvent.block()`·`changedType()` 을 게으르게. 서버는 상태 번호(int, 불변)만 잡는다. 공개 생성자(미리 만든 값)는 그대로 두고 게으른 생성자를 더했다.

## 측정 (4코어 컨테이너, 이 PR 전후 같은 장치)

다리 거친 물리 이벤트 처리량 — 헤드리스 서버, Bukkit 처리기 셋(공유 상태 없음), 시험 스레드가 버스에 직접 `call`. 라운드 5번 중 최고, 두 번 잰 값:

| 스레드 | 전 (M 이벤트/s) | 후 (M 이벤트/s) |
|---|---|---|
| 1 | 1.81 / 1.42 | 7.78 / 9.17 |
| 2 | 1.03 / 0.99 | 13.85 / 17.17 |
| 4 | 0.92 / 0.88 | 23.27 / 56.80 |
| 8 | 0.84 / 1.11 | 53.49 / 68.41 |

전: 1스레드 551~706 ns/이벤트, 스레드를 늘려도 초당 약 100만에서 막힘(O12 보고서 측정과 같은 모양). 후: 1스레드 109~129 ns, 코어 수만큼 늘어남.

게으른 물리 이벤트 내용(레드스톤 가루 상태, 원인 반복기) — 처리기가 좌표만 볼 때 이벤트 만들기: 미리 변환 669~744 ns → 게으름 46~58 ns. Bukkit 처리기가 있으면 다리가 `CompatBlockData.of(f.block())` 로 곧바로 읽어서 이득이 없다(Bukkit 쪽 게으른 BlockData 는 후속 — 아래).

## 결과 동일 증명

- IdentityKit 차분 `EventSlotIdentityTest.bridgeSlotsMatchTheLegacyWeakMapBridge`: 옛 다리 맵 사본(`identity/legacy/plugin/LegacyBridgeLiveMap`, since 14767c19)과 붙임 칸 다리를 무작위(이벤트 1~6, 짝 1~4, SKIP 25%, 조회 1~40번) 2000번 — 조회마다 "몇 번째로 만든 객체를 받는가"와 만들기 횟수가 같다. 변이 점검: 칸 없이 매번 새로 만들면 잡힌다.
- 전수: 모든 블록 상태에서 게으른 `block()`·`changedType()` = 미리 만든 값(`lazyPhysicsEventEqualsEagerForEveryState`).
- 동시성: 8 스레드가 같은 이벤트·열쇠 4개에 처음 붙이기를 겨뤄도 열쇠마다 한 객체(`@RepeatedTest(20)` × 200라운드), `LazyValue` 동시 첫 읽기도 한 객체.
- 호환 계약(`EventBridgeSlotTest`): Bukkit LOW·NORMAL·HIGH 처리기(사이에 Furnace 처리기)가 같은 Bukkit 객체, 같은 Furnace 이벤트 재발행 = 같은 객체, 새 이벤트 = 새 객체, 8 스레드 × 2000 물리 이벤트에서 LOW 가 받은 객체를 HIGH 가 그대로 받음(어긋남 0).
- 장면 라이브러리 금 파일: 장면에 플러그인이 없어 이 경로를 타지 않는다 — 전체 시험에서 금 파일 그대로 통과(바뀐 금 파일 없음).

## 설계 판단: "바뀔 수 있는 원본 + 변경 번호" 모드는 넣지 않았다

반론 C A-3.6 은 공용 도우미에 두 모드를 두자고 했다: ① 불변 값 참조, ② 바뀔 수 있는 원본이면 변경 번호를 같이 잡고 "처음 읽을 때 번호가 다르거나 소유 스레드가 아니면 즉시 사본(=지금 동작)으로 돌아간다".
②는 그대로는 결과가 달라진다. 처음 읽을 때 번호가 달라졌음을 알아도, 그때는 원본이 이미 바뀌어 이벤트 시점의 값을 되살릴 수 없다(예: 처리기 A 가 엔티티 PDC 를 고친 뒤 처리기 B 가 이벤트의 PDC 스냅샷을 읽으면 옛 값이어야 한다). 결과를 같게 하려면 원본 쪽이 **바뀌기 직전에** 사본을 떠 두는 장치(쓰기 시 복사, 또는 원본에 "지켜보는 게으른 값" 목록)가 필요하다. 그래서:
- T-033(O09-01 칸 스냅샷)은 반론 C 의 말대로 "칸 참조 배열 복사(싸다) + 아이템 변환만 게으르게" = 모드 ①로 된다(`ItemStack` 불변).
- O12-07(엔티티 PDC)은 PDC 저장소에 쓰기 시 복사(바뀌기 직전 고정)가 먼저다 — 측정 TASK 그대로 두고, 할 때 이 판단을 따른다.

## 후속 후보

- Bukkit `BlockPhysicsEvent` 의 `changed` BlockData 를 게으르게(`CompatBlockData` 가 Furnace BlockData 를 게으르게 잡게) — 보호 플러그인이 `getBlock()` 좌표만 볼 때 이득. O08-07 측정과 같이.
- `Mappings.FORMAT_BASE`(채팅 서식, `synchronizedMap(WeakHashMap)`)도 같은 칸으로 옮길 수 있다. 채팅은 드물어 이번에는 두었다.
