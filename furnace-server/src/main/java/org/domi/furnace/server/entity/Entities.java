// 저장된 엔티티 NBT → 엔티티(바닐라 EntityType.create + load). 아직 다루지 못하는 종류는 null — 부르는 쪽이 원본 NBT 를 보존한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class Entities {

    private Entities() {}

    public static Entity load(ChunkLevel level, NbtCompound tag) {
        if (!(tag.tags().get("id") instanceof String id)) return null;
        Entity e = switch (id) {
            case "minecraft:item" -> new ItemEntity(level, level.nextEntityId());
            case "minecraft:experience_orb" -> new ExperienceOrb(level, level.nextEntityId());
            case "minecraft:villager" -> new Villager(level, level.nextEntityId());
            default -> null;
        };
        if (e == null) return null;
        e.load(tag);
        return e;
    }
}
