# T-076 Bukkit 이벤트 내용 게으르게 (2026-11-14)

TASK: `docs/roadmap/optimization-tasks.md` T-076 (P-O13-09 (a)(b), 반론 2차 1장). 바닐라 동작과는 무관한 호환 계층 경로다(장면 금 파일 그대로). (c)(`SnapshotInventory` 즉시 복사)는 T-033 계약이라 이 PR 에서 하지 않는다.

## 바꾼 것

### (a) `BlockPhysicsEvent` 의 블록 데이터
- Bukkit `BlockPhysicsEvent` 가 `BlockData` 대신 `LazyValue<BlockData>` 를 들고, `getChangedType()`·`getChangedBlockData()` 가 처음 읽을 때 만든다. Paper 모양의 공개 생성자 둘(`(Block, BlockData)`, `(Block, BlockData, Block)`)은 그대로이고(미리 만든 값을 `LazyValue.ready` 로 감쌈), 호환 모듈 전용 생성자 `(Block, LazyValue<BlockData>, Block)` 를 더했다.
- 다리(`Mappings`)는 `LazyValue.of(furnaceEvent, e -> CompatBlockData.of(e.block()))` 를 넘긴다. 원본은 Furnace 이벤트가 이미 잡은 불변 상태 번호(T-032, T-072 의 상태 번호별 공유 `BlockData` 캐시)라 처음 읽을 때 만들어도 이벤트 시점의 값과 같다. #306 의 원칙(바뀔 수 있는 원본 금지)을 지킨다.
- `getChangedBlockData()` 는 예전처럼 읽을 때마다 `clone()`(Paper 도 같다).

### (b) 깔때기 `InventoryMoveItemEvent` 의 처리기마다 도는 아이템 비교
- 다리는 Bukkit 처리기 호출 앞(pull)·뒤(push)마다 `furnace(b.getItem()).equals(f.item())`(종류 이름 찾기 + 구성 요소 깊은 비교)를 했다.
- 어댑터 `ItemStack` 에 **변경 번호**(`changeStamp()`)를 뒀다. 값을 스스로 들고 있는 칸(`PlainSlot`)의 쓰기는 `set`·`setAmount` 둘뿐이고 둘 다 번호를 올린다. 인벤토리 거울 칸은 추적 안 함(-1, 늘 값으로 비교).
- 다리는 "이 (스택 객체, 번호, Furnace 아이템 참조) 에서 같음을 확인했다"를 이벤트 붙임 칸에 기억하고, 셋이 모두 그대로면 비교를 건너뛴다. 하나라도 다르면 옛 식 그대로 값으로 비교한다. 거짓 "같음"이 나오려면 번호를 올리지 않는 쓰기가 있어야 하므로 **반사 전수 시험**이 `ItemStack` 의 모든 공개 메서드를 변경/읽기로 분류하게 하고(새 메서드가 생기면 시험이 깨진다), 변경 메서드가 전부 번호를 올리는지 확인한다.
- 동일성(`==`)만으로 판정하자던 원안은 반론에서 기각됐다: 처리기가 `e.getItem().setAmount(1)` 처럼 제자리에서 고치는 것을 놓친다. 번호가 있으면 놓치지 않는다.
- `ItemStack` 하위 클래스(`getAmount` 등을 덮어쓰는 플러그인)는 값이 번호 밖에서 바뀔 수 있어 추적하지 않는다(`getClass() == ItemStack.class` 일 때만).

### 확인한 한 줄: 1.21.1 `InventoryMoveItemEvent.getItem()` 은 사본인가
- **Paper 1.21.1**: 사본 없음. `Optimize-Hoppers.patch`(API)가 Spigot 의 `return itemStack.clone();` 을 `return itemStack; // Paper - Removed clone` 으로 바꾼다(원문: 커밋 `33ff3dff` 의 `patches/api/Optimize-Hoppers.patch`). 최신 main 도 같다. Spigot 은 사본을 돌려준다.
- 어댑터는 `getItem()` 이 내부 객체를 그대로 돌려주므로 **Paper 와 같다.** 어긋남 없음. `setItem` 은 둘 다 `clone()`.

## 측정 (4코어 컨테이너, 임시 벤치 — 커밋 안 함)

- (b) 처리기 한 번의 앞 또는 뒤 비교(`furnace(b.getItem()).equals(f.item())` vs `moveItemMatches`, 같음일 때): 구성 요소 5개 아이템 **56 → 3 ns**, 구성 요소 없는 아이템 36 → 3.5 ns. 처리기마다 앞뒤 두 번이라 처리기당 약 100 ns 절약. 구성 요소가 많을수록 이득이 크다.
- 끝에서 끝(`bus.call`, 처리기 하나, 같은 장치로 전·후 두 번씩 번갈아): 물리 이벤트 91~135 ns / 깔때기 350~650 ns 로 **전후 차이가 흔들림 안**이다. 이벤트 만들기·용기 사본이 비용 대부분이라 비교 생략분은 안 보인다.
- (a)는 기대한 −50% 가 **아니다**: 그 가설은 T-072 앞(미리 `BlockData` 만들기)의 수치다. T-072 로 상태 번호 → `BlockData` 가 공유 캐시가 되어 지금 미리 만드는 비용은 작은 래퍼 하나뿐이다. 이득은 "처리기가 좌표만 볼 때 래퍼·람다 하나 덜 만듦" 수준이고, 실서버 몫은 T-075 장면으로 재야 한다. 그래도 `BlockData` 를 한 번도 안 읽는 흔한 경우 변환 0번이라는 구조는 유지된다.

## 결과 동일 증명

- 모든 블록 상태(26684개 중 가진 것 전부): 게으른 Bukkit 이벤트의 `getChangedType`·`getChangedBlockData`·`getAsString` = 미리 만든 이벤트의 것 (`lazyBukkitPhysicsEventEqualsTheEagerOneForEveryState`).
- 실제 다리 경유: 좌표만 보는 Bukkit 처리기는 블록 값 변환 0번, 읽는 처리기가 있어도 이벤트당 변환 1번(`bridgeBuildsTheBlockDataOnlyWhenAHandlerReadsIt`).
- 깔때기: 무작위 연산열(Bukkit 쪽 변경 8종·객체 교체·Furnace 쪽 교체·같은 값 다른 참조, 300라운드×40단계)에서 새 비교 = 옛 비교 `furnace(b.getItem()).equals(f.item())`, 매 단계 두 번(재조회로 기억한 길도) (`moveItemComparisonMatchesTheLegacyComparisonUnderRandomOperations`).
- 실제 다리에서 처리기가 아이템을 안 건드림(같은 Furnace 객체 유지)·제자리 `setAmount`·메타 수정·`setItem` 교체가 모두 옛 동작대로 Furnace 이벤트에 반영된다(`bridgeWritesBackOnlyWhatHandlersChanged`).
- 변경 번호: 반사 전수 분류·연산별 번호 상승·읽기 메서드는 번호를 안 올림·사본은 자기 번호·거울 스택은 -1·하위 클래스는 값 비교.

## 이어서

- T-075(호환 기준 장면)가 들어오면 `getType`/`getBlockData` 장면과 함께 이 두 경로를 장면에 넣어 실서버 몫을 측정한다(지금 수치는 미니 벤치).
- (c) `SnapshotInventory` 즉시 복사는 T-033 지연 스냅샷 PR 에서.
