# /item·/loot 엔티티 contents 칸 (M3 검토 후속)

바닐라 1.21.1 `getSlot(int)` 바이트코드와 대조해 `ItemSlots.of` 에 넣었다.

- 약탈자 가방 300..304: `SlotAccess.forContainer(inventory, i)`. 쓸 때 min(99, 아이템 최대)로 자른다.
- 칸 0(`contents`): 아이템 엔티티(getItem/setItem), 아이템 액자·빛나는 액자(setItem → 1개로 줄이고 add_item 소리·비교기 갱신), 아이템 디스플레이(개수 그대로), 화살·삼지창(pickup 아이템; 빈 스택이면 기본 줍는 아이템), 던진 아이템(눈덩이·알·진주·경험치 병·물약; 1개로 줄이고 빈 스택이면 기본 아이템).
- 고침: `ThrowableProjectile.setItem` 은 바닐라 `ThrowableItemProjectile.setItem` 처럼 빈 스택을 기본 아이템으로 바꾼다(전엔 빈 채로 둠).
- 시험: `EntityContentsSlotTest`.
