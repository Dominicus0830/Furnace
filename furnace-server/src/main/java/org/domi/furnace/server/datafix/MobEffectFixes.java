// 3568 MobEffectIdFix: 숫자 효과 id → 이름, 효과 필드 이름을 소문자 꼴로. 신호기·엔티티·아이템(물약·수상한 스튜) 세 갈래.
package org.domi.furnace.server.datafix;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class MobEffectFixes {
    private MobEffectFixes() {}

    private static final String[] IDS = {
        null, "speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage", "jump_boost", "nausea",
        "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility", "blindness", "night_vision", "hunger", "weakness",
        "poison", "wither", "health_boost", "absorption", "saturation", "glowing", "levitation", "luck", "unluck", "slow_falling",
        "conduit_power", "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness"};

    /** 숫자 id → "minecraft:이름"(모르는 숫자·숫자가 아니면 null). */
    private static String idOf(Object v) {
        if (!(v instanceof Number n)) return null;
        int i = n.intValue();
        return i > 0 && i < IDS.length ? "minecraft:" + IDS[i] : null;
    }

    /** replaceField(from, to, 이름 id): from 은 늘 지우고, 이름이 있으면 dst 에 to 로 넣는다. */
    private static void replaceId(NbtCompound src, String from, NbtCompound dst, String to) {
        String id = idOf(src.tags().get(from));
        dst.tags().remove(from);
        if (id != null) {
            dst.tags().remove(to);
            dst.putString(to, id);
        }
    }

    /** 효과 하나(MobEffectInstance 저장 꼴). */
    static NbtCompound effectInstance(NbtCompound e) {
        replaceId(e, "Id", e, "id");
        FixUtil.rename(e, "Ambient", "ambient");
        FixUtil.rename(e, "Amplifier", "amplifier");
        FixUtil.rename(e, "Duration", "duration");
        FixUtil.rename(e, "ShowParticles", "show_particles");
        FixUtil.rename(e, "ShowIcon", "show_icon");
        Object hidden = e.tags().remove("HiddenEffect");
        if (hidden instanceof NbtCompound h) e.put("hidden_effect", effectInstance(h));
        else if (hidden != null) e.put("hidden_effect", hidden);
        return e;
    }

    /** 효과 목록 이름 바꾸기(from 이 목록이 아니면 지운다 — replaceField 에 빈 값). */
    private static void effectList(NbtCompound c, String from, String to) {
        if (!c.contains(from)) return;
        Object v = c.tags().remove(from);
        if (v instanceof NbtList l) {
            c.tags().remove(to);
            c.put(to, FixUtil.mapCompounds(l, MobEffectFixes::effectInstance));
        }
    }

    /** 수상한 스튜 효과 하나: EffectId·EffectDuration → id·duration(같은 compound 안에서). */
    private static NbtCompound stewEffect(NbtCompound e) {
        Object dur = e.tags().get("EffectDuration");
        replaceId(e, "EffectId", e, "id");
        e.tags().remove("EffectDuration");
        if (dur != null) e.put("duration", dur);
        return e;
    }

    static NbtCompound beacon(NbtCompound be) {
        replaceId(be, "Primary", be, "primary_effect");
        replaceId(be, "Secondary", be, "secondary_effect");
        return be;
    }

    static NbtCompound entity(NbtCompound e) {
        switch (FixUtil.id(e)) {
            case "minecraft:mooshroom" -> {
                NbtCompound stew = new NbtCompound();
                replaceId(e, "EffectId", stew, "id");
                if (e.contains("EffectDuration")) stew.put("duration", e.tags().get("EffectDuration"));
                if (!stew.tags().isEmpty()) {
                    NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
                    l.add(stew);
                    e.putList("stew_effects", l);
                }
                e.tags().remove("EffectId");
                e.tags().remove("EffectDuration");
            }
            case "minecraft:arrow" -> effectList(e, "CustomPotionEffects", "custom_potion_effects");
            case "minecraft:area_effect_cloud" -> effectList(e, "Effects", "effects");
            default -> { }
        }
        effectList(e, "ActiveEffects", "active_effects");
        return e;
    }

    private static final java.util.Set<String> POTIONS = java.util.Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");

    static NbtCompound item(NbtCompound item) {
        if (!(item.tags().get("tag") instanceof NbtCompound tag)) return item;
        String id = FixUtil.id(item);
        if (id.equals("minecraft:suspicious_stew")) {
            if (tag.contains("Effects")) {
                Object v = tag.tags().remove("Effects");
                if (v instanceof NbtList l) tag.put("effects", FixUtil.mapCompounds(l, MobEffectFixes::stewEffect));
            }
        } else if (POTIONS.contains(id)) {
            effectList(tag, "CustomPotionEffects", "custom_potion_effects");
        }
        return item;
    }
}
