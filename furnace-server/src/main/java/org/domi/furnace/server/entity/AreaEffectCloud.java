// 바닐라 AreaEffectCloud: 지속 물약·드래곤의 숨결이 남기는 구름. 대기(waitTime) 뒤 5틱마다 반경 안 생물에게 효과를 주고(대상마다 reapplicationDelay),
// 반경이 틱·사용마다 줄고 duration 이 끝나면 사라진다. 효과는 물약 효과 목록으로 직접 가진다(물약 기본 효과의 1/4 지속 시간은 만든 쪽이 적용).
// TODO: 입자 종류 지정(NBT Particle), canBeAffected 면역 검사.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class AreaEffectCloud extends Entity {

    /** getPistonPushReaction = IGNORE. */
    @Override
    public boolean ignoresPistonPush() { return true; }

    public static final int DATA_RADIUS = 8, DATA_WAITING = 9, DATA_PARTICLE = 10;
    private static final int ENTITY_EFFECT_PARTICLE = 20;
    public static final int DRAGON_BREATH_PARTICLE = 7;

    private final List<MobEffectInstance> effects = new ArrayList<>();
    private int color = -1;   // -1 이면 효과 없음(바닐라 기본 입자 색)
    private final Map<Object, Integer> victims = new HashMap<>();
    private int duration = 600, waitTime = 20, reapplicationDelay = 20, durationOnUse;
    private float radiusOnUse, radiusPerTick;
    private Targetable owner;
    private UUID ownerUUID;

    public AreaEffectCloud(ChunkLevel level, int id) {
        super(EntityType.AREA_EFFECT_CLOUD, level, id);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, SetEntityData.SERIALIZER_FLOAT, 3.0F);
        entityData.define(DATA_WAITING, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_PARTICLE, SetEntityData.SERIALIZER_PARTICLE, new int[] {ENTITY_EFFECT_PARTICLE, -1});
    }

    public float getRadius() { return (Float) entityData.get(DATA_RADIUS); }

    public void setRadius(float r) {
        entityData.set(DATA_RADIUS, Math.max(0.0F, Math.min(32.0F, r)));
        setDimensions(getRadius() * 2.0F, 0.5F, dimEye);
    }

    public void setEffects(List<MobEffectInstance> list, int rgb) {
        effects.clear();
        effects.addAll(list);
        color = rgb;
        entityData.set(DATA_PARTICLE, new int[] {ENTITY_EFFECT_PARTICLE, effects.isEmpty() ? 0 : 0xFF000000 | rgb});
    }

    public void setParticle(int[] particle) { entityData.set(DATA_PARTICLE, particle); }

    public int durationTicks() { return duration; }

    public void setOwner(Targetable o) {
        owner = o;
        ownerUUID = o == null ? null : NeutralMob.uuidOf(o);
    }

    public Targetable getOwner() { return owner; }

    public void setDuration(int d) { duration = d; }

    public void setWaitTime(int t) { waitTime = t; }

    public void setRadiusOnUse(float r) { radiusOnUse = r; }

    public void setRadiusPerTick(float r) { radiusPerTick = r; }

    public void setDurationOnUse(int d) { durationOnUse = d; }

    private boolean isWaiting() { return (Boolean) entityData.get(DATA_WAITING); }

    @Override
    public void tick() {
        double px = x(), py = y(), pz = z();
        super.tick();
        setPos(px, py, pz);
        if (tickCount >= waitTime + duration) {
            discard();
            return;
        }
        boolean waiting = tickCount < waitTime;
        if (isWaiting() != waiting) entityData.set(DATA_WAITING, waiting);
        if (waiting) return;
        float radius = getRadius();
        if (radiusPerTick != 0.0F) {
            radius += radiusPerTick;
            if (radius < 0.5F) {
                discard();
                return;
            }
            setRadius(radius);
        }
        if (tickCount % 5 != 0) return;
        victims.entrySet().removeIf(e -> tickCount >= e.getValue());
        if (effects.isEmpty()) {
            victims.clear();
            return;
        }
        List<Object> targets = new ArrayList<>();
        for (Entity e : level.getEntities(this, boundingBox(), en -> en instanceof LivingEntity)) targets.add(e);
        level.forEachPlayerIn(boundingBox(), targets::add);
        for (Object t : targets) {
            if (victims.containsKey(t)) continue;
            double tx = t instanceof Player p ? p.x() : ((Entity) t).x(), tz = t instanceof Player p ? p.z() : ((Entity) t).z();
            double dx = tx - x(), dz = tz - z();
            if (dx * dx + dz * dz > radius * radius) continue;
            victims.put(t, tickCount + reapplicationDelay);
            for (MobEffectInstance mi : effects) {
                if (mi.effect().isInstantaneous()) {
                    if (t instanceof Player p) p.vitals().applyInstantaneousEffect(mi.effect(), mi.amplifier(), 0.5);
                    else mi.effect().applyInstantenousEffect(((LivingEntity) t).effectHost(), mi.amplifier(), 0.5);
                } else if (t instanceof Player p) {
                    p.vitals().addEffect(new MobEffectInstance(mi));
                } else {
                    ((LivingEntity) t).addEffect(new MobEffectInstance(mi));
                }
            }
            if (radiusOnUse != 0.0F) {
                radius += radiusOnUse;
                if (radius < 0.5F) {
                    discard();
                    return;
                }
                setRadius(radius);
            }
            if (durationOnUse != 0) {
                duration += durationOnUse;
                if (duration <= 0) {
                    discard();
                    return;
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putInt("Age", tickCount);
        tag.putInt("Duration", duration);
        tag.putInt("WaitTime", waitTime);
        tag.putInt("ReapplicationDelay", reapplicationDelay);
        tag.putInt("DurationOnUse", durationOnUse);
        tag.putFloat("RadiusOnUse", radiusOnUse);
        tag.putFloat("RadiusPerTick", radiusPerTick);
        tag.putFloat("Radius", getRadius());
        if (ownerUUID != null) tag.putIntArray("Owner", new int[] {(int) (ownerUUID.getMostSignificantBits() >> 32), (int) ownerUUID.getMostSignificantBits(), (int) (ownerUUID.getLeastSignificantBits() >> 32), (int) ownerUUID.getLeastSignificantBits()});
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        tickCount = tag.getInt("Age");
        duration = tag.getInt("Duration");
        waitTime = tag.getInt("WaitTime");
        reapplicationDelay = tag.getInt("ReapplicationDelay");
        durationOnUse = tag.getInt("DurationOnUse");
        radiusOnUse = tag.getFloat("RadiusOnUse");
        radiusPerTick = tag.getFloat("RadiusPerTick");
        setRadius(tag.getFloat("Radius"));
        if (tag.tags().get("Owner") instanceof int[] u && u.length == 4) {
            ownerUUID = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
            owner = level.getTargetableByUuid(ownerUUID);
        }
    }
}
