package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/**
 * T-141 물결 1-C: Block 빛·getDrops·breakNaturally.
 * 기대값은 Paper 1.21.1 CraftBlock(Improve-Block-breakNaturally-API·Properly-handle-experience-dropping 패치 반영)에서 옮겼다:
 * 도구가 null 이면 도구 검사 없음, 드롭은 경험치 없이(dropResources(…, false)) → 효과 → dropExperience 일 때만 경험치 →
 * removeBlock(유체 남김) → 얼음은 물, 거북알은 하나 줄임. 반환은 "칸이 바뀌었고 드롭 조건도 맞음".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitBlockBreakTest {

    FurnaceTestServer server;
    TestPlayer tp;
    World world;
    BlockPos origin;
    int next;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Juno");
        server.tick(3);
        var loc = tp.player().location();
        // 높이는 고정(슈퍼플랫 땅 -60 위 40칸): 접속 직후 location().y 가 아직 0 일 때가 있어 그 위로 잡으면 64칸 밖이 돼 레벨 이벤트를 못 받는다
        origin = new BlockPos((int) Math.floor(loc.x()) & ~15, -20, (int) Math.floor(loc.z()) & ~15);
        server.loadChunk(origin.chunkX(), origin.chunkZ());
        world = Compat.world(server.overworld());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    /** 같은 청크 안에서 시험마다 다른 칸(위·아래가 공기인 칸). */
    Block fresh() {
        int i = next++;
        return world.getBlockAt(origin.x() + (i % 8) * 2, origin.y(), origin.z() + (i / 8) * 2);
    }

    void set(Block b, String data) { owner(() -> { b.setBlockData(Bukkit.createBlockData(data)); return null; }); }

    List<Entity> near(Block b) {
        return owner(() -> List.copyOf(b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)));
    }

    boolean brk(Block b) {
        Boolean r = owner(b::breakNaturally);
        return r;
    }

    void clear(List<Entity> es) { owner(() -> { es.forEach(Entity::remove); return null; }); }

    @Test
    void ownerThreadOnly() {
        Block b = fresh();
        assertThrows(ThreadOwnershipException.class, b::getLightLevel);
        assertThrows(ThreadOwnershipException.class, b::getDrops);
        assertThrows(ThreadOwnershipException.class, b::breakNaturally);
    }

    @Test
    void lightFollowsSkyDarkening() {
        Block b = fresh();
        world.setFullTime(6_000);
        server.tick(2);
        assertEquals(15, (int) owner(b::getLightFromSky));
        assertEquals(0, (int) owner(b::getLightFromBlocks));
        assertEquals(15, (int) owner(b::getLightLevel));
        world.setFullTime(18_000);
        server.tick(2);
        assertEquals(15, (int) owner(b::getLightFromSky), "하늘빛 자체는 낮밤 무관");
        assertEquals(4, (int) owner(b::getLightLevel), "한밤 하늘 어두움 11 을 뺀다");
        world.setFullTime(6_000);
        server.tick(2);
    }

    /*
     * 시험 서버에는 바닐라 전리품 표가 없어(데이터 팩 없음) 드롭 내용은 늘 비어 있다. 드롭 내용은 플레이어 파괴와 같은 길
     * (BlockBehavior.getDrops → 전리품 표)이고 서버 VanillaBlockLootTest 가 바닐라 데이터로 잰다. 여기서는 도구 조건을 반환값으로 잰다.
     */
    @Test
    void toolGateDecidesResult() {
        Block stone = fresh();
        set(stone, "minecraft:stone");
        assertTrue(owner(() -> stone.getDrops(new ItemStack(Material.AIR))).isEmpty(), "맨손은 돌에 맞는 도구가 아니다");
        assertTrue(owner(() -> stone.getDrops(new ItemStack(Material.DIAMOND_SHOVEL))).isEmpty());
        assertEquals(Material.STONE, owner(stone::getType), "getDrops 는 칸을 바꾸지 않는다");
        assertTrue(brk(stone), "도구 null = 검사 없음");
        assertEquals(Material.AIR, owner(stone::getType));

        Block pick = fresh();
        set(pick, "minecraft:stone");
        assertTrue(owner(() -> pick.breakNaturally(new ItemStack(Material.WOODEN_PICKAXE))), "맞는 도구");

        Block bare = fresh();
        set(bare, "minecraft:stone");
        assertFalse(owner(() -> bare.breakNaturally(new ItemStack(Material.AIR))), "맞지 않는 도구(빈손): 드롭 없음 → false");
        assertEquals(Material.AIR, owner(bare::getType), "그래도 칸은 지운다");

        Block dirt = fresh();
        set(dirt, "minecraft:dirt");
        assertTrue(owner(() -> dirt.breakNaturally(new ItemStack(Material.AIR))), "도구가 필요 없는 블록은 빈손도 된다");

        Block air = fresh();
        assertFalse(brk(air), "공기는 드롭 조건도, 칸 바꾸기도 아니다");
        Block cave = fresh();
        set(cave, "minecraft:cave_air");
        assertTrue(brk(cave), "동굴 공기는 드롭 조건을 통과하고(Blocks.AIR 만 막는다) 공기로 바뀐다(Paper 와 같다)");
        assertEquals(Material.AIR, owner(cave::getType));
    }

    @Test
    void fluidAndSpecialCases() {
        Block slab = fresh();
        set(slab, "minecraft:oak_slab[waterlogged=true]");
        assertTrue(brk(slab));
        assertEquals(Material.WATER, owner(slab::getType), "물 잠긴 블록은 물을 남긴다");
        clear(near(slab));
        set(slab, "minecraft:air");

        Block ice = fresh();
        set(ice.getRelative(0, -1, 0), "minecraft:stone");
        set(ice, "minecraft:ice");
        assertTrue(brk(ice));
        assertEquals(Material.WATER, owner(ice::getType), "아래가 막힌 얼음은 물이 된다");
        set(ice, "minecraft:air");

        Block eggs = fresh();
        set(eggs.getRelative(0, -1, 0), "minecraft:sand");
        set(eggs, "minecraft:turtle_egg[eggs=3]");
        assertTrue(brk(eggs));
        assertEquals("minecraft:turtle_egg[eggs=2,hatch=0]", owner(() -> eggs.getBlockData().getAsString()), "거북알은 하나만 준다");
    }

    @Test
    void experienceOnlyWhenAsked() {
        Block quiet = fresh();
        set(quiet, "minecraft:spawner");
        assertTrue(brk(quiet), "도구 검사 없음 → 드롭 조건 통과(전리품은 비어 있다)");
        assertTrue(near(quiet).stream().noneMatch(e -> e.getType() == EntityType.EXPERIENCE_ORB), "dropExperience 가 거짓이면 경험치 없음(Paper 는 dropResources(…, false))");

        Block xp = fresh();
        set(xp, "minecraft:spawner");
        assertTrue(owner(() -> xp.breakNaturally(false, true)));
        List<Entity> orbs = near(xp).stream().filter(e -> e.getType() == EntityType.EXPERIENCE_ORB).toList();
        assertFalse(orbs.isEmpty(), "스포너 경험치 15~43");
        clear(orbs);
    }

    @Test
    void effectSendsLevelEvent() {
        Block glass = fresh();
        set(glass, "minecraft:glass");
        tp.clearReceived();
        assertTrue(owner(() -> glass.breakNaturally(true, false)));
        server.tick(1);   // 보낸 패킷은 틱 끝에 내보낸다
        var e = tp.awaitPacket(PacketTypes.Clientbound.LEVEL_EVENT, p -> new BlockPos(glass.getX(), glass.getY(), glass.getZ()).equals(p.get("pos")));
        assertEquals(2001, e.get("type"));
    }
}
