// 바닐라 ItemBasedSteering(bti): 돼지·스트라이더의 안장과 막대 가속 상태. 안장 여부와 가속 총 시간은 동기화 데이터에 둔다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class ItemBasedSteering {
    private final SynchedData data;
    private final int boostTimeId, saddleId;
    private boolean boosting;
    private int boostTime;

    public ItemBasedSteering(SynchedData data, int boostTimeId, int saddleId) {
        this.data = data;
        this.boostTimeId = boostTimeId;
        this.saddleId = saddleId;
    }

    /** boost(random): 가속 중이면 거짓, 아니면 시작하고 총 시간 nextInt(841)+140 틱을 동기화한다. */
    public boolean boost(RandomSource random) {
        if (boosting) return false;
        boosting = true;
        boostTime = 0;
        data.set(boostTimeId, random.nextInt(841) + 140);
        return true;
    }

    /** tickBoost: 경과가 총 시간을 넘으면(비교 뒤 증가) 가속 끝. */
    public void tickBoost() {
        if (boosting && boostTime++ > boostTimeTotal()) boosting = false;
    }

    /** boostFactor: 가속 중이면 1 + 1.15·sin(경과/총 시간·π). 서버에서는 조종이 클라이언트 쪽이라 쓰지 않지만 바닐라 값을 둔다. */
    public float boostFactor() {
        return boosting ? 1.0F + 1.15F * org.domi.furnace.server.entity.ai.control.Mth.sin((float) boostTime / (float) boostTimeTotal() * (float) Math.PI) : 1.0F;
    }

    public boolean isBoosting() { return boosting; }

    private int boostTimeTotal() { return (Integer) data.get(boostTimeId); }

    public void addAdditionalSaveData(NbtCompound tag) { tag.putBoolean("Saddle", hasSaddle()); }

    public void readAdditionalSaveData(NbtCompound tag) { setSaddle(tag.getBoolean("Saddle")); }

    public void setSaddle(boolean v) { data.set(saddleId, v); }

    public boolean hasSaddle() { return (Boolean) data.get(saddleId); }

    /** Player.isHolding(item): 주 손 또는 보조 손(창 칸 45). */
    public static boolean isHolding(Player p, String item) {
        var inv = p.inventory();
        return ItemTools.itemName(inv.get(inv.heldWindowSlot())).equals(item) || ItemTools.itemName(inv.get(45)).equals(item);
    }
}
