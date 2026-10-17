// 블록을 부수는 플레이어 행위자: 손·머리 장비 사본, 땅·물속 상태, 허기·통계·도구 부서짐 이벤트까지(ServerPlayerGameMode 가 보는 Player 부분).
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.GameMode;

public class BreakerActor extends PlayerActor {

    private final ItemStack head, originalMain;
    private final boolean eyeInWater;
    private final int heldWindowSlot;

    public BreakerActor(Player player, ItemStack mainHand, ItemStack offHand, ItemStack head, int permissionLevel, boolean eyeInWater,
                        int heldWindowSlot) {
        super(player, mainHand, offHand, permissionLevel);
        this.head = head == null ? ItemStack.EMPTY : head;
        this.originalMain = mainHand == null ? ItemStack.EMPTY : mainHand;
        this.eyeInWater = eyeInWater;
        this.heldWindowSlot = heldWindowSlot;
    }

    /** 사본을 잡을 때의 주 손 스택(인벤토리 반영 때 그 칸이 그대로인지 본다). */
    public ItemStack originalMainHand() { return originalMain; }

    /** 주 손 창 칸(36 + 선택 칸). */
    public int heldWindowSlot() { return heldWindowSlot; }

    @Override public boolean onGround() { return player().onGround(); }
    @Override public boolean eyeInWater() { return eyeInWater; }
    @Override public ItemStack headItem() { return head; }
    @Override public int entityId() { return player().entityId(); }
    @Override public void causeFoodExhaustion(float amount) { player().vitals().causeFoodExhaustion(amount); }

    /**
     * Player.blockActionRestricted(level, pos, gameMode): 모험·관전만 제한 — 관전은 늘, 모험은 mayBuild 가 아니면
     * 손 아이템의 can_break 술어가 그 블록에 맞아야 한다.
     */
    public boolean blockActionRestricted(BlockLevel level, Pos pos) {
        GameMode mode = player().gameMode();
        if (mode != GameMode.ADVENTURE && mode != GameMode.SPECTATOR) return false;
        if (mode == GameMode.SPECTATOR) return true;
        if (mayBuild()) return false;
        ItemStack held = itemInHand(MAIN_HAND);
        if (held.isEmpty()) return true;
        Object c = BlockPlacement.component(held, "minecraft:can_break");
        return c == null || !AdventurePredicate.test(c, level, pos);
    }

    /** LivingEntity.onEquippedItemBroken(item, MAINHAND): 부서짐 엔티티 이벤트(47)를 자신·추적자에게, 통계 broken. */
    public void onMainHandBroken(ItemStack broken) {
        awardStat("minecraft:broken:" + org.domi.furnace.server.item.ItemTools.itemName(broken));
        brokenEvent(broken);
    }

    /** 부서짐 이벤트 방송(연결이 엔티티 이벤트 패킷을 보낸다). */
    protected void brokenEvent(ItemStack broken) {}
}
