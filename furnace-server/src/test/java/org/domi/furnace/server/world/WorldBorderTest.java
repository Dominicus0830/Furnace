// 월드 보더: 기본값, 크기 보간 수학(바닐라 MovingBorderExtent), tick 으로 정적 전환, 리스너 알림, 설정 저장·복원, 피해 계산.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class WorldBorderTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final WorldBorder border = new WorldBorder(now::get);

    @Test
    void vanillaDefaults() {
        assertEquals(59999968.0, WorldBorder.MAX_SIZE);
        assertEquals(59999968.0, border.getSize());
        assertEquals(59999968.0, border.getLerpTarget());
        assertEquals(0L, border.getLerpRemainingTime());
        assertEquals(0.0, border.getCenterX());
        assertEquals(0.0, border.getCenterZ());
        assertEquals(0.2, border.getDamagePerBlock());
        assertEquals(5.0, border.getDamageSafeZone());
        assertEquals(5, border.getWarningBlocks());
        assertEquals(15, border.getWarningTime());
        assertEquals(29999984, border.getAbsoluteMaxSize());
        assertEquals(WorldBorder.Status.STATIONARY, border.getStatus());
        // 크기 59999968 이라 절대 최대(±29999984) 로 잘린다
        assertEquals(-29999984.0, border.getMinX());
        assertEquals(29999984.0, border.getMaxZ());
    }

    @Test
    void lerpIsLinearInWallClockMillis() {
        border.setSize(100.0);
        border.lerpSizeBetween(100.0, 200.0, 10_000L);
        assertEquals(100.0, border.getSize());
        assertEquals(200.0, border.getLerpTarget());
        assertEquals(10_000L, border.getLerpRemainingTime());
        assertEquals(WorldBorder.Status.GROWING, border.getStatus());
        assertEquals(0.01, border.getLerpSpeed(), 1e-12);
        now.addAndGet(2_500L);
        assertEquals(125.0, border.getSize(), 1e-9);
        assertEquals(7_500L, border.getLerpRemainingTime());
        assertEquals(-62.5, border.getMinX(), 1e-9);
        assertEquals(62.5, border.getMaxX(), 1e-9);
        now.addAndGet(7_499L);
        assertEquals(199.99, border.getSize(), 1e-9);
        now.addAndGet(1L);
        // 종료 시각이 지나면 목표값. tick 전에는 남은 시간이 0 이하여도 아직 이동 상태
        assertEquals(200.0, border.getSize());
        assertEquals(0L, border.getLerpRemainingTime());
        assertEquals(WorldBorder.Status.GROWING, border.getStatus());
        border.tick();
        assertEquals(WorldBorder.Status.STATIONARY, border.getStatus());
        assertEquals(200.0, border.getSize());
        assertEquals(200.0, border.getLerpTarget());
    }

    @Test
    void tickKeepsMovingUntilEnd() {
        border.lerpSizeBetween(1000.0, 10.0, 4_000L);
        assertEquals(WorldBorder.Status.SHRINKING, border.getStatus());
        now.addAndGet(3_999L);
        border.tick();
        assertEquals(WorldBorder.Status.SHRINKING, border.getStatus());
        assertEquals(1L, border.getLerpRemainingTime());
        now.addAndGet(1L);
        border.tick();
        assertEquals(WorldBorder.Status.STATIONARY, border.getStatus());
        assertEquals(10.0, border.getSize());
    }

    @Test
    void lerpBetweenEqualSizesIsStaticButStillNotifies() {
        List<String> events = new ArrayList<>();
        border.addListener(new Recorder(events));
        border.lerpSizeBetween(50.0, 50.0, 5_000L);
        assertEquals(WorldBorder.Status.STATIONARY, border.getStatus());
        assertEquals(0L, border.getLerpRemainingTime());
        assertEquals(List.of("lerp 50.0 50.0 5000"), events);
    }

    @Test
    void listenersGetVanillaCalls() {
        List<String> events = new ArrayList<>();
        border.addListener(new Recorder(events));
        border.setSize(300.0);
        border.setCenter(10.5, -3.0);
        border.setWarningTime(3);
        border.setWarningBlocks(9);
        border.setDamagePerBlock(1.5);
        border.setDamageSafeZone(2.0);
        assertEquals(List.of("size 300.0", "center 10.5 -3.0", "warnTime 3", "warnBlocks 9", "damage 1.5", "buffer 2.0"), events);
    }

    @Test
    void boundsAndDistance() {
        border.setSize(100.0);
        border.setCenter(10.0, 20.0);
        assertEquals(-40.0, border.getMinX());
        assertEquals(60.0, border.getMaxX());
        assertEquals(-30.0, border.getMinZ());
        assertEquals(70.0, border.getMaxZ());
        assertTrue(border.isWithinBounds(-40, -30));
        assertFalse(border.isWithinBounds(60, 20), "최대 변은 배타");
        assertTrue(border.isWithinBounds(60.0, 20.0, 1.0));
        assertTrue(border.isWithinBounds(new ChunkPos(0, 0)));
        assertFalse(border.isWithinBounds(new ChunkPos(3, 0)), "블록 48..63 은 최대 x(60) 를 넘는다");
        assertEquals(50.0, border.getDistanceToBorder(10.0, 20.0));
        assertEquals(-5.0, border.getDistanceToBorder(65.0, 20.0));
        // AABB: 최대 모서리에서 1e-5 를 뺀다
        assertTrue(border.isWithinBounds(59.0, 0.0, 60.0, 1.0));
        assertFalse(border.isWithinBounds(59.0, 0.0, 60.1, 1.0));
    }

    @Test
    void borderDamageFollowsVanillaRules() {
        border.setSize(100.0);   // x,z ∈ [-50, 50)
        // 안에서는 0
        assertEquals(0f, border.computePlayerDamage(-0.3, -0.3, 0.3, 0.3, 0, 0));
        // 경계 밖이지만 안전 거리(5) 안쪽: 거리 -3 + 5 = 2 >= 0
        assertEquals(0f, border.computePlayerDamage(52.7, 0, 53.3, 0.6, 53.0, 0.3));
        // 거리 -20 + 5 = -15, 0.2/블록 → floor(3.0) = 3
        assertEquals(3f, border.computePlayerDamage(69.7, 0, 70.3, 0.6, 70.0, 0.3));
        // 최소 1
        assertEquals(1f, border.computePlayerDamage(55.7, 0, 56.3, 0.6, 56.0, 0.3));
        border.setDamagePerBlock(0.0);
        assertEquals(0f, border.computePlayerDamage(69.7, 0, 70.3, 0.6, 70.0, 0.3));
    }

    @Test
    void settingsRoundTripPreservesLerpRemaining() {
        border.setCenter(1.0, 2.0);
        border.setDamagePerBlock(0.5);
        border.setDamageSafeZone(7.0);
        border.setWarningBlocks(11);
        border.setWarningTime(22);
        border.lerpSizeBetween(100.0, 400.0, 60_000L);
        now.addAndGet(15_000L);
        WorldBorder.Settings s = border.createSettings();
        assertEquals(175.0, s.size(), 1e-9);
        assertEquals(45_000L, s.sizeLerpTime());
        assertEquals(400.0, s.sizeLerpTarget());

        NbtCompound nbt = new NbtCompound();
        s.write(nbt);
        assertEquals(1.0, nbt.tags().get("BorderCenterX"));
        assertEquals(175.0, (Double) nbt.tags().get("BorderSize"), 1e-9);
        assertEquals(45_000L, nbt.tags().get("BorderSizeLerpTime"));
        assertEquals(11.0, nbt.tags().get("BorderWarningBlocks"));
        assertEquals(22.0, nbt.tags().get("BorderWarningTime"));
        assertEquals(WorldBorder.Settings.read(nbt, WorldBorder.DEFAULT_SETTINGS), s);

        WorldBorder restored = new WorldBorder(now::get);
        restored.applySettings(WorldBorder.Settings.read(nbt, WorldBorder.DEFAULT_SETTINGS));
        assertEquals(175.0, restored.getSize(), 1e-9);
        assertEquals(45_000L, restored.getLerpRemainingTime());
        assertEquals(400.0, restored.getLerpTarget());
        assertEquals(WorldBorder.Status.GROWING, restored.getStatus());
        assertEquals(11, restored.getWarningBlocks());
        assertEquals(22, restored.getWarningTime());
        assertEquals(0.5, restored.getDamagePerBlock());
        assertEquals(7.0, restored.getDamageSafeZone());
        now.addAndGet(45_000L);
        restored.tick();
        assertEquals(400.0, restored.getSize());
        assertEquals(WorldBorder.Status.STATIONARY, restored.getStatus());
    }

    @Test
    void settingsReadDefaultsClampsAndTruncates() {
        NbtCompound empty = new NbtCompound();
        assertEquals(WorldBorder.DEFAULT_SETTINGS, WorldBorder.Settings.read(empty, WorldBorder.DEFAULT_SETTINGS));
        NbtCompound c = new NbtCompound();
        c.put("BorderCenterX", 5.0E9);
        c.put("BorderCenterZ", -5.0E9);
        c.put("BorderWarningBlocks", 7.9);
        c.put("BorderWarningTime", 3);
        c.put("BorderSize", "garbage");
        WorldBorder.Settings s = WorldBorder.Settings.read(c, WorldBorder.DEFAULT_SETTINGS);
        assertEquals(29999984.0, s.centerX());
        assertEquals(-29999984.0, s.centerZ());
        assertEquals(7, s.warningBlocks());
        assertEquals(3, s.warningTime());
        assertEquals(59999968.0, s.size(), "숫자가 아니면 기본값");
    }

    @Test
    void applySettingsWithoutLerpTimeIsStatic() {
        border.applySettings(new WorldBorder.Settings(0, 0, 0.2, 5.0, 5, 15, 1234.0, 0L, 99999.0));
        assertEquals(1234.0, border.getSize());
        assertEquals(1234.0, border.getLerpTarget(), "보간 시간이 0 이면 저장된 보간 목표는 무시");
    }

    private record Recorder(List<String> events) implements WorldBorder.Listener {
        @Override
        public void onSizeSet(WorldBorder b, double size) { events.add("size " + size); }

        @Override
        public void onSizeLerping(WorldBorder b, double from, double to, long timeMs) { events.add("lerp " + from + " " + to + " " + timeMs); }

        @Override
        public void onCenterSet(WorldBorder b, double x, double z) { events.add("center " + x + " " + z); }

        @Override
        public void onWarningTimeSet(WorldBorder b, int seconds) { events.add("warnTime " + seconds); }

        @Override
        public void onWarningBlocksSet(WorldBorder b, int blocks) { events.add("warnBlocks " + blocks); }

        @Override
        public void onDamagePerBlockSet(WorldBorder b, double damage) { events.add("damage " + damage); }

        @Override
        public void onDamageSafeZoneSet(WorldBorder b, double safeZone) { events.add("buffer " + safeZone); }
    }
}
