# T-070 칸 거울 약한 참조 목록 → 세대(epoch) (2026-11-13)

`CompatInventory.mirror(index)` 는 호출마다 `liveMirrors[index]` 목록에 `WeakReference` 를 더하고 그 목록 전체를 `removeIf` 로 훑었다.
GC 사이 같은 칸 읽기 수에 비례해 목록이 자라 `getItemInMainHand()` 가 17~372µs(O13 `O13PlayerBench`, 기준 14767c19) 였다.

## 바꾼 것
- 칸마다 "세대"(`Epoch`, `AtomicReference<ItemStack>`)를 두고 그 칸의 거울이 모두 같은 세대를 쥔다. 값이 비어 있으면 거울은 살아 있다.
- 거울을 거치지 않는 쓰기(`setItem`·`clear`·`setContents`)는 `detachMirrors` 가 지금 값의 `PlainSlot` 사본 하나를 세대에 한 번 넣고(`compareAndSet`) 세대를 지도에서 뺀다. 같은 세대 거울은 그 순간 모두 떨어져 나가 **그 사본 하나를 함께 본다**.
- 거울을 만드는 일 = 지도 `get` + 객체 하나. 거울 목록·약한 참조·GC 의존이 없다. 칸을 읽은 횟수와 무관.
- 소유 스레드가 아니라 칸을 못 읽는 경우는 옛 구현처럼 떼어 내지 않는다. 읽기는 예외 없는 `peekSlot`(Future 가 안 끝났으면 null)으로 해서, 예외를 만들었다 버리는 비용이 없다(검토 R2).

## 관측 계약
거울을 쓰면 칸에 반영, 거울을 거치지 않고 칸을 바꾸면 그 칸의 거울은 그 순간 값의 사본으로 떨어지고, 떨어진 뒤의 쓰기는 사본에만(칸 영향 없음).
**한 가지가 일부러 바뀌었다(검토 R1, Paper 와 맞춤):** 같은 칸에서 꺼낸 거울 둘은 칸이 바뀐 뒤에도 사본 하나를 공유한다. CraftBukkit 1.21.1 에서 `getItem` 은 `asCraftMirror(nms)` 라 두 거울이 같은 NMS 객체를 쥐고 `setItem` 은 `asNMSCopy` 라 그 객체는 고아가 되어 둘이 공유하기 때문이다(검토 시험: Paper=9, 옛 Furnace=3). 옛 구현은 거울마다 따로 사본을 만들어 Paper 와 달랐다.
"같은 칸 거울 재사용"(P-O13-03 (b)) 자체는 TASK 지시대로 하지 않았다 — 세대는 거울 목록 없이 공유 상태만 쥔다.
범위 밖(옛 구현부터, 검토 O1~O3): 살아 있는 거울에 `setType(AIR)` 하면 Furnace 는 칸을 비우지만 Paper 는 그 래퍼만 비움, `setAmount(0)` 뒤 `setAmount(5)` 되살아남, 게임 쪽 칸 교체. 별도 TASK 후보.

## 증명
- `InventoryMirrorModelTest.randomOperationSequencesMatchModel`: 무작위 조작 열(getItem·손·getContents·setItem·clear·setContents·거울 setAmount·setType·clone·거울을 칸에 넣기·setHeldItemSlot·setItemInMainHand·addItem·removeItem·setStorageContents, 씨앗 12×1000 단계, 41칸)을 실제 어댑터와 모델에 같이 주고 매 단계 모든 거울·칸 값을 비교. 처음 모델(공유 사본만 뺀 것)은 옛 구현에서 먼저 통과시켜 검증했고, 독립 검토가 조작 16종·씨앗 20개 옛/새 흔적 해시 20개 일치를 따로 확인했다. `detachMirrors` 를 무력화하면 실패(변이 확인).
- `mirrorsOfTheSameSlotShareTheOrphanLikePaper`: 고아 공유(Paper 와 같은 9).
- `readingTheSameSlotManyTimesKeepsOneEpoch`: 같은 칸을 20만 번 읽어도 세대 하나, 41칸 다 읽어도 41 이하(벽시계 단언 없음 — 검토 R3).
- 기존 `InventoryMirrorDetachTest`·`InventoryAdapterTest` 등 compat-paper 전체 시험.

## 측정(`O13PlayerBench`, region 스레드, 2만×5, 같은 컨테이너 교대 실행)
| | 옛 | 새 |
|---|---|---|
| `getItemInMainHand()` 중앙값 | 197.6 µs | 369 ns |
| `.getType()` 까지 | 348 µs | 372 ns |
