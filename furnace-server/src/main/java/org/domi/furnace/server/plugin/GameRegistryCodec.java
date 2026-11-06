// 플러그인 바이옴·차원 타입 정의 → 클라이언트가 구성 단계 레지스트리 데이터로 받는 네트워크 NBT.
// 필드 이름과 값 종류는 바닐라 1.21.1 Biome.NETWORK_CODEC · DimensionType.DIRECT_CODEC 의 NBT 표현과 같다.
// 소리는 항상 직접 형식({sound_id})으로 보낸다 — 레지스트리 참조 형식은 클라이언트에 없는 id 면 레지스트리 전체를 거부당하지만
// 직접 형식은 리소스 팩 소리도 된다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.worldgen.BiomeDefinition;
import org.domi.furnace.api.worldgen.DimensionTypeDefinition;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;

final class GameRegistryCodec {

    /** 옵션 없이 {type} 만으로 쓸 수 없는 1.21.1 입자(먼지·블록·아이템 …). */
    private static final java.util.Set<String> PARTICLES_NEEDING_OPTIONS = java.util.Set.of(
            "minecraft:block", "minecraft:block_marker", "minecraft:dust", "minecraft:dust_color_transition", "minecraft:entity_effect",
            "minecraft:falling_dust", "minecraft:sculk_charge", "minecraft:item", "minecraft:vibration", "minecraft:shriek");

    private GameRegistryCodec() {}

    /** 정의가 이 서버에서 쓸 수 있는지 검사한다(알 수 없는 입자 등). 안 되면 IllegalArgumentException. */
    static void validate(BiomeDefinition def) {
        var p = def.ambientParticle();
        if (p != null) {
            if (!ParticleOptions.Registry.has(p.type())) throw new IllegalArgumentException("알 수 없는 입자 종류: " + p.type());
            if (PARTICLES_NEEDING_OPTIONS.contains(p.type())) throw new IllegalArgumentException("옵션이 필요한 입자는 바이옴 주변 입자로 쓸 수 없다: " + p.type());
        }
        for (var e : def.spawners().entrySet()) {
            for (var s : e.getValue()) {
                if (!org.domi.furnace.server.command.arguments.EntityTypes.isKnown(s.entityType())) {
                    throw new IllegalArgumentException("알 수 없는 몹 종류: " + s.entityType());
                }
            }
        }
        for (String type : def.spawnCosts().keySet()) {
            if (!org.domi.furnace.server.command.arguments.EntityTypes.isKnown(type)) throw new IllegalArgumentException("알 수 없는 몹 종류: " + type);
        }
    }

    static NbtCompound biome(BiomeDefinition d) {
        NbtCompound b = new NbtCompound();
        b.putBoolean("has_precipitation", d.hasPrecipitation());
        b.putFloat("temperature", d.temperature());
        if (d.temperatureModifier() != BiomeDefinition.TemperatureModifier.NONE) b.putString("temperature_modifier", d.temperatureModifier().key());
        b.putFloat("downfall", d.downfall());
        NbtCompound e = new NbtCompound();
        e.putInt("fog_color", d.fogColor());
        e.putInt("water_color", d.waterColor());
        e.putInt("water_fog_color", d.waterFogColor());
        e.putInt("sky_color", d.skyColor());
        d.foliageColor().ifPresent(c -> e.putInt("foliage_color", c));
        d.grassColor().ifPresent(c -> e.putInt("grass_color", c));
        if (d.grassColorModifier() != BiomeDefinition.GrassColorModifier.NONE) e.putString("grass_color_modifier", d.grassColorModifier().key());
        var p = d.ambientParticle();
        if (p != null) {
            NbtCompound options = new NbtCompound();
            options.putString("type", p.type());
            NbtCompound particle = new NbtCompound();
            particle.putCompound("options", options);
            particle.putFloat("probability", p.probability());
            e.putCompound("particle", particle);
        }
        if (d.ambientSound() != null) e.putCompound("ambient_sound", sound(d.ambientSound()));
        var mood = d.moodSound();
        if (mood != null) {
            NbtCompound m = new NbtCompound();
            m.putCompound("sound", sound(mood.sound()));
            m.putInt("tick_delay", mood.tickDelay());
            m.putInt("block_search_extent", mood.blockSearchExtent());
            m.putDouble("offset", mood.offset());
            e.putCompound("mood_sound", m);
        }
        var add = d.additionsSound();
        if (add != null) {
            NbtCompound a = new NbtCompound();
            a.putCompound("sound", sound(add.sound()));
            a.putDouble("tick_chance", add.tickChance());
            e.putCompound("additions_sound", a);
        }
        var music = d.music();
        if (music != null) {
            NbtCompound m = new NbtCompound();
            m.putCompound("sound", sound(music.sound()));
            m.putInt("min_delay", music.minDelay());
            m.putInt("max_delay", music.maxDelay());
            m.putBoolean("replace_current_music", music.replaceCurrentMusic());
            e.putCompound("music", m);
        }
        b.putCompound("effects", e);
        return b;
    }

    private static NbtCompound sound(String id) {
        NbtCompound s = new NbtCompound();
        s.putString("sound_id", id);
        return s;
    }

    static NbtCompound dimensionType(DimensionTypeDefinition d) {
        NbtCompound t = new NbtCompound();
        d.fixedTime().ifPresent(v -> t.putLong("fixed_time", v));
        t.putBoolean("has_skylight", d.hasSkyLight());
        t.putBoolean("has_ceiling", d.hasCeiling());
        t.putBoolean("ultrawarm", d.ultraWarm());
        t.putBoolean("natural", d.natural());
        t.putDouble("coordinate_scale", d.coordinateScale());
        t.putBoolean("bed_works", d.bedWorks());
        t.putBoolean("respawn_anchor_works", d.respawnAnchorWorks());
        t.putInt("min_y", d.minY());
        t.putInt("height", d.height());
        t.putInt("logical_height", d.logicalHeight());
        t.putString("infiniburn", "#" + d.infiniburn());
        t.putString("effects", d.effects());
        t.putFloat("ambient_light", d.ambientLight());
        t.putBoolean("piglin_safe", d.piglinSafe());
        t.putBoolean("has_raids", d.hasRaids());
        if (d.monsterSpawnLightMin() == d.monsterSpawnLightMax()) {
            t.putInt("monster_spawn_light_level", d.monsterSpawnLightMin());
        } else {
            NbtCompound u = new NbtCompound();
            u.putString("type", "minecraft:uniform");
            u.putInt("min_inclusive", d.monsterSpawnLightMin());
            u.putInt("max_inclusive", d.monsterSpawnLightMax());
            t.putCompound("monster_spawn_light_level", u);
        }
        t.putInt("monster_spawn_block_light_limit", d.monsterSpawnBlockLightLimit());
        return t;
    }
}
