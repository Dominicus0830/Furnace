// playerdata/<uuid>.dat (gzip NBT) 위치 read/write. M4c가 Inventory/Health 확장.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class PlayerData {

    private PlayerData() {}

    private static Path file(Path worldDir, UUID id) {
        return worldDir.resolve("playerdata").resolve(id + ".dat");
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround) {
        NbtCompound root = new NbtCompound();
        NbtList pos = new NbtList(NbtIo.TAG_DOUBLE);
        pos.add(x); pos.add(y); pos.add(z);
        root.putList("Pos", pos);
        NbtList rot = new NbtList(NbtIo.TAG_FLOAT);
        rot.add(yaw); rot.add(pitch);
        root.putList("Rotation", rot);
        root.putByte("OnGround", (byte) (onGround ? 1 : 0));
        try {
            Path f = file(worldDir, id);
            Files.createDirectories(f.getParent());
            try (var os = Files.newOutputStream(f)) {
                NbtIo.writeGzip(os, root, "");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 저장 실패 " + id, e);
        }
    }

    /** {x,y,z,yaw,pitch,onGround(1/0)} 또는 null(없음). */
    public static double[] load(Path worldDir, UUID id) {
        Path f = file(worldDir, id);
        if (!Files.exists(f)) return null;
        try (var is = Files.newInputStream(f)) {
            NbtCompound root = NbtIo.readGzip(is);
            NbtList pos = root.getList("Pos");
            NbtList rot = root.getList("Rotation");
            return new double[]{
                    (Double) pos.get(0), (Double) pos.get(1), (Double) pos.get(2),
                    (Float) rot.get(0), (Float) rot.get(1),
                    root.getByte("OnGround") != 0 ? 1.0 : 0.0
            };
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 로드 실패 " + id, e);
        }
    }
}
