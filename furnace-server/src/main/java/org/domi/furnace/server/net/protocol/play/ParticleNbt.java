// 입자 옵션 → NBT(바닐라 ParticleTypes.CODEC 의 저장 모양): {type, ...옵션 필드}. 읽기는 ParticleArgument.decodeNbt.
package org.domi.furnace.server.net.protocol.play;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class ParticleNbt {

    private ParticleNbt() {}

    private static NbtList floats(float... v) {
        NbtList l = new NbtList(NbtIo.TAG_FLOAT);
        for (float f : v) l.add(f);
        return l;
    }

    public static NbtCompound encode(ParticleOptions p) {
        NbtCompound t = new NbtCompound();
        t.putString("type", p.type());
        switch (p) {
            case ParticleOptions.Block b -> t.putCompound("block_state", org.domi.furnace.server.block.entity.PistonMovingBlockEntity.writeBlockState(b.blockState()));
            case ParticleOptions.Dust d -> {
                t.putList("color", floats(d.r(), d.g(), d.b()));
                t.putFloat("scale", d.scale());
            }
            case ParticleOptions.DustTransition d -> {
                t.putList("from_color", floats(d.fr(), d.fg(), d.fb()));
                t.putList("to_color", floats(d.tr(), d.tg(), d.tb()));
                t.putFloat("scale", d.scale());
            }
            case ParticleOptions.Color c -> t.putInt("color", c.argb());
            case ParticleOptions.Item i -> {
                NbtCompound item = new NbtCompound();
                item.putString("id", org.domi.furnace.server.item.ItemContext.name("minecraft:item", i.stack().itemId()));
                t.putCompound("item", item);
            }
            case ParticleOptions.SculkCharge s -> t.putFloat("roll", s.roll());
            case ParticleOptions.Shriek s -> t.putInt("delay", s.delay());
            case ParticleOptions.Vibration v -> {
                if (!v.entitySource()) {
                    NbtCompound dest = new NbtCompound();
                    dest.putString("type", "minecraft:block");
                    dest.putIntArray("pos", new int[] {v.x(), v.y(), v.z()});
                    t.putCompound("destination", dest);
                    t.putInt("arrival_in_ticks", v.arrivalTicks());
                }
            }
            case ParticleOptions.Simple s -> {}
        }
        return t;
    }
}
