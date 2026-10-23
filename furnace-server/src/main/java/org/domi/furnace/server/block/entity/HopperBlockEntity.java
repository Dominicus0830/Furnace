// 바닐라 HopperBlockEntity: 5칸 용기. 옮기기(쿨다운·흡수)는 아직 — TODO(깔때기 동작). 저장에는 TransferCooldown 이 있다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class HopperBlockEntity extends ContainerBlockEntity {

    /** 다음 옮기기까지 남은 틱(바닐라 cooldownTime, 기본 -1). */
    public int cooldownTime = -1;

    public HopperBlockEntity(Pos pos, int state) { super("minecraft:hopper", pos, state, 5); }

    @Override public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "hopper"); }

    @Override public String defaultTitleKey() { return "container.hopper"; }

    @Override
    public void load(NbtCompound tag) {
        super.load(tag);
        cooldownTime = tag.contains("TransferCooldown") ? tag.getIntOr0("TransferCooldown") : -1;
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        super.saveAdditional(tag);
        tag.putInt("TransferCooldown", cooldownTime);
    }
}
