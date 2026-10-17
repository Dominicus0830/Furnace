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

    /** 상자 화면 열기(바닐라 openMenu — 블록 엔티티 체계가 들어오기 전에는 상자만). */
    default void openChest(Pos pos) {}

    /** canUseGameMasterBlocks = instabuild && 권한 2 이상. */
    default boolean canUseGameMasterBlocks() { return instabuild() && permissionLevel() >= 2; }

    /** Player.hasCorrectToolForDrops(state): 도구가 필요 없는 블록이거나 손 도구가 맞으면. TODO(3-4b): 도구 등급·태그 판정. */
    default boolean hasCorrectToolForDrops(int state) { return !BlockInfo.get().requiresCorrectToolForDrops(Blocks.get().blockOf(state)); }

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
}
