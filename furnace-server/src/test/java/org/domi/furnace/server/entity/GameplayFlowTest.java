// 실제 소켓 접속: 서바이벌로 들어와 /gamemode 로 바뀌고, /xp 가 SetExperience 로, /kill 이 사망 화면·사망 메시지로,
// client_command(리스폰)가 Respawn·체력 회복·경험치 초기화로 이어진다(바닐라 PlayerList.respawn 순서).
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
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameplayFlowTest {

    /** 블록 동작 표(가마솥 상호작용)가 아이템 레지스트리를 쓴다 — 단독 실행에서도 설치돼 있어야 한다. */
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

    @Test
    void gameModeExperienceDeathAndRespawn() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        NetworkServer server = null;
        try {
            ServerCore core = TestServers.core(tmp, sched);
            core.world().startTicking();
            ServerLoginContext ctx = ServerLoginContext.offline("t").core(core);
            server = new NetworkServer(0, -1, ctx, sched, core.world(), new EntityIdAllocator());
            int port = server.start();
            try (TestClient c = TestClient.join(port, "Tester")) {
                ByteBuf abilities = c.await(0x38);
                assertEquals(0, abilities.readByte(), "level.dat 기본 서바이벌: 능력 플래그 없음");

                for (int i = 0; i < 500 && core.playerByName("Tester") == null; i++) Thread.sleep(20);   // 접속 처리 완료 대기(부하 시 최대 10초)
                CommandSource console = CommandSource.console(core);
                core.commands().performPrefixedCommandAsync(console, "gamemode creative Tester").join();
                ByteBuf ev = c.await(0x22, b -> b.readByte() == 3);
                ev.readByte();
                assertEquals(1.0f, ev.readFloat(), "CHANGE_GAME_MODE → creative(1)");
                assertTrue(text(c.await(0x6C)).contains("gameMode.changed"), "대상에게 알림");
                Player p = core.playerByName("Tester");
                assertEquals(GameMode.CREATIVE, p.gameMode());
                assertEquals(GameMode.SURVIVAL, p.vitals().previousGameMode());

                core.commands().performPrefixedCommandAsync(console, "xp add Tester 5 levels").join();
                ByteBuf xp = c.await(0x5C, b -> { b.readFloat(); return Types.readVarInt(b) == 5; });
                assertNotNull(xp, "다음 틱에 SetExperience(레벨 5)");

                core.commands().performPrefixedCommandAsync(console, "kill Tester").join();
                ByteBuf kill = c.await(0x3C);
                assertEquals(p.entityId(), Types.readVarInt(kill));
                assertTrue(text(kill).contains("death.attack.genericKill"), "사망 화면 메시지");
                assertTrue(p.vitals().isDeadOrDying());

                c.write(0x09, b -> Types.writeVarInt(b, 0));   // client_command: 리스폰
                ByteBuf respawn = c.await(0x47);
                Types.readVarInt(respawn);                              // 차원 종류
                assertEquals("minecraft:overworld", Types.readString(respawn, 32767));
                respawn.readLong();
                assertEquals(1, respawn.readByte(), "게임 모드 유지");
                ByteBuf xpAfter = c.await(0x5C);
                xpAfter.readFloat();
                assertEquals(0, Types.readVarInt(xpAfter), "keepInventory 꺼짐 → 경험치 잃음");
                ByteBuf health = c.await(0x5D);
                assertEquals(20.0f, health.readFloat(), "리스폰 후 체력 회복");
                assertFalse(p.vitals().isDeadOrDying());
            }
        } finally {
            if (server != null) server.stop();
            sched.close();
        }
    }
}
