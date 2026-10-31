// 명령 블록 시험(기대값은 바닐라 디컴파일 손계산): 저장 키, 임펄스 실행과 체인 순서, 조건부 판정, 체인 길이 상한, 같은 틱 재실행 방지,
// 꺼진 서버 설정, 반복 재예약, 비교기 출력, set_command_block 적용(블록 종류 교체 + 블록 엔티티 유지), 출력 기록.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.entity.BaseCommandBlock;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.block.entity.CommandBlockEntity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class CommandBlockTest {

    static final Prop FACING = B.prop("facing"), CONDITIONAL = B.prop("conditional");

    static class Lvl extends BlockPlacementTest.TestLevel {
        long time = 100;
        boolean enabled = true;
        int maxChain = 65536;
        final List<String> ran = new ArrayList<>();
        final Map<String, Integer> script = new HashMap<>();
        @Override public long gameTime() { return time; }
        @Override public boolean commandBlockEnabled() { return enabled; }
        @Override public int gameRuleInt(String name) { return name.equals("maxCommandChainLength") ? maxChain : super.gameRuleInt(name); }
        @Override public void runBlockCommand(BlockCommandSink sink, String command, java.util.function.IntConsumer done) {
            ran.add(command);
            done.accept(script.getOrDefault(command, 1));
        }
    }

    static final Pos P = new Pos(1, -60, 1);

    static int state(String block, String facing, boolean conditional) {
        return CONDITIONAL.with(FACING.with(B.defaultState(block), facing), conditional);
    }

    static CommandBlockEntity put(Lvl l, Pos p, String block, String facing, boolean conditional, String cmd) {
        l.setBlock(p, state(block, facing, conditional), 3);
        var be = (CommandBlockEntity) l.getBlockEntity(p);
        be.logic().setCommand(cmd);
        return be;
    }

    static void tick(Lvl l, Pos p) {
        int s = l.getBlockState(p);
        BlockBehaviors.of(s).tick(s, l, p, l.random());
    }

    @Test
    void saveKeysAndRoundTrip() {
        var be = new CommandBlockEntity(P, state("command_block", "north", false));
        be.logic().setCommand("say hi");
        be.logic().setSuccessCount(3);
        be.logic().setCustomName(Text.text("N"));
        be.setPowered(true);
        be.setAutomaticNoSchedule(true);
        NbtCompound t = be.saveWithoutMetadata();
        assertEquals("say hi", t.getString("Command"));
        assertEquals(3, t.getInt("SuccessCount"));
        assertTrue(t.contains("CustomName"));
        assertEquals((byte) 1, t.getByte("TrackOutput"));
        assertEquals((byte) 1, t.getByte("UpdateLastExecution"));
        assertFalse(t.contains("LastExecution"), "0 이하면 쓰지 않는다");
        assertFalse(t.contains("LastOutput"));
        assertEquals((byte) 1, t.getByte("powered"));
        assertEquals((byte) 0, t.getByte("conditionMet"));
        assertEquals((byte) 1, t.getByte("auto"));
        var back = (CommandBlockEntity) BlockEntities.loadStatic(P, state("command_block", "north", false), be.saveWithFullMetadata());
        assertEquals("say hi", back.logic().command());
        assertEquals(3, back.logic().successCount());
        assertEquals("N", back.logic().customName().getString());
        assertTrue(back.isPowered());
        assertTrue(back.isAutomatic());
        assertEquals("N", back.logic().textName());
        assertEquals("@", new CommandBlockEntity(P, 0).logic().textName());
    }

    @Test
    void modeComesFromBlock() {
        assertEquals(CommandBlockEntity.Mode.REDSTONE, new CommandBlockEntity(P, state("command_block", "up", false)).mode());
        assertEquals(CommandBlockEntity.Mode.AUTO, new CommandBlockEntity(P, state("repeating_command_block", "up", false)).mode());
        assertEquals(CommandBlockEntity.Mode.SEQUENCE, new CommandBlockEntity(P, state("chain_command_block", "up", false)).mode());
        // 체인은 항상 활성으로 생성, 나머지는 꺼짐
        var l = new Lvl();
        assertTrue(put(l, P, "chain_command_block", "east", false, "x").isAutomatic());
        assertFalse(put(l, P.above(), "command_block", "east", false, "x").isAutomatic());
    }

    @Test
    void impulseRunsThenChainInOrderWithConditional() {
        var l = new Lvl();
        var head = put(l, P, "command_block", "east", false, "c1");
        put(l, P.east(), "chain_command_block", "east", false, "c2");
        var c3 = put(l, P.relative(Dir.EAST, 2), "chain_command_block", "east", true, "c3");
        l.script.put("c1", 1);
        l.script.put("c2", 0);   // 실패 → 조건부 c3 는 실행하지 않는다
        head.setPowered(true);
        head.markConditionMet(l);
        tick(l, P);
        assertEquals(List.of("c1", "c2"), l.ran);
        assertEquals(1, head.logic().successCount());
        assertEquals(0, c3.logic().successCount());
        assertEquals(1, B.behavior(l.getBlockState(P)).getAnalogOutputSignal(l.getBlockState(P), l, P), "성공 횟수가 비교기 출력");
    }

    @Test
    void conditionalRunsWhenPreviousSucceeded() {
        var l = new Lvl();
        var head = put(l, P, "command_block", "east", false, "a");
        put(l, P.east(), "chain_command_block", "east", true, "b");
        l.script.put("a", 2);
        head.setPowered(true);
        head.markConditionMet(l);
        tick(l, P);
        assertEquals(List.of("a", "b"), l.ran);
        assertEquals(2, ((CommandBlockEntity) l.getBlockEntity(P)).logic().successCount());
    }

    @Test
    void chainLimitStopsAfterMaxSteps() {
        var l = new Lvl();
        var head = put(l, P, "command_block", "east", false, "h");
        for (int i = 1; i <= 3; i++) put(l, P.relative(Dir.EAST, i), "chain_command_block", "east", false, "c" + i);
        l.maxChain = 2;
        head.setPowered(true);
        head.markConditionMet(l);
        tick(l, P);
        assertEquals(List.of("h", "c1", "c2"), l.ran, "상한 2 걸음");
    }

    @Test
    void notPoweredImpulseDoesNotRunButZeroesSuccess() {
        var l = new Lvl();
        var head = put(l, P, "command_block", "east", false, "h");
        head.logic().setSuccessCount(5);
        // conditionMet 이 거짓이고 조건부가 아니면 아무것도 하지 않는다
        tick(l, P);
        assertTrue(l.ran.isEmpty());
        assertEquals(5, head.logic().successCount());
    }

    @Test
    void sameTickGuardAndDisabledServer() {
        var l = new Lvl();
        var be = put(l, P, "command_block", "east", false, "h");
        assertEquals(BaseCommandBlock.Performed.RAN, be.logic().performCommand(l, lv -> {}));
        assertEquals(BaseCommandBlock.Performed.NOT_RUN, be.logic().performCommand(l, lv -> {}), "같은 게임 틱 재실행 금지");
        l.time++;
        l.enabled = false;
        be.logic().setSuccessCount(4);
        assertEquals(BaseCommandBlock.Performed.RAN, be.logic().performCommand(l, lv -> {}));
        assertEquals(0, be.logic().successCount(), "꺼져 있으면 실행 없이 성공 0");
        assertEquals(1, l.ran.size());
    }

    @Test
    void emptyCommandAndSearge() {
        var l = new Lvl();
        var be = put(l, P, "command_block", "east", false, "");
        assertEquals(BaseCommandBlock.Performed.RAN, be.logic().performCommand(l, lv -> {}));
        assertTrue(l.ran.isEmpty());
        l.time++;
        be.logic().setCommand("sEARGE");
        assertEquals(BaseCommandBlock.Performed.RAN, be.logic().performCommand(l, lv -> {}));
        assertEquals(1, be.logic().successCount());
        assertEquals("#itzlipofutzli", be.logic().lastOutput().getString());
    }

    @Test
    void repeatingReschedulesWhilePowered() {
        var l = new Lvl();
        var be = put(l, P, "repeating_command_block", "east", false, "r");
        be.setPowered(true);
        be.markConditionMet(l);
        tick(l, P);
        assertEquals(List.of("r"), l.ran);
        assertTrue(l.hasScheduledTick(P, B.id("repeating_command_block")), "신호가 있으면 1틱 뒤 다시");
    }

    @Test
    void neighborChangedSchedulesOnceOnNewSignal() {
        var l = new Lvl() {
            @Override public boolean hasNeighborSignal(Pos pos) { return true; }
        };
        put(l, P, "command_block", "east", false, "x");
        int s = l.getBlockState(P);
        B.behavior(s).neighborChanged(s, l, P, 0, P.west(), false);
        assertTrue(l.hasScheduledTick(P, B.id("command_block")));
        assertTrue(((CommandBlockEntity) l.getBlockEntity(P)).wasConditionMet());
    }

    @Test
    void applyEditSwitchesBlockKeepsEntity() {
        var l = new Lvl();
        var be = put(l, P, "command_block", "south", false, "old");
        assertTrue(CommandBlockEntity.applyEdit(l, P, "new", CommandBlockEntity.Mode.AUTO, false, true, true));
        int s = l.getBlockState(P);
        assertEquals(B.id("repeating_command_block"), B.block(s));
        assertEquals("south", FACING.valueName(s), "방향 유지");
        assertTrue(CONDITIONAL.is(s));
        assertSame(be, l.getBlockEntity(P), "블록 엔티티 유지");
        assertEquals("new", be.logic().command());
        assertFalse(be.logic().isTrackOutput());
        assertTrue(be.isAutomatic());
        assertTrue(l.hasScheduledTick(P, B.id("repeating_command_block")), "반복 모드로 바뀌면 항상 활성이라 예약");
        assertFalse(CommandBlockEntity.applyEdit(l, P.above(), "z", CommandBlockEntity.Mode.SEQUENCE, true, false, false), "블록 엔티티 없음");
    }

    @Test
    void outputIsTimestampedAndRespectsTracking() {
        var l = new Lvl();
        var be = put(l, P, "command_block", "east", false, "x");
        BaseCommandBlock.clock = () -> "12:34:56";
        be.logic().performCommand(l, lv -> {});
        be.logic().sendSystemMessage(Text.text("hi"));
        assertEquals("[12:34:56] hi", be.logic().lastOutput().getString());
        assertTrue(be.logic().acceptsSuccess());
        be.logic().setTrackOutput(false);
        assertFalse(be.logic().acceptsFailure());
        be.logic().sendSystemMessage(Text.text("later"));
        assertEquals("[12:34:56] hi", be.logic().lastOutput().getString(), "기록 끄면 그대로");
    }

    @Test
    void sourceRotationFromFacing() {
        var l = new Lvl();
        assertEquals(90f, put(l, P, "command_block", "west", false, "x").logic().yRot());
        assertEquals(0f, put(l, P.above(), "command_block", "south", false, "x").logic().yRot());
        assertEquals(270f, put(l, P.above(2), "command_block", "down", false, "x").logic().yRot(), "아래 = 번호 -1 & 3 = 3");
        assertEquals(P.x() + 0.5, put(l, P.above(3), "command_block", "up", false, "x").logic().x());
    }
}
