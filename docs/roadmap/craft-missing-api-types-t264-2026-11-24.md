# T-264 이 드러낸 Craft 엔티티의 빠진 API·Paper 내부 형식 (2026-11-24)

T-264 P1(PR #580)이 엔티티 Craft 클래스의 상위 사슬을 Paper 와 같게 맞추자, 서명 관문(T-228)이 형식 닫힘을 견주며 **가려져 있던 빈칸 243줄**(`type-missing`)이 드러났다. 새 퇴행이 아니라 할 일 목록이다. 줄은 `furnace-compat-paper/src/test/resources/shim-signature-baseline.txt` 에 이유 "T-264 P1: …" 로 있고(기준 쪽 이름은 지문), 그 형식을 들여오면 관문이 "낡은 기준선" 으로 알려 주니 그 줄을 지운다. 같은 차이 중 Craft 클래스의 직접 인터페이스 몫은 `craft-hierarchy-baseline.txt` 의 `interfaces` 줄(5 + P2a 1)이다.

영역 번호는 `docs/roadmap/full-shells-2026-11-23.md` 8장(`fill-areas.txt`). API 형식(`org/bukkit`·`io/papermc`·`com/destroystokyo` 의 paper-api 형식)을 들여오는 일은 **영역 11**, 그 형식의 동작(엔티티 값 읽기·쓰기)은 **영역 2**, 인벤토리·전리품은 **영역 4** 와 함께 한다.

| 빠진 형식(API → Paper 내부 구현) | 줄 | 걸린 Craft 클래스(예) | 맡을 영역 | 메모 |
|---|--:|---|---|---|
| `io.papermc.paper.entity.Leashable` → `PaperLeashable` | 156 | 생물 78종(`CraftAbstractHorse`·`CraftBat`·`CraftBoat` …) | 11 + 2 | 줄 묶기(1.21 `Leashable`). 가장 큰 묶음 — 먼저 하면 156줄이 한 번에 준다 |
| `com.destroystokyo.paper.entity.RangedEntity` → `CraftRangedEntity` | 28 | 14종(`CraftSkeleton`·`CraftPillager`·`CraftLlama` …) | 11 + 2 | `rangedAttack`·`setChargingAttack` |
| `com.destroystokyo.paper.loottable.LootableInventory`·`LootableEntityInventory`, `org.bukkit.loot.Lootable` → `PaperLootable*` 4 | 28 | 4종(`CraftChestBoat`·`CraftMinecartChest`·`CraftMinecartContainer`·`CraftMinecartHopper`) | 11 + 4 | 전리품 표 시드·다시 채움 |
| `io.papermc.paper.entity.Bucketable` → `PaperBucketable` | 12 | 물고기 6종(`CraftFish`·`CraftCod` …) | 11 + 2 | `isFromBucket`·`getBaseBucketItem` |
| `org.bukkit.inventory.Merchant` | 4 | `CraftAbstractVillager`·`CraftVillager`·`CraftWanderingTrader`·`CraftMerchant` | 11 + 4 | 거래 목록 |
| `io.papermc.paper.entity.Shearable` → `PaperShearable` | 6 | `CraftBogged`·`CraftMushroomCow`·`CraftSnowman` | 11 + 2 | |
| `org.bukkit.inventory.InventoryHolder`(주민) | 3 | `CraftAbstractVillager` 와 하위 | 2 | 형식은 있음. 주민 인벤토리 8칸이 Furnace 핸들에 없음 — 엔티티 스레드에 넘김(11-24) |
| `io.papermc.paper.command.CommandBlockHolder` → `PaperCommandBlockHolder` | 2 | `CraftMinecartCommand` | 11 + 8 | |
| `org.bukkit.spawner.Spawner` → `PaperSharedSpawnerLogic` | 2 | `CraftMinecartMobSpawner` | 11 + 2 | 형식은 있으나 몸통 없는 메서드라 생성기가 붙이지 않음 |
| `org.bukkit.entity.SplashPotion`·`LingeringPotion` | 2 | `CraftThrownPotion` | 11 | Paper 1.21.1 은 한 클래스가 둘 다 구현 |

- 합계 243(= 156 + 28 + 28 + 12 + 4 + 6 + 3 + 2 + 2 + 2).
- P2a(인벤토리 이름 옮김)에서 1줄 더: `org.bukkit.inventory.EntityEquipment` — `CraftInventoryPlayer` 가 Paper 처럼 구현해야 하나 형식이 없다(영역 11 + 4, 이유 "T-264 P2a: …"). 계층 기준선 `interfaces` 줄 1개도 같은 몫이다.
- P2b-3(아이템 메타 이름 옮김)에서 6줄 더: `org.bukkit.inventory.meta.BlockDataMeta` — `CraftMetaItem` 과 하위 5종(`CraftMetaArmor`·`CraftMetaBanner`·`CraftMetaColorableArmor`·`CraftMetaLeatherArmor`·`CraftMetaSkull`)이 Paper 처럼 구현해야 하나 형식이 없다(영역 11 + 4, 이유 "T-264 P2b: …"). 동작은 `minecraft:block_state` 컴포넌트 읽기·쓰기와 직렬화 키 `BlockStateTag`(지금은 "unhandled" 로 산다). 계층 기준선 `interfaces` 줄 1개도 같은 몫이다.
- P2d(상태 효과 종류 이름 옮김)에서 3줄 더: `CraftPotionEffectType`(옛 `PotionEffectType$Vanilla`)이 Paper 처럼 구현하는 내부 인터페이스 `org.bukkit.craftbukkit.util.Handleable`(NMS `MobEffect` 손잡이)·`io.papermc.paper.world.flag.PaperFeatureDependent` 와 그 상위가 없다(이유 "T-264 P2d: …"). 계층 기준선 `interfaces` 줄 1개도 같은 몫이다. 손잡이 몫은 NMS 효과 shim 과, 기능 플래그 몫은 `FeatureFlagSetHolder`(T-218)와 함께 붙인다.
- 순서 제안: Leashable(156) → RangedEntity(28)·Lootable(28) → 나머지. 형식을 들여올 때는 T-264 생성기(`tools/compat/gen-bukkit-entity-classes.py`)를 다시 돌리면 Craft 클래스에 인터페이스가 붙는다(몸통 없는 메서드가 없을 때만 — 몸통은 손 사슬 또는 default 로).
- 함께 볼 기준선: `craft-hierarchy-baseline.txt`(Craft 직접 인터페이스), `craft-live-api-methods-1.21.1.txt`(살아 있는 메서드는 늘기만).
