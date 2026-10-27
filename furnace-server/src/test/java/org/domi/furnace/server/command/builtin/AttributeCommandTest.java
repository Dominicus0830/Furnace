// /attribute: 실제 region 의 소에 get/base/modifier 를 적용하고(명령 경로), 본문의 오류 키를 직접 확인한다.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttributeCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Cow cow;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            cow = new Cow(lvl, lvl.nextEntityId());
            cow.moveTo(0.5, -60, 0.5, 0, 0);
            lvl.addFreshEntity(cow);
            cow.addTag("target");
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private static String key(CommandSyntaxException e) { return e.getRawMessage().translationKey(); }

    @Test
    void baseSetAndModifiersThroughCommand() throws Exception {
        var d = core.commands().getDispatcher();
        var console = CommandSource.console(core);
        var hp = cow.attributes().get(Attribute.MAX_HEALTH);
        assertEquals(10.0, hp.baseValue());
        d.execute("attribute @e[tag=target,limit=1] minecraft:generic.max_health base set 30", console);
        TeleportCommandTest.awaitTrue(() -> hp.baseValue() == 30.0);
        d.execute("attribute @e[tag=target,limit=1] generic.max_health modifier add test:boost 5 add_value", console);
        TeleportCommandTest.awaitTrue(() -> hp.hasModifier("test:boost"));
        assertEquals(35.0, hp.value());
        d.execute("attribute @e[tag=target,limit=1] generic.max_health modifier add test:double 1 add_multiplied_total", console);
        TeleportCommandTest.awaitTrue(() -> hp.hasModifier("test:double"));
        assertEquals(70.0, hp.value(), 1e-9);
        d.execute("attribute @e[tag=target,limit=1] generic.max_health modifier remove test:double", console);
        TeleportCommandTest.awaitTrue(() -> !hp.hasModifier("test:double"));
        assertEquals(35.0, hp.value());
        assertEquals(AttributeModifier.ADD_VALUE, hp.getModifier("test:boost").operation());
    }

    @Test
    void getReturnsScaledValue() throws Exception {
        var console = CommandSource.console(core);
        assertEquals(10, AttributeCommand.opGet(console, cow, Attribute.MAX_HEALTH, 1.0));
        assertEquals(25, AttributeCommand.opGet(console, cow, Attribute.MAX_HEALTH, 2.5));
        assertEquals(10, AttributeCommand.opGetBase(console, cow, Attribute.MAX_HEALTH, 1.0));
        AttributeCommand.opAddModifier(console, cow, Attribute.MAX_HEALTH, "t:m", 4.0, AttributeModifier.ADD_VALUE);
        assertEquals(14, AttributeCommand.opGet(console, cow, Attribute.MAX_HEALTH, 1.0));
        assertEquals(10, AttributeCommand.opGetBase(console, cow, Attribute.MAX_HEALTH, 1.0), "기본값은 수정자에 안 변한다");
        assertEquals(8, AttributeCommand.opGetModifier(console, cow, Attribute.MAX_HEALTH, "t:m", 2.0));
    }

    @Test
    void errorKeys() {
        var console = CommandSource.console(core);
        // 소는 공격력 속성이 없다
        assertEquals("commands.attribute.failed.no_attribute",
                key(assertThrows(CommandSyntaxException.class, () -> AttributeCommand.opGet(console, cow, Attribute.ATTACK_DAMAGE, 1.0))));
        assertEquals("commands.attribute.failed.no_attribute",
                key(assertThrows(CommandSyntaxException.class, () -> AttributeCommand.opSetBase(console, cow, Attribute.ATTACK_DAMAGE, 1.0))));
        assertEquals("commands.attribute.failed.no_modifier",
                key(assertThrows(CommandSyntaxException.class, () -> AttributeCommand.opGetModifier(console, cow, Attribute.MAX_HEALTH, "t:none", 1.0))));
        assertEquals("commands.attribute.failed.no_modifier",
                key(assertThrows(CommandSyntaxException.class, () -> AttributeCommand.opRemoveModifier(console, cow, Attribute.MAX_HEALTH, "t:none"))));
        assertDoesNotThrow(() -> AttributeCommand.opAddModifier(console, cow, Attribute.MAX_HEALTH, "t:dup", 1.0, AttributeModifier.ADD_VALUE));
        assertEquals("commands.attribute.failed.modifier_already_present",
                key(assertThrows(CommandSyntaxException.class, () -> AttributeCommand.opAddModifier(console, cow, Attribute.MAX_HEALTH, "t:dup", 2.0, AttributeModifier.ADD_VALUE))));
        assertEquals(11.0, cow.attributes().get(Attribute.MAX_HEALTH).value(), "중복 추가는 값을 바꾸지 않는다");
        // 레지스트리에 없는 속성
        var d = core.commands().getDispatcher();
        assertEquals("argument.resource.not_found",
                key(assertThrows(CommandSyntaxException.class, () -> d.execute("attribute @e[tag=target,limit=1] minecraft:nope get", console))));
    }

    @Test
    void playerHasVanillaDefaults() throws Exception {
        var console = CommandSource.console(core);
        var p = new Player(11, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        assertEquals(20, AttributeCommand.opGet(console, p, Attribute.MAX_HEALTH, 1.0));
        assertEquals(4, AttributeCommand.opGet(console, p, Attribute.ATTACK_SPEED, 1.0));
        assertEquals(4, AttributeCommand.opGet(console, p, Attribute.BLOCK_INTERACTION_RANGE, 1.0));
        assertEquals(1, AttributeCommand.opGet(console, p, Attribute.ATTACK_DAMAGE, 1.0));
        assertEquals("commands.attribute.failed.no_attribute",
                key(assertThrows(CommandSyntaxException.class, () -> AttributeCommand.opGet(console, p, Attribute.FOLLOW_RANGE, 1.0))));
    }
}
