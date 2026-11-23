package net.minecraft.world.effect;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * 상태 효과 한 건(Mojang 이름 shim, 효과·프로필·속성 묶음). 바닐라처럼 <b>값 객체</b>다 — 엔티티에 붙은 살아 있는 객체가 아니라 종류·지속·증폭·표시와
 * 숨은 효과를 든다. 엔티티에서 읽은 것({@code LivingEntity#getEffect})은 읽은 순간의 사본이라 고쳐도 엔티티는 안 바뀐다(다시 걸려면 {@code addEffect}).
 * 서버가 효과를 틱하므로 틱·시작·제거 알림({@code tick}·{@code onEffectStarted}·{@code onMobRemoved}·{@code onMobHurt}·{@code onEffectAdded})·클라이언트
 * 색 섞기·{@code CODEC}·{@code STREAM_CODEC} 은 두지 않았다(부르면 없는 멤버).
 * <p>알려진 차이: 숨은 효과({@code hiddenEffect})는 엔티티 API 가 주지 않아 엔티티에서 읽은 사본에는 없다(직접 만든 것·{@code update} 가 만든 것은 있다).
 */
public class MobEffectInstance implements Comparable<MobEffectInstance> {

    public static final int INFINITE_DURATION = -1;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;

    private static final System.Logger LOGGER = System.getLogger("MobEffectInstance");

    private final Holder<MobEffect> effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    private MobEffectInstance hiddenEffect;

    public MobEffectInstance(Holder<MobEffect> effect) { this(effect, 0, 0); }

    public MobEffectInstance(Holder<MobEffect> effect, int duration) { this(effect, duration, 0); }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier) { this(effect, duration, amplifier, false, true); }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) {
        this(effect, duration, amplifier, ambient, visible, visible);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        this(effect, duration, amplifier, ambient, visible, showIcon, null);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon,
            MobEffectInstance hiddenEffect) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Math.max(MIN_AMPLIFIER, Math.min(MAX_AMPLIFIER, amplifier));   // 바닐라 Mth.clamp(amplifier, 0, 255)
        this.ambient = ambient;
        this.visible = visible;
        this.showIcon = showIcon;
        this.hiddenEffect = hiddenEffect;
    }

    /** 복사 생성자: 효과 종류는 그대로, 나머지(숨은 효과 포함)는 값으로 복사한다. */
    public MobEffectInstance(MobEffectInstance other) {
        this.effect = other.effect;
        setDetailsFrom(other);
    }

    void setDetailsFrom(MobEffectInstance other) {
        this.duration = other.duration;
        this.amplifier = other.amplifier;
        this.ambient = other.ambient;
        this.visible = other.visible;
        this.showIcon = other.showIcon;
        this.hiddenEffect = other.hiddenEffect == null ? null : new MobEffectInstance(other.hiddenEffect);
    }

    /**
     * 같은 종류의 새 효과를 합친다(바닐라 규칙): 더 센 것이 이기고 약해진 쪽은 숨은 효과로 남으며, 같은 세기면 더 긴 지속이 이긴다.
     * 바뀐 것이 있으면 true. 종류가 다르면 경고만 남기고 그대로 합친다(바닐라 같음).
     */
    public boolean update(MobEffectInstance other) {
        if (!effect.equals(other.effect)) LOGGER.log(System.Logger.Level.WARNING, "This method should only be called for matching effects!");
        boolean changed = false;
        if (other.amplifier > amplifier) {
            if (other.isShorterDurationThan(this)) {
                MobEffectInstance older = hiddenEffect;
                hiddenEffect = new MobEffectInstance(this);
                hiddenEffect.hiddenEffect = older;
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
        if ((!other.ambient && ambient) || changed) {
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

    private boolean isShorterDurationThan(MobEffectInstance other) {
        return !isInfiniteDuration() && (duration < other.duration || other.isInfiniteDuration());
    }

    public boolean isInfiniteDuration() { return duration == INFINITE_DURATION; }

    /** 끝나기까지 이 틱 수 안인가(끝나지 않는 효과는 항상 false). */
    public boolean endsWithin(int ticks) { return !isInfiniteDuration() && duration <= ticks; }

    /** 끝나지 않는 효과·지속 0 은 그대로, 아니면 함수를 거친 지속. */
    public int mapDuration(Int2IntFunction mapper) { return !isInfiniteDuration() && duration != 0 ? mapper.applyAsInt(duration) : duration; }

    public Holder<MobEffect> getEffect() { return effect; }

    public int getDuration() { return duration; }

    public int getAmplifier() { return amplifier; }

    public boolean isAmbient() { return ambient; }

    public boolean isVisible() { return visible; }

    public boolean showIcon() { return showIcon; }

    public boolean is(Holder<MobEffect> effect) { return this.effect.equals(effect); }

    public String getDescriptionId() { return effect.value().getDescriptionId(); }

    @Override
    public String toString() {
        String out = amplifier > 0
                ? getDescriptionId() + " x " + (amplifier + 1) + ", Duration: " + describeDuration()
                : getDescriptionId() + ", Duration: " + describeDuration();
        if (!visible) out += ", Particles: false";
        if (!showIcon) out += ", Show Icon: false";
        return out;
    }

    private String describeDuration() { return isInfiniteDuration() ? "infinite" : Integer.toString(duration); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MobEffectInstance m && duration == m.duration && amplifier == m.amplifier && ambient == m.ambient && visible == m.visible
                && showIcon == m.showIcon && effect.equals(m.effect);
    }

    @Override
    public int hashCode() {
        int h = effect.hashCode();
        h = 31 * h + duration;
        h = 31 * h + amplifier;
        h = 31 * h + (ambient ? 1 : 0);
        h = 31 * h + (visible ? 1 : 0);
        h = 31 * h + (showIcon ? 1 : 0);
        return h;
    }

    /**
     * 바닐라 저장 꼴({@code id}·{@code amplifier}·{@code duration}·{@code ambient}·{@code show_particles}·{@code show_icon}·{@code hidden_effect}).
     * 기본값인 칸(증폭 0, 지속 0, 환경 아님, 입자 보임)은 적지 않고 {@code show_icon} 은 늘 적는다.
     */
    public Tag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", effect.getRegisteredName());
        saveDetails(t);
        return t;
    }

    private void saveDetails(CompoundTag t) {
        if (amplifier != 0) t.putByte("amplifier", (byte) amplifier);
        if (duration != 0) t.putInt("duration", duration);
        if (ambient) t.putBoolean("ambient", true);
        if (!visible) t.putBoolean("show_particles", false);
        t.putBoolean("show_icon", showIcon);
        if (hiddenEffect != null) {
            CompoundTag h = new CompoundTag();
            hiddenEffect.saveDetails(h);
            t.put("hidden_effect", h);
        }
    }

    /** 저장 꼴에서 읽는다. 효과 이름이 없거나 레지스트리에 없으면 null(바닐라는 오류를 로그로 남기고 null). */
    public static MobEffectInstance load(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) return null;
        Holder<MobEffect> effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
        return effect == null ? null : loadDetails(effect, tag);
    }

    private static MobEffectInstance loadDetails(Holder<MobEffect> effect, CompoundTag t) {
        boolean visible = !t.contains("show_particles") || t.getBoolean("show_particles");
        boolean icon = t.contains("show_icon") ? t.getBoolean("show_icon") : visible;
        MobEffectInstance hidden = t.contains("hidden_effect", Tag.TAG_COMPOUND) ? loadDetails(effect, t.getCompound("hidden_effect")) : null;
        return new MobEffectInstance(effect, t.getInt("duration"), t.getByte("amplifier") & 0xFF, t.getBoolean("ambient"), visible, icon, hidden);
    }

    /**
     * 바닐라 정렬: 지속이 아주 길거나(32147 틱 초과) 둘 다 환경이면 환경 아닌 쪽이 앞·색 순, 아니면 환경 아닌 것 → 끝나는 것 → 짧은 것 → 색 순.
     */
    @Override
    public int compareTo(MobEffectInstance other) {
        final int veryLong = 32147;
        if (getDuration() > veryLong && other.getDuration() > veryLong || isAmbient() && other.isAmbient()) {
            int c = Boolean.compare(isAmbient(), other.isAmbient());
            return c != 0 ? c : Integer.compare(getEffect().value().getColor(), other.getEffect().value().getColor());
        }
        int c = Boolean.compare(isAmbient(), other.isAmbient());
        if (c == 0) c = Boolean.compare(isInfiniteDuration(), other.isInfiniteDuration());
        if (c == 0) c = Integer.compare(getDuration(), other.getDuration());
        if (c == 0) c = Integer.compare(getEffect().value().getColor(), other.getEffect().value().getColor());
        return c;
    }
}
