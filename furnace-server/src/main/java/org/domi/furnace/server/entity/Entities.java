// 저장된 엔티티 NBT → 엔티티(바닐라 EntityType.create + load). 아직 다루지 못하는 종류는 null — 부르는 쪽이 원본 NBT 를 보존한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class Entities {

    private Entities() {}

    /** EntityType.create: 새 엔티티(없는 종류면 null). */
    public static Entity create(ChunkLevel level, String id) {
        return switch (id) {
            case "minecraft:zombie" -> new Zombie(level, level.nextEntityId());
            case "minecraft:villager" -> new Villager(level, level.nextEntityId());
            case "minecraft:chicken" -> new Chicken(level, level.nextEntityId());
            case "minecraft:cow" -> new Cow(level, level.nextEntityId());
            case "minecraft:pig" -> new Pig(level, level.nextEntityId());
            case "minecraft:rabbit" -> new Rabbit(level, level.nextEntityId());
            case "minecraft:wolf" -> new Wolf(level, level.nextEntityId());
            case "minecraft:ocelot" -> new Ocelot(level, level.nextEntityId());
            case "minecraft:cat" -> new Cat(level, level.nextEntityId());
            case "minecraft:husk" -> new Husk(level, level.nextEntityId());
            case "minecraft:giant" -> new Giant(level, level.nextEntityId());
            case "minecraft:arrow" -> new Arrow(level, level.nextEntityId());
            case "minecraft:witch" -> new Witch(level, level.nextEntityId());
            case "minecraft:potion" -> new ThrownPotion(level, level.nextEntityId());
            case "minecraft:silverfish" -> new Silverfish(level, level.nextEntityId());
            case "minecraft:endermite" -> new Endermite(level, level.nextEntityId());
            case "minecraft:enderman" -> new Enderman(level, level.nextEntityId());
            case "minecraft:slime" -> new Slime(level, level.nextEntityId());
            case "minecraft:magma_cube" -> new MagmaCube(level, level.nextEntityId());
            case "minecraft:spider" -> new Spider(level, level.nextEntityId());
            case "minecraft:cave_spider" -> new CaveSpider(level, level.nextEntityId());
            case "minecraft:creeper" -> new Creeper(level, level.nextEntityId());
            case "minecraft:tnt" -> new PrimedTnt(level, level.nextEntityId());
            case "minecraft:skeleton" -> new Skeleton(level, level.nextEntityId());
            case "minecraft:stray" -> new Stray(level, level.nextEntityId());
            case "minecraft:bogged" -> new Bogged(level, level.nextEntityId());
            case "minecraft:wither_skeleton" -> new WitherSkeleton(level, level.nextEntityId());
            case "minecraft:zombie_villager" -> new ZombieVillager(level, level.nextEntityId());
            case "minecraft:zombified_piglin" -> new ZombifiedPiglin(level, level.nextEntityId());
            case "minecraft:polar_bear" -> new PolarBear(level, level.nextEntityId());
            case "minecraft:mooshroom" -> new Mooshroom(level, level.nextEntityId());
            case "minecraft:sheep" -> new Sheep(level, level.nextEntityId());
            default -> null;
        };
    }

    public static Entity load(ChunkLevel level, NbtCompound tag) {
        if (!(tag.tags().get("id") instanceof String id)) return null;
        Entity e = switch (id) {
            case "minecraft:item" -> new ItemEntity(level, level.nextEntityId());
            case "minecraft:experience_orb" -> new ExperienceOrb(level, level.nextEntityId());
            case "minecraft:villager" -> new Villager(level, level.nextEntityId());
            case "minecraft:zombie" -> new Zombie(level, level.nextEntityId());
            case "minecraft:chicken" -> new Chicken(level, level.nextEntityId());
            case "minecraft:cow" -> new Cow(level, level.nextEntityId());
            case "minecraft:pig" -> new Pig(level, level.nextEntityId());
            case "minecraft:rabbit" -> new Rabbit(level, level.nextEntityId());
            case "minecraft:wolf" -> new Wolf(level, level.nextEntityId());
            case "minecraft:ocelot" -> new Ocelot(level, level.nextEntityId());
            case "minecraft:cat" -> new Cat(level, level.nextEntityId());
            case "minecraft:husk" -> new Husk(level, level.nextEntityId());
            case "minecraft:giant" -> new Giant(level, level.nextEntityId());
            case "minecraft:arrow" -> new Arrow(level, level.nextEntityId());
            case "minecraft:witch" -> new Witch(level, level.nextEntityId());
            case "minecraft:potion" -> new ThrownPotion(level, level.nextEntityId());
            case "minecraft:silverfish" -> new Silverfish(level, level.nextEntityId());
            case "minecraft:endermite" -> new Endermite(level, level.nextEntityId());
            case "minecraft:enderman" -> new Enderman(level, level.nextEntityId());
            case "minecraft:slime" -> new Slime(level, level.nextEntityId());
            case "minecraft:magma_cube" -> new MagmaCube(level, level.nextEntityId());
            case "minecraft:spider" -> new Spider(level, level.nextEntityId());
            case "minecraft:cave_spider" -> new CaveSpider(level, level.nextEntityId());
            case "minecraft:creeper" -> new Creeper(level, level.nextEntityId());
            case "minecraft:tnt" -> new PrimedTnt(level, level.nextEntityId());
            case "minecraft:skeleton" -> new Skeleton(level, level.nextEntityId());
            case "minecraft:stray" -> new Stray(level, level.nextEntityId());
            case "minecraft:bogged" -> new Bogged(level, level.nextEntityId());
            case "minecraft:wither_skeleton" -> new WitherSkeleton(level, level.nextEntityId());
            case "minecraft:zombie_villager" -> new ZombieVillager(level, level.nextEntityId());
            case "minecraft:zombified_piglin" -> new ZombifiedPiglin(level, level.nextEntityId());
            case "minecraft:polar_bear" -> new PolarBear(level, level.nextEntityId());
            case "minecraft:mooshroom" -> new Mooshroom(level, level.nextEntityId());
            case "minecraft:sheep" -> new Sheep(level, level.nextEntityId());
            default -> null;
        };
        if (e == null) return null;
        e.load(tag);
        return e;
    }
}
