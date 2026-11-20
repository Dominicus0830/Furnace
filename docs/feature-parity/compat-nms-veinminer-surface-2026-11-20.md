# NMS shim: VeinMiner 가 부르는 41곳 채우기 (2026-11-20)

감사 도구(`nmsShimAudit`)로 확인한 VeinMiner 의 빈 곳 41개를 모두 채웠다. 감사 결과 다시 0개.

## 채운 것
- **손 칸 되쓰기**: 접속한 플레이어의 `getItemInHand` 는 손 칸에 묶인 보기(`ItemStack.liveHand`)다. `setDamageValue` 등이 서버 인벤토리에 되쓰인다. 서버의 `PlayerInventory.replace` 는 객체 동일성 비교라 값 비교(`ChunkInternals.replaceSlotNow`)로 바꿨다. 그새 칸이 바뀌면 보기가 떨어진다.
- **허기**: `FoodData`(보기), `ServerPlayer.causeFoodExhaustion`, `Player.isCreative/isCrouching`. 다리: `ChunkInternals.foodNow·setFoodNow·causeFoodExhaustionNow`.
- **도구 판정**: `ItemStack.isCorrectToolForDrops`, `BlockStateBase.requiresCorrectToolForDrops`, `ItemEnchantments`·`Enchantment`(이름만).
- **채팅**: `ClickEvent`·`HoverEvent`, `Style` 의 get/with.
- **팩**: `PackType`·`PackResources`·`Pack` 등 값 틀, `WorldVersion.getPackVersion`(리소스 34·데이터 48).
- **세계·엔티티**: `Level.levelEvent`(64칸 안 플레이어에게), `Entity.moveTo`, `ItemEntity`·`ExperienceOrb`, `BaseFireBlock`, `CraftBlock`(세계·이벤트 블록 형).
- **명령 틀**: `CommandBuildContext`·`CommandSourceStack`·`BlockPredicateArgument`·`ItemPredicateArgument`·`BlockInWorld`. Brigadier 대역은 컴파일 전용(jar 에 안 들어감).

## 한계
- 명령·팩 클래스는 겉모양뿐이다. `parse`·`open()` 은 `UnsupportedNmsCall` 을 던진다(Paper 의 Brigadier 명령 API·팩 저장소가 호환 계층에 없음).
- Paper 의 허기 소모 이벤트는 내지 않는다. `Enchantment` 는 이름만 든다. `FoodData` 의 먹기·틱은 없다.
- 명령 인자 클래스는 Brigadier 가 있을 때만 올라가므로 `CompatLinkTest` 가 건너뛴다.
