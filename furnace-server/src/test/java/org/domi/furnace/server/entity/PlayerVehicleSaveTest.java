// 탄 채 나간 플레이어의 탈것: 퇴장 때 RootVehicle 저장·탈것 빼기, 접속 때 복원, 본체 저장과의 순서, 청크 저장 제외.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerVehicleSaveTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Boat boat(BlockPlacementTest.TestLevel level) {
        Boat b = new Boat(level, level.nextEntityId());
        b.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(b);
        return b;
    }

    private static Player player(int id, List<double[]> teleports) {
        Player p = new Player(id, UUID.randomUUID(), "rider" + id, new ConcurrentEmbeddedChannel());
        p.teleporter(teleports::add);
        return p;
    }

    private static NbtCompound readFile(Path dir, UUID id) {
        return PlayerData.load(dir, id).root();
    }

    @Test
    void quitWhileRidingSavesAndRemovesMountThenRejoinRestores(@TempDir Path dir) {
        var level = new BlockPlacementTest.TestLevel();
        Boat b = boat(level);
        Chicken c = new Chicken(level, level.nextEntityId());
        c.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(c);
        assertTrue(c.startRiding(b, true));
        List<double[]> teleports = new ArrayList<>();
        Player p = player(970, teleports);
        assertTrue(p.startRiding(b, true));
        assertTrue(b.hasExactlyOnePlayerPassenger());
        assertFalse(b.savedWithChunk(), "플레이어 하나만 태운 탈것은 청크에 저장하지 않는다");
        // 본체 저장이 먼저 파일을 만든다(순서가 바뀌어도 RootVehicle 은 덧쓰기라 같다)
        PlayerData.save(dir, p.uuid(), 1, 2, 3, 0, 0, true, new ItemStack[46], new NbtCompound());

        PlayerVehicleSave.onDisconnect(p, Runnable::run, dir);
        assertNull(p.vehicle());
        assertTrue(teleports.isEmpty(), "끊긴 플레이어는 내릴 자리로 옮기지 않는다");
        assertSame(Entity.RemovalReason.UNLOADED_WITH_PLAYER, b.removalReason());
        assertSame(Entity.RemovalReason.UNLOADED_WITH_PLAYER, c.removalReason());

        NbtCompound root = readFile(dir, p.uuid());
        NbtCompound rv = root.getCompound(PlayerVehicleSave.KEY);
        assertArrayEquals(Entity.uuidToInts(b.uuid()), rv.getIntArray("Attach"));
        NbtCompound et = rv.getCompound("Entity");
        assertEquals("minecraft:boat", et.getString("id"));
        NbtList passengers = et.getList("Passengers");
        assertEquals(1, passengers.size(), "플레이어가 아닌 승객(닭)만 같이 저장한다");
        assertEquals("minecraft:chicken", ((NbtCompound) passengers.get(0)).getString("id"));

        // 다시 퇴장 처리해도 두 번 쓰지 않는다
        PlayerVehicleSave.onDisconnect(p, Runnable::run, dir);
        assertTrue(readFile(dir, p.uuid()).contains(PlayerVehicleSave.KEY));

        // 본체 저장(자동저장·퇴장)은 RootVehicle 을 지우지 않는다
        PlayerData.save(dir, p.uuid(), 1, 2, 3, 0, 0, true, new ItemStack[46], new NbtCompound());
        root = readFile(dir, p.uuid());
        assertTrue(root.contains(PlayerVehicleSave.KEY));

        // 재접속: 같은 UUID 의 보트에 닭과 함께 다시 탄다
        var level2 = new BlockPlacementTest.TestLevel();
        Player p2 = new Player(971, p.uuid(), p.name(), new ConcurrentEmbeddedChannel());
        p2.teleporter(t -> {});
        PlayerVehicleSave.restore(p2, level2, root);
        assertNotNull(p2.vehicle());
        assertInstanceOf(Boat.class, p2.vehicle());
        assertEquals(b.uuid(), p2.vehicle().uuid());
        assertEquals(2, p2.vehicle().passengers().size());
        assertTrue(p2.rootVehicleOnDisk);

        // 같은 UUID 가 이미 월드에 있으면 다시 넣지 않는다(복제 방지)
        Player p3 = new Player(972, p.uuid(), p.name(), new ConcurrentEmbeddedChannel());
        PlayerVehicleSave.restore(p3, level2, root);
        assertNull(p3.vehicle());

        // 내린 뒤 자동저장: 파일의 RootVehicle 을 지운다
        p2.detachFromVehicle();
        PlayerVehicleSave.autosave(p2, Runnable::run, dir);
        assertFalse(readFile(dir, p.uuid()).contains(PlayerVehicleSave.KEY));
        assertFalse(p2.rootVehicleOnDisk);
    }

    @Test
    void sharedMountStaysInWorld(@TempDir Path dir) {
        var level = new BlockPlacementTest.TestLevel();
        Boat b = boat(level);
        Player a = player(973, new ArrayList<>());
        Player o = player(974, new ArrayList<>());
        assertTrue(a.startRiding(b, true));
        assertTrue(o.startRiding(b, true));
        assertFalse(b.hasExactlyOnePlayerPassenger());
        assertTrue(b.savedWithChunk());
        assertNull(PlayerVehicleSave.rootVehicleTag(a));
        PlayerVehicleSave.onDisconnect(a, Runnable::run, dir);
        assertNull(a.vehicle());
        assertFalse(b.isRemoved(), "다른 플레이어도 탄 탈것은 남는다");
        assertEquals(List.of(o), b.passengers());
        assertFalse(java.nio.file.Files.exists(dir.resolve("playerdata")), "쓸 것이 없으면 파일을 만들지 않는다");
    }
}
