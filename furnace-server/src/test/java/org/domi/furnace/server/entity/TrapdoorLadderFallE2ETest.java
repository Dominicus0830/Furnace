// 실제 소켓 접속(오프라인 모드): 열린 다락문(바로 아래 같은 방향 사다리) 칸에 매달렸다가 옆으로 비켜 떨어져 죽으면
// 사망 메시지가 death.fell.accident.ladder, 떨어지기 전에 그 칸을 부수면 other_climbable 이다(바닐라 FallLocation).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.net.NetworkServer;
import org.domi.furnace.server.net.ServerLoginContext;
import org.domi.furnace.server.net.TestClient;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrapdoorLadderFallE2ETest {

    @BeforeAll
    static void installItems() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @TempDir Path tmp;

    private static String text(ByteBuf b) {
        byte[] rest = new byte[b.readableBytes()];
        b.readBytes(rest);
        return new String(rest, StandardCharsets.ISO_8859_1);
    }

    /** 위치 동기화(0x40)를 받아 수락하고 위치를 돌려준다. */
    private static double[] acceptTeleport(TestClient c) throws Exception { return acceptTeleport(c, Double.NaN); }

    /** y 가 주어지면 그 높이로 보낸 동기화만(접속 직후 동기화가 여러 번 올 수 있다). */
    private static double[] acceptTeleport(TestClient c, double y0) throws Exception {
        ByteBuf pos = c.await(0x40, b -> { b.readDouble(); return Double.isNaN(y0) || b.readDouble() == y0; });
        double x = pos.readDouble(), y = pos.readDouble(), z = pos.readDouble();
        pos.readFloat();
        pos.readFloat();
        pos.readByte();
        int id = Types.readVarInt(pos);
        DBG.append("[tp id=").append(id).append(" y=").append(y).append("]");
        c.write(0x00, b -> Types.writeVarInt(b, id));
        return new double[] {x, y, z};
    }

    /** 사망 화면(0x3C)까지 읽는다 — keep-alive 는 답하고, 위치 보정(0x40)이 오면 실패로 본다. */
    private static ByteBuf awaitDeath(TestClient c, Player p) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            ByteBuf f = c.read();
            int id = Types.readVarInt(f);
            if (id == 0x3C) return f.slice();
            if (id == 0x26) { long v = f.readLong(); c.write(0x18, b -> b.writeLong(v)); }
            if (id == 0x40) {
                double x = f.readDouble(), y = f.readDouble(), z = f.readDouble();
                f.readFloat(); f.readFloat(); f.readByte();
                fail("서버가 위치를 되돌렸다: " + x + "," + y + "," + z + " id=" + Types.readVarInt(f) + " 체력=" + p.vitals().health() + " " + DBG);
            }
        }
        fail("사망하지 않았다: 체력=" + p.vitals().health() + " y=" + p.y());
        return null;
    }

    private static void move(TestClient c, double x, double y, double z, boolean ground) throws Exception {
        c.write(0x1A, b -> { b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeBoolean(ground); });
    }

    /** 실패 메시지에 붙일 실행 기록(명령 결과·위치 동기화). */
    static final StringBuilder DBG = new StringBuilder();
    private static void run(ServerCore core, String cmd) {
        DBG.append(cmd).append(" -> ").append(core.commands().performPrefixedCommandAsync(CommandSource.console(core), cmd).join()).append("; ");
    }

    @Test
    void trapdoorLadderFallDeathMessage() throws Exception {
        assertEquals("death.fell.accident.ladder", fallDeath(false));
    }

    @Test
    void trapdoorBrokenBeforeLandingIsOtherClimbable() throws Exception {
        assertEquals("death.fell.accident.other_climbable", fallDeath(true));
    }

    /** 다락문 칸에 매달린 뒤 동쪽 칸으로 비켜 25칸 떨어진다. breakFirst 면 떨어지기 전에 다락문을 부순다. 사망 화면의 번역 키. */
    private String fallDeath(boolean breakFirst) throws Exception {
        DBG.setLength(0);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        NetworkServer server = null;
        try {
            ServerCore core = TestServers.core(tmp, sched);
            // 실제 서버(FurnaceMain)처럼 블록 레지스트리를 건다 — 플레이어 주변 블록 판정(PlayerProbe)이 이것을 쓴다
            core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), null);
            core.world().startTicking();
            ServerLoginContext ctx = ServerLoginContext.offline("t").core(core);
            server = new NetworkServer(0, -1, ctx, sched, core.world(), new EntityIdAllocator());
            int port = server.start();
            try (TestClient c = TestClient.join(port, "Climber")) {
                double[] spawn = acceptTeleport(c);
                for (int i = 0; i < 500 && core.playerByName("Climber") == null; i++) Thread.sleep(20);
                Player p = core.playerByName("Climber");
                assertNotNull(p);
                int x = (int) Math.floor(spawn[0]), z = (int) Math.floor(spawn[2]);
                int top = 100, floor = top - 25;
                // 벽(남쪽) · 사다리(북쪽을 봄) · 그 위 열린 다락문(같은 방향) · 바닥
                run(core, "fill " + x + " " + floor + " " + (z + 1) + " " + x + " " + top + " " + (z + 1) + " minecraft:stone");
                run(core, "setblock " + x + " " + (top - 1) + " " + z + " minecraft:ladder[facing=north]");
                run(core, "setblock " + x + " " + top + " " + z + " minecraft:oak_trapdoor[facing=north,open=true]");
                run(core, "fill " + (x - 2) + " " + floor + " " + (z - 2) + " " + (x + 2) + " " + floor + " " + (z - 1) + " minecraft:stone");
                run(core, "setblock " + (x + 1) + " " + floor + " " + z + " minecraft:stone");
                Thread.sleep(3500);   // 스폰 무적(60틱)이 끝나게

                run(core, "tp Climber " + (x + 0.5) + " " + top + " " + (z + 0.5));
                acceptTeleport(c, top);
                for (int i = 0; i < 100 && !p.onClimbable; i++) Thread.sleep(20);
                assertTrue(p.onClimbable, "열린 다락문 + 같은 방향 사다리 칸에 매달렸다: " + p.x() + "," + p.y() + "," + p.z()
                        + " spawn=" + java.util.Arrays.toString(spawn) + " " + DBG);

                if (breakFirst) run(core, "setblock " + x + " " + top + " " + z + " minecraft:air");
                double y = top;
                move(c, x + 1.5, y, z + 0.5, false);   // 동쪽 칸으로 비킨다(다락문 판은 남쪽 면)
                Thread.sleep(100);
                while (y > floor + 1) {
                    y = Math.max(floor + 1, y - 1);
                    move(c, x + 1.5, y, z + 0.5, false);
                    Thread.sleep(10);
                }
                move(c, x + 1.5, floor + 1, z + 0.5, true);
                ByteBuf kill = awaitDeath(c, p);
                assertEquals(p.entityId(), Types.readVarInt(kill));
                String msg = text(kill);
                assertTrue(p.vitals().isDeadOrDying(), msg);
                int i = msg.indexOf("death.fell.accident.");
                assertTrue(i >= 0, msg);
                int end = i + "death.fell.accident.".length();
                while (end < msg.length() && (Character.isLetter(msg.charAt(end)) || msg.charAt(end) == '_')) end++;
                return msg.substring(i, end);
            }
        } finally {
            if (server != null) server.stop();
            sched.close();
        }
    }
}
