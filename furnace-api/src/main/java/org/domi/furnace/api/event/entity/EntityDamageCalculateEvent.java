// 엔티티가 받을 피해를 단계별로 줄여 가는 계산 — 맞는 엔티티를 가진 region 스레드에서, 체력을 깎기 직전에 불린다.
// 기본 값은 바닐라와 같다: 아무것도 바꾸지 않으면 결과가 바닐라와 비트 단위로 같다.
//
//   base ─ARMOR→ ─RESISTANCE→ ─ENCHANTMENT→ ─ENTITY→ ─ABSORPTION→ finalDamage (체력에서 깎이는 값)
//
// 단계마다 input(들어온 값)·output(나간 값)·reduction(= input - output)을 읽을 수 있고, 줄이는 양을 바꾸면
// 뒤 단계는 새 input 으로 바닐라 식을 다시 계산한다(그 단계도 바꿨으면 바꾼 대로). 이벤트는 취소할 수 없다 —
// 피해를 아예 막으려면 EntityDamageEvent 를 취소하거나 base 를 0 으로 한다.
//
// 이 이벤트가 불리지 않는 경우: 방패가 전부 막아 피해가 0 인 경우, 늑대 갑옷이 피해를 대신 받는 경우(체력을 깎지 않는다).
// 방어구 내구도는 이 이벤트가 불리기 전에 이미 닳았다(바닐라와 같은 순서 — 방어력은 닳은 뒤의 값). base 를 바꿔도 내구도는 되돌지 않는다.
package org.domi.furnace.api.event.entity;

import java.util.Objects;
import org.domi.furnace.api.entity.Entity;

public final class EntityDamageCalculateEvent extends EntityEvent {

    private static final DamageStage[] STAGES = DamageStage.values();
    private static final int N = STAGES.length;
    private static final int ABS = DamageStage.ABSORPTION.ordinal();

    private enum Mode { NONE, ABSOLUTE, SCALE }

    private final String cause;
    private final Entity attacker;
    private final Entity directEntity;
    private final float absorption;
    private final DamageRules rules;
    private float base;

    private final Mode[] mode = new Mode[N];
    private final float[] modeValue = new float[N];
    private final Boolean[] appliedOverride = new Boolean[N];
    private final float[] in = new float[N];
    private final float[] out = new float[N];
    private final boolean[] defaultApplies = new boolean[N];
    private boolean dirty = true;

    /**
     * 서버가 만든다.
     *
     * @param attacker     피해의 책임자(화살이면 쏜 쪽). 없으면 null
     * @param directEntity 직접 맞힌 엔티티(화살 자체). 없으면 null
     * @param base         계산을 시작하는 값(방어 전, 난이도 배율·방패 뒤)
     * @param absorption   지금 가진 흡수 체력
     */
    public EntityDamageCalculateEvent(Entity entity, String cause, Entity attacker, Entity directEntity, float base, float absorption, DamageRules rules) {
        super(entity);
        this.cause = Objects.requireNonNull(cause, "cause");
        this.attacker = attacker;
        this.directEntity = directEntity;
        this.base = base;
        this.absorption = Math.max(absorption, 0f);
        this.rules = Objects.requireNonNull(rules, "rules");
        java.util.Arrays.fill(mode, Mode.NONE);
        for (DamageStage s : STAGES) defaultApplies[s.ordinal()] = s == DamageStage.ABSORPTION ? this.absorption > 0f : rules.applies(s);
    }

    // ------------------------------------------------------------------ 맥락

    /** 피해 종류 키(minecraft:fall, minecraft:player_attack …). */
    public String cause() { return cause; }

    /** 피해의 책임자(화살이면 쏜 쪽). 없으면 null. */
    public Entity attacker() { return attacker; }

    /** 직접 맞힌 엔티티(화살 자체). 없으면 null. */
    public Entity directEntity() { return directEntity; }

    /** 이벤트가 불린 시점에 엔티티가 가진 흡수 체력. */
    public float absorption() { return absorption; }

    // ------------------------------------------------------------------ 기준값

    /** 계산을 시작하는 값(방어 전). */
    public float base() { return base; }

    /** 시작 값을 바꾼다. 모든 단계가 새 값으로 다시 계산된다(바꾼 단계는 바꾼 대로). 0 이상의 유한한 값. */
    public void setBase(float base) {
        if (!(base >= 0f) || Float.isInfinite(base)) throw new IllegalArgumentException("base 는 0 이상의 유한한 값: " + base);
        this.base = base;
        dirty = true;
    }

    // ------------------------------------------------------------------ 단계 읽기

    /** 이 단계가 지금 적용되는가(바닐라 기본 또는 {@link #setApplied}). 적용 안 되면 input 이 그대로 output 이다. */
    public boolean applies(DamageStage stage) {
        Boolean o = appliedOverride[stage.ordinal()];
        return o != null ? o : defaultApplies[stage.ordinal()];
    }

    /** 이 단계에 들어온 값. */
    public float input(DamageStage stage) { calc(); return in[stage.ordinal()]; }

    /** 이 단계를 거친 값. */
    public float output(DamageStage stage) { calc(); return out[stage.ordinal()]; }

