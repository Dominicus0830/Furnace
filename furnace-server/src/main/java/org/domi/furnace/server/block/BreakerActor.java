// 블록을 부수는 플레이어 행위자: 손·머리 장비 사본, 땅·물속 상태, 허기·통계·도구 부서짐 이벤트까지(ServerPlayerGameMode 가 보는 Player 부분).
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.GameMode;

public class BreakerActor extends PlayerActor {

    private final ItemStack head, originalMain, originalOff;
    private final boolean eyeInWater;
    private final int heldWindowSlot;
    /** 창 칸 5..8(갑옷)의 사본·처음 값·바뀜. */
    private final ItemStack[] armor = new ItemStack[4], originalArmor = new ItemStack[4];
    private final boolean[] armorChanged = new boolean[4];
    private Player.InventoryHost inventory;

    public BreakerActor(Player player, ItemStack mainHand, ItemStack offHand, ItemStack head, int permissionLevel, boolean eyeInWater,
                        int heldWindowSlot) {
        super(player, mainHand, offHand, permissionLevel);
        this.head = head == null ? ItemStack.EMPTY : head;
        this.originalMain = mainHand == null ? ItemStack.EMPTY : mainHand;
        this.originalOff = offHand == null ? ItemStack.EMPTY : offHand;
        this.eyeInWater = eyeInWater;
        this.heldWindowSlot = heldWindowSlot;
        java.util.Arrays.fill(armor, ItemStack.EMPTY);
        java.util.Arrays.fill(originalArmor, ItemStack.EMPTY);
        armor[0] = originalArmor[0] = this.head;
    }

    /** 인벤토리(갑옷 칸 읽기·넣기·스폰 보호 판정)를 잇는다. */
    public BreakerActor withInventory(Player.InventoryHost inv) {
        this.inventory = inv;
        if (inv != null) {
            for (int i = 0; i < 4; i++) armor[i] = originalArmor[i] = inv.slot(5 + i);
        }
        return this;
    }

    public ItemStack originalOffHand() { return originalOff; }

    public boolean armorChanged(int windowSlot) { return armorChanged[windowSlot - 5]; }

    public ItemStack originalArmor(int windowSlot) { return originalArmor[windowSlot - 5]; }

    @Override
    public ItemStack equipment(int windowSlot) { return armor[windowSlot - 5]; }

    @Override
    public void setEquipment(int windowSlot, ItemStack stack) {
        armor[windowSlot - 5] = stack == null ? ItemStack.EMPTY : stack;
        armorChanged[windowSlot - 5] = true;
    }

    @Override
    public boolean mayInteract(BlockLevel level, Pos pos) {
        return (inventory == null || inventory.mayInteract(pos.x(), pos.z())) && level.withinBorder(pos);
    }

    @Override
    public long nextEntityLong() { return player().random().nextLong(); }

    /** Inventory.add: 다 넣었으면 true(일부만 들어가도 그만큼은 들어간다 — 남은 것은 부르는 쪽이 떨군다). */
    @Override
    public boolean addToInventory(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemStack left = player().inventory().add(stack, instabuild(), org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(stack),
                org.domi.furnace.server.item.ItemTools.damage(stack) > 0);
        return left.isEmpty();
    }

    @Override
    public boolean inventoryContains(ItemStack stack) { return player().inventory().contains(stack); }

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
    public void onMainHandBroken(ItemStack broken) { onEquippedItemBroken(MAIN_HAND, broken); }

    @Override
    public void onEquippedItemBroken(int hand, ItemStack broken) {
        awardStat("minecraft:broken:" + org.domi.furnace.server.item.ItemTools.itemName(broken));
        brokenEvent(hand, broken);
    }

    /** 부서짐 이벤트 방송(주 손 47, 왼손 48 — 연결이 엔티티 이벤트 패킷을 보낸다). */
    protected void brokenEvent(int hand, ItemStack broken) {}
}
