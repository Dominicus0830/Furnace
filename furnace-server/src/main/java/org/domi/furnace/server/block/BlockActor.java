// 블록 동작이 보는 행위자(바닐라 Player/LivingEntity 의 필요한 부분). 손 번호: 0 주 손, 1 보조 손.
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;

public interface BlockActor {
    int MAIN_HAND = 0, OFF_HAND = 1;

    float yRot();

    float xRot();

    double x();

    double y();

    double z();

    /** 눈 높이(서 있을 때 1.62, 웅크리면 1.27). */
    double eyeHeight();

    /** isSecondaryUseActive(= 웅크리기). */
    boolean isSecondaryUseActive();

    /** abilities.mayBuild. */
    boolean mayBuild();

    /** abilities.instabuild(크리에이티브 — hasInfiniteMaterials). */
    boolean instabuild();

    boolean isSpectator();

    boolean isCreative();

    int permissionLevel();

    ItemStack itemInHand(int hand);

    void setItemInHand(int hand, ItemStack stack);

    /** 행위자 본체(소리 등에서 "이 플레이어는 빼고" 판단용). */
    Object handle();

    /** Entity.getDirection = Direction.fromYRot(yRot). */
    default int direction() { return Dir.fromYRot(yRot()); }

    /** 그 아이템이 재사용 대기 중인가(getCooldowns().isOnCooldown). */
    default boolean isOnCooldown(int itemId) { return false; }

    /** 발전 과제 기준 발동(placed_block·item_used_on_block·any_block_use·default_block_use) — pos 의 블록은 지금 상태. */
    default void trigger(String criterion, Pos pos, ItemStack stack) {}

    /** 설치 직전 허가(플러그인 BlockPlaceEvent) — 거짓이면 설치하지 않는다(FAIL). */
    default boolean allowPlace(Pos pos, int state, Pos against) { return true; }

    /** 그 칸 용기(상자·통·셜커 상자·깔때기·발사기·공급기)의 화면 열기(바닐라 openMenu(getMenuProvider)). */
    default void openContainer(Pos pos) {}

    /** 제작대 화면 열기(CraftingTableBlock.useWithoutItem → player.openMenu). */
    default void openCraftingTable(Pos pos) {}

    /** 석재 절단기 화면 열기. */
    default void openStonecutter(Pos pos) {}

    /** 숫돌 화면 열기. */
    default void openGrindstone(Pos pos) {}

    /** 대장장이 작업대 화면 열기. */
    default void openSmithing(Pos pos) {}

    /** canUseGameMasterBlocks = instabuild && 권한 2 이상. */
    default boolean canUseGameMasterBlocks() { return instabuild() && permissionLevel() >= 2; }

    /** Player.hasCorrectToolForDrops(state): 도구가 필요 없는 블록이거나 손 도구(tool 컴포넌트 규칙)가 맞으면. */
    default boolean hasCorrectToolForDrops(int state) {
        return !BlockInfo.get().requiresCorrectToolForDrops(Blocks.get().blockOf(state))
                || org.domi.furnace.server.item.ItemTools.isCorrectToolForDrops(itemInHand(MAIN_HAND), state);
    }

    /** 땅에 서 있는가(이동 패킷의 onGround). */
    default boolean onGround() { return true; }

    /** 눈이 물에 잠겼는가(Entity.isEyeInFluid(#water) — 지난 틱 값). */
    default boolean eyeInWater() { return false; }

    /** 머리 칸 장비(물속 채굴 속도 — 친수성). */
    default ItemStack headItem() { return ItemStack.EMPTY; }

    /** Player.causeFoodExhaustion. */
    default void causeFoodExhaustion(float amount) {}

    /** 엔티티 id(파괴 단계 패킷의 주인). */
    default int entityId() { return -1; }

