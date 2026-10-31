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

    /** 플레이어 UUID(플레이어가 아니면 null). */
    default java.util.UUID uuid() { return null; }

    /** isTextFilteringEnabled — 이 서버는 글 거르기를 하지 않는다. */
    default boolean isTextFilteringEnabled() { return false; }

    /** ServerPlayer.openTextEdit: 그 칸 블록 갱신 + open_sign_editor. */
    default void openTextEdit(Pos pos, int state, boolean front) {}

    /** ServerPlayer.openCommandBlock: 명령 블록 편집 화면용으로 블록 엔티티 데이터(saveCustomOnly)를 보낸다. */
    default void openCommandBlock(Pos pos, int blockEntityTypeId, org.domi.furnace.server.net.io.nbt.NbtCompound tag) {}

    /** Entity.getDirection = Direction.fromYRot(yRot). */
    default int direction() { return Dir.fromYRot(yRot()); }

    /** Player.startSleepInBed(pos) — pos 는 침대 머리 칸. 실패해 보일 메시지(성공·조용한 실패면 null). 플레이어가 아니면 아무것도 안 한다. */
    default org.domi.furnace.api.text.Text startSleepInBed(BlockLevel level, Pos pos) { return null; }

    /** 리스폰 지점이 그 차원의 그 칸인가(ServerPlayer.getRespawnDimension·getRespawnPosition). */
    default boolean respawnPointIs(String dimension, Pos pos) { return false; }

    /** ServerPlayer.setRespawnPosition(dim, pos, angle, forced, sendMessage). */
    default void setRespawnPosition(String dimension, Pos pos, float angle, boolean forced, boolean sendMessage) {}

    /** 그 아이템이 재사용 대기 중인가(getCooldowns().isOnCooldown). */
    default boolean isOnCooldown(int itemId) { return false; }

    /** getCooldowns().addCooldown(item, 틱). */
    default void addCooldown(int itemId, int ticks) {}

    /** getKnownMovement: 마지막 이동 패킷의 {dx, dy, dz}(던지기 속도에 더한다). */
    default double[] knownMovement() { return new double[3]; }

    /** 발전 과제 기준 발동(placed_block·item_used_on_block·any_block_use·default_block_use) — pos 의 블록은 지금 상태. */
    default void trigger(String criterion, Pos pos, ItemStack stack) {}

    /** LivingEntity.swing(hand, true): 팔 휘두르기를 본인과 보는 이에게 보낸다(소유 region 스레드에서 쓰는 쪽만 구현). */
    default void swing(int hand) {}

    /** 발전 과제 used_ender_eye(CriteriaTriggers.USED_ENDER_EYE.trigger(player, 구조물 칸)). */
    default void usedEnderEye(Pos structure) {}

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

    /** 모루 화면 열기. */
    default void openAnvil(Pos pos) {}

    /** 마법 부여대 화면 열기. */
    default void openEnchanting(Pos pos) {}

    /** 대장장이 작업대 화면 열기. */
    default void openSmithing(Pos pos) {}

    /** 베틀 화면 열기. */
    default void openLoom(Pos pos) {}

    /** 지도 제작대 화면 열기. */
    default void openCartography(Pos pos) {}

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

    /** 걸려 있는 상태 효과(속성 수정자·성급함/피로 판정용). 효과가 없는 행위자는 빈 목록. */
    default java.util.List<org.domi.furnace.server.effect.MobEffectInstance> activeEffects() { return java.util.List.of(); }

    /**
     * Player.getDestroySpeed(state): 손 도구 속도 → (1 넘으면) + 채굴 효율 속성(효율 인챈트 = 레벨²+1) → 성급함·전달체 힘 × (1 + (증폭+1)×0.2)
     * → 채굴 피로 × (0.3, 0.09, 0.0027, 8.1E-4) → × 블록 파괴 속도 속성 → 눈이 물에 잠기면 × 물속 채굴 속도(0.2, 친수성이면 ×(1+4·레벨))
     * → 공중이면 ÷ 5. float 연산 순서까지 바닐라 그대로.
     */
    default float destroySpeed(int state) {
        ItemStack hand = itemInHand(MAIN_HAND);
        var fx = activeEffects();
        ItemStack[] armor = {headItem(), equipment(6), equipment(7), equipment(8)};
        float f = org.domi.furnace.server.item.ItemTools.destroySpeed(hand, state);
        if (f > 1.0F) {
            f += (float) org.domi.furnace.server.entity.PlayerAttributes.value(
                    org.domi.furnace.server.attribute.Attribute.MINING_EFFICIENCY, hand, armor, fx);
        }
        int dig = -1, fatigue = -1;   // MobEffectUtil.hasDigSpeed/getDigSpeedAmplification: 성급함과 전달체 힘 중 큰 증폭
        for (var e : fx) {
            if (e.effect() == org.domi.furnace.server.effect.MobEffect.HASTE || e.effect() == org.domi.furnace.server.effect.MobEffect.CONDUIT_POWER) {
                dig = Math.max(dig, e.amplifier());
            } else if (e.effect() == org.domi.furnace.server.effect.MobEffect.MINING_FATIGUE) {
                fatigue = e.amplifier();
            }
        }
        if (dig >= 0) f *= 1.0F + (float) (dig + 1) * 0.2F;
        if (fatigue >= 0) {
            f *= switch (fatigue) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
        }
        f *= (float) org.domi.furnace.server.entity.PlayerAttributes.value(
                org.domi.furnace.server.attribute.Attribute.BLOCK_BREAK_SPEED, hand, armor, fx);
        if (eyeInWater()) {
            f *= (float) org.domi.furnace.server.entity.PlayerAttributes.value(
                    org.domi.furnace.server.attribute.Attribute.SUBMERGED_MINING_SPEED, hand, armor, fx);
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