    /** 이 단계가 줄인 양(input - output). ABSORPTION 이면 흡수 체력에서 빠지는 양이다. */
    public float reduction(DamageStage stage) { calc(); return in[stage.ordinal()] - out[stage.ordinal()]; }

    /** 바닐라 기본 계산이 그 단계에서 줄였을 양(지금 input 기준). 바꾼 값과 비교하는 데 쓴다. */
    public float vanillaReduction(DamageStage stage) {
        calc();
        int i = stage.ordinal();
        return in[i] - defaultOut(i, in[i]);
    }

    /** 체력에서 깎이는 최종 피해(= ABSORPTION 단계의 output). */
    public float finalDamage() { calc(); return out[ABS]; }

    /** 흡수 체력에서 빠지는 양(= ABSORPTION 단계의 reduction). */
    public float absorbed() { return reduction(DamageStage.ABSORPTION); }

    /** 그 단계를 바꿨는가. */
    public boolean isModified(DamageStage stage) {
        int i = stage.ordinal();
        return mode[i] != Mode.NONE || appliedOverride[i] != null;
    }

    // ------------------------------------------------------------------ 단계 바꾸기

    /**
     * 그 단계가 줄이는 양을 정한다(input 이 바뀌어도 이 양을 그대로 줄인다). 0..input 으로 잘리고,
     * ABSORPTION 은 가진 흡수 체력도 넘지 못한다. 적용 안 되는 단계도 정하면 그만큼 줄인다.
     */
    public void setReduction(DamageStage stage, float reduction) {
        if (!(reduction >= 0f) || Float.isInfinite(reduction)) throw new IllegalArgumentException("reduction 은 0 이상의 유한한 값: " + reduction);
        int i = stage.ordinal();
        if (appliedOverride[i] == Boolean.FALSE) appliedOverride[i] = null;   // 건너뛰기 뒤에 양을 정하면 양이 이긴다
        mode[i] = Mode.ABSOLUTE;
        modeValue[i] = reduction;
        dirty = true;
    }

    /**
     * 바닐라 기본 계산이 줄이는 양에 factor 를 곱한다(input 이 바뀌면 바닐라 양도 새로 계산해 곱한다).
     * 바닐라에서 적용되지 않는 단계는 곱할 양이 0 이라 그대로다(먼저 {@link #setApplied} 로 켠다).
     * 예: 방어력을 반만 쳐 주려면 {@code scaleReduction(ARMOR, 0.5f)}. factor 는 0 이상의 유한한 값, 결과는 0..input 으로 잘린다.
     */
    public void scaleReduction(DamageStage stage, float factor) {
        if (!(factor >= 0f) || Float.isInfinite(factor)) throw new IllegalArgumentException("factor 는 0 이상의 유한한 값: " + factor);
        int i = stage.ordinal();
        if (appliedOverride[i] == Boolean.FALSE) appliedOverride[i] = null;
        mode[i] = Mode.SCALE;
        modeValue[i] = factor;
        dirty = true;
    }

    /**
     * 적용 여부를 강제한다. false 면 그 단계를 건너뛰고(= reduction 0), true 면 피해 유형 조건이 건너뛰게 해도 바닐라 식을 적용한다
     * (예: bypasses_armor 인 피해에도 방어력을 쳐 준다). false 로 건너뛴 단계는 이어서 {@link #setReduction} 으로 양을 정하면 다시 켜진다.
     */
    public void setApplied(DamageStage stage, boolean applied) {
        appliedOverride[stage.ordinal()] = applied;
        dirty = true;
    }

    /** 그 단계를 건너뛴다({@code setApplied(stage, false)}). */
    public void skip(DamageStage stage) { setApplied(stage, false); }

    /** 그 단계를 바닐라 기본으로 되돌린다(줄이는 양과 적용 여부 둘 다). */
    public void reset(DamageStage stage) {
        int i = stage.ordinal();
        mode[i] = Mode.NONE;
        appliedOverride[i] = null;
        dirty = true;
    }

    // ------------------------------------------------------------------ 내부 계산

    private float defaultOut(int i, float x) {
        return i == ABS ? Math.max(x - absorption, 0.0F) : rules.apply(STAGES[i], x);
    }

    private void calc() {
        if (!dirty) return;
        float x = base;
        for (int i = 0; i < N; i++) {
            in[i] = x;
            float o = x;
            Boolean forced = appliedOverride[i];
            boolean on = forced != null ? forced : defaultApplies[i];
            if (x > 0f && forced != Boolean.FALSE) {
                float limit = i == ABS ? Math.min(x, absorption) : x;
                switch (mode[i]) {
                    case NONE -> { if (on) o = defaultOut(i, x); }
                    case ABSOLUTE -> o = x - Math.min(modeValue[i], limit);
                    case SCALE -> {
                        if (on) o = modeValue[i] == 1f ? defaultOut(i, x) : x - Math.min((x - defaultOut(i, x)) * modeValue[i], limit);
                    }
                }
            }
            out[i] = o;
            x = o;
        }
        dirty = false;
    }
}
