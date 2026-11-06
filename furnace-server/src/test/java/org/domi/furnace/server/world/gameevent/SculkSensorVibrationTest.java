// 진동 체계 시험: 발행→후보 선택→이동 틱→도착, 차단(#occludes_vibration_signals)·흡수(#dampens_vibrations), 선택기 우선순위,
// 조각 감지기 상태 전이(active 30틱 → cooldown 10틱 → inactive), 신호 세기, 보정 감지기 주파수 거름, 저장 왕복.
package org.domi.furnace.server.world.gameevent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.SculkSensorBlockEntity;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class SculkSensorVibrationTest {

    static final Blocks B = Blocks.get();
    static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    /** 게임 시간이 흐르는 시험 레벨. */
    static final class Lvl extends BlockPlacementTest.TestLevel {
        long time = 100;
        final LevelServices svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public long gameTime() { return time; }
        };
        @Override protected LevelServices services() { return svc; }

        void step() {
            time++;
            runScheduledTicks();
            tickBlockEntities(k -> true);
        }
    }

    static final Pos S = new Pos(0, -60, 0);

    static Lvl levelWithSensor() {
        Lvl l = new Lvl();
        l.setBlock(S, B.defaultState("minecraft:sculk_sensor"), 3);
        assertInstanceOf(SculkSensorBlockEntity.class, l.getBlockEntity(S));
        return l;
    }

    static String phase(Lvl l) { return org.domi.furnace.server.block.B.prop("sculk_sensor_phase").valueName(l.getBlockState(S)); }

    static int power(Lvl l) { return org.domi.furnace.server.block.B.prop("power").intValue(l.getBlockState(S)); }

    static void place(Lvl l, int x) {
        l.gameEvent(GameEvent.BLOCK_PLACE, new Vec3(x + 0.5, -59.5, 0.5), GameEventContext.EMPTY);
    }

    @Test
    void vibrationTravelsDistanceTicksThenActivates() {
        Lvl l = levelWithSensor();
        place(l, 4);
        var be = (SculkSensorBlockEntity) l.getBlockEntity(S);
        assertTrue(be.getVibrationData().getSelectionStrategy().chosenCandidate(l.time + 1).isPresent(), "후보로 들어갔다");
        for (int i = 0; i < 3; i++) {
            l.step();
            assertEquals("inactive", phase(l), "틱 " + i);
        }
        l.step();
        assertEquals("active", phase(l));
        assertEquals(8, power(l), "15 - floor(15/8 × 4)");
        assertEquals(13, be.getLastVibrationFrequency(), "block_place 주파수");
        int st = l.getBlockState(S);
        assertEquals(13, org.domi.furnace.server.block.B.behavior(st).getAnalogOutputSignal(st, l, S), "비교기 출력 = 마지막 주파수");
        // 30틱 켜짐 → cooldown 10틱 → inactive
        for (int i = 0; i < 29; i++) l.step();
        assertEquals("active", phase(l));
        l.step();
        assertEquals("cooldown", phase(l));
        assertEquals(0, power(l));
        for (int i = 0; i < 9; i++) l.step();
        assertEquals("cooldown", phase(l));
        l.step();
        assertEquals("inactive", phase(l));
    }

    @Test
    void woolInLineOccludesAndDampenedStateIsIgnored() {
        Lvl l = levelWithSensor();
        l.setBlock(new Pos(2, -60, 0), B.defaultState("minecraft:white_wool"), 3);
        place(l, 4);
        for (int i = 0; i < 10; i++) l.step();
        assertEquals("inactive", phase(l), "양털이 가로막는다");

        Lvl l2 = levelWithSensor();
        l2.gameEvent(GameEvent.BLOCK_PLACE, new Vec3(3.5, -59.5, 0.5), GameEventContext.ofState(B.defaultState("minecraft:white_wool")));
        for (int i = 0; i < 10; i++) l2.step();
        assertEquals("inactive", phase(l2), "양털 상태 이벤트는 흡수");
    }

    @Test
    void outOfRangeIsNotHeardAndSensorIgnoresOwnPlacement() {
        Lvl l = levelWithSensor();
        place(l, 9);   // 반경 8 밖
        for (int i = 0; i < 12; i++) l.step();
        assertEquals("inactive", phase(l));
        var be = (SculkSensorBlockEntity) l.getBlockEntity(S);
        assertFalse(be.getVibrationUser().canReceiveVibration(l, S, GameEvent.BLOCK_PLACE, GameEventContext.EMPTY));
        assertTrue(be.getVibrationUser().canReceiveVibration(l, S, GameEvent.BLOCK_CHANGE, GameEventContext.EMPTY));
    }

    @Test
    void selectorPrefersCloserThenHigherFrequency() {
        var sel = new VibrationSystem.VibrationSelector();
        var far = VibrationSystem.VibrationInfo.of(GameEvent.EXPLODE, 5.0F, new Vec3(0, 0, 0), null);
        var near = VibrationSystem.VibrationInfo.of(GameEvent.STEP, 3.0F, new Vec3(0, 0, 0), null);
        var nearHigh = VibrationSystem.VibrationInfo.of(GameEvent.BLOCK_PLACE, 3.0F, new Vec3(0, 0, 0), null);
        sel.addCandidate(far, 10);
        sel.addCandidate(near, 10);
        sel.addCandidate(nearHigh, 10);
        sel.addCandidate(VibrationSystem.VibrationInfo.of(GameEvent.EXPLODE, 1.0F, new Vec3(0, 0, 0), null), 11);   // 다른 틱은 무시
        assertTrue(sel.chosenCandidate(10).isEmpty(), "같은 틱에는 내주지 않는다");
        assertSame(nearHigh, sel.chosenCandidate(11).orElseThrow());
    }

    @Test
    void dataSaveRoundTripMidTravel() {
        Lvl l = levelWithSensor();
        place(l, 6);
        l.step();
        l.step();
        var be = (SculkSensorBlockEntity) l.getBlockEntity(S);
        assertEquals(4, be.getVibrationData().getTravelTimeInTicks());
        var tag = be.saveWithoutMetadata();
        var back = new SculkSensorBlockEntity(S, l.getBlockState(S));
        back.load(tag);
        var d = back.getVibrationData();
        assertEquals(4, d.getTravelTimeInTicks());
        assertEquals(GameEvent.BLOCK_PLACE, d.getCurrentVibration().gameEvent());
        assertEquals(6.0F, d.getCurrentVibration().distance());
        assertTrue(d.shouldReloadVibrationParticle(), "불러오면 입자 재전송 표시");
        var again = back.saveWithoutMetadata().getCompound("listener");
        assertEquals(4, again.getInt("event_delay"));
        assertEquals(-1L, again.getCompound("selector").getLong("tick"), "선택기는 비었다");
        assertEquals("minecraft:block_place", again.getCompound("event").getString("game_event"));
    }

    @Test
    void calibratedSensorFiltersByBackSignal() {
        Lvl l = new Lvl();
        int cal = REG.stateIdOrAir("minecraft:calibrated_sculk_sensor",
                Map.of("facing", "north", "power", "0", "sculk_sensor_phase", "inactive", "waterlogged", "false"));
        l.setBlock(S, cal, 3);
        var be = (SculkSensorBlockEntity) l.getBlockEntity(S);
        // 뒤(남쪽)에 신호 15 → 주파수 15 만
        l.setBlock(S.south(), B.defaultState("minecraft:redstone_block"), 3);
        assertFalse(be.getVibrationUser().canReceiveVibration(l, new Pos(5, -60, 0), GameEvent.BLOCK_PLACE, GameEventContext.EMPTY));
        assertTrue(be.getVibrationUser().canReceiveVibration(l, new Pos(5, -60, 0), GameEvent.EXPLODE, GameEventContext.EMPTY));
        assertEquals(16, be.getVibrationUser().getListenerRadius());
    }
}
