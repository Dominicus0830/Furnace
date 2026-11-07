// 바닐라 AreaEffectCloud: 지속 물약·드래곤의 숨결이 남기는 구름. 대기(waitTime) 뒤 5틱마다 반경 안 생물에게 효과를 주고(대상마다 reapplicationDelay),
// 반경이 틱·사용마다 줄고 duration 이 끝나면 사라진다. 효과는 바닐라 PotionContents 처럼 기본 물약(potion, 효과 지속 1/4) + 사용자 지정 효과(custom_effects, 그대로)로 가진다.
// 입자(NBT Particle)는 아무 종류나 가질 수 있고, entity_effect 면 물약 색으로 다시 칠한다. 생물마다 canBeAffected 로 걸 수 있는 효과가 하나도 없으면 건너뛴다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.command.arguments.ParticleArgument;
import org.domi.furnace.server.effect.Potions;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.ParticleNbt;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class AreaEffectCloud extends Entity {

    /** getPistonPushReaction = IGNORE. */
    @Override
    public boolean ignoresPistonPush() { return true; }

    public static final int DATA_RADIUS = 8, DATA_WAITING = 9, DATA_PARTICLE = 10;
    public static final ParticleOptions DRAGON_BREATH_PARTICLE = ParticleOptions.simple("dragon_breath");

    /** PotionContents: 기본 물약 id(없으면 null) + 사용자 지정 효과 + 사용자 지정 색(없으면 null). */
    private String potion;
    private final List<MobEffectInstance> effects = new ArrayList<>();
    private Integer customColor;
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
        entityData.define(DATA_PARTICLE, SetEntityData.SERIALIZER_PARTICLE, new ParticleOptions.Color(-1));
    }

    public float getRadius() { return (Float) entityData.get(DATA_RADIUS); }

    public void setRadius(float r) {
        entityData.set(DATA_RADIUS, Math.max(0.0F, Math.min(32.0F, r)));
        setDimensions(getRadius() * 2.0F, 0.5F, dimEye);
    }

    /** 사용자 지정 효과만 둔다(바닐라 addEffect: 지속 시간을 줄이지 않는다). */
    public void setEffects(List<MobEffectInstance> list) { setPotionContents(null, list, null); }

    /** 바닐라 setPotionContents. 입자가 entity_effect 면 그 색을 물약 색(불투명)으로 바꾼다. */
    public void setPotionContents(String potionId, List<MobEffectInstance> custom, Integer color) {
        potion = potionId;
        effects.clear();
        effects.addAll(custom);
        customColor = color;
        updateColor();
    }

    private boolean contentsEmpty() { return potion == null && effects.isEmpty() && customColor == null; }

    /** PotionContents.getAllEffects 의 구름 쪽: 기본 물약 효과는 지속 시간 1/4, 사용자 지정 효과는 그대로. */
    List<MobEffectInstance> allEffects() {
        List<MobEffectInstance> list = new ArrayList<>();
        if (potion != null) {
            for (MobEffectInstance mi : Potions.effectsOf(potion)) {
                list.add(new MobEffectInstance(mi.effect(), mi.effect().isInstantaneous() ? mi.duration() : mi.duration() / 4, mi.amplifier(), mi.ambient(), mi.visible(), mi.showIcon()));
            }
        }
        list.addAll(effects);
        return list;
    }

    /** PotionContents.getColor: 사용자 지정 색, 없으면 효과 색 평균(효과가 없으면 물 색). 지속 시간과 무관해 1/4 전 효과로 센다. */
    private int contentsColor() {
        if (customColor != null) return customColor;
        List<MobEffectInstance> all = new ArrayList<>(potion == null ? List.<MobEffectInstance>of() : Potions.effectsOf(potion));
        all.addAll(effects);
        return all.isEmpty() ? -13083194 : Creeper.effectsColor(all);
    }

    private void updateColor() {
        if (entityData.get(DATA_PARTICLE) instanceof ParticleOptions.Color) {
            int c = contentsEmpty() ? 0 : contentsColor();
            entityData.set(DATA_PARTICLE, new ParticleOptions.Color(0xFF000000 | c));
        }
    }

    public ParticleOptions getParticle() { return (ParticleOptions) entityData.get(DATA_PARTICLE); }

    public void setParticle(ParticleOptions particle) {
        entityData.set(DATA_PARTICLE, particle);
        updateColor();
    }

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
        List<MobEffectInstance> effects = allEffects();
        if (effects.isEmpty()) {
            victims.clear();
            return;
        }
        List<Object> targets = new ArrayList<>();
        for (Entity e : level.getEntities(this, boundingBox(), en -> en instanceof LivingEntity le && le.isAffectedByPotions())) targets.add(e);
        level.forEachPlayerIn(boundingBox(), p -> { if (!(p.vitals().health() <= 0.0F)) targets.add(p); });   // 죽어 가는 대상은 건너뜀(isAffectedByPotions)
        for (Object t : targets) {
            if (victims.containsKey(t)) continue;
            if (t instanceof LivingEntity le && effects.stream().noneMatch(le::canBeAffected)) continue;   // 걸 수 있는 효과가 하나도 없으면(언데드의 재생·독 등)
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
        tag.putCompound("Particle", ParticleNbt.encode(getParticle()));
        if (!contentsEmpty()) tag.putCompound("potion_contents", savePotionContents());
        if (ownerUUID != null) tag.putIntArray("Owner", new int[] {(int) (ownerUUID.getMostSignificantBits() >> 32), (int) ownerUUID.getMostSignificantBits(), (int) (ownerUUID.getLeastSignificantBits() >> 32), (int) ownerUUID.getLeastSignificantBits()});
    }

    /** PotionContents.CODEC 저장 모양: {potion, custom_color, custom_effects}. */
    private NbtCompound savePotionContents() {
        NbtCompound c = new NbtCompound();
        if (potion != null) c.putString("potion", potion);
        if (customColor != null) c.putInt("custom_color", customColor);
        if (!effects.isEmpty()) {
            NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
            for (MobEffectInstance mi : effects) l.add(mi.save());
            c.putList("custom_effects", l);
        }
        return c;
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
        if (tag.tags().get("Particle") instanceof NbtCompound pc) {
            ParticleOptions p = ParticleArgument.decodeNbt(pc);
            if (p != null) entityData.set(DATA_PARTICLE, p);
        }
        if (tag.tags().get("potion_contents") instanceof String ps) {
            // 바닐라 PotionContents.CODEC 의 대체 형식: 물약 id 문자열만.
            String id = ps.indexOf(':') < 0 ? "minecraft:" + ps : ps;
            setPotionContents(Potions.exists(id) ? id : null, new ArrayList<>(), null);
        } else if (tag.tags().get("potion_contents") instanceof NbtCompound pc) {
            String id = pc.tags().get("potion") instanceof String s ? (s.indexOf(':') < 0 ? "minecraft:" + s : s) : null;
            if (id != null && !Potions.exists(id)) id = null;
            Integer col = pc.tags().get("custom_color") instanceof Number n ? n.intValue() : null;
            List<MobEffectInstance> custom = new ArrayList<>();
            if (pc.tags().get("custom_effects") instanceof NbtList l) {
                for (Object o : l.items()) if (o instanceof NbtCompound ec && MobEffectInstance.load(ec) instanceof MobEffectInstance mi) custom.add(mi);
            }
            setPotionContents(id, custom, col);
        }
        if (tag.tags().get("Owner") instanceof int[] u && u.length == 4) {
            ownerUUID = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
            owner = level.getTargetableByUuid(ownerUUID);
        }
    }
}
