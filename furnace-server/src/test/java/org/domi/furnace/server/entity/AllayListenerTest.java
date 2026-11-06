// 알레이 수신기: 주크박스 재생/정지 이벤트로 춤, 음표 블록 소리로 좋아하는 칸 기억, 진동 데이터 NBT "listener" 저장/복원.
// 기대값은 바닐라 1.21.1 디컴파일(cgk.a, cgk.b, cgk.b(jd, boolean), AllayAi.hearNoteblock)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.domi.furnace.server.world.gameevent.GameEventListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AllayListenerTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Allay allay(BlockPlacementTest.TestLevel level) {
        Allay a = new Allay(level, level.nextEntityId());
        a.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(a);
        return a;
    }

    private static GameEventListener jukeboxListener(Allay a) {
        return (GameEventListener) a.dynamicGameEventListeners().get(1).getListener();
    }

    @Test
    void hasTwoListenersWithRadii() {
        var level = new BlockPlacementTest.TestLevel();
        Allay a = allay(level);
        assertEquals(2, a.dynamicGameEventListeners().size());
        assertEquals(10, jukeboxListener(a).getListenerRadius());                       // jukebox_play 반경
        assertEquals(16, a.dynamicGameEventListeners().get(0).getListener().getListenerRadius());   // 진동 수신기
    }

    @Test
    void jukeboxPlayStartsDancingAndStopOnlyForSameJukebox() {
        var level = new BlockPlacementTest.TestLevel();
        Allay a = allay(level);
        var l = jukeboxListener(a);
        var pos = new org.domi.furnace.server.entity.Vec3(5.5, -59.5, 3.5);   // 칸 (5,-60,3) 가운데
        assertTrue(l.handleGameEvent(level, GameEvent.JUKEBOX_PLAY, GameEventContext.EMPTY, pos));
        assertTrue(a.isDancing());
        // 다른 칸의 정지 이벤트는 무시(기억한 칸이 있으므로)
        assertTrue(l.handleGameEvent(level, GameEvent.JUKEBOX_STOP_PLAY, GameEventContext.EMPTY, new org.domi.furnace.server.entity.Vec3(9.5, -59.5, 3.5)));
        assertTrue(a.isDancing());
        assertTrue(l.handleGameEvent(level, GameEvent.JUKEBOX_STOP_PLAY, GameEventContext.EMPTY, pos));
        assertFalse(a.isDancing());
        // 다른 이벤트는 처리하지 않는다
        assertFalse(l.handleGameEvent(level, GameEvent.NOTE_BLOCK_PLAY, GameEventContext.EMPTY, pos));
    }

    @Test
    void alreadyDancingIgnoresSecondJukebox() {
        var level = new BlockPlacementTest.TestLevel();
        Allay a = allay(level);
        a.setJukeboxPlaying(new Pos(1, -60, 1), true);
        a.setJukeboxPlaying(new Pos(2, -60, 2), true);   // 이미 춤추는 중이라 기억한 칸이 바뀌지 않는다
        a.setJukeboxPlaying(new Pos(2, -60, 2), false);  // 기억한 칸이 아니므로 무시
        assertTrue(a.isDancing());
        a.setJukeboxPlaying(new Pos(1, -60, 1), false);
        assertFalse(a.isDancing());
    }

    @Test
    void noteBlockRememberedAndFiltersOtherPositions() {
        var level = new BlockPlacementTest.TestLevel();
        Allay a = allay(level);
        var user = a.getVibrationUser();
        var p1 = new Pos(3, -60, 3);
        assertTrue(user.canReceiveVibration(level, p1, GameEvent.NOTE_BLOCK_PLAY, GameEventContext.EMPTY));   // 기억 없음: 모두 받는다
        user.onReceiveVibration(level, p1, GameEvent.NOTE_BLOCK_PLAY, null, null, 3.0F);
        var liked = a.getBrain().getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
        assertTrue(liked.isPresent());
        assertEquals(p1, liked.get().pos());
        assertEquals(600, a.getBrain().getMemory(MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS).orElse(-1));
        // 기억한 뒤에는 그 칸만
        assertTrue(user.canReceiveVibration(level, p1, GameEvent.NOTE_BLOCK_PLAY, GameEventContext.EMPTY));
        assertFalse(user.canReceiveVibration(level, new Pos(4, -60, 3), GameEvent.NOTE_BLOCK_PLAY, GameEventContext.EMPTY));
        // 음표 블록이 아닌 진동은 기억을 바꾸지 않는다
        user.onReceiveVibration(level, new Pos(9, -60, 9), GameEvent.STEP, null, null, 1.0F);
        assertEquals(p1, a.getBrain().getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION).get().pos());
    }

    @Test
    void onlyAllayCanListenEventsReachTheVibrationListener() {
        var level = new BlockPlacementTest.TestLevel();
        Allay a = allay(level);
        var user = a.getVibrationUser();
        assertEquals(GameEvent.ALLAY_CAN_LISTEN, user.getListenableEvents());
        assertFalse(user.isValidVibration(GameEvent.STEP, GameEventContext.EMPTY));          // 걸음은 듣지 않는다
        assertFalse(user.isValidVibration(GameEvent.BLOCK_PLACE, GameEventContext.EMPTY));   // 블록 설치도
        assertTrue(user.isValidVibration(GameEvent.NOTE_BLOCK_PLAY, GameEventContext.EMPTY));
        var l = a.dynamicGameEventListeners().get(0).getListener();
        var at = new org.domi.furnace.server.entity.Vec3(3.5, -59.5, 3.5);
        assertFalse(l.handleGameEvent(level, GameEvent.STEP, GameEventContext.EMPTY, at));
        assertTrue(l.handleGameEvent(level, GameEvent.NOTE_BLOCK_PLAY, GameEventContext.EMPTY, at));
    }

    @Test
    void listenerNbtRoundTrip() {
        var level = new BlockPlacementTest.TestLevel();
        Allay a = allay(level);
        var tag = a.save();
        assertTrue(tag.tags().get("listener") instanceof org.domi.furnace.server.net.io.nbt.NbtCompound);
        Allay b = (Allay) Entities.load(level, tag);
        assertTrue(b.save().tags().get("listener") instanceof org.domi.furnace.server.net.io.nbt.NbtCompound);
    }
}
