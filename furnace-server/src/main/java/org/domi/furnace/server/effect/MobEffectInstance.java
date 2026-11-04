// 바닐라 MobEffectInstance: 걸린 효과 하나(지속시간·증폭·환경(비콘)·입자·아이콘·덮어써진 약한 효과).
package org.domi.furnace.server.effect;

import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class MobEffectInstance implements Comparable<MobEffectInstance> {

    public static final int INFINITE_DURATION = -1;

    private final MobEffect effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    private MobEffectInstance hiddenEffect;

    public MobEffectInstance(MobEffect effect) { this(effect, 0, 0); }

    public MobEffectInstance(MobEffect effect, int duration) { this(effect, duration, 0); }

    public MobEffectInstance(MobEffect effect, int duration, int amplifier) { this(effect, duration, amplifier, false, true); }

    public MobEffectInstance(MobEffect effect, int duration, int amplifier, boolean ambient, boolean visible) { this(effect, duration, amplifier, ambient, visible, visible); }

    public MobEffectInstance(MobEffect effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        this(effect, duration, amplifier, ambient, visible, showIcon, null);
    }

    public MobEffectInstance(MobEffect effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon, MobEffectInstance hidden) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Math.clamp(amplifier, 0, 255);
        this.ambient = ambient;
        this.visible = visible;
        this.showIcon = showIcon;
        this.hiddenEffect = hidden;
    }

    public MobEffectInstance(MobEffectInstance o) {
        this.effect = o.effect;
        setDetailsFrom(o);
    }

    /** 숨은 효과까지 복사(복사 생성자는 바닐라처럼 숨은 효과를 버린다). */
    public MobEffectInstance deepCopy() {
        MobEffectInstance c = new MobEffectInstance(this);
        c.hiddenEffect = hiddenEffect == null ? null : hiddenEffect.deepCopy();
        return c;
    }

    void setDetailsFrom(MobEffectInstance o) {
        duration = o.duration;
        amplifier = o.amplifier;
        ambient = o.ambient;
        visible = o.visible;
        showIcon = o.showIcon;
    }

    public MobEffect effect() { return effect; }

    /** 바닐라 getParticleOptions: 이 효과가 몸에서 내는 입자. */
    public org.domi.furnace.server.net.protocol.play.ParticleOptions particleOptions() { return effect.particle(ambient); }

    /** 바닐라 updateSynchronizedMobEffectParticles 의 목록: 입자를 보이는 효과들의 입자(걸린 순서). */
    public static java.util.List<org.domi.furnace.server.net.protocol.play.ParticleOptions> visibleParticles(java.util.Collection<MobEffectInstance> effects) {
        if (effects.isEmpty()) return java.util.List.of();
        var out = new java.util.ArrayList<org.domi.furnace.server.net.protocol.play.ParticleOptions>(effects.size());
        for (MobEffectInstance e : effects) if (e.visible) out.add(e.particleOptions());
        return java.util.List.copyOf(out);
    }

    /** 바닐라 LivingEntity.areAllEffectsAmbient: 모든 효과가 환경 효과인가(비어 있으면 true). */
    public static boolean allAmbient(java.util.Collection<MobEffectInstance> effects) {
        for (MobEffectInstance e : effects) if (!e.ambient) return false;
        return true;
    }

    public int duration() { return duration; }

    public int amplifier() { return amplifier; }

    public boolean ambient() { return ambient; }

    public boolean visible() { return visible; }

    public boolean showIcon() { return showIcon; }

    public MobEffectInstance hiddenEffect() { return hiddenEffect; }

    public boolean isInfiniteDuration() { return duration == INFINITE_DURATION; }

    public boolean endsWithin(int ticks) { return !isInfiniteDuration() && duration <= ticks; }

    /**
     * update(other): 같은 효과가 또 걸렸다 — 더 센 쪽이 이기고 약한 쪽은 숨은 효과로 남는다. 바뀐 게 있으면 true.
     */
    public boolean update(MobEffectInstance other) {
        boolean changed = false;
        if (other.amplifier > amplifier) {
            if (other.isShorterDurationThan(this)) {
                MobEffectInstance old = hiddenEffect;
                hiddenEffect = new MobEffectInstance(this);
                hiddenEffect.hiddenEffect = old;
            }
            amplifier = other.amplifier;
            duration = other.duration;
            changed = true;
        } else if (isShorterDurationThan(other)) {
            if (other.amplifier == amplifier) {
                duration = other.duration;
                changed = true;
            } else if (hiddenEffect == null) {
                hiddenEffect = new MobEffectInstance(other);
            } else {
                hiddenEffect.update(other);
            }
        }
        if (!other.ambient && ambient || changed) {
            ambient = other.ambient;
            changed = true;
        }
        if (other.visible != visible) {
            visible = other.visible;
            changed = true;
        }
        if (other.showIcon != showIcon) {
            showIcon = other.showIcon;
            changed = true;
        }
        return changed;
    }

    private boolean isShorterDurationThan(MobEffectInstance o) { return !isInfiniteDuration() && (duration < o.duration || o.isInfiniteDuration()); }

    private boolean isActive() { return isInfiniteDuration() || duration > 0; }

    /**
     * tick(host, onRefresh): 이번 틱에 효과를 적용하고 지속시간을 줄인다. 아직 살아 있으면 true.
     * @param entityTickCount 무한 효과의 주기 판단에 쓰는 엔티티 틱 수
     */
    public boolean tick(MobEffect.Host host, int entityTickCount, Runnable onRefresh, Runnable onRemove) {
        if (isActive()) {
            int d = isInfiniteDuration() ? entityTickCount : duration;
            if (effect.shouldApplyEffectTickThisTick(d, amplifier) && !effect.applyEffectTick(host, amplifier)) {
                duration = 0;   // 흡수가 다 닳아 이 효과를 지운다
                return false;
            }
            tickDownDuration();
            if (duration == 0 && hiddenEffect != null) {
                setDetailsFrom(hiddenEffect);
                hiddenEffect = hiddenEffect.hiddenEffect;
                onRefresh.run();
            }
        }
        return isActive();
    }

    private void tickDownDuration() {
        if (hiddenEffect != null) hiddenEffect.tickDownDuration();
        if (!isInfiniteDuration() && duration != 0) duration--;
    }

    /** 표시 순서(CombatTracker/GUI): 아이콘 없는 것 뒤, 무한 뒤, 지속 짧은 순, 효과 번호 순. */
    @Override
    public int compareTo(MobEffectInstance o) {
        int c = Boolean.compare(showIcon, o.showIcon);
        if (c != 0) return c;
        c = Boolean.compare(isInfiniteDuration(), o.isInfiniteDuration());
        if (c != 0) return c;
        c = Integer.compare(duration, o.duration);
        return c != 0 ? c : Integer.compare(effect.registryId(), o.effect.registryId());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MobEffectInstance i && duration == i.duration && amplifier == i.amplifier && ambient == i.ambient && visible == i.visible
                && showIcon == i.showIcon && effect == i.effect;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(effect, duration, amplifier, ambient, visible, showIcon); }

    // ------------------------------------------------------------------ NBT(active_effects 의 한 항목)

    /** 기본값(증폭 0·환경 거짓·입자 참)은 쓰지 않는다. show_icon 은 항상. */
    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        t.putString("id", effect.name());
        saveDetails(t);
        return t;
    }

    private void saveDetails(NbtCompound t) {
        if (amplifier != 0) t.putByte("amplifier", (byte) amplifier);
        if (duration != 0) t.putInt("duration", duration);
        if (ambient) t.putByte("ambient", (byte) 1);
        if (!visible) t.putByte("show_particles", (byte) 0);
        t.putByte("show_icon", (byte) (showIcon ? 1 : 0));
        if (hiddenEffect != null) {
            NbtCompound h = new NbtCompound();
            hiddenEffect.saveDetails(h);
            t.putCompound("hidden_effect", h);
        }
    }

    public static MobEffectInstance load(NbtCompound t) {
        if (!(t.tags().get("id") instanceof String id)) return null;
        MobEffect e = MobEffect.byName(id);
        return e == null ? null : loadDetails(e, t);
    }

    private static MobEffectInstance loadDetails(MobEffect e, NbtCompound t) {
        int amp = t.tags().get("amplifier") instanceof Number n ? n.intValue() : 0;
        int dur = t.tags().get("duration") instanceof Number n ? n.intValue() : 0;
        boolean ambient = t.tags().get("ambient") instanceof Number n && n.intValue() != 0;
        boolean visible = !(t.tags().get("show_particles") instanceof Number n) || n.intValue() != 0;
        boolean icon = t.tags().get("show_icon") instanceof Number n ? n.intValue() != 0 : visible;
        MobEffectInstance hidden = t.tags().get("hidden_effect") instanceof NbtCompound h ? loadDetails(e, h) : null;
        return new MobEffectInstance(e, dur, Math.clamp(amp, 0, 255), ambient, visible, icon, hidden);
    }

    @Override
    public String toString() { return effect.name() + (amplifier > 0 ? " x " + (amplifier + 1) : "") + ", Duration: " + (isInfiniteDuration() ? "infinite" : duration); }
}
