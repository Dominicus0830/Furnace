// 바닐라 Potions 레지스트리(minecraft:potion 46종): 물약 이름 → 걸리는 효과들(지속 틱·단계). 디컴파일 원본의 표를 옮겼다.
package org.domi.furnace.server.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Potions {
    private Potions() { }

    private static final Map<String, List<MobEffectInstance>> BY_NAME = new LinkedHashMap<>();

    private static MobEffectInstance e(String effect, int duration, int amplifier) {
        return new MobEffectInstance(MobEffect.byName(effect), duration, amplifier);
    }

    private static void p(String name, MobEffectInstance... effects) {
        BY_NAME.put("minecraft:" + name, List.of(effects));
    }

    static {
        p("water");
        p("mundane");
        p("thick");
        p("awkward");
        p("night_vision", e("night_vision", 3600, 0));
        p("long_night_vision", e("night_vision", 9600, 0));
        p("invisibility", e("invisibility", 3600, 0));
        p("long_invisibility", e("invisibility", 9600, 0));
        p("leaping", e("jump_boost", 3600, 0));
        p("long_leaping", e("jump_boost", 9600, 0));
        p("strong_leaping", e("jump_boost", 1800, 1));
        p("fire_resistance", e("fire_resistance", 3600, 0));
        p("long_fire_resistance", e("fire_resistance", 9600, 0));
        p("swiftness", e("speed", 3600, 0));
        p("long_swiftness", e("speed", 9600, 0));
        p("strong_swiftness", e("speed", 1800, 1));
        p("slowness", e("slowness", 1800, 0));
        p("long_slowness", e("slowness", 4800, 0));
        p("strong_slowness", e("slowness", 400, 3));
        p("turtle_master", e("slowness", 400, 3), e("resistance", 400, 2));
        p("long_turtle_master", e("slowness", 800, 3), e("resistance", 800, 2));
        p("strong_turtle_master", e("slowness", 400, 5), e("resistance", 400, 3));
        p("water_breathing", e("water_breathing", 3600, 0));
        p("long_water_breathing", e("water_breathing", 9600, 0));
        p("healing", e("instant_health", 1, 0));
        p("strong_healing", e("instant_health", 1, 1));
        p("harming", e("instant_damage", 1, 0));
        p("strong_harming", e("instant_damage", 1, 1));
        p("poison", e("poison", 900, 0));
        p("long_poison", e("poison", 1800, 0));
        p("strong_poison", e("poison", 432, 1));
        p("regeneration", e("regeneration", 900, 0));
        p("long_regeneration", e("regeneration", 1800, 0));
        p("strong_regeneration", e("regeneration", 450, 1));
        p("strength", e("strength", 3600, 0));
        p("long_strength", e("strength", 9600, 0));
        p("strong_strength", e("strength", 1800, 1));
        p("weakness", e("weakness", 1800, 0));
        p("long_weakness", e("weakness", 4800, 0));
        p("luck", e("luck", 6000, 0));
        p("slow_falling", e("slow_falling", 1800, 0));
        p("long_slow_falling", e("slow_falling", 4800, 0));
        p("wind_charged", e("wind_charged", 3600, 0));
        p("weaving", e("weaving", 3600, 0));
        p("oozing", e("oozing", 3600, 0));
        p("infested", e("infested", 3600, 0));
    }

    /** 물약 이름("minecraft:healing")의 효과 복사본(모르는 물약·효과 없음이면 빈 목록). */
    public static List<MobEffectInstance> effectsOf(String potion) {
        List<MobEffectInstance> src = BY_NAME.get(potion);
        List<MobEffectInstance> out = new ArrayList<>();
        if (src != null) for (MobEffectInstance i : src) out.add(new MobEffectInstance(i));
        return out;
    }

    public static boolean exists(String potion) { return BY_NAME.containsKey(potion); }
}
