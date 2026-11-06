// 바닐라 JumpControl: jump() 가 불리면 이번 틱 끝에 몹의 jumping 을 켠다.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.entity.Mob;

public class JumpControl {
    private final Mob mob;
    protected boolean jump;

    public JumpControl(Mob mob) { this.mob = mob; }

    public void jump() { jump = true; }

    public void tick() {
        mob.setJumping(jump);
        jump = false;
    }
}
