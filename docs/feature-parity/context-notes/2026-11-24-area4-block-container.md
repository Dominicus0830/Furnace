# 영역 4: 용기 블록 엔티티 Container 몸통 (2026-11-24)

- 공통 상위 조각 `BaseContainerBlockEntity` 가 Container 메서드를 `NmsBlockContainer` 로 잇는다. 구체 12종은 상위가 구현하므로 생성 던지는 몸통이 안 붙는다(미구현 추상 285→117).
- 월드의 보기: `ChunkAccess#furnaceBlockInventory` — 서버 `inventoryAtNow`(주인 스레드, 다른 스레드는 `NmsOwner` 규칙)로 칸을 읽고 쓴다. 떨어진 블록 엔티티는 NBT `Items`(`ItemNbtCodec`).
- 돌려준 스택·넘긴 스택은 `ItemStack#liveContainerSlot`/`furnaceBindLive`(손 칸과 같은 LiveSlot)로 칸에 묶여 바닐라처럼 고치면 칸이 바뀐다. 칸이 그새 바뀌면 떨어진다(비교-교환).
- `ItemStack#limitSize` 를 더했다(바닐라 setItem 이 쓴다).
- 함정: `shimLockCheck` 가 이 클라우드 서명표에서는 `@FunctionalInterface`·`@Deprecated` 애노테이션 줄 6개가 "낡음"으로 나온다(기준 브랜치 그대로도 같음 — 노트북 표와 다름). 잠금은 건드리지 않았다.
- 시험: `NmsBlockContainerTest`(크기·주인 스레드 읽기쓰기·살아 있는 스택·다른 스레드·떨어진 상자).

## Ingredient (같은 날, 엔티티 스레드 TemptGoal 요청)
- `net.minecraft.world.item.crafting.Ingredient` 손 shim: `of(ItemLike…/ItemStack…/Stream/TagKey)`·`EMPTY`·`test`(종류만, Paper `exact` 면 컴포넌트까지)·`getItems`·`isEmpty`·`equals`. 중첩 레코드 `ItemValue`·`TagValue`(equals 는 바닐라처럼 종류+개수 / 태그 이름만). 코덱·`getStackingIds` 는 껍데기로 남음. 근거: Paper 1.21.1 바이트코드 대조(읽기만).
- 안쪽 레코드를 가진 바깥 클래스라 생성기가 아직 못 만들어 손 shim 이다.
- 시험 `NmsIngredientTest`. 이 클라우드에서는 `generateFullShells` 의 서명표 지문이 달라 못 돌렸다 — CI 에서 확인.
