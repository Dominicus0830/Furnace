// 바닐라 ServerPlayerGameMode 의 블록 부수기(handleBlockBreakAction·tick·destroyBlock·destroyAndAck).
// 플레이어마다 하나, 그 플레이어가 속한 region 스레드에서만 만진다. 서바이벌은 시작~완료 사이 경과 틱으로 진행도를 다시 계산해 검증하고
// (0.7 이상이면 바로, 모자라면 지연 파괴로 틱마다 이어 계산), 금 가는 단계는 다른 플레이어에게 방송한다.
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;

public final class BlockBreaking {

    /** 클라이언트 player_action 의 상태 번호. */
    public static final int START_DESTROY_BLOCK = 0, ABORT_DESTROY_BLOCK = 1, STOP_DESTROY_BLOCK = 2;

    /** 부수기 결과를 바깥(연결)에 알리는 통로 — 블록 갱신 패킷 보내기·플러그인 파괴 이벤트. */
    public interface Host {
        /** ClientboundBlockUpdatePacket(pos, 지금 상태)를 그 플레이어에게. */
        void sendBlockUpdate(BlockLevel level, Pos pos);

        /** 파괴 직전 허가(Paper BlockBreakEvent) — 거짓이면 부수지 않고 블록을 되돌려 보낸다. */
        default boolean allowBreak(BlockLevel level, Pos pos, int state) { return true; }

        /** 그 칸을 지금 region 이 만질 수 있는가(다른 region 칸이면 지연 파괴를 미룬다). */
        default boolean owns(Pos pos) { return true; }
    }

    private int gameTicks;
    private boolean isDestroyingBlock;
    private int destroyProgressStart;
    private Pos destroyPos = Pos.ZERO;
    private boolean hasDelayedDestroy;
    private Pos delayedDestroyPos = Pos.ZERO;
    private int delayedTickStart;
    private int lastSentState = -1;

    public int gameTicks() { return gameTicks; }

    public boolean isDestroyingBlock() { return isDestroyingBlock; }

    public boolean hasDelayedDestroy() { return hasDelayedDestroy; }

    /**
     * handleBlockBreakAction(pos, action, face, maxBuildHeight, sequence). ack 는 부르는 쪽이 이 뒤에 보낸다.
     * mayInteract: 스폰 보호·월드 보더 판정 결과(ServerLevel.mayInteract).
     */
    public void handleAction(BlockLevel level, BreakerActor player, Host host, Pos pos, int action, int maxBuildHeight, boolean mayInteract) {
        if (!player.canInteractWithBlock(pos, 1.0)) return;   // "too far" — 패킷 없음
        if (pos.y() >= maxBuildHeight) {
            host.sendBlockUpdate(level, pos);   // "too high"
            return;
        }
        switch (action) {
            case START_DESTROY_BLOCK -> {
                if (!mayInteract) {
                    host.sendBlockUpdate(level, pos);
                    return;
                }
                if (player.isCreative()) {
                    destroyAndAck(level, player, host, pos);
                    return;
                }
                if (player.blockActionRestricted(level, pos)) {
                    host.sendBlockUpdate(level, pos);
                    return;
                }
                destroyProgressStart = gameTicks;
                float f = 1.0F;
                int state = level.getBlockState(pos);
                boolean air = BlockInfo.get().isAir(state);
                if (!air) {
                    // TODO(인챈트 효과): EnchantmentHelper.onHitBlock(손 아이템 hit_block 효과 — 바닐라 인챈트에는 없다)
                    BlockBehaviors.of(state).attack(state, level, pos, player);
                    f = BlockBehaviors.of(state).getDestroyProgress(state, player, level, pos);
                }
                if (!air && f >= 1.0F) {
                    destroyAndAck(level, player, host, pos);
                } else {
                    if (isDestroyingBlock) host.sendBlockUpdate(level, destroyPos);   // 다른 블록을 시작함(클라 즉시 파괴를 서버가 거부)
                    isDestroyingBlock = true;
                    destroyPos = pos;
                    int i = (int) (f * 10.0F);
                    level.destroyBlockProgress(player.entityId(), pos, i);
                    lastSentState = i;
                }
            }
            case STOP_DESTROY_BLOCK -> {
                if (pos.equals(destroyPos)) {
                    int j = gameTicks - destroyProgressStart;
                    int state = level.getBlockState(pos);
                    if (!BlockInfo.get().isAir(state)) {
                        float f = BlockBehaviors.of(state).getDestroyProgress(state, player, level, pos) * (float) (j + 1);
                        if (f >= 0.7F) {
                            isDestroyingBlock = false;
                            level.destroyBlockProgress(player.entityId(), pos, -1);
                            destroyAndAck(level, player, host, pos);
                            return;
                        }
                        if (!hasDelayedDestroy) {
                            isDestroyingBlock = false;
                            hasDelayedDestroy = true;
                            delayedDestroyPos = pos;
                            delayedTickStart = destroyProgressStart;
                        }
                    }
                }
            }
            case ABORT_DESTROY_BLOCK -> {
                isDestroyingBlock = false;
                if (!destroyPos.equals(pos)) level.destroyBlockProgress(player.entityId(), destroyPos, -1);   // 바닐라는 경고 로그도 남긴다
                level.destroyBlockProgress(player.entityId(), pos, -1);
            }
            default -> {}
        }
    }

