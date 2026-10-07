// 바닐라/Paper 형식이거나 손상된 playerdata 파일이 접속을 막지 않는지 검증
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataVanillaTest {

    @TempDir Path tmp;

    private void write(UUID id, NbtCompound root) throws Exception {
        Path f = tmp.resolve("playerdata").resolve(id + ".dat");
        Files.createDirectories(f.getParent());
        try (var os = Files.newOutputStream(f)) { NbtIo.writeGzip(os, root, ""); }
    }

    @Test
    void vanillaFormat_loadsPositionWithoutThrowing() throws Exception {
        UUID id = UUID.randomUUID();
        NbtCompound root = new NbtCompound();
        NbtList pos = new NbtList(NbtIo.TAG_DOUBLE); pos.add(10.5); pos.add(70.0); pos.add(-3.25);
        root.putList("Pos", pos);
        NbtList rot = new NbtList(NbtIo.TAG_FLOAT); rot.add(90f); rot.add(15f);
        root.putList("Rotation", rot);
        root.putByte("OnGround", (byte) 1);
        NbtList inv = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound e = new NbtCompound();
        e.putByte("Slot", (byte) 0);        // 바닐라: byte Slot, 문자열 id, 소문자 count
        e.putString("id", "minecraft:stone");
        e.putInt("count", 5);
        inv.add(e);
        root.putList("Inventory", inv);
        write(id, root);

        PlayerData.Snapshot s = PlayerData.load(tmp, id);
        assertNotNull(s);
        assertEquals(10.5, s.x());
        assertEquals(-3.25, s.z());
        assertEquals(90f, s.yaw());
    }

    @Test
    void corruptFile_returnsNull_andKeepsBackup() throws Exception {
        UUID id = UUID.randomUUID();
        Path f = tmp.resolve("playerdata").resolve(id + ".dat");
        Files.createDirectories(f.getParent());
        Files.write(f, new byte[]{1, 2, 3, 4, 5});
        assertNull(PlayerData.load(tmp, id), "손상된 파일은 새 플레이어처럼 시작해야 한다");
        assertTrue(Files.exists(tmp.resolve("playerdata").resolve(id + ".dat.corrupt")), "원본은 백업으로 보존");
    }

    @Test
    void missingPos_returnsNull() throws Exception {
        UUID id = UUID.randomUUID();
        write(id, new NbtCompound());
        assertNull(PlayerData.load(tmp, id));
    }
}
