// 범용 코덱(E4a) 왕복: 서버가 실제로 보낸 프레임 가운데 GenericPacket 으로 읽는 종류를 풀고 다시 써서 바이트가 똑같은지 본다.
// 서버 직렬화기는 실제 클라이언트·봇으로 시험된 것이라, 스키마 필드 순서·와이어가 어긋나면 여기서 드러난다(본문이 남거나 바이트가 다름).
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class GenericPacketRoundTripTest {

    private record Frame(int id, byte[] body) {}

    @Test
    void serverSentGenericPacketsRoundTripByteForByte() {
        var frames = new ConcurrentLinkedQueue<Frame>();
        TestPlayer.frameTap = (id, body) -> frames.add(new Frame(id, body));
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).timeout(10_000).start()) {
            server.execute("op Steve");
            TestPlayer steve = server.join("Steve");
            for (String c : List.of("weather rain", "weather clear", "difficulty hard", "effect give Steve minecraft:speed 30 1",
                    "effect clear Steve", "worldborder center 10 10", "worldborder set 500", "worldborder set 400 5",
                    "worldborder warning distance 7", "worldborder warning time 12", "tick rate 25", "tick step 2", "tick rate 20",
                    "gamemode creative Steve", "gamemode survival Steve", "spawnpoint Steve 1 70 1", "give Steve minecraft:diamond 3",
                    "playsound minecraft:entity.pig.ambient master Steve", "summon minecraft:pig ~ ~ ~", "ride Steve mount @e[type=pig,limit=1]",
                    "ride Steve dismount", "scoreboard objectives add obj dummy", "scoreboard objectives setdisplay sidebar obj",
                    "scoreboard players set Steve obj 3", "scoreboard players reset Steve obj", "damage Steve 1", "xp add Steve 5 levels",
                    "kill @e[type=pig]", "time set 1000", "setworldspawn 0 70 0")) {
                server.execute(c);
                server.tick(2);
            }
            steve.flushReceived();
            var codec = server.internals().plugins().packetsImpl().codec();
            Map<String, Integer> seen = new TreeMap<>();
            List<String> bad = new ArrayList<>();
            for (Frame f : frames) {
                PacketType<?> t = codec.clientboundType(f.id());
                if (t == null || t.typed() || !codec.readable(t)) continue;
                try {
                    var p = (GenericPacket) codec.decode(t, f.id(), Unpooled.wrappedBuffer(f.body()));
                    assertTrue(p.readable());
                    ByteBuf out = Unpooled.buffer();
                    int id = codec.encode(p, out);
                    byte[] again = ByteBufUtil.getBytes(out);
                    out.release();
                    if (id != f.id() || !java.util.Arrays.equals(again, f.body())) bad.add(t + " 다시 쓴 바이트가 다르다: " + p);
                    seen.merge(t.name(), 1, Integer::sum);
                } catch (RuntimeException e) {
                    bad.add(t + " 읽기 실패: " + e);
                }
            }
            System.out.println("[E4a 왕복] 확인한 종류 " + seen.size() + ": " + seen);
            assertEquals(List.of(), bad);
            assertTrue(seen.size() >= 15, "서버가 보낸 범용 종류가 너무 적다: " + seen);
        } finally {
            TestPlayer.frameTap = null;
        }
    }
}
