// 바닐라 BegGoal: 먹이나 뼈를 든 플레이어에게 고개를 갸웃하며 구걸한다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Wolf;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.item.ItemTools;

public class BegGoal extends Goal {
    private final Wolf wolf;
    private Player player;
    private final float lookDistance;
    private int lookTime;
    private final TargetingConditions begTargeting;

    public BegGoal(Wolf wolf, float lookDistance) {
        this.wolf = wolf;
        this.lookDistance = lookDistance;
        this.begTargeting = TargetingConditions.forNonCombat().range(lookDistance);
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        player = wolf.level().getNearestPlayer(begTargeting, wolf, wolf.x(), wolf.y(), wolf.z());
        return player == null ? false : playerHoldingInteresting(player);
    }

    @Override
    public boolean canContinueToUse() {
        if (!player.isAlive()) return false;
        double dx = wolf.x() - player.x(), dy = wolf.y() - player.y(), dz = wolf.z() - player.z();
        if (dx * dx + dy * dy + dz * dz > lookDistance * lookDistance) return false;
        return lookTime > 0 && playerHoldingInteresting(player);
    }

    @Override
    public void start() {
        wolf.setIsInterested(true);
        lookTime = adjustedTickDelay(40 + wolf.random().nextInt(40));
    }

    @Override
    public void stop() {
        wolf.setIsInterested(false);
        player = null;
    }

    @Override
    public void tick() {
        wolf.getLookControl().setLookAt(player.x(), player.eyeY(), player.z(), 10.0F, wolf.getMaxHeadXRot());
        lookTime--;
    }

    private boolean playerHoldingInteresting(Player p) {
        var inv = p.inventory();
        for (int slot : new int[] {inv.heldWindowSlot(), 45}) {
            var s = inv.get(slot);
            if (!s.isEmpty() && (ItemTools.itemName(s).equals("minecraft:bone") || wolf.isFood(s))) return true;
        }
        return false;
    }
}
