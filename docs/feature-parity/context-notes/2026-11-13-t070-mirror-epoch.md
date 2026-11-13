# T-070 칸 거울 약한 참조 목록 → 세대(epoch) (2026-11-13)

`CompatInventory.mirror(index)` 는 호출마다 `liveMirrors[index]` 목록에 `WeakReference` 를 더하고 그 목록 전체를 `removeIf` 로 훑었다.
GC 사이 같은 칸 읽기 수에 비례해 목록이 자라 `getItemInMainHand()` 가 17~372µs(O13 `O13PlayerBench`, 기준 14767c19) 였다.

## 바꾼 것
- 칸마다 "세대"(`Epoch`, `AtomicReference<ItemStack>`)를 두고 그 칸의 거울이 모두 같은 세대를 쥔다. 값이 비어 있으면 거울은 살아 있다.
- 거울을 거치지 않는 쓰기(`setItem`·`clear`·`setContents`)는 `detachMirrors` 가 지금 값을 세대에 한 번 넣고(`compareAndSet`) 세대를 지도에서 뺀다. 같은 세대 거울은 그 순간 모두 떨어져 나가고, 거울마다 처음 쓸 때 자기 `PlainSlot` 사본을 만든다(떨어진 뒤 서로 독립인 점은 옛 구현과 같다).
- 거울을 만드는 일 = 지도 `get` + 객체 하나. 거울 목록·약한 참조·GC 의존이 없다. 칸을 읽은 횟수와 무관.
- 소유 스레드가 아니라 칸을 못 읽는 경우는 옛 구현처럼 떼어 내지 않는다.

## 관측 계약(안 바뀜)
거울을 쓰면 칸에 반영, 거울을 거치지 않고 칸을 바꾸면 그 칸의 거울은 그 순간 값으로 독립 사본, 떨어진 뒤의 쓰기는 자기 사본에만. "같은 칸 거울 재사용"(P-O13-03 (b))은 떨어진 뒤 사본 독립이 깨져서 하지 않았다.

## 증명
- `InventoryMirrorModelTest.randomOperationSequencesMatchModel`: 무작위 조작 열(getItem·손·getContents·setItem·clear·setContents·거울 setAmount, 씨앗 12×1500 단계)을 실제 어댑터와 모델에 같이 주고 매 단계 모든 거울·칸 값을 비교. **옛 구현에서 먼저 통과**시켜 모델을 검증했고 새 구현도 통과. `detachMirrors` 를 무력화하면 실패(변이 확인).
- `InventoryMirrorModelTest.readingTheSameSlotManyTimesStaysFlat`: 같은 칸 40만 번 읽기의 마지막 묶음이 첫 묶음과 비슷(옛 구현은 이차로 늘어 시간 초과).
- 기존 `InventoryMirrorDetachTest`·`InventoryAdapterTest` 등 compat-paper 전체 시험.

## 측정(`O13PlayerBench`, region 스레드, 2만×5, 같은 컨테이너 교대 실행)
| | 옛 | 새 |
|---|---|---|
| `getItemInMainHand()` 중앙값 | 197.6 µs | 369 ns |
| `.getType()` 까지 | 348 µs | 372 ns |
