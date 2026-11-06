// 바닐라 OpenDoorGoal: 문 앞에서 열고, closeDoor 면 20틱 뒤 지나가서 닫는다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.Mob;

public class OpenDoorGoal extends DoorInteractGoal {
    private final boolean closeDoor;
    private int forgetTime;

    public OpenDoorGoal(Mob mob, boolean closeDoor) {
        super(mob);
        this.closeDoor = closeDoor;
    }

    @Override
    public boolean canContinueToUse() { return closeDoor && forgetTime > 0 && super.canContinueToUse(); }

    @Override
    public void start() {
        forgetTime = 20;
        setOpen(true);
    }

    @Override
    public void stop() { setOpen(false); }

    @Override
    public void tick() {
        forgetTime--;
        super.tick();
    }
}
