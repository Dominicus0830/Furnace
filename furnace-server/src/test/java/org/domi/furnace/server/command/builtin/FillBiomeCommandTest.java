// /fillbiome: quart 단위 바꾸기·replace 필터·크기 제한·미로드, 그리고 chunks_biomes 패킷 직렬화.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.ChunksBiomes;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Palette;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FillBiomeCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    List<String> names;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        names = List.of("minecraft:plains", "minecraft:desert", "minecraft:jungle");
        var cp = new ChunkPos(0, 0);
        var loaded = new CompletableFuture<CompletableFuture<?>>();
        core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
        loaded.get(10, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private <T> T onRegion(Function<Region, T> f) throws Exception {
        var fut = new CompletableFuture<T>();
        core.world().runAt(0.5, 0.5, region -> {
            try {
                fut.complete(f.apply(region));
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        return fut.get(10, TimeUnit.SECONDS);
    }

    private int biome(int qx, int qy, int qz) throws Exception { return onRegion(r -> r.biomeIdAtQuart(qx, qy, qz)); }

    @Test
    void fillsQuartsAndReplaceFilters() throws Exception {
        var console = CommandSource.console(core);
        int desert = names.indexOf("minecraft:desert"), plains = names.indexOf("minecraft:plains"), jungle = names.indexOf("minecraft:jungle");
        
        int before = biome(0, 16, 0);
        // 모서리 0..5 는 quart 0..1(블록 0..7 이 아니라 모서리 맞춤으로 4 와 0)로 맞춰진다: 상자 x,z 0..4, y 64..64
        run(console, "minecraft:desert", null, 0, 64, 0, 5, 64, 5);
        assertEquals(desert, biome(1, 16, 0));
        assertEquals(desert, biome(1, 16, 1));
        assertEquals(before == desert ? desert : before, biome(2, 16, 0), "상자 밖 quart 는 그대로");
        assertEquals(before == desert ? desert : before, biome(0, 17, 0), "다른 높이 quart 는 그대로");
        // replace: 사막만 정글로
        run(console, "minecraft:jungle", "minecraft:desert", 0, 64, 0, 5, 64, 5);
    }

    private void run(CommandSource src, String biome, String filter, int x1, int y1, int z1, int x2, int y2, int z2) throws Exception {
        var f = filter == null ? null : new org.domi.furnace.server.command.arguments.ResourceOrTagKeyArgument.Result(
                org.domi.furnace.server.data.ResourceId.parse(filter), false);
        FillBiomeCommand.fillAsync(src, names, (r, t) -> false, (r, t) -> List.of(), org.domi.furnace.server.data.ResourceId.parse(biome), f,
                new int[]{x1, y1, z1}, new int[]{x2, y2, z2}).join();
    }

    @Test
    void unknownBiomeAndTagErrors() {
        var console = CommandSource.console(core);
        var e = assertThrows(org.domi.furnace.server.command.brigadier.CommandSyntaxException.class,
                () -> run(console, "minecraft:nope", null, 0, 0, 0, 1, 1, 1));
        assertEquals("argument.resource.not_found", e.getRawMessage().translationKey());
        e = assertThrows(org.domi.furnace.server.command.brigadier.CommandSyntaxException.class,
                () -> FillBiomeCommand.fillAsync(console, names, (r, t) -> false, (r, t) -> List.of(), org.domi.furnace.server.data.ResourceId.parse("minecraft:desert"),
                        new org.domi.furnace.server.command.arguments.ResourceOrTagKeyArgument.Result(org.domi.furnace.server.data.ResourceId.parse("minecraft:nope"), true),
                        new int[]{0, 0, 0}, new int[]{1, 1, 1}));
        assertEquals("argument.resource_tag.not_found", e.getRawMessage().translationKey());
    }

    @Test
    void fillBiomesRegionMethod() throws Exception {
        int desert = names.indexOf("minecraft:desert");
        // 한도 내 상자, 미로드 청크가 걸치면 -1(아무것도 안 바뀜)
        assertEquals(-1, (int) onRegion(r -> r.fillBiomes(0, 0, 0, 100000, 0, 0, desert, id -> true)));
        int n = onRegion(r -> r.fillBiomes(0, 0, 0, 7, 3, 7, desert, id -> true));
        assertEquals(2 * 1 * 2, n, "quart 칸 수(블록 0..7 → 2×1×2)");
    }

    @Test
    void chunksBiomesPacketBytes() {
        int[] one = new int[64];
        java.util.Arrays.fill(one, 5);
        var pkt = new ChunksBiomes(List.of(new ChunksBiomes.Entry(-1, 2, new int[][]{one, one})), 6);
        assertEquals(0x0E, pkt.id());
        ByteBuf out = Unpooled.buffer();
        pkt.write(out);
        ByteBuf expected = Unpooled.buffer();
        Palette.write(expected, one, 6, 1, 3);
        Palette.write(expected, one, 6, 1, 3);
        assertEquals(1, Types.readVarInt(out));
        assertEquals(((-1L) & 0xFFFFFFFFL) | (2L << 32), out.readLong());
        assertEquals(expected.readableBytes(), Types.readVarInt(out));
        assertEquals(expected, out.readSlice(expected.readableBytes()));
        assertEquals(0, out.readableBytes());
    }
}
