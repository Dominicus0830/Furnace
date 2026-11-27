// 바닐라 Interaction: 크기(width, height)만 가진 보이지 않는 상호작용 상자. 마지막으로 때린/우클릭한 플레이어와 시각을 NBT 로 가진다.
// 플레이어 공격은 skipAttackInteraction, 우클릭은 interact 가 기록한다(Region.attackEntity·ConnectionHandler 가 부른다).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.entity.Interaction")
public class Interaction extends Entity {

    /** getPistonPushReaction = IGNORE. */
    @Override
    public boolean ignoresPistonPush() { return true; }

    public static final int DATA_WIDTH = EntityDataIds.Interaction_DATA_WIDTH_ID, DATA_HEIGHT = EntityDataIds.Interaction_DATA_HEIGHT_ID, DATA_RESPONSE = EntityDataIds.Interaction_DATA_RESPONSE_ID;
    private int[] attackPlayer, interactionPlayer;
    private long attackTime, interactionTime;

    public Interaction(ChunkLevel level, int id) {
        super(EntityType.INTERACTION, level, id);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_WIDTH, SetEntityData.SERIALIZER_FLOAT, 1.0F);
        entityData.define(DATA_HEIGHT, SetEntityData.SERIALIZER_FLOAT, 1.0F);
        entityData.define(DATA_RESPONSE, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    /**
     * 바닐라 makeBoundingBox: 상자는 늘 칸의 너비·높이로 만든다(getDimensions() = scalable(width, height)). 몸 크기(dimW·dimH·눈 높이)는
     * 종류 기본값(0×0) 그대로이고 자세가 바뀔 때({@link #refreshDimensions})만 칸을 따라간다 — 칸 쓰기는 상자만 바꾼다.
     */
    @Override
    protected AABB makeBb(double px, double py, double pz) {
        double hw = (Float) entityData.get(DATA_WIDTH) / 2.0F;
        float h = (Float) entityData.get(DATA_HEIGHT);
        return new AABB(px - hw, py, pz - hw, px + hw, py + h, pz + hw);
    }

    /** 바닐라 refreshDimensions(getDimensions(pose) = scalable(width, height), 눈 높이 0.85 배). noPhysics 라 자리 옮기기는 없다. */
    @Override
    protected void refreshDimensions() {
        float h = (Float) entityData.get(DATA_HEIGHT);
        setDimensions((Float) entityData.get(DATA_WIDTH), h, h * 0.85F);
    }

    public void setWidth(float w) { entityData.set(DATA_WIDTH, w); }

    public void setHeight(float h) { entityData.set(DATA_HEIGHT, h); }

    /** Interaction.onSyncedDataUpdated: 너비·높이 칸이 바뀌면 상자만 다시 만든다(Entity 다음, setBoundingBox(makeBoundingBox())). */
    @Override
    protected void onSyncedDataUpdated(int index) {
        super.onSyncedDataUpdated(index);
        if (index == DATA_HEIGHT || index == DATA_WIDTH) refreshBoundingBox();
    }

    public void setResponse(boolean r) { entityData.set(DATA_RESPONSE, r); }

    public boolean response() { return (Boolean) entityData.get(DATA_RESPONSE); }

    @Override public void tick() {}

    @Override public boolean isPickable() { return true; }

    /**
     * 바닐라 skipAttackInteraction: 때린 플레이어와 게임 시각을 기록하고 player_hurt_entity(일반 피해 1/1, 막지 않음)를 낸다.
     * response 가 꺼져 있으면 true(일반 공격 처리를 건너뜀), 켜져 있으면 false(공격 소리·소모는 평소대로).
     */
    @Override
    public boolean skipAttackInteraction(Player attacker) {
        attackPlayer = uuidInts(attacker.uuid());
        attackTime = level.gameTime();
        var adv = attacker.advancements();
        if (adv != null && adv.hasListeners("minecraft:player_hurt_entity")) {
            var victim = KillCredit.lootOf(this);
            var dmg = KillCredit.source("generic", null, null);
            attacker.triggerAdvancement(a -> a.triggerHurtEntity(victim, dmg, 1.0F, 1.0F, false));
        }
        return !response();
    }

    /** 바닐라 interact(서버): 우클릭한 플레이어와 게임 시각을 기록한다(결과 CONSUME). */
    public boolean interact(Player p) {
        interactionPlayer = uuidInts(p.uuid());
        interactionTime = level.gameTime();
        return true;
    }

    private static int[] uuidInts(java.util.UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    /** 마지막으로 때린 플레이어 UUID(없으면 null)와 시각(게임 틱). */
    public java.util.UUID lastAttackPlayer() { return fromInts(attackPlayer); }

    public long lastAttackTime() { return attackTime; }

    public java.util.UUID lastInteractionPlayer() { return fromInts(interactionPlayer); }

    public long lastInteractionTime() { return interactionTime; }

    private static java.util.UUID fromInts(int[] u) {
        return u == null ? null : new java.util.UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putFloat("width", (Float) entityData.get(DATA_WIDTH));
        tag.putFloat("height", (Float) entityData.get(DATA_HEIGHT));
        if (attackPlayer != null) tag.putCompound("attack", record(attackPlayer, attackTime));
        if (interactionPlayer != null) tag.putCompound("interaction", record(interactionPlayer, interactionTime));
        tag.putBoolean("response", response());
    }

    private static NbtCompound record(int[] player, long time) {
        NbtCompound c = new NbtCompound();
        c.putIntArray("player", player);
        c.putLong("timestamp", time);
        return c;
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.contains("width")) entityData.set(DATA_WIDTH, tag.getFloat("width"));
        if (tag.contains("height")) entityData.set(DATA_HEIGHT, tag.getFloat("height"));
        attackPlayer = null;
        interactionPlayer = null;
        if (tag.tags().get("attack") instanceof NbtCompound c && c.tags().get("player") instanceof int[] p) {
            attackPlayer = p;
            attackTime = c.getLong("timestamp");
        }
        if (tag.tags().get("interaction") instanceof NbtCompound c && c.tags().get("player") instanceof int[] p) {
            interactionPlayer = p;
            interactionTime = c.getLong("timestamp");
        }
        entityData.set(DATA_RESPONSE, tag.getBoolean("response"));
        refreshBoundingBox();
    }
}
