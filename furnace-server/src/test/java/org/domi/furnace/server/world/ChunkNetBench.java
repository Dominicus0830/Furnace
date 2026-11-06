// 청크 송출·저장소 경로 마이크로벤치(기본 꺼짐: -Dfurnace.bench=true). 실제 바닐라 청크 본문으로 잰다.
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.Deflater;
import org.domi.furnace.server.net.io.CompressionDecoder;
import org.domi.furnace.server.net.io.CompressionEncoder;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkNetBench {

    static volatile long sink;
    static ChunkColumn column;
    static byte[] body;

    @BeforeAll
    static void load() throws Exception {
        if (!Boolean.getBoolean("furnace.bench")) return;
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var biomes = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
        var codec = new AnvilChunkCodec(reg, ItemRegistry.load("items/1.21.1.txt"), biomes,
                biomes.indexOf("minecraft:plains"), ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        try (InputStream in = ChunkNetBench.class.getResourceAsStream("/storage/vanilla-chunk--25.-4.nbt.gz")) {
            column = codec.fromNbt(NbtIo.readGzip(in));
        }
        ByteBuf b = Unpooled.buffer();
        ChunkSerializer.serialize(b, ChunkSnapshot.of(-25, -4, column), reg.directBits(), 7);
        body = new byte[b.readableBytes() + 1];
        body[0] = 0x27;   // 패킷 id(VarInt 1바이트)
        b.getBytes(0, body, 1, b.readableBytes());
    }

    private static double timeUs(Runnable r, int iters) {
        for (int i = 0; i < iters; i++) r.run();   // 워밍업
        long t = System.nanoTime();
        for (int i = 0; i < iters; i++) r.run();
        return (System.nanoTime() - t) / 1000.0 / iters;
    }

    @Test
    void compressionLevels() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        byte[] out = new byte[body.length * 2];
        for (int level : new int[]{1, 2, 4, 6}) {
            Deflater d = new Deflater(level);
            int[] size = new int[1];
            double us = timeUs(() -> {
                d.reset();
                d.setInput(body);
                d.finish();
                size[0] = d.deflate(out);
            }, 2000);
            d.end();
            System.out.printf("[BENCH] deflate level %d: %.1f us/청크, %d -> %d 바이트%n", level, us, body.length, size[0]);
        }
    }

    @Test
    void encoderDecoderPerPacket() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        EmbeddedChannel enc = new ConcurrentEmbeddedChannel(new CompressionEncoder(256));
        EmbeddedChannel dec = new ConcurrentEmbeddedChannel(new CompressionDecoder(256));
        ByteBuf[] frame = new ByteBuf[1];
        double encUs = timeUs(() -> {
            enc.writeOutbound(Unpooled.wrappedBuffer(body));
            ByteBuf f = enc.readOutbound();
            if (frame[0] == null) frame[0] = f.copy();
            sink += f.readableBytes();
            f.release();
        }, 3000);
        double decUs = timeUs(() -> {
            dec.writeInbound(frame[0].retainedDuplicate());
            ByteBuf o = dec.readInbound();
            sink += o.readableBytes();
            o.release();
        }, 3000);
        // 작은 패킷(임계값 이상, 300바이트): 고정 비용(임시 배열 등) 비중이 큰 경우
        byte[] small = java.util.Arrays.copyOf(body, 300);
        double smallUs = timeUs(() -> {
            enc.writeOutbound(Unpooled.wrappedBuffer(small));
            ByteBuf f = enc.readOutbound();
            sink += f.readableBytes();
            f.release();
        }, 50000);
        System.out.printf("[BENCH] encoder 청크 %.1f us, decoder 청크 %.1f us, encoder 300B %.2f us%n", encUs, decUs, smallUs);
        enc.finishAndReleaseAll();
        dec.finishAndReleaseAll();
    }

    // 같은 청크 본문을 10명에게 보낼 때 채널당 압축(예전) vs 공유 프레임(새)
    @Test
    void sharedFrameTenPlayers() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        byte[] cached = java.util.Arrays.copyOfRange(body, 1, body.length);
        EmbeddedChannel[] chs = new EmbeddedChannel[10];
        for (int i = 0; i < chs.length; i++) {
            chs[i] = new ConcurrentEmbeddedChannel();
            chs[i].pipeline().addLast("compress", new CompressionEncoder(256));
        }
        double perChannel = timeUs(() -> {
            for (EmbeddedChannel ch : chs) {
                ch.writeOutbound(org.domi.furnace.server.net.io.SharedChunkFrames.encode(ch,
                        new org.domi.furnace.server.net.protocol.play.LevelChunkWithLight(Unpooled.wrappedBuffer(cached))));
                ByteBuf f = ch.readOutbound();
                sink += f.readableBytes();
                f.release();
            }
        }, 300);
        // 매 반복 새 청크 버전(새 배열)이라 압축 1회 + 공유 9회
        double shared = timeUs(() -> {
            byte[] version = cached.clone();
            for (EmbeddedChannel ch : chs) {
                ch.writeOutbound(org.domi.furnace.server.net.io.SharedChunkFrames.encode(ch,
                        org.domi.furnace.server.net.protocol.play.LevelChunkWithLight.ofShared(version)));
                ByteBuf f = ch.readOutbound();
                sink += f.readableBytes();
                f.release();
            }
        }, 300);
        System.out.printf("[BENCH] 청크 1개 x 10명: 채널별 압축 %.1f us, 공유 프레임 %.1f us%n", perChannel, shared);
    }

    @Test
    void bitStorageAndPalette() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        Random rnd = new Random(1);
        for (int bits : new int[]{4, 5, 15}) {
            BitStorage bs = new BitStorage(bits, 4096);
            int max = (1 << bits) - 1;
            double setUs = timeUs(() -> { for (int i = 0; i < 4096; i++) bs.set(i, i & max); }, 20000);
            double getUs = timeUs(() -> { long s = 0; for (int i = 0; i < 4096; i++) s += bs.get(i); sink += s; }, 20000);
            System.out.printf("[BENCH] BitStorage bits=%d: set %.2f us/4096, get %.2f us/4096%n", bits, setUs, getUs);
        }
        for (int distinct : new int[]{8, 60, 200}) {
            int[] src = new int[4096];
            int[] values = new int[distinct];
            for (int i = 0; i < distinct; i++) values[i] = 1000 + rnd.nextInt(20000);
            for (int i = 0; i < 4096; i++) src[i] = values[rnd.nextInt(distinct)];
            double loadUs = timeUs(() -> {
                PaletteStorage ps = new PaletteStorage(4096, 4, 8, 15, 0);
                ps.load(src, 0);
                sink += ps.get(17);
            }, 5000);
            double setUs = timeUs(() -> {
                PaletteStorage ps = new PaletteStorage(4096, 4, 8, 15, 0);
                for (int i = 0; i < 4096; i++) ps.set(i, src[i]);
                sink += ps.get(17);
            }, 5000);
            System.out.printf("[BENCH] PaletteStorage distinct=%d: load %.1f us, set x4096 %.1f us%n", distinct, loadUs, setUs);
        }
    }

    @Test
    void motionBlockingScan() {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        double us = timeUs(() -> sink += column.motionBlockingNoLeavesHeightmap()[3], 2000);
        System.out.printf("[BENCH] motionBlockingNoLeavesHeightmap %.1f us/청크%n", us);
    }
}