    /** ServerPlayerGameMode.tick 의 부수기 부분 — 매 틱 플레이어 틱에서. 행위자는 부수는 중일 때만 만든다. */
    public void tick(BlockLevel level, java.util.function.Supplier<BreakerActor> actor, Host host) {
        gameTicks++;
        if (!hasDelayedDestroy && !isDestroyingBlock) return;
        BreakerActor player = actor.get();
        if (player == null) return;
        tick(level, player, host);
    }

    private void tick(BlockLevel level, BreakerActor player, Host host) {
        if (hasDelayedDestroy) {
            if (!host.owns(delayedDestroyPos)) return;
            int state = level.getBlockState(delayedDestroyPos);
            if (BlockInfo.get().isAir(state)) {
                hasDelayedDestroy = false;
            } else {
                float f = incrementDestroyProgress(level, player, state, delayedDestroyPos, delayedTickStart);
                if (f >= 1.0F) {
                    hasDelayedDestroy = false;
                    destroyBlock(level, player, host, delayedDestroyPos);
                }
            }
        } else if (isDestroyingBlock) {
            if (!host.owns(destroyPos)) return;
            int state = level.getBlockState(destroyPos);
            if (BlockInfo.get().isAir(state)) {
                level.destroyBlockProgress(player.entityId(), destroyPos, -1);
                lastSentState = -1;
                isDestroyingBlock = false;
            } else {
                incrementDestroyProgress(level, player, state, destroyPos, destroyProgressStart);
            }
        }
    }

    private float incrementDestroyProgress(BlockLevel level, BreakerActor player, int state, Pos pos, int startTick) {
        int i = gameTicks - startTick;
        float f = BlockBehaviors.of(state).getDestroyProgress(state, player, level, pos) * (float) (i + 1);
        int j = (int) (f * 10.0F);
        if (j != lastSentState) {
            level.destroyBlockProgress(player.entityId(), pos, j);
            lastSentState = j;
        }
        return f;
    }

    /** destroyAndAck: 못 부수면 블록을 되돌려 보낸다. */
    public void destroyAndAck(BlockLevel level, BreakerActor player, Host host, Pos pos) {
        if (!destroyBlock(level, player, host, pos)) host.sendBlockUpdate(level, pos);
    }

    /**
     * destroyBlock(pos): 손 아이템이 블록을 칠 수 있는가 → 관리자 블록 권한 → 모험 제한 → playerWillDestroy → removeBlock → destroy →
     * (크리에이티브 끝) 맞는 도구 판정·도구 내구도(mineBlock) → 지웠고 맞는 도구면 playerDestroy(드롭).
     */
    public boolean destroyBlock(BlockLevel level, BreakerActor player, Host host, Pos pos) {
        int state = level.getBlockState(pos);
        ItemStack hand = player.itemInHand(BlockActor.MAIN_HAND);
        if (!canAttackBlock(hand, player)) return false;
        Object blockEntity = level.getBlockEntity(pos);
        BlockBehavior block = BlockBehaviors.of(state);
        if (isGameMasterBlock(state) && !player.canUseGameMasterBlocks()) {
            host.sendBlockUpdate(level, pos);   // sendBlockUpdated(pos, state, state, 3)
            return false;
        }
        if (player.blockActionRestricted(level, pos)) return false;
        if (!host.allowBreak(level, pos, state)) {
            host.sendBlockUpdate(level, pos);
            return false;
        }
        int willState = block.playerWillDestroy(level, pos, state, player);
        boolean removed = level.removeBlock(pos, false);
        if (removed) block.destroy(level, pos, willState);
        if (player.isCreative()) return true;
        ItemStack tool = player.itemInHand(BlockActor.MAIN_HAND);
        boolean correct = player.hasCorrectToolForDrops(willState);
        mineBlock(level, player, willState, pos, tool);
        if (removed && correct) block.playerDestroy(level, player, pos, willState, blockEntity, tool);
        return true;
    }

