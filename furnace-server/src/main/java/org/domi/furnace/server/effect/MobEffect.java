// 바닐라 MobEffect(상태 효과) 정의: 이름·분류·색, 틱 때 하는 일. 효과 39종(minecraft:mob_effect 레지스트리)을 레지스트리 순서대로 둔다.
// 속성 수정(이동 속도 등)은 클라이언트가 효과 패킷으로 스스로 계산하므로 서버에는 두지 않는다 — 서버가 쓰는 건 체력 증가·흡수만.
package org.domi.furnace.server.effect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.item.ItemContext;

public final class MobEffect {

    public enum Category { BENEFICIAL, HARMFUL, NEUTRAL }

    /** 틱/시작 때의 서버 동작 종류. */
    public enum Kind { PLAIN, REGENERATION, POISON, WITHER, HUNGER, SATURATION, ABSORPTION, HEALTH_BOOST, INSTANT_HEALTH, INSTANT_DAMAGE }

    private static final Map<String, MobEffect> BY_NAME = new LinkedHashMap<>();

    private final String name;
    private final Category category;
    private final int color;
    private final Kind kind;

    private MobEffect(String name, Category category, int color, Kind kind) {
        this.name = name;
        this.category = category;
        this.color = color;
        this.kind = kind;
        BY_NAME.put(name, this);
    }

    private static MobEffect of(String n, Category c, int color, Kind k) { return new MobEffect("minecraft:" + n, c, color, k); }

    private static MobEffect of(String n, Category c, int color) { return of(n, c, color, Kind.PLAIN); }

    public static final MobEffect SPEED = of("speed", Category.BENEFICIAL, 3402751);
    public static final MobEffect SLOWNESS = of("slowness", Category.HARMFUL, 9154528);
    public static final MobEffect HASTE = of("haste", Category.BENEFICIAL, 14270531);
    public static final MobEffect MINING_FATIGUE = of("mining_fatigue", Category.HARMFUL, 4866583);
    public static final MobEffect STRENGTH = of("strength", Category.BENEFICIAL, 16762624);
    public static final MobEffect INSTANT_HEALTH = of("instant_health", Category.BENEFICIAL, 16262179, Kind.INSTANT_HEALTH);
    public static final MobEffect INSTANT_DAMAGE = of("instant_damage", Category.HARMFUL, 11101546, Kind.INSTANT_DAMAGE);
    public static final MobEffect JUMP_BOOST = of("jump_boost", Category.BENEFICIAL, 16646020);
    public static final MobEffect NAUSEA = of("nausea", Category.HARMFUL, 5578058);
    public static final MobEffect REGENERATION = of("regeneration", Category.BENEFICIAL, 13458603, Kind.REGENERATION);
    public static final MobEffect RESISTANCE = of("resistance", Category.BENEFICIAL, 9520880);
    public static final MobEffect FIRE_RESISTANCE = of("fire_resistance", Category.BENEFICIAL, 16750848);
    public static final MobEffect WATER_BREATHING = of("water_breathing", Category.BENEFICIAL, 10017472);
    public static final MobEffect INVISIBILITY = of("invisibility", Category.BENEFICIAL, 16185078);
    public static final MobEffect BLINDNESS = of("blindness", Category.HARMFUL, 2039587);
    public static final MobEffect NIGHT_VISION = of("night_vision", Category.BENEFICIAL, 12779366);
    public static final MobEffect HUNGER = of("hunger", Category.HARMFUL, 5797459, Kind.HUNGER);
    public static final MobEffect WEAKNESS = of("weakness", Category.HARMFUL, 4738376);
    public static final MobEffect POISON = of("poison", Category.HARMFUL, 8889187, Kind.POISON);
    public static final MobEffect WITHER = of("wither", Category.HARMFUL, 7561558, Kind.WITHER);
    public static final MobEffect HEALTH_BOOST = of("health_boost", Category.BENEFICIAL, 16284963, Kind.HEALTH_BOOST);
    public static final MobEffect ABSORPTION = of("absorption", Category.BENEFICIAL, 2445989, Kind.ABSORPTION);
    public static final MobEffect SATURATION = of("saturation", Category.BENEFICIAL, 16262179, Kind.SATURATION);
    public static final MobEffect GLOWING = of("glowing", Category.NEUTRAL, 9740385);
    public static final MobEffect LEVITATION = of("levitation", Category.HARMFUL, 13565951);
    public static final MobEffect LUCK = of("luck", Category.BENEFICIAL, 5882118);
    public static final MobEffect UNLUCK = of("unluck", Category.HARMFUL, 12624973);
    public static final MobEffect SLOW_FALLING = of("slow_falling", Category.BENEFICIAL, 15978425);
    public static final MobEffect CONDUIT_POWER = of("conduit_power", Category.BENEFICIAL, 1950417);
    public static final MobEffect DOLPHINS_GRACE = of("dolphins_grace", Category.BENEFICIAL, 8954814);
    public static final MobEffect BAD_OMEN = of("bad_omen", Category.NEUTRAL, 745784);
    public static final MobEffect HERO_OF_THE_VILLAGE = of("hero_of_the_village", Category.BENEFICIAL, 4521796);
    public static final MobEffect DARKNESS = of("darkness", Category.HARMFUL, 2696993);
    public static final MobEffect TRIAL_OMEN = of("trial_omen", Category.NEUTRAL, 1484454);
    public static final MobEffect RAID_OMEN = of("raid_omen", Category.NEUTRAL, 14565464);
    public static final MobEffect WIND_CHARGED = of("wind_charged", Category.HARMFUL, 12438015);
    public static final MobEffect WEAVING = of("weaving", Category.HARMFUL, 7891290);
    public static final MobEffect OOZING = of("oozing", Category.HARMFUL, 10092451);
    public static final MobEffect INFESTED = of("infested", Category.HARMFUL, 9214860);

