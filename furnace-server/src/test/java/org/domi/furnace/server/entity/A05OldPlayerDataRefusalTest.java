// A05-04: 이 서버보다 옛 판(픽서 없음)·새 판으로 저장된 플레이어 파일은 열지 않는다. 깨진 파일이 아니므로 .corrupt 로 옮기거나 .dat_old 로 바꿔 읽지 않고
// 파일을 그대로 둔다(그러면 접속이 거부되고, 새 플레이어로 덮어써 인벤토리가 사라지는 일이 없다).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.anvil.NewerDataVersionException;
import org.domi.furnace.server.world.anvil.OlderDataVersionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A05OldPlayerDataRefusalTest {

    @TempDir Path world;

    private Path dat(UUID id) { return world.resolve("playerdata").resolve(id + ".dat"); }

    private void saveWithDataVersion(UUID id, int version) throws Exception {
        PlayerData.save(world, id, 100, 64, 0, 0f, 0f, true, new ItemStack[46]);
        NbtCompound root;
        try (var in = Files.newInputStream(dat(id))) { root = NbtIo.readGzip(in); }
        root.putInt("DataVersion", version);
        try (var out = Files.newOutputStream(dat(id))) { NbtIo.writeGzip(out, root, ""); }
    }

    @Test
    void olderPlayerFile_isRefusedAndKeptAsIs() throws Exception {
        UUID id = UUID.randomUUID();
        saveWithDataVersion(id, 3700);
        byte[] before = Files.readAllBytes(dat(id));
        assertThrows(OlderDataVersionException.class, () -> PlayerData.load(world, id));
        assertThrows(OlderDataVersionException.class, () -> PlayerData.load(world, id));   // 두 번째에도 파일이 그 자리에 있다
        assertArrayEquals(before, Files.readAllBytes(dat(id)));
        assertFalse(Files.exists(dat(id).resolveSibling(id + ".dat.corrupt")), "깨진 파일로 다루지 않는다");
    }

    @Test
    void newerPlayerFile_isRefused() throws Exception {
        UUID id = UUID.randomUUID();
        saveWithDataVersion(id, 4189);
        assertThrows(NewerDataVersionException.class, () -> PlayerData.load(world, id));
        assertTrue(Files.exists(dat(id)));
    }

    @Test
    void currentPlayerFile_loads() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerData.save(world, id, 100, 64, 0, 0f, 0f, true, new ItemStack[46]);
        assertNotNull(PlayerData.load(world, id));
    }

    @Test
    void override_readsOlderPlayerFile() throws Exception {
        UUID id = UUID.randomUUID();
        saveWithDataVersion(id, 3700);
        System.setProperty(OlderDataVersionException.IGNORE_PROPERTY, "true");
        try {
            assertNotNull(PlayerData.load(world, id));
        } finally {
            System.clearProperty(OlderDataVersionException.IGNORE_PROPERTY);
        }
    }

    @Test
    void boundary_3944Refused_3945And3953Load() throws Exception {   // 바닐라 1.21.1 DataFixers 마지막 스키마 3945
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        saveWithDataVersion(a, 3944);
        assertThrows(OlderDataVersionException.class, () -> PlayerData.load(world, a));
        saveWithDataVersion(b, 3945);
        assertNotNull(PlayerData.load(world, b));
        saveWithDataVersion(c, 3953);
        assertNotNull(PlayerData.load(world, c));
    }
}
