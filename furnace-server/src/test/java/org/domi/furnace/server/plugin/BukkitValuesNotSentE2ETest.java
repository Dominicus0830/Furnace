// 블록 엔티티 영속 데이터(PublicBukkitValues)가 실제 접속의 청크 패킷·되돌림(ClientResync) block_entity_data 로 나가지 않는다
// (Paper Sanitize-sent-BlockEntity-NBT). 표지판 글(front_text)은 같은 패킷에 실려 나가는 것으로 대조한다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ConnectionHandler;
import org.domi.furnace.server.net.ServerLoginContextAccess;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.BlockEntityDataPacket;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BukkitValuesNotSentE2ETest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    /** 스폰 청크(0,0) 안의 바닥 위 칸. */
    private static final Pos SIGN = new Pos(3, -60, 3);
    private static final byte[] KEY = BlockEntity.BUKKIT_VALUES.getBytes(StandardCharsets.UTF_8);
    private static final byte[] FRONT = "front_text".getBytes(StandardCharsets.UTF_8);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private Player p;
    private final List<Pkt> out = new ArrayList<>();

    record Pkt(int id, byte[] body) {}

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt"))); }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt"), org.domi.furnace.server.world.ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"));
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
        // 접속 전에 영속 데이터를 든 표지판을 둔다 — 스폰 청크 패킷에 실린다
        at(r -> {
            r.level().setBlock(SIGN, B.defaultState("minecraft:oak_sign"), 3);
            var be = (BlockEntity) r.level().getBlockEntity(SIGN);
            NbtCompound pdc = be.bukkitValues(true);
            pdc.putString("demo:secret", "token");
            be.settleBukkitValues(r.level(), true);
            return null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ch != null) ch.close();
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private static ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    private Object field(String name) throws Exception {
        Field f = ConnectionHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(handler);
    }

    /** 접속하고 스폰 청크 전달이 끝날 때까지 나간 패킷을 모두 모은다(지우지 않는다). */
    private void join() throws Exception {
        var login = ServerLoginContextAccess.offline("t").core(core);
        handler = new ConnectionHandler(login, -1, sched, core.world(), core.world().entityIds());
        ch = new ConcurrentEmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!(boolean) field("spawnChunksDelivered")) {
            ch.runPendingTasks();
            drain();
            assertTrue(System.nanoTime() < deadline, "스폰 청크 전달이 끝나지 않음");
            Thread.sleep(20);
        }
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 1)));
        ch.runPendingTasks();
        p = (Player) field("player");
        assertNotNull(p);
        drain();
    }

    private void drain() {
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            int id = Types.readVarInt(bb);
            byte[] body = new byte[bb.readableBytes()];
            bb.readBytes(body);
            bb.release();
            out.add(new Pkt(id, body));
        }
    }

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    /** 청크 패킷(ChunkSnapshot 의 블록 엔티티 갱신 태그)에 표지판 글은 있고 영속 데이터는 없다. */
    @Test
    void chunkPacketDropsBukkitValues() throws Exception {
        join();
        List<Pkt> chunks = out.stream().filter(k -> k.id() == LevelChunkWithLight.ID).toList();
        assertFalse(chunks.isEmpty(), "스폰 청크 패킷이 나갔다");
        assertTrue(chunks.stream().anyMatch(k -> contains(k.body(), FRONT)), "표지판 블록 엔티티가 청크 패킷에 실렸다");
        assertTrue(chunks.stream().noneMatch(k -> contains(k.body(), KEY)), "영속 데이터는 청크 패킷으로 안 간다");
        boolean kept = at(r -> ((BlockEntity) r.level().getBlockEntity(SIGN)).saveCustomOnly().contains(BlockEntity.BUKKIT_VALUES));
        assertTrue(kept, "서버 값은 그대로");
    }

    /** 취소 뒤 되돌림(ClientResync.block)이 보내는 block_entity_data 에도 없다. */
    @Test
    void resyncBlockEntityDataDropsBukkitValues() throws Exception {
        join();
        out.clear();
        at(r -> {
            ClientResync.block(p, r.level(), SIGN);
            return null;
        });
        long deadline = System.nanoTime() + 10_000_000_000L;
        List<Pkt> data;
        while ((data = out.stream().filter(k -> k.id() == BlockEntityDataPacket.ID).toList()).isEmpty()) {
            at(r -> { r.tick(); return null; });
            drain();
            assertTrue(System.nanoTime() < deadline, "되돌림 block_entity_data 가 안 나왔다");
            Thread.sleep(20);
        }
        assertTrue(contains(data.get(0).body(), FRONT), "표지판 글은 실린다");
        assertFalse(contains(data.get(0).body(), KEY), "영속 데이터는 안 간다");
    }
}
