package net.minecraft.world.entity.animal.camel;

import org.domi.furnace.api.internal.ChunkInternals.EntityEdit;
import org.domi.furnace.compat.nms.NmsEntityOwner;

/** 낙타 앉기·서기(FancyNpcs). 서버 낙타가 바닐라 규칙(소리·자세·entity_action·자세 바꾼 틱)으로 한다 — 월드 밖 낙타는 기록·재생. */
class Camel {

    /** 바닐라 sitDown: 앉아 있지 않으면 앉는다(자세 바꾼 틱은 −게임 시간). */
    public void sitDown() {
        NmsEntityOwner.write(this, "Camel#sitDown");
        furnaceCamelPose("Camel#sitDown", 0);
    }

    /** 바닐라 standUp: 앉아 있으면 일어선다(일어서는 동작을 거친다). */
    public void standUp() {
        NmsEntityOwner.write(this, "Camel#standUp");
        furnaceCamelPose("Camel#standUp", 1);
    }

    /** 바닐라 standUpInstantly: 동작 없이 바로 선 자세. */
    public void standUpInstantly() {
        NmsEntityOwner.write(this, "Camel#standUpInstantly");
        furnaceCamelPose("Camel#standUpInstantly", 2);
    }

    private void furnaceCamelPose(String what, int action) {
        var op = new EntityEdit.CamelPose(action);
        if (furnaceNotInWorld()) furnaceDetachedOp(what, op);
        else furnaceEditOf(what, op);
    }
}