    /**
     * Item.canAttackBlock: 칼·삼지창·철퇴는 크리에이티브에서 블록을 못 친다, 디버그 막대는 언제나 못 친다(상호작용으로 쓴다).
     */
    static boolean canAttackBlock(ItemStack hand, BlockActor player) {
        String n = ItemTools.itemName(hand);
        if (n.equals("minecraft:debug_stick")) return false;
        if (n.endsWith("_sword") || n.equals("minecraft:trident") || n.equals("minecraft:mace")) return !player.isCreative();
        return true;
    }

    private static final int COMMAND_BLOCK = B.id("command_block"), CHAIN_COMMAND_BLOCK = B.id("chain_command_block"),
            REPEATING_COMMAND_BLOCK = B.id("repeating_command_block"), STRUCTURE_BLOCK = B.id("structure_block"), JIGSAW = B.id("jigsaw");

    /** GameMasterBlock: 명령 블록 3종·구조물 블록·직소 블록. */
    static boolean isGameMasterBlock(int state) {
        int b = B.block(state);
        return b == COMMAND_BLOCK || b == CHAIN_COMMAND_BLOCK || b == REPEATING_COMMAND_BLOCK || b == STRUCTURE_BLOCK || b == JIGSAW;
    }

    /**
     * ItemStack.mineBlock → Item.mineBlock: 도구(tool 컴포넌트)면 경도가 0 이 아닐 때 damage_per_block 만큼 내구도를 깎고 통계 used.
     */
    static void mineBlock(BlockLevel level, BreakerActor player, int state, Pos pos, ItemStack tool) {
        ItemTools.Tool t = ItemTools.tool(tool);
        if (t == null) return;
        if (BlockInfo.get().destroyTime(B.block(state)) != 0.0F && t.damagePerBlock() > 0) {
            hurtAndBreak(level, player, tool, t.damagePerBlock());
        }
        player.awardStat("minecraft:used:" + ItemTools.itemName(tool));
    }

    /**
     * ItemStack.hurtAndBreak(amount, level, player, MAINHAND): 무한 재료면 그대로, 내구 인챈트(remove_binomial: 레벨/(레벨+1),
     * 갑옷은 2L/(5L+5)… 여기서는 도구라 앞의 것)로 줄인 뒤 깎고, 다 닳으면 하나 줄이고 부서짐 이벤트.
     */
    static void hurtAndBreak(BlockLevel level, BreakerActor player, ItemStack stack, int amount) {
        if (!ItemTools.isDamageable(stack) || player.instabuild()) return;
        if (amount > 0) {
            int unbreaking = ItemTools.enchantmentLevel(stack, "minecraft:unbreaking");
            if (unbreaking > 0) {
                boolean armor = org.domi.furnace.server.item.ItemContext.isInTag("minecraft:item", "minecraft:enchantable/armor", stack.itemId());
                float chance = armor ? (2.0F + 2.0F * (unbreaking - 1)) / (10.0F + 5.0F * (unbreaking - 1))
                        : (float) unbreaking / (2.0F + (unbreaking - 1));
                int removed = 0;
                for (int j = 0; j < amount; j++) if (level.random().nextFloat() < chance) removed++;
                amount -= removed;
            }
            if (amount <= 0) return;
        }
        int newDamage = ItemTools.damage(stack) + amount;
        ItemStack damaged = ItemTools.withDamage(stack, newDamage);
        // TODO(발전 과제): CriteriaTriggers.ITEM_DURABILITY_CHANGED
        if (newDamage >= ItemTools.maxDamage(stack)) {
            player.setItemInHand(BlockActor.MAIN_HAND, damaged.withCount(damaged.count() - 1));
            player.onMainHandBroken(stack);
        } else {
            player.setItemInHand(BlockActor.MAIN_HAND, damaged);
        }
    }
}
