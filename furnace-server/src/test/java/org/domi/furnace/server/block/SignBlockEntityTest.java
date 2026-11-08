// 표지판 시험: 저장 왕복·앞뒤 판정·편집 화면 열기와 글 바꾸기·염료/발광/밀랍·클릭 명령·편집 허락 해제·패킷 바이트.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.entity.SignBlockEntity;
import org.domi.furnace.server.block.entity.SignText;
import org.domi.furnace.server.block.types.SignBlock;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.BlockEntityDataPacket;
import org.domi.furnace.server.net.protocol.play.OpenSignEditor;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SignBlockEntityTest {

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<Object[]> sounds = new ArrayList<>();
        final List<int[]> levelEvents = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        @Override public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) { sounds.add(new Object[] {sound, source}); }
        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { levelEvents.add(new int[] {type, data}); }
        @Override public void runSignCommand(BlockActor player, Pos pos, String command) { commands.add(command); }
        /** 면을 아는 쪽을 가로채는 시험용: 이 값이 false 면 플러그인이 취소한 것처럼 돌려준다. */
        boolean allow = true;
        final List<String> sides = new ArrayList<>();
        boolean sideAware;
        @Override public boolean runSignCommand(BlockActor player, Pos pos, boolean front, String command) {
            if (!sideAware) {
                runSignCommand(player, pos, command);
                return true;
            }
            sides.add((front ? "front:" : "back:") + command);
            return allow;
        }
    }

    static final class Act implements BlockActor {
        final ItemStack[] hands = {ItemStack.EMPTY, ItemStack.EMPTY};
        final UUID id = UUID.randomUUID();
        double x = 1.5, y = -60, z = 5.5;
        final List<Object[]> opened = new ArrayList<>();
        public float yRot() { return 0; }
        public float xRot() { return 0; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public double eyeHeight() { return 1.62; }
        public boolean isSecondaryUseActive() { return false; }
        public boolean mayBuild() { return true; }
        public boolean instabuild() { return false; }
        public boolean isSpectator() { return false; }
        public boolean isCreative() { return false; }
        public int permissionLevel() { return 0; }
        public ItemStack itemInHand(int hand) { return hands[hand]; }
        public void setItemInHand(int hand, ItemStack s) { hands[hand] = s; }
        public Object handle() { return this; }
        @Override public UUID uuid() { return id; }
        @Override public void openTextEdit(Pos pos, int state, boolean front) { opened.add(new Object[] {pos, front}); }
    }

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    static final Pos POS = new Pos(1, -60, 1);

    static Lvl placed(int[] stateOut) {
        Lvl level = new Lvl();
        int state = B.defaultState("minecraft:oak_sign");   // rotation 0: 앞면이 남쪽(+z)
        level.setBlock(POS, state, 3);
        stateOut[0] = state;
        return level;
    }

    @Test
    void defaultSaveMatchesVanillaShapeAndRoundTrips() {
        int[] st = new int[1];
        Lvl level = placed(st);
        var be = (SignBlockEntity) level.getBlockEntity(POS);
        assertNotNull(be);
        assertEquals("minecraft:sign", be.type());
        NbtCompound tag = be.saveWithoutMetadata();
        NbtCompound front = tag.getCompound("front_text");
        assertEquals(List.of("\"\"", "\"\"", "\"\"", "\"\""), front.getList("messages").items());
        assertFalse(front.contains("filtered_messages"), "messages 와 같으면 쓰지 않는다");
        assertEquals("black", front.getString("color"));
        assertEquals((byte) 0, front.getByte("has_glowing_text"));
        assertEquals((byte) 0, tag.getByte("is_waxed"));

        be.updateText(t -> t.setMessage(0, Text.text("hi")).setColor("red").setHasGlowingText(true), true, level);
        be.setWaxed(true, level);
        var back = new SignBlockEntity(POS, st[0]);
        back.load(be.saveWithoutMetadata());
        assertEquals("hi", back.frontText().message(0, false).getString());
        assertEquals("red", back.frontText().color());
        assertTrue(back.frontText().hasGlowingText());
        assertTrue(back.isWaxed());
        assertEquals(be.saveWithoutMetadata().getCompound("front_text").getList("messages").items(),
                back.saveWithoutMetadata().getCompound("front_text").getList("messages").items());
    }

    @Test
    void badSideKeepsDefaultAndHangingType() {
        var be = new SignBlockEntity(POS, B.defaultState("minecraft:oak_sign"));
        NbtCompound tag = new NbtCompound();
        NbtCompound f = new NbtCompound();
        NbtList three = new NbtList((byte) 8);
        three.add("\"a\"");
        f.putList("messages", three);
        tag.putCompound("front_text", f);
        be.load(tag);
        assertEquals("", be.frontText().message(0, false).getString(), "4줄이 아니면 그 면은 그대로");
        assertEquals("minecraft:hanging_sign", SignBlockEntity.hanging(POS, B.defaultState("minecraft:oak_hanging_sign")).type());
        assertTrue(new SignBlockEntity(POS, 0).isValid(B.defaultState("minecraft:oak_wall_sign")));
        assertFalse(new SignBlockEntity(POS, 0).isValid(B.defaultState("minecraft:oak_hanging_sign")));
    }

    @Test
    void facingFrontDependsOnSide() {
        int[] st = new int[1];
        Lvl level = placed(st);
        var be = (SignBlockEntity) level.getBlockEntity(POS);
        var block = (SignBlock) B.behavior(st[0]);
        Act a = new Act();
        a.z = 5.5;
        assertTrue(block.isFacingFrontText(be, a));
        a.z = -3.5;
        assertFalse(block.isFacingFrontText(be, a));
    }

    @Test
    void emptyHandOpensEditorThenUpdateKeepsStyleAndBroadcastsData() {
        int[] st = new int[1];
        Lvl level = placed(st);
        level.broadcastChanges();
        level.broadcasts.clear();
        var be = (SignBlockEntity) level.getBlockEntity(POS);
        var block = (SignBlock) B.behavior(st[0]);
        Act a = new Act();
        Hit hit = new Hit(POS, Dir.SOUTH, 1.5, -59.5, 1.6, false);
        assertEquals(InteractionResult.SUCCESS, block.useWithoutItem(st[0], level, POS, a, hit));
        assertEquals(1, a.opened.size());
        assertEquals(true, a.opened.get(0)[1]);
        assertEquals(a.id, be.playerWhoMayEdit());

        // 다른 사람이 보낸 갱신은 무시
        be.updateSignText(UUID.randomUUID(), false, true, List.of("x", "", "", ""), level);
        assertEquals("", be.frontText().message(0, false).getString());

        be.updateSignText(a.id, false, true, List.of("one", "two", "", "four"), level);
        assertEquals("two", be.frontText().message(1, false).getString());
        assertNull(be.playerWhoMayEdit(), "갱신 뒤 허락은 지운다");
        level.broadcastChanges();
        var data = level.broadcasts.stream().filter(p -> p instanceof BlockEntityDataPacket).map(p -> (BlockEntityDataPacket) p).toList();
        assertEquals(1, data.size());
        assertEquals(7, data.get(0).typeId());
        assertEquals(List.of("\"one\"", "\"two\"", "\"\"", "\"four\""),
                data.get(0).tag().getCompound("front_text").getList("messages").items());

        // 이제 글이 있어도 글자 내용이면 다시 열 수 있다
        assertEquals(InteractionResult.SUCCESS, block.useWithoutItem(st[0], level, POS, a, hit));
        assertEquals(2, a.opened.size());
        // 다른 사람은 편집 중이라 못 연다
        Act other = new Act();
        assertEquals(InteractionResult.PASS, block.useWithoutItem(st[0], level, POS, other, hit));
    }

    @Test
    void applicatorsDyeGlowInkAndWax() {
        int[] st = new int[1];
        Lvl level = placed(st);
        var be = (SignBlockEntity) level.getBlockEntity(POS);
        var block = (SignBlock) B.behavior(st[0]);
        Act a = new Act();
        Hit hit = new Hit(POS, Dir.SOUTH, 1.5, -59.5, 1.6, false);
        // 글이 없으면 염료는 기본 상호작용으로
        a.hands[0] = item("red_dye", 3);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, block.useItemOn(a.hands[0], st[0], level, POS, a, 0, hit));
        be.updateText(t -> t.setMessage(0, Text.text("x")), true, level);
        assertEquals(ItemInteractionResult.SUCCESS, block.useItemOn(a.hands[0], st[0], level, POS, a, 0, hit));
        assertEquals("red", be.frontText().color());
        assertEquals("black", be.backText().color());
        assertEquals(2, a.hands[0].count());
        assertEquals("minecraft:item.dye.use", level.sounds.get(level.sounds.size() - 1)[0]);
        // 같은 색은 바뀌지 않아 PASS
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, block.useItemOn(a.hands[0], st[0], level, POS, a, 0, hit));

        a.hands[0] = item("glow_ink_sac", 1);
        assertEquals(ItemInteractionResult.SUCCESS, block.useItemOn(a.hands[0], st[0], level, POS, a, 0, hit));
        assertTrue(be.frontText().hasGlowingText());
        a.hands[0] = item("ink_sac", 1);
        assertEquals(ItemInteractionResult.SUCCESS, block.useItemOn(a.hands[0], st[0], level, POS, a, 0, hit));
        assertFalse(be.frontText().hasGlowingText());

        // 벌집은 글이 없어도(뒷면) 밀랍 칠
        a.z = -3.5;
        a.hands[0] = item("honeycomb", 1);
        assertEquals(ItemInteractionResult.SUCCESS, block.useItemOn(a.hands[0], st[0], level, POS, a, 0, hit));
        assertTrue(be.isWaxed());
        assertArrayEquals(new int[] {3003, 0}, level.levelEvents.get(0));
        // 밀랍이면 더는 바르지 못하고, 손으로 누르면 실패 소리
        a.hands[0] = item("honeycomb", 1);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, block.useItemOn(a.hands[0], st[0], level, POS, a, 0, hit));
        assertEquals(InteractionResult.SUCCESS, block.useWithoutItem(st[0], level, POS, a, hit));
        assertEquals("minecraft:block.sign.waxed_interact_fail", level.sounds.get(level.sounds.size() - 1)[0]);
        assertEquals("blocks", level.sounds.get(level.sounds.size() - 1)[1]);
        assertTrue(a.opened.isEmpty());
    }

    @Test
    void clickCommandsRunOnUseAndBlockEditing() {
        int[] st = new int[1];
        Lvl level = placed(st);
        var be = (SignBlockEntity) level.getBlockEntity(POS);
        var block = (SignBlock) B.behavior(st[0]);
        be.updateText(t -> t.setMessage(1, Text.text("go").clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hi"))), true, level);
        Act a = new Act();
        Hit hit = new Hit(POS, Dir.SOUTH, 1.5, -59.5, 1.6, false);
        assertFalse(be.canExecuteClickCommands(true, false), "밀랍이 아니면 canExecute 는 거짓");
        assertEquals(InteractionResult.SUCCESS, block.useWithoutItem(st[0], level, POS, a, hit));
        assertEquals(List.of("/say hi"), level.commands);
        assertTrue(a.opened.isEmpty(), "명령이 돌면 편집 화면을 열지 않는다");
        be.setWaxed(true, level);
        assertTrue(be.canExecuteClickCommands(true, false));
        // 뒷면은 명령 없음
        assertFalse(be.canExecuteClickCommands(false, false));
    }

    @Test
    void cancelledClickCommandStopsAtTheLineAndReportsNothingRan() {
        int[] st = new int[1];
        Lvl level = placed(st);
        level.sideAware = true;
        var be = (SignBlockEntity) level.getBlockEntity(POS);
        be.updateText(t -> t.setMessage(0, Text.text("a").clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "first"))), false, level);
        be.updateText(t -> t.setMessage(1, Text.text("b").clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "second"))), false, level);
        Act a = new Act();
        assertTrue(be.executeClickCommandsIfPresent(a, level, false, false));
        assertEquals(List.of("back:first", "back:second"), level.sides, "면이 레벨까지 전해진다");
        level.sides.clear();
        level.allow = false;   // 플러그인이 PlayerSignCommandPreprocessEvent 를 취소했다
        assertFalse(be.executeClickCommandsIfPresent(a, level, false, false), "Paper: 취소되면 false 로 끝난다");
        assertEquals(List.of("back:first"), level.sides, "첫 줄에서 멈추고 나머지 줄은 보지 않는다");
    }

    @Test
    void tickClearsEditorWhenPlayerGone() {
        int[] st = new int[1];
        Lvl level = placed(st);
        var be = (SignBlockEntity) level.getBlockEntity(POS);
        be.setAllowedPlayerEditor(UUID.randomUUID());
        assertTrue(be.ticking());
        be.tick(level);
        assertNull(be.playerWhoMayEdit());
    }

    @Test
    void packetBytes() {
        ByteBuf b = Unpooled.buffer();
        new OpenSignEditor(1, -60, 1, false).write(b);
        // 위치 long: x<<38 | z<<12 & ... | y
        long packed = ((long) 1 & 0x3FFFFFF) << 38 | ((long) 1 & 0x3FFFFFF) << 12 | (-60 & 0xFFF);
        assertEquals(packed, b.readLong());
        assertFalse(b.readBoolean());
        assertEquals(0, b.readableBytes());
        assertEquals(0x34, new OpenSignEditor(0, 0, 0, true).id());

        ByteBuf d = Unpooled.buffer();
        NbtCompound t = new NbtCompound();
        t.putBoolean("is_waxed", true);
        new BlockEntityDataPacket(0, 0, 0, 7, t).write(d);
        byte[] out = new byte[d.readableBytes()];
        d.readBytes(out);
        // pos 0(8바이트) · VarInt 7 · 이름 없는 compound{ byte "is_waxed" 1 } · END
        byte[] expect = {0, 0, 0, 0, 0, 0, 0, 0, 7, 10, 1, 0, 8, 'i', 's', '_', 'w', 'a', 'x', 'e', 'd', 1, 0};
        assertArrayEquals(expect, out);
        assertEquals(0x07, new BlockEntityDataPacket(0, 0, 0, 7, t).id());
    }

    @Test
    void signTextFilteredWrittenOnlyWhenDifferent() {
        SignText s = new SignText().setMessage(0, Text.text("a"), Text.text("b"));
        NbtCompound tag = s.save();
        assertEquals(List.of("\"b\"", "\"\"", "\"\"", "\"\""), tag.getList("filtered_messages").items());
        SignText back = SignText.load(tag);
        assertEquals("b", back.message(0, true).getString());
        assertEquals("a", back.message(0, false).getString());
    }
}
