// 메뉴가 플레이어에게 바라는 것 — 바닐라 AbstractContainerMenu/Slot 이 Player 를 통해 하는 일을 좁은 인터페이스로 둔다(서버 Player 와 시험용 가짜가 구현).
package org.domi.furnace.server.menu;

import java.util.List;
import org.domi.furnace.server.entity.ItemStack;

public interface MenuPlayer {

    /** abilities.instabuild(크리에이티브) — CLONE·드래그 3번 유형·결속 저주 무시에 쓴다. */
    boolean isCreative();

    boolean isSpectator();

    /** 바닐라 !isAlive() — 죽었거나 죽어 가는 중. */
    boolean isDeadOrDying();

    /** ServerPlayer.hasDisconnected — 접속이 끊긴 뒤의 정리. */
    boolean hasDisconnected();

    /** Player.drop(stack, thrower): 월드에 던진다. 빈 스택은 무시. includeThrower 가 true 면 thrower 를 기록(줍기 발전 과제). */
    void drop(ItemStack stack, boolean includeThrower);

    /** Inventory.placeItemBackInInventory: 인벤토리에 넣고, 넘치면 던진다. */
    void placeItemBackInInventory(ItemStack stack);

    /** Inventory.add(stack) — 넣고 남은 스택(다 넣었으면 EMPTY). */
    ItemStack addToInventory(ItemStack stack);

    /** Inventory 번호(0..8 핫바, 9..35 주, 36..39 발·다리·가슴·머리, 40 왼손)의 내용. */
    ItemStack inventoryItem(int inventoryIndex);

    void setInventoryItem(int inventoryIndex, ItemStack stack);

    /** 그 아이템이 들어갈 장비 칸(바닐라 getEquipmentSlotForItem). */
    EquipSlot equipmentSlotFor(ItemStack stack);

    // --- 제작(4-2c) -------------------------------------------------------------------------

    /** 서버의 레시피 매니저(없으면 null — 시험용·데이터 팩 없는 서버는 제작이 되지 않는다). */
    default org.domi.furnace.server.recipe.RecipeManager recipes() { return null; }

    /** 게임 규칙 doLimitedCrafting. */
    default boolean doLimitedCrafting() { return false; }

    /** 레시피 북이 아는 레시피인가. */
    default boolean hasRecipe(org.domi.furnace.server.data.ResourceId id) { return true; }

    /** 바닐라 ServerPlayer.awardRecipes — 새로 배운 것만 알린다. */
    default void awardRecipes(List<org.domi.furnace.server.data.ResourceId> ids) { }

    /** 바닐라 triggerRecipeCrafted — recipe_crafted 발전 과제 조건. */
    default void triggerRecipeCrafted(org.domi.furnace.server.data.ResourceId recipe, List<ItemStack> grid) { }

    /** 플러그인 PrepareItemCraftEvent: 결과 칸에 둘 스택(recipe 는 없으면 null). */
    default ItemStack prepareCraft(AbstractContainerMenu menu, String recipe, ItemStack result) { return result; }

    /** 플러그인 PrepareItemEnchantEvent: 최종 제안 세 칸, null 이면 취소. */
    default org.domi.furnace.server.plugin.GameEvents.EnchantOffer[] prepareEnchant(AbstractContainerMenu menu, int[] table, ItemStack item, int bookshelves,
                                                                                 org.domi.furnace.server.plugin.GameEvents.EnchantOffer[] offers) { return offers; }

    /** 플러그인 EnchantItemEvent: 최종 값, null 이면 취소. */
    default org.domi.furnace.server.plugin.GameEvents.EnchantApply enchantItem(AbstractContainerMenu menu, int[] table, ItemStack item, int button, int cost,
                                                                             java.util.Map<String, Integer> enchants) {
        return new org.domi.furnace.server.plugin.GameEvents.EnchantApply(cost, enchants);
    }

