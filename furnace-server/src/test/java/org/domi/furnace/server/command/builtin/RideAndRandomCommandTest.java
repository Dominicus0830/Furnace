// /ride(실제 region 의 소 둘, 오류 키)와 /random(고정 시퀀스 값·reset·오류 키).
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.noise.RandomSupport;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RideAndRandomCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private static String key(CommandSyntaxException e) { return e.getRawMessage().translationKey(); }

    @Test
    void rideMountAndDismountOnRegion() throws Exception {
        Cow[] cows = new Cow[2];
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            for (int i = 0; i < 2; i++) {
                cows[i] = new Cow(lvl, lvl.nextEntityId());
                cows[i].moveTo(0.5 + i, -60, 0.5, 0, 0);
                lvl.addFreshEntity(cows[i]);
            }
            cows[0].addTag("rider");
            cows[1].addTag("vehicle");
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        var d = core.commands().getDispatcher();
        var console = CommandSource.console(core);
        core.commands().performPrefixedCommandAsync(console, "ride @e[tag=rider,limit=1] mount @e[tag=vehicle,limit=1]").join();
        TeleportCommandTest.awaitTrue(() -> cows[0].vehicle() == cows[1]);
        assertTrue(cows[1].hasPassenger(cows[0]));
        // 이미 탔다
        assertEquals("commands.ride.already_riding",
                key(assertThrows(CommandSyntaxException.class, () -> RideCommand.mount(console, cows[0], cows[1]))));
        // 자기 자신 / 자기 위에 탄 것에 다시 타기는 순환
        assertEquals("commands.ride.mount.failure.loop",
                key(assertThrows(CommandSyntaxException.class, () -> RideCommand.mount(console, cows[1], cows[1]))));
        assertEquals("commands.ride.mount.failure.loop",
                key(assertThrows(CommandSyntaxException.class, () -> RideCommand.mount(console, cows[1], cows[0]))));
        // 플레이어는 탈것이 될 수 없다
        var p = new Player(11, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        assertEquals("commands.ride.mount.failure.cant_ride_players",
                key(assertThrows(CommandSyntaxException.class, () -> RideCommand.mount(console, cows[1], p))));
        core.commands().performPrefixedCommandAsync(console, "ride @e[tag=rider,limit=1] dismount").join();
        TeleportCommandTest.awaitTrue(() -> cows[0].vehicle() == null);
        assertFalse(cows[1].isVehicle());
        assertEquals("commands.ride.not_riding",
                key(assertThrows(CommandSyntaxException.class, () -> RideCommand.dismount(console, cows[0]))));
    }

    @Test
    void randomValueFollowsNamedSequence() throws Exception {
        var d = core.commands().getDispatcher();
        var src = CommandSource.console(core);
        // 시드 0, 월드 시드·시퀀스 id 모두 빼면 시퀀스는 그냥 Xoroshiro(upgradeSeedTo128bit(0)) 하나다
        d.execute("random reset * 0 false false", src);
        var expected = new XoroshiroRandomSource(RandomSupport.upgradeSeedTo128bitUnmixed(0).mixed());
        int a = d.execute("random value 1..100 minecraft:test", src);
        assertEquals(expected.nextInt(100) + 1, a);
        int b = d.execute("random value 1..100 minecraft:test", src);
        assertEquals(expected.nextInt(100) + 1, b);
        // 음수 범위: nextInt(max-min+1)+min
        int c = d.execute("random value -10..-5 minecraft:test", src);
        assertEquals(expected.nextInt(6) - 10, c);
        // 한 시퀀스만 reset 하면 처음부터
        assertEquals(1, d.execute("random reset minecraft:test", src.withPermission(4)));
        assertEquals(a, d.execute("random value 1..100 minecraft:test", src), "reset 하면 같은 규칙으로 다시 시작한다(기본값은 시드 0 로 바꿨으므로 첫 값이 같다)");
        // 시드를 주어 reset: 다른 시드는 다른 흐름
        d.execute("random reset minecraft:test 1 false false", src);
        var other = new XoroshiroRandomSource(RandomSupport.upgradeSeedTo128bitUnmixed(1).mixed());
        assertEquals(other.nextInt(1000) + 1, d.execute("random value 1..1000 minecraft:test", src));
        // reset * 은 지운 시퀀스 개수를 돌려준다
        assertTrue(d.execute("random reset *", src) >= 1);
        assertEquals(0, d.execute("random reset *", src));
    }

    @Test
    void randomRangeErrors() {
        var d = core.commands().getDispatcher();
        var src = CommandSource.console(core);
        assertEquals("commands.random.error.range_too_small", key(assertThrows(CommandSyntaxException.class, () -> d.execute("random value 5..5", src))));
        assertEquals("commands.random.error.range_too_small", key(assertThrows(CommandSyntaxException.class, () -> d.execute("random roll 7", src))));
        assertEquals("commands.random.error.range_too_large", key(assertThrows(CommandSyntaxException.class, () -> d.execute("random value 0..2147483647", src))));
        assertEquals("commands.random.error.range_too_large", key(assertThrows(CommandSyntaxException.class, () -> d.execute("random value ..5", src))));
        // 시퀀스 인자와 reset 은 권한 2 이상
        assertThrows(CommandSyntaxException.class, () -> d.execute("random value 1..5 minecraft:x", src.withPermission(0)));
        assertThrows(CommandSyntaxException.class, () -> d.execute("random reset *", src.withPermission(0)));
        // 권한 0 도 시퀀스 없는 value 는 된다
        int v = assertDoesNotThrow(() -> d.execute("random value 3..4", src.withPermission(0)));
        assertTrue(v == 3 || v == 4);
    }
}
