package net.minecraft.world.entity;

import org.domi.furnace.api.internal.ChunkInternals.EntityEdit;
import org.domi.furnace.compat.nms.NmsEntityOwner;

/** 길들인 동물의 앉은 자세(FancyNpcs 는 이벤트 없는 꼴로 부른다). 서버 엔티티가 Paper 규칙으로 한다 — 월드 밖은 기록·재생. */
class TamableAnimal {

    /** Paper setInSittingPose(sit): EntityToggleSitEvent 를 낸다. */
    public void setInSittingPose(boolean sit) {
        setInSittingPose(sit, true);
    }

    /** Paper setInSittingPose(sit, fireEvent): 이벤트를 내면 플러그인이 취소할 수 있다(취소면 그대로), 아니면 깃발 1 을 켜고 끈다. */
    public void setInSittingPose(boolean sit, boolean fireEvent) {
        NmsEntityOwner.write(this, "TamableAnimal#setInSittingPose");
        furnaceSitPose("TamableAnimal#setInSittingPose", sit, fireEvent);
    }

    private void furnaceSitPose(String what, boolean sit, boolean fireEvent) {
        var op = new EntityEdit.SitPose(sit, fireEvent);
        if (furnaceNotInWorld()) {
            // Paper 는 이벤트에 getBukkitEntity() 를 싣는다 — 월드 밖 엔티티는 Bukkit 엔티티가 없어 getBukkitEntity 처럼 멈춘다
            if (fireEvent) getBukkitEntity();
            furnaceDetachedOp(what, op);
        } else furnaceEditOf(what, op);
    }
}
