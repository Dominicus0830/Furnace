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

    /** 효과가 거는 속성 수정자(바닐라 MobEffect.addAttributeModifier): {속성 이름, 수정자 id, 단계당 양, 연산(0 더하기·1 기본 곱·2 총 곱)}. */
    public java.util.List<Object[]> attributeModifiers() {
        return switch (name) {
            case "minecraft:speed" -> java.util.List.<Object[]>of(new Object[] {"generic.movement_speed", "minecraft:effect.speed", 0.2F, 2});
            case "minecraft:slowness" -> java.util.List.<Object[]>of(new Object[] {"generic.movement_speed", "minecraft:effect.slowness", -0.15F, 2});
            case "minecraft:haste" -> java.util.List.<Object[]>of(new Object[] {"generic.attack_speed", "minecraft:effect.haste", 0.1F, 2});
            case "minecraft:mining_fatigue" -> java.util.List.<Object[]>of(new Object[] {"generic.attack_speed", "minecraft:effect.mining_fatigue", -0.1F, 2});
            case "minecraft:strength" -> java.util.List.<Object[]>of(new Object[] {"generic.attack_damage", "minecraft:effect.strength", 3.0, 0});
            case "minecraft:jump_boost" -> java.util.List.<Object[]>of(new Object[] {"generic.safe_fall_distance", "minecraft:effect.jump_boost", 1.0, 0});
            case "minecraft:weakness" -> java.util.List.<Object[]>of(new Object[] {"generic.attack_damage", "minecraft:effect.weakness", -4.0, 0});
            case "minecraft:health_boost" -> java.util.List.<Object[]>of(new Object[] {"generic.max_health", "minecraft:effect.health_boost", 4.0, 0});
            case "minecraft:absorption" -> java.util.List.<Object[]>of(new Object[] {"generic.max_absorption", "minecraft:effect.absorption", 4.0, 0});
            case "minecraft:luck" -> java.util.List.<Object[]>of(new Object[] {"generic.luck", "minecraft:effect.luck", 1.0, 0});
            case "minecraft:unluck" -> java.util.List.<Object[]>of(new Object[] {"generic.luck", "minecraft:effect.unluck", -1.0, 0});
            default -> java.util.List.of();
        };
    }

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

    /** 바닐라 InstantenousMobEffect 를 잇는 효과: 즉시 치유·즉시 피해와 포화(SaturationMobEffect). 1.21.1 바이트코드로 확인. */
    public boolean isInstantaneous() { return kind == Kind.INSTANT_HEALTH || kind == Kind.INSTANT_DAMAGE || kind == Kind.SATURATION; }

    /** 레지스트리 번호(update_mob_effect·remove_mob_effect 에 실린다). */
    public int registryId() {
        Integer id = ItemContext.id("minecraft:mob_effect", name);
        return id == null ? 0 : id;
    }

    /** 환경(비콘·전달체) 효과 입자의 알파: 바닐라 MobEffect.AMBIENT_ALPHA = floor(0.15 * 255). */
    public static final int AMBIENT_PARTICLE_ALPHA = 38;

    /**
     * 바닐라 MobEffect.createParticleOptions: 몸에서 나는 입자. 대부분은 효과 색의 entity_effect(환경 효과면 반투명)이고,
     * 고유 입자를 쓰는 6종(1.21.1 MobEffects 바이트코드 확인)은 색 없이 그 입자를 쓴다.
     */
    public org.domi.furnace.server.net.protocol.play.ParticleOptions particle(boolean ambient) {
        String special = switch (name) {
            case "minecraft:trial_omen" -> "minecraft:trial_omen";
            case "minecraft:raid_omen" -> "minecraft:raid_omen";
            case "minecraft:wind_charged" -> "minecraft:small_gust";
            case "minecraft:weaving" -> "minecraft:item_cobweb";
            case "minecraft:oozing" -> "minecraft:item_slime";
            case "minecraft:infested" -> "minecraft:infested";
            default -> null;
        };
        if (special != null) return new org.domi.furnace.server.net.protocol.play.ParticleOptions.Simple(special);
        int alpha = ambient ? AMBIENT_PARTICLE_ALPHA : 255;
        return new org.domi.furnace.server.net.protocol.play.ParticleOptions.Color(alpha << 24 | color & 0xFFFFFF);
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
            case HUNGER, ABSORPTION -> true;
            // 바닐라 InstantenousMobEffect: 남은 지속시간이 1 이상인 틱마다 적용(/effect give 는 지속시간만큼 매 틱 건다)
            case SATURATION, INSTANT_HEALTH, INSTANT_DAMAGE -> duration >= 1;
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

    /**
     * applyInstantenousEffect(근접도): 마시면 1.0, 투척이면 거리에 따라. 바닐라 HealOrHarmMobEffect 처럼 치유 쪽이면 4 << 단계,
     * 피해 쪽이면 6 << 단계 — 언데드는 치유 효과가 피해 쪽(6)이 되고 피해 효과가 치유 쪽(4)이 된다.
     */
    public void applyInstantenousEffect(Host h, int amplifier, double proximity) {
        if (kind == Kind.INSTANT_HEALTH || kind == Kind.INSTANT_DAMAGE) {
            boolean heals = (kind == Kind.INSTANT_HEALTH) != h.invertedHealAndHarm();
            if (heals) h.heal((int) (proximity * (4 << amplifier) + 0.5));
            else h.hurtBy("magic", (int) (proximity * (6 << amplifier) + 0.5));
        } else {
            applyEffectTick(h, amplifier);
        }
    }

    /** 즉시 피해(치유/피해 효과가 대상에게 해로울 때)의 피해량. 해당하지 않으면 -1. 투척 물약이 indirectMagic 으로 직접 때릴 때 쓴다. */
    public int instantHarmAmount(Host h, int amplifier, double proximity) {
        if (kind != Kind.INSTANT_HEALTH && kind != Kind.INSTANT_DAMAGE) return -1;
        boolean heals = (kind == Kind.INSTANT_HEALTH) != h.invertedHealAndHarm();
        return heals ? -1 : (int) (proximity * (6 << amplifier) + 0.5);
    }

    /** onEffectStarted: 흡수는 시작할 때 흡수량을 올린다. */
    public void onEffectStarted(Host h, int amplifier) {
        if (kind == Kind.ABSORPTION) h.setAbsorption(Math.max(h.absorption(), 4 * (1 + amplifier)));
    }
}
