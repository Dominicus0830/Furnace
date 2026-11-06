// 내부 접근 다리(Internals)와 엔티티·블록 엔티티 NBT API(/data 와 같은 경로): 소유 스레드 규칙, 형 확인, 버전 확인, 바뀜 여부.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.internal.Internals;
import org.domi.furnace.api.internal.UnsupportedVersionException;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.RegionLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InternalsApiTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final ChunkPos FAR = new ChunkPos(40, 0);   // 다른 region

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    private <T> T at(ChunkPos cp, java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(cp, f).get(30, TimeUnit.SECONDS);
    }

    private void tick(ChunkPos cp, int n) throws Exception {
        at(cp, r -> {
            for (int i = 0; i < n; i++) r.tick();
            return null;
        });
    }

    /** 엔티티 작업은 엔티티 틱에 돈다 — 끝날 때까지 region 을 틱한다. */
    private <T> T settle(CompletableFuture<T> f) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!f.isDone() && System.nanoTime() < end) tick(C00, 1);
        return f.get(1, TimeUnit.SECONDS);
    }

    private Throwable settleFail(CompletableFuture<?> f) throws Exception {
        var ex = assertThrows(ExecutionException.class, () -> settle(f));
        return ex.getCause();
    }

    private Cow cow(Region r) {
        var level = r.level();
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(8, -50, 8, 0, 0);
        assertTrue(level.addFreshEntity(cow));
        return cow;
    }

    private Player player() {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        return p;
    }

    // ------------------------------------------------------------------ 엔티티 NBT

    @Test
    void entityNbtReadsAndEditsLikeDataCommand() throws Exception {
        Cow[] c = new Cow[1];
        var handle = at(C00, r -> EntityHandle.of(host, c[0] = cow(r)));

        NbtCompound nbt = settle(handle.readNbt());
        assertEquals(c[0].uuid(), nbt.getUuid("UUID"));
        assertEquals(10f, nbt.getFloat("Health"));
        assertFalse(nbt.getBoolean("NoAI"));

        // 묶음 안쪽까지 고치고, 바뀌었으면 true
        assertTrue(settle(handle.editNbt(n -> {
            n.putBoolean("NoAI", true);
            n.putBoolean("Silent", true);
            n.list("Tags").add("boss");
            n.putUuid("UUID", UUID.randomUUID());   // UUID 는 /data 처럼 바뀌지 않는다
        })));
        assertTrue(this.<Boolean>at(C00, r -> c[0].isNoAi()), "Entity.load 로 실렸다");
        assertTrue(this.<Boolean>at(C00, r -> c[0].getTags().contains("boss")));
        assertEquals(c[0].uuid(), handle.uniqueId());
        NbtCompound after = settle(handle.readNbt());
        assertEquals(c[0].uuid(), after.getUuid("UUID"), "UUID 는 유지");
        assertEquals(NbtList.of("boss"), after.getList("Tags"));

        // 그대로면 false(싣지 않는다)
        assertFalse(settle(handle.editNbt(n -> n.putBoolean("NoAI", true))));
        // merge = /data merge
        assertTrue(settle(handle.mergeNbt(NbtCompound.parse("{NoAI:0b,CustomName:'\"Bessie\"'}"))));
        assertFalse(this.<Boolean>at(C00, r -> c[0].isNoAi()));
        assertTrue(this.<Boolean>at(C00, r -> c[0].hasCustomName()));

        // 사라진 엔티티
        at(C00, r -> {
            c[0].discard();
            return null;
        });
        assertInstanceOf(IllegalStateException.class, settleFail(handle.readNbt()));
        assertInstanceOf(IllegalStateException.class, settleFail(handle.editNbt(n -> fail("돌면 안 된다"))));
    }

    @Test
    void playerNbtIsReadOnly() throws Exception {
        Player p = player();
        at(C00, r -> {
            r.adopt(p);
            return null;
        });
        var ph = host.player(p);
        NbtCompound nbt = settle(ph.readNbt());
        assertEquals(p.uuid(), nbt.getUuid("UUID"));
        assertTrue(nbt.contains("Inventory"));
        assertInstanceOf(UnsupportedOperationException.class, settleFail(ph.editNbt(n -> n.putBoolean("NoAI", true))));
    }

    // ------------------------------------------------------------------ 블록 엔티티 NBT

    @Test
    void blockEntityNbtReadsAndEditsLikeDataCommand() throws Exception {
        var w = api();
        BlockPos pos = new BlockPos(2, -59, 2);
        assertTrue(w.setBlock(pos, BlockData.of("chest")).get(10, TimeUnit.SECONDS));
        NbtCompound nbt = w.blockEntityNbt(pos).get(10, TimeUnit.SECONDS).orElseThrow();
        assertEquals("minecraft:chest", nbt.getString("id"));
        assertEquals(2, nbt.getInt("x"));
        assertEquals(-59, nbt.getInt("y"));

        NbtCompound item = NbtCompound.parse("{Slot:4b,id:\"minecraft:diamond\",count:5}");
        assertTrue(w.editBlockEntityNbt(pos, n -> n.list("Items").add(item)).get(10, TimeUnit.SECONDS));
        assertEquals(5, (int) this.<Integer>at(C00, r -> r.chestAt(2, -59, 2).items[4].count()), "loadWithComponents 로 실렸다");
        assertFalse(w.editBlockEntityNbt(pos, n -> {}).get(10, TimeUnit.SECONDS), "그대로면 false");
        assertTrue(w.mergeBlockEntityNbt(pos, NbtCompound.parse("{CustomName:'\"Loot\"'}")).get(10, TimeUnit.SECONDS));
        assertTrue(w.blockEntityNbt(pos).get(10, TimeUnit.SECONDS).orElseThrow().contains("CustomName"));

        // 블록 엔티티가 없는 칸·로드되지 않은 칸
        BlockPos stone = new BlockPos(3, -59, 3);
        assertTrue(w.setBlock(stone, BlockData.of("stone")).get(10, TimeUnit.SECONDS));
        assertEquals(Optional.empty(), w.blockEntityNbt(stone).get(10, TimeUnit.SECONDS));
        assertFalse(w.editBlockEntityNbt(stone, n -> n.putInt("x", 1)).get(10, TimeUnit.SECONDS));
        BlockPos far = new BlockPos(16 * 500, 10, 16 * 500);
        int before = core.world().loadedChunkCount();
        assertEquals(Optional.empty(), w.blockEntityNbt(far).get(10, TimeUnit.SECONDS));
        assertEquals(before, core.world().loadedChunkCount(), "청크를 만들지 않는다");
    }

    // ------------------------------------------------------------------ Internals

    @Test
    void versionChecks() {
        InternalsImpl in = host.internals();
        assertEquals("1.21.1", in.minecraftVersion());
        assertEquals(767, in.protocolVersion());
        assertEquals(3955, in.dataVersion());
        assertTrue(in.isMinecraft("1.21", "1.21.1"));
        in.requireMinecraft("1.21.1");
        var ex = assertThrows(UnsupportedVersionException.class, () -> in.requireMinecraft("1.21.4", "1.21.5"));
        assertEquals("1.21.1", ex.actual());
        assertTrue(ex.getMessage().contains("1.21.4"), ex.getMessage());
        assertTrue(in.users().isEmpty(), "플러그인 클래스로더가 아닌 곳에서 부르면 이름을 남기지 않는다");
    }

    @Test
    void withEntityHandsTypedInternalObjectOnOwnerThread() throws Exception {
        Cow[] c = new Cow[1];
        var handle = at(C00, r -> EntityHandle.of(host, c[0] = cow(r)));
        Internals in = host.internals();

        // 타입 지정으로 꺼내 쓰고, 그 region 스레드에서 돈다
        boolean[] owner = new boolean[1];
        settle(in.runWithEntity(handle, Cow.class, cow -> {
            owner[0] = core.world().ownsChunkOnCurrentThread(0, 0);
            cow.setNoAi(true);
        }));
        assertTrue(owner[0]);
        assertTrue(this.<Boolean>at(C00, r -> c[0].isNoAi()));
        assertEquals(10f, (float) settle(in.<org.domi.furnace.server.entity.LivingEntity, Float>withEntity(handle, org.domi.furnace.server.entity.LivingEntity.class, le -> le.maxHealth())));

        // 형이 틀리면 실제 클래스 이름을 알려 준다
        var cce = settleFail(in.withEntity(handle, Zombie.class, z -> z));
        assertInstanceOf(ClassCastException.class, cce);
        assertTrue(cce.getMessage().contains(Cow.class.getName()), cce.getMessage());

        // entityNow: 소유 스레드에서만
        assertThrows(ThreadOwnershipException.class, () -> in.entityNow(handle, Cow.class));
        CompletableFuture<Cow> now = new CompletableFuture<>();
        handle.entityScheduler().execute(() -> now.complete(in.entityNow(handle, Cow.class)), null, 0);
        assertSame(c[0], settle(now));
        // 다른 region 스레드에서도 거부
        at(FAR, r -> r.chunkAtBlocking(FAR));
        var farEx = assertThrows(ExecutionException.class, () -> at(FAR, r -> in.entityNow(handle, Cow.class)));
        assertInstanceOf(ThreadOwnershipException.class, farEx.getCause());

        // wrapEntity: 내부 → API (소유 스레드에서만)
        var wrapped = at(C00, r -> in.wrapEntity(c[0]));
        assertEquals(c[0].uuid(), wrapped.uniqueId());
        assertInstanceOf(org.domi.furnace.api.entity.LivingEntity.class, wrapped);
        assertThrows(ThreadOwnershipException.class, () -> in.wrapEntity(c[0]));
        assertThrows(IllegalArgumentException.class, () -> in.wrapEntity("not an entity"));

        at(C00, r -> {
            c[0].discard();
            return null;
        });
        assertInstanceOf(IllegalStateException.class, settleFail(in.withEntity(handle, Cow.class, x -> x)));
        assertThrows(IllegalStateException.class, () -> in.entityNow(handle, Cow.class));
    }

    @Test
    void withEntityOnPlayerUsesPlayerOwner() throws Exception {
        Player p = player();
        at(C00, r -> {
            r.adopt(p);
            return null;
        });
        Internals in = host.internals();
        var ph = host.player(p);
        assertSame(p, settle(in.withEntity(ph, Player.class, x -> x)));
        assertThrows(ThreadOwnershipException.class, () -> in.entityNow(ph, Player.class));
        assertSame(ph, in.wrapEntity(p), "플레이어 핸들은 아무 스레드에서");
        p.markDisconnected();
        assertInstanceOf(IllegalStateException.class, settleFail(in.withEntity(ph, Player.class, x -> x)));
    }

    @Test
    void levelServerAndWorldAccess() throws Exception {
        Internals in = host.internals();
        var w = api();
        BlockPos pos = new BlockPos(1, -59, 1);
        assertTrue(w.setBlock(pos, BlockData.of("gold_block")).get(10, TimeUnit.SECONDS));

        // withLevel: 그 칸의 region 레벨
        String name = in.withLevel(w, pos, RegionLevel.class, level -> {
            int state = level.getBlockState(new org.domi.furnace.server.block.Pos(1, -59, 1));
            return core.world().blockRegistry().nameOf(state);
        }).get(10, TimeUnit.SECONDS);
        assertTrue(name.contains("gold_block"), name);
        var unloaded = assertThrows(ExecutionException.class,
                () -> in.withLevel(w, new BlockPos(16 * 500, 0, 0), RegionLevel.class, l -> l).get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, unloaded.getCause());

        // levelNow: 소유 스레드에서만
        assertThrows(ThreadOwnershipException.class, () -> in.levelNow(w, pos, RegionLevel.class));
        assertNotNull(w.scheduler(pos).submit(() -> in.levelNow(w, pos, RegionLevel.class)).get(10, TimeUnit.SECONDS));

        // 월드·서버
        assertSame(core.world(), in.world(w, org.domi.furnace.server.world.World.class));
        assertSame(core, in.server(ServerCore.class));
        assertSame(w, in.wrapWorld(core.world()));
        assertThrows(IllegalArgumentException.class, () -> in.wrapWorld(new Object()));
        assertThrows(ClassCastException.class, () -> in.server(String.class));
        Thread test = Thread.currentThread();
        assertTrue(in.withServer(ServerCore.class, s -> s == core && Thread.currentThread() != test).get(10, TimeUnit.SECONDS));
    }
}