    /** 이름("minecraft:speed")으로 찾는다(없으면 null). */
    public static MobEffect byName(String name) {
        if (name != null && name.indexOf(':') < 0) name = "minecraft:" + name;
        return BY_NAME.get(name);
    }

    public static List<MobEffect> all() { return List.copyOf(BY_NAME.values()); }

    public String name() { return name; }

    public Category category() { return category; }

    public int color() { return color; }

    public Kind kind() { return kind; }

    public boolean isBeneficial() { return category == Category.BENEFICIAL; }

    public boolean isInstantaneous() { return kind == Kind.INSTANT_HEALTH || kind == Kind.INSTANT_DAMAGE; }

    /** 레지스트리 번호(update_mob_effect·remove_mob_effect 에 실린다). */
    public int registryId() {
        Integer id = ItemContext.id("minecraft:mob_effect", name);
        return id == null ? 0 : id;
    }

    /** 번역 키(effect.minecraft.speed → "effect.minecraft.speed"). */
    public String translationKey() { return "effect." + name.replace(':', '.'); }

    /** 서버에서 쓰는 효과 대상. 플레이어(와 앞으로 몹)가 구현한다. */
    public interface Host {
        float health();

        float maxHealth();

        void heal(float amount);

        /** 마법·위더 피해(type 은 DamageType 키 "magic"/"wither"). */
        boolean hurtBy(String damageKey, float amount);

        boolean isPlayer();

        void causeFoodExhaustion(float amount);

        void eat(int nutrition, float saturationModifier);

        float absorption();

        void setAbsorption(float amount);

        /** 언데드는 치유/피해가 뒤집힌다(지금은 없음). */
        default boolean invertedHealAndHarm() { return false; }
    }

    /** 틱마다 효과를 적용할 차례인가(shouldApplyEffectTickThisTick). */
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return switch (kind) {
            case REGENERATION -> {
                int i = 50 >> amplifier;
                yield i > 0 ? duration % i == 0 : true;
            }
            case POISON -> {
                int i = 25 >> amplifier;
                yield i > 0 ? duration % i == 0 : true;
            }
            case WITHER -> {
                int i = 40 >> amplifier;
                yield i > 0 ? duration % i == 0 : true;
            }
            case HUNGER, SATURATION, ABSORPTION -> true;
            default -> false;
        };
    }

    /** applyEffectTick: false 를 돌려주면 효과를 지운다(흡수가 다 닳았을 때). */
    public boolean applyEffectTick(Host h, int amplifier) {
        switch (kind) {
            case REGENERATION -> {
                if (h.health() < h.maxHealth()) h.heal(1.0F);
            }
            case POISON -> {
                if (h.health() > 1.0F) h.hurtBy("magic", 1.0F);
            }
            case WITHER -> h.hurtBy("wither", 1.0F);
            case HUNGER -> {
                if (h.isPlayer()) h.causeFoodExhaustion(0.005F * (amplifier + 1));
            }
            case SATURATION -> {
                if (h.isPlayer()) h.eat(amplifier + 1, 1.0F);
            }
            case ABSORPTION -> {
                return h.absorption() > 0.0F;
            }
            case INSTANT_HEALTH -> {
                if (!h.invertedHealAndHarm()) h.heal(Math.max(4 << amplifier, 0));
                else h.hurtBy("magic", 6 << amplifier);
            }
            case INSTANT_DAMAGE -> {
                if (h.invertedHealAndHarm()) h.heal(Math.max(4 << amplifier, 0));
                else h.hurtBy("magic", 6 << amplifier);
            }
            default -> { }
        }
        return true;
    }

    /** applyInstantenousEffect(근접도): 마시면 1.0, 투척이면 거리에 따라. */
    public void applyInstantenousEffect(Host h, int amplifier, double proximity) {
        if (kind == Kind.INSTANT_HEALTH) {
            int i = (int) (proximity * (4 << amplifier) + 0.5);
            if (!h.invertedHealAndHarm()) h.heal(i); else h.hurtBy("magic", i);
        } else if (kind == Kind.INSTANT_DAMAGE) {
            int i = (int) (proximity * (6 << amplifier) + 0.5);
            if (h.invertedHealAndHarm()) h.heal(i); else h.hurtBy("magic", i);
        } else {
            applyEffectTick(h, amplifier);
        }
    }

    /** onEffectStarted: 흡수는 시작할 때 흡수량을 올린다. */
    public void onEffectStarted(Host h, int amplifier) {
        if (kind == Kind.ABSORPTION) h.setAbsorption(Math.max(h.absorption(), 4 * (1 + amplifier)));
    }
}
