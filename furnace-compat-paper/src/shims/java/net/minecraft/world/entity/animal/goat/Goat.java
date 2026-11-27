package net.minecraft.world.entity.animal.goat;

import org.domi.furnace.compat.nms.NmsEntityData;
import org.domi.furnace.compat.nms.NmsEntityOwner;

/** 염소 뿔(FancyNpcs). 바닐라 addHorns·removeHorns 는 두 칸을 차례로 쓴다 — 염소는 onSyncedDataUpdated 를 덮지 않아 반응 없는 쓰기다. */
class Goat {

    /** 바닐라 addHorns: 왼쪽 → 오른쪽 뿔 칸을 참으로. */
    public void addHorns() {
        NmsEntityOwner.write(this, "Goat#addHorns");
        NmsEntityData.set(this, DATA_HAS_LEFT_HORN, true);
        NmsEntityData.set(this, DATA_HAS_RIGHT_HORN, true);
    }

    /** 바닐라 removeHorns: 왼쪽 → 오른쪽 뿔 칸을 거짓으로. */
    public void removeHorns() {
        NmsEntityOwner.write(this, "Goat#removeHorns");
        NmsEntityData.set(this, DATA_HAS_LEFT_HORN, false);
        NmsEntityData.set(this, DATA_HAS_RIGHT_HORN, false);
    }
}
