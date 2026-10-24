// 바닐라 GlobalPos: 차원 + 블록 위치.
package org.domi.furnace.server.entity.ai.brain;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public record GlobalPos(String dimension, Pos pos) {
    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("dimension", dimension);
        c.putIntArray("pos", new int[] {pos.x(), pos.y(), pos.z()});
        return c;
    }

    public static GlobalPos fromNbt(Object o) {
        if (o instanceof NbtCompound c && c.tags().get("pos") instanceof int[] p && p.length == 3 && c.tags().get("dimension") instanceof String d) {
            return new GlobalPos(d, new Pos(p[0], p[1], p[2]));
        }
        return null;
    }
}
