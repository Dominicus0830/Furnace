// tellraw·title·playsound·stopsound·bossbar·transfer — 인자 파싱, 패킷 바이트, 메시지 키 분기, 보스바 저장 왕복.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.bossbar.CustomBossEvent;
import org.domi.furnace.server.bossbar.CustomBossEvents;
import org.domi.furnace.server.command.arguments.ComponentArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.BossEvent;
import org.domi.furnace.server.net.protocol.play.StopSound;
import org.domi.furnace.server.net.protocol.play.Transfer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sound.SoundSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextSoundBossBarCommandsTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private final List<String> out = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = VanillaCommandTreeTest.core(tmp, sched);
        core.addConsoleListener(t -> out.add(t.getString()));
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private List<String> run(String command) {
        out.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).join();
        return List.copyOf(out);
    }

    private static byte[] bytes(Packet p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        byte[] a = new byte[b.readableBytes()];
        b.readBytes(a);
        return a;
    }

    // ------------------------------------------------------------------ 인자 파싱

    @Test
    void componentArgumentReadsOneLenientJsonValue() throws Exception {
        StringReader r = new StringReader("{text:'hi',bold:true} rest");
        Text t = ComponentArgument.textComponent().parse(r);
        assertEquals("hi", t.literal());
        assertTrue(t.hasDecoration(org.domi.furnace.api.text.TextDecoration.BOLD));
        assertEquals(" rest", r.getRemaining());

        assertEquals("a b", ComponentArgument.textComponent().parse(new StringReader("\"a b\"")).literal());
        assertEquals("plain", ComponentArgument.textComponent().parse(new StringReader("plain")).literal());
        Text list = ComponentArgument.textComponent().parse(new StringReader("[\"x\",{\"text\":\"y\"}]"));
        assertEquals("x", list.literal());
        assertEquals(1, list.children().size());
    }

    @Test
    void componentArgumentRejectsBrokenJson() {
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class,
                () -> ComponentArgument.textComponent().parse(new StringReader("{\"text\":")));
        assertEquals("argument.component.invalid", e.getRawMessage().translationKey());
    }

    @Test
    void tellrawWithoutMatchingPlayersSaysNotFound() {
        assertEquals(List.of("No player was found"), run("tellraw @a {\"text\":\"hi\"}"));
    }

    // ------------------------------------------------------------------ 패킷 바이트

    @Test
    void stopSoundPacketBytes() {
        assertArrayEquals(new byte[] {0}, bytes(new StopSound(-1, null)));
        assertArrayEquals(new byte[] {1, 8}, bytes(new StopSound(SoundSource.AMBIENT.ordinal(), null)));
        ByteBuf b = Unpooled.wrappedBuffer(bytes(new StopSound(SoundSource.RECORDS.ordinal(), "minecraft:a.b")));
        assertEquals(3, b.readByte());
        assertEquals(2, Types.readVarInt(b));
        assertEquals("minecraft:a.b", Types.readString(b, 32767));
        assertEquals(0, b.readableBytes());
        assertEquals(0x6A, StopSound.ID);
    }

    @Test
    void transferPacketBytes() {
        ByteBuf b = Unpooled.wrappedBuffer(bytes(new Transfer("example.org", 25566)));
        assertEquals("example.org", Types.readString(b, 32767));
        assertEquals(25566, Types.readVarInt(b));
        assertEquals(0, b.readableBytes());
        assertEquals(0x73, Transfer.ID);
    }

    @Test
    void bossEventStyleAndPropertiesBytes() {
        UUID id = new UUID(1, 2);
        ByteBuf s = Unpooled.wrappedBuffer(bytes(BossEvent.style(id, 3, 4)));
        s.skipBytes(16);
        assertEquals(BossEvent.UPDATE_STYLE, Types.readVarInt(s));
        assertEquals(3, Types.readVarInt(s));
        assertEquals(4, Types.readVarInt(s));
        assertEquals(0, s.readableBytes());
        ByteBuf p = Unpooled.wrappedBuffer(bytes(BossEvent.properties(id, 5)));
        p.skipBytes(16);
        assertEquals(BossEvent.UPDATE_PROPERTIES, Types.readVarInt(p));
        assertEquals(5, p.readByte());
    }

    // ------------------------------------------------------------------ 메시지 키 분기

    @Test
    void transferNeedsPlayerWhenNoTargetGiven() {
        assertEquals(List.of("A player is required to run this command here"), run("transfer example.org"));
        assertEquals(List.of("No player was found"), run("transfer example.org 25565 @a"));
    }

    @Test
    void stopsoundWithoutTargetsSaysNotFound() {
        assertEquals(List.of("No player was found"), run("stopsound @a master minecraft:ambient.cave"));
    }

    @Test
    void bossbarCommandFlow() {
        assertEquals(List.of("Created custom bossbar [Test]"), run("bossbar add test {\"text\":\"Test\"}"));
        assertEquals(List.of("A bossbar already exists with the ID 'minecraft:test'"), run("bossbar add test x"));
        assertEquals(List.of("There are 1 custom bossbar(s) active: [Test]"), run("bossbar list"));
        assertEquals(List.of("Custom bossbar [Test] has a value of 0"), run("bossbar get test value"));
        assertEquals(List.of("Custom bossbar [Test] has a maximum of 100"), run("bossbar get test max"));
        assertEquals(List.of("Custom bossbar [Test] has changed value to 30"), run("bossbar set test value 30"));
        assertEquals(List.of("Nothing changed. That's already the value of this bossbar"), run("bossbar set test value 30"));
        assertEquals(List.of("Custom bossbar [Test] has changed maximum to 60"), run("bossbar set test max 60"));
        assertEquals(0.5f, core.customBossEvents().get(ResourceId.parse("test")).progress());
        assertEquals(List.of("Nothing changed. That's already the color of this bossbar"), run("bossbar set test color white"));
        assertEquals(List.of("Custom bossbar [Test] has changed color"), run("bossbar set test color red"));
        assertEquals(List.of("Custom bossbar [Test] has changed style"), run("bossbar set test style notched_10"));
        assertEquals(List.of("Nothing changed. The bossbar is already visible"), run("bossbar set test visible true"));
        assertEquals(List.of("Custom bossbar [Test] is now hidden"), run("bossbar set test visible false"));
        assertEquals(List.of("Nothing changed. The bossbar is already hidden"), run("bossbar set test visible false"));
        assertEquals(List.of("Custom bossbar [Test] has no players currently online"), run("bossbar get test players"));
        assertEquals(List.of("Nothing changed. Those players are already on the bossbar with nobody to add or remove"), run("bossbar set test players"));
        assertEquals(List.of("No bossbar exists with the ID 'minecraft:nope'"), run("bossbar get nope value"));
        assertEquals(List.of("Removed custom bossbar [Test]"), run("bossbar remove test"));
        assertEquals(List.of("There are no custom bossbars active"), run("bossbar list"));
    }

    // ------------------------------------------------------------------ 보스바 저장 왕복

    @Test
    void bossBarSaveLoadRoundTrip() {
        CustomBossEvents a = new CustomBossEvents(u -> null);
        CustomBossEvent e = a.create(ResourceId.parse("my:bar"), Text.text("Boss").color(org.domi.furnace.api.text.NamedTextColor.GOLD));
        e.setMax(40);
        e.setValue(10);
        e.setColor(CustomBossEvent.Color.PURPLE);
        e.setOverlay(CustomBossEvent.Overlay.NOTCHED_12);
        e.setVisible(false);
        e.setDarkenScreen(true);
        e.setCreateWorldFog(true);
        UUID u = new UUID(0x0123456789abcdefL, 0xfedcba9876543210L);
        e.addOfflinePlayer(u);

        NbtCompound saved = a.save();
        NbtCompound tag = saved.getCompound("my:bar");
        assertEquals("purple", tag.getString("Color"));
        assertEquals("notched_12", tag.getString("Overlay"));
        assertEquals(10, tag.getInt("Value"));
        assertEquals(40, tag.getInt("Max"));
        assertEquals(0, tag.getByte("Visible"));
        assertEquals(1, tag.getByte("DarkenScreen"));
        assertEquals(0, tag.getByte("PlayBossMusic"));
        assertEquals("{\"text\":\"Boss\",\"color\":\"gold\"}", tag.getString("Name"));
        assertArrayEquals(CustomBossEvents.uuidToInts(u), (int[]) tag.getList("Players").get(0));

        CustomBossEvents b = new CustomBossEvents(x -> null);
        b.load(saved);
        CustomBossEvent r = b.get(ResourceId.parse("my:bar"));
        assertNotNull(r);
        assertEquals(e.name(), r.name());
        assertEquals(10, r.value());
        assertEquals(40, r.max());
        assertEquals(0.25f, r.progress());
        assertEquals(CustomBossEvent.Color.PURPLE, r.color());
        assertEquals(CustomBossEvent.Overlay.NOTCHED_12, r.overlay());
        assertFalse(r.isVisible());
        assertTrue(r.darkenScreen());
        assertFalse(r.playBossMusic());
        assertTrue(r.createWorldFog());
        assertEquals(java.util.Set.of(u), r.playerIds());
        assertEquals(saved.tags().keySet(), b.save().tags().keySet());
    }

    @Test
    void bossBarSurvivesLevelDataWrite() {
        run("bossbar add keep {\"text\":\"Keep\"}");
        run("bossbar set keep value 7");
        core.writeLevelData();
        NbtCompound data = core.levelData().toNbt(0L);
        assertEquals(7, data.getCompound("CustomBossEvents").getCompound("minecraft:keep").getInt("Value"));
    }
}
