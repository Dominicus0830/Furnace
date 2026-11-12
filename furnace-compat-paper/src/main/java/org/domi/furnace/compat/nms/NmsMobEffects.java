package org.domi.furnace.compat.nms;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import org.domi.furnace.api.Furnace;

/**
 * 상태 효과 레지스트리 shim(easy-nms E4c-1b). 번호 순서는 서버 내장 레지스트리 목록({@code builtIn("minecraft:mob_effect")}, 39개)이고,
 * 분류·색·즉시 여부는 바닐라 1.21.1 MobEffects 정의 값이다(서버 effect.MobEffect 와 같은지 시험이 본다). 기본값 없는 레지스트리다.
 */
public final class NmsMobEffects {

    private NmsMobEffects() {}

    private record Row(MobEffectCategory category, int color, boolean instant) {}

    private static final Map<String, Row> ROWS = new HashMap<>();

    private static void row(String name, MobEffectCategory category, int color, boolean instant) {
        ROWS.put("minecraft:" + name, new Row(category, color, instant));
    }

    static {   // 바닐라 1.21.1 MobEffects 정의(이름, 분류, 색, 즉시 효과인가)
        row("speed", MobEffectCategory.BENEFICIAL, 3402751, false);
        row("slowness", MobEffectCategory.HARMFUL, 9154528, false);
        row("haste", MobEffectCategory.BENEFICIAL, 14270531, false);
        row("mining_fatigue", MobEffectCategory.HARMFUL, 4866583, false);
        row("strength", MobEffectCategory.BENEFICIAL, 16762624, false);
        row("instant_health", MobEffectCategory.BENEFICIAL, 16262179, true);
        row("instant_damage", MobEffectCategory.HARMFUL, 11101546, true);
        row("jump_boost", MobEffectCategory.BENEFICIAL, 16646020, false);
        row("nausea", MobEffectCategory.HARMFUL, 5578058, false);
        row("regeneration", MobEffectCategory.BENEFICIAL, 13458603, false);
        row("resistance", MobEffectCategory.BENEFICIAL, 9520880, false);
        row("fire_resistance", MobEffectCategory.BENEFICIAL, 16750848, false);
        row("water_breathing", MobEffectCategory.BENEFICIAL, 10017472, false);
        row("invisibility", MobEffectCategory.BENEFICIAL, 16185078, false);
        row("blindness", MobEffectCategory.HARMFUL, 2039587, false);
        row("night_vision", MobEffectCategory.BENEFICIAL, 12779366, false);
        row("hunger", MobEffectCategory.HARMFUL, 5797459, false);
        row("weakness", MobEffectCategory.HARMFUL, 4738376, false);
        row("poison", MobEffectCategory.HARMFUL, 8889187, false);
        row("wither", MobEffectCategory.HARMFUL, 7561558, false);
        row("health_boost", MobEffectCategory.BENEFICIAL, 16284963, false);
        row("absorption", MobEffectCategory.BENEFICIAL, 2445989, false);
        row("saturation", MobEffectCategory.BENEFICIAL, 16262179, true);
        row("glowing", MobEffectCategory.NEUTRAL, 9740385, false);
        row("levitation", MobEffectCategory.HARMFUL, 13565951, false);
        row("luck", MobEffectCategory.BENEFICIAL, 5882118, false);
        row("unluck", MobEffectCategory.HARMFUL, 12624973, false);
        row("slow_falling", MobEffectCategory.BENEFICIAL, 15978425, false);
        row("conduit_power", MobEffectCategory.BENEFICIAL, 1950417, false);
        row("dolphins_grace", MobEffectCategory.BENEFICIAL, 8954814, false);
        row("bad_omen", MobEffectCategory.NEUTRAL, 745784, false);
        row("hero_of_the_village", MobEffectCategory.BENEFICIAL, 4521796, false);
        row("darkness", MobEffectCategory.HARMFUL, 2696993, false);
        row("trial_omen", MobEffectCategory.NEUTRAL, 1484454, false);
        row("raid_omen", MobEffectCategory.NEUTRAL, 14565464, false);
        row("wind_charged", MobEffectCategory.HARMFUL, 12438015, false);
        row("weaving", MobEffectCategory.HARMFUL, 7891290, false);
        row("oozing", MobEffectCategory.HARMFUL, 10092451, false);
        row("infested", MobEffectCategory.HARMFUL, 9214860, false);
    }

    private static volatile NmsMappedRegistry<MobEffect> registry;

    public static Registry<MobEffect> registry() { return mapped(); }

    private static NmsMappedRegistry<MobEffect> mapped() {
        NmsMappedRegistry<MobEffect> r = registry;
        if (r == null) {
            synchronized (NmsMobEffects.class) {
                r = registry;
                if (r == null) {
                    r = new NmsMappedRegistry<>(Registries.MOB_EFFECT);
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:mob_effect");
                    if (ids.isEmpty()) throw new IllegalStateException("서버가 효과 레지스트리 목록을 주지 않았다");
                    for (String id : ids) {
                        Row row = ROWS.get(id);
                        if (row == null) throw new IllegalStateException("효과 정의가 없는 서버 효과: " + id);
                        r.add(ResourceLocation.parse(id), row.instant()
                                ? new MobEffect(row.category(), row.color()) {
                                    @Override
                                    public boolean isInstantenous() { return true; }
                                }
                                : new MobEffect(row.category(), row.color()) {});
                    }
                    registry = r;
                }
            }
        }
        return r;
    }

    /** MobEffects 상수용: 이름의 등록된 손잡이. */
    public static Holder<MobEffect> holder(String id) {
        return mapped().getHolder(ResourceLocation.parse(id)).orElseThrow(() -> new IllegalStateException("서버 효과 레지스트리에 없는 이름: " + id));
    }
}