    /**
     * Player.getDestroySpeed(state): 손 도구 속도(1 넘으면 + 채굴 효율 속성 = 효율 레벨²+1) × 블록 파괴 속도 속성(1),
     * 눈이 물에 잠기면 × 물속 채굴 속도(0.2, 친수성이면 0.2·(1 + 4·레벨)), 공중이면 ÷5.
     * TODO(효과·속성): 성급함·채굴 피로, 아이템 attribute_modifiers 의 채굴 속성.
     */
    default float destroySpeed(int state) {
        ItemStack hand = itemInHand(MAIN_HAND);
        float f = org.domi.furnace.server.item.ItemTools.destroySpeed(hand, state);
        if (f > 1.0F) {
            int eff = org.domi.furnace.server.item.ItemTools.enchantmentLevel(hand, "minecraft:efficiency");
            if (eff > 0) f += (float) (eff * eff + 1);
        }
        f *= 1.0F;   // minecraft:player.block_break_speed 기본값
        if (eyeInWater()) {
            int aqua = org.domi.furnace.server.item.ItemTools.enchantmentLevel(headItem(), "minecraft:aqua_affinity");
            double submerged = 0.2;
            if (aqua > 0) submerged += 0.2 * (4.0 * aqua);
            f *= (float) submerged;
        }
        if (!onGround()) f /= 5.0F;
        return f;
    }

    /** Player.canEat(ignoreHunger). */
    default boolean canEat(boolean ignoreHunger) { return ignoreHunger; }

    /** player.getFoodData().eat(nutrition, saturationModifier). */
    default void eat(int nutrition, float saturationModifier) {}

    /** Player.awardStat(통계 이름, 1). TODO(통계). */
    default void awardStat(String stat) {}

    /** getInventory().add(stack): 다 들어가면 참. TODO(인벤토리): 블록 쪽에서 인벤토리에 넣는 통로가 생기면. */
    default boolean addToInventory(ItemStack stack) { return false; }

    /** getInventory().contains(stack). TODO(인벤토리). */
    default boolean inventoryContains(ItemStack stack) { return false; }

    /** Player.drop(stack, false): 바닥에 떨군다. TODO(엔티티). */
    default void drop(ItemStack stack) {}

    /**
     * ItemUtils.createFilledResult(empty, player, filled, preventDuplicates): 손에 둘 결과 — 무한 재료면 빈 그릇을 그대로 두고(중복 방지면 없을 때만 넣고),
     * 아니면 하나 줄여 다 쓰면 채운 것을, 남으면 채운 것은 인벤토리(안 되면 바닥)로.
     */
    default ItemStack createFilledResult(ItemStack empty, ItemStack filled, boolean preventDuplicates) {
        boolean infinite = instabuild();
        if (preventDuplicates && infinite) {
            if (!inventoryContains(filled)) addToInventory(filled);
            return empty;
        }
        ItemStack rest = infinite ? empty : empty.withCount(empty.count() - 1);
        if (rest.isEmpty()) return filled;
        if (!addToInventory(filled)) drop(filled);
        return rest;
    }

    default ItemStack createFilledResult(ItemStack empty, ItemStack filled) { return createFilledResult(empty, filled, true); }

    /** 갑옷 칸(창 칸 5 머리 · 6 가슴 · 7 다리 · 8 발)의 스택. */
    default ItemStack equipment(int windowSlot) { return ItemStack.EMPTY; }

    /** setItemSlot(갑옷 칸) — onEquipItem 소리·게임 이벤트는 부르는 쪽이. */
    default void setEquipment(int windowSlot, ItemStack stack) {}

    /** Level.mayInteract(player, pos): 스폰 보호 밖이고 월드 보더 안. */
    default boolean mayInteract(BlockLevel level, Pos pos) { return level.withinBorder(pos); }

    /** LivingEntity.onEquippedItemBroken(item, slot): 통계 broken + 부서짐 엔티티 이벤트(주 손 47, 왼손 48). */
    default void onEquippedItemBroken(int hand, ItemStack broken) {
        awardStat("minecraft:broken:" + org.domi.furnace.server.item.ItemTools.itemName(broken));
    }

    /** 플레이어 자신의 난수(Entity.random — 장착 소리 씨앗 등). */
    default long nextEntityLong() { return 0L; }
}