    /** 플러그인 PrepareAnvilEvent: 바꾼 값, null 이면 그대로. */
    default org.domi.furnace.server.plugin.GameEvents.AnvilPrepared prepareAnvil(AbstractContainerMenu menu, ItemStack result, int repairCost, String renameText) { return null; }

    /** 바닐라 ItemStack.onCraftedBy(level, player, count) — crafted 통계와 아이템 훅. */
    default void onItemCrafted(ItemStack stack, int count) { }

    /** Entity.distanceToSqr(x, y, z) — 컨테이너가 열려 있는 동안 사거리 검사(stillValid)에 쓴다. */
    double distanceToSqr(double x, double y, double z);

    /** 결속 저주(prevent_armor_change)가 붙었는가. */
    boolean hasBindingCurse(ItemStack stack);

    /** 인챈트의 getMinCost(level) — 숫돌 경험치. 데이터 팩 인챈트 정의를 못 보면 0. */
    default int enchantmentMinCost(String enchantment, int level) { return 0; }

    // --- 모루(4-2i) ----------------------------------------------------------------------------

    /** Player.experienceLevel. */
    default int experienceLevel() { return 0; }

    /** ServerPlayer.giveExperienceLevels(levels) — 음수면 깎는다. */
    default void giveExperienceLevels(int levels) { }

    /** 데이터 팩 인챈트 정의(없으면 null). */
    default org.domi.furnace.server.loot.Enchantment enchantment(String id) { return null; }

    /** 두 인챈트가 함께 붙을 수 있는가(Enchantment.areCompatible). */
    default boolean enchantmentsCompatible(String a, String b) { return true; }

    /** 그 인챈트가 그 아이템에 붙을 수 있는가(Enchantment.canEnchant). */
    default boolean enchantmentSupports(String enchantment, ItemStack stack) { return true; }

    /** 가방 소리(item.bundle.insert / remove_one, 음량 0.8, 높이 0.8 + 난수 × 0.4)를 낸다 — 본인에게는 보내지 않는다(Player.playSound). */
    default void playBundleSound(String sound) { }

    /** 경험치 점수를 더한다(화로 꺼내기 등) — 서버는 경험치 구슬을 만들어 준다(ConnectionHandler). */
    default void awardExperience(int amount) { }

    // --- 마법 부여대(4-2l) ----------------------------------------------------------------------

    /** Player.getEnchantmentSeed. */
    default int enchantmentSeed() { return 0; }

    /** Player.onEnchantmentPerformed(stack, levels). */
    default void onEnchantmentPerformed(int levels) { }

    /** 데이터 팩 인챈트/태그 등록부(없으면 null — 마법 부여 불가). */
    default org.domi.furnace.server.loot.LootRegistries lootRegistries() { return null; }

    /** abilities.mayBuild(모험·관전 모드는 거짓) — 독서대 책 꺼내기 등. */
    default boolean mayBuild() { return true; }

    /** Player.awardStat(통계, n). */
    default void awardStat(String stat, int amount) { }

    /** LivingEntity.onEquipItem(slot, old, new): 장비 칸이 바뀌었다 — 장착 소리(모두에게)와 equip/unequip 게임 이벤트. */
    default void onEquipItem(EquipSlot slot, ItemStack oldItem, ItemStack newItem) { }

    // --- 엔티티 용기(말·상자 배·상자 수레) -----------------------------------------------------------

    /** Player.canInteractWithEntity(box, buffer): 눈에서 상자까지 거리² < (엔티티 상호작용 거리 + buffer)². 시험용 가짜는 늘 참. */
    default boolean canInteractWithEntity(org.domi.furnace.server.entity.AABB box, double buffer) { return true; }

    /** getVehicle() == entity. */
    default boolean isRiding(Object entity) { return false; }

    /** 이 메뉴를 쥔 서버 Player(거래 중인 몹의 목표가 위치·시선을 읽는다). 시험용 가짜는 null. */
    default org.domi.furnace.server.entity.Player entityPlayer() { return null; }
}
