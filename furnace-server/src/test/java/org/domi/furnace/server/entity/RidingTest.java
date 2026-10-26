// 탑승 기반: 승하차 상태 전이, 승객 위치(부착점), 탈것 틱의 승객 갱신, 탈것 제거 시 내리기, Passengers 저장 왕복,
// set_passengers·move_vehicle 패킷, 플레이어 승객(회전만 받기·웅크려 내리기·조종 탈것 이동 검증), 닭 기수 좀비.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.SetPassengers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RidingTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Chicken chicken(BlockPlacementTest.TestLevel level) {
        Chicken c = new Chicken(level, level.nextEntityId());
        c.moveTo(0.5, -60, 0.5, 0, 0);
        return c;
    }

    private static Zombie zombie(BlockPlacementTest.TestLevel level) {
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(3.5, -60, 3.5, 0, 0);
        return z;
    }

    @Test
    void mountAndDismountTransitions() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = chicken(level);
        Zombie z = zombie(level);
        assertFalse(z.isPassenger());
        assertSame(List.of(), c.passengers(), "승객 없음은 공유 빈 목록");
        assertTrue(z.startRiding(c));
        assertSame(c, z.vehicle());
        assertEquals(List.of(z), c.passengers());
        assertTrue(c.isVehicle());
        assertFalse(z.startRiding(c), "같은 탈것에 다시 타지 않는다");
        assertFalse(c.startRiding(z), "자기 승객에 타지 않는다(고리)");
        Zombie z2 = zombie(level);
        assertFalse(z2.startRiding(c), "닭은 승객 하나만");
        assertTrue(z2.startRiding(c, true), "강제면 탄다");
        assertEquals(List.of(z, z2), c.passengers());
        z2.stopRiding();
        assertEquals(60, z2.boardingCooldown(), "내리면 60 틱 기다린다");
        assertFalse(z2.canRide(c));
        z.stopRiding();
        assertNull(z.vehicle());
        assertTrue(c.passengers().isEmpty());
        // 내릴 자리: 탈것 몸통 꼭대기 가운데
        assertEquals(0.5, z.x(), 1e-9);
        assertEquals(-60 + 0.7, z.y(), 1e-6);
        assertEquals(0.5, z.z(), 1e-9);
    }

    @Test
    void passengerPositionUsesAttachments() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = chicken(level);
        Zombie z = zombie(level);
        z.startRiding(c);
        c.positionRider(z);
        // 닭 승객점 (0, 0.7, -0.1) − 좀비 탈것점 (0, 0.7, 0)
        assertEquals(0.5, z.x(), 1e-6);
        assertEquals(-60.0, z.y(), 1e-6);
        assertEquals(0.4, z.z(), 1e-6);
        // 아기 좀비: 탈것점 0.5 배, 닭이 90 도 돌면 승객점도 돈다(x = +0.1)
        Zombie baby = zombie(level);
        baby.setBaby(true);
        z.stopRiding();
        assertTrue(baby.startRiding(c, true));
        c.setYRot(90.0F);
        c.positionRider(baby);
        assertEquals(0.6, baby.x(), 1e-6);
        assertEquals(-60 + 0.7 - 0.35, baby.y(), 1e-6);
        assertEquals(0.5, baby.z(), 1e-6);
    }

    @Test
    void vehicleTickMovesPassengerAndRemovalDismounts() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = chicken(level);
        Zombie z = zombie(level);
        level.addFreshEntity(c);
        level.addFreshEntity(z);
        z.startRiding(c);
        int zombieTicks = z.tickCount;
        level.tickEntities(k -> true);
        assertEquals(zombieTicks + 1, z.tickCount, "승객은 탈것이 한 번만 틱한다");
        assertEquals(c.y(), z.y(), 1e-6);
        assertEquals(c.z() - 0.1, z.z(), 1e-6);
        c.discard();
        assertNull(z.vehicle(), "탈것이 사라지면 내린다");
        assertTrue(z.isAlive());
    }

    @Test
    void passengersSaveRoundTrip() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = chicken(level);
        Zombie z = zombie(level);
        z.startRiding(c);
        assertNull(z.save(), "탄 것은 탈것이 저장한다");
        var tag = c.save();
        var list = tag.getList("Passengers");
        assertNotNull(list);
        assertEquals(1, list.size());
        var zt = (org.domi.furnace.server.net.io.nbt.NbtCompound) list.get(0);
        assertEquals("minecraft:zombie", zt.getString("id"));
        var pos = zt.getList("Pos");
        assertEquals(c.x(), ((Number) pos.get(0)).doubleValue(), 1e-9, "승객의 Pos x·z 는 탈것 것");
        var level2 = new BlockPlacementTest.TestLevel();
        Entity loaded = Entities.load(level2, tag);
        assertInstanceOf(Chicken.class, loaded);
        assertEquals(1, loaded.passengers().size());
        assertInstanceOf(Zombie.class, loaded.passengers().get(0));
        assertTrue(level2.addFreshEntityWithPassengers(loaded));
        assertEquals(2, level2.entities().size());
    }

    @Test
    void setPassengersPacketFormat() {
        ByteBuf b = Unpooled.buffer();
        new SetPassengers(300, new int[] {5, 129}).write(b);
        assertEquals(300, Types.readVarInt(b));
        assertEquals(2, Types.readVarInt(b));
        assertEquals(5, Types.readVarInt(b));
        assertEquals(129, Types.readVarInt(b));
        assertEquals(0, b.readableBytes());
        assertEquals(0x5F, SetPassengers.ID);
        ByteBuf m = Unpooled.buffer();
        new org.domi.furnace.server.net.protocol.play.MoveVehicle(1.5, 2.5, 3.5, 90.0F, -10.0F).write(m);
        assertEquals(1.5, m.readDouble());
        assertEquals(2.5, m.readDouble());
        assertEquals(3.5, m.readDouble());
        assertEquals(90.0F, m.readFloat());
        assertEquals(-10.0F, m.readFloat());
        assertEquals(0x31, org.domi.furnace.server.net.protocol.play.MoveVehicle.ID);
    }

    /** 채널에 쌓인 패킷 (id, 내용) 들. */
    private static List<ByteBuf> drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<ByteBuf> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) out.add((ByteBuf) o);
        return out;
    }

    private static int[] passengersPacket(List<ByteBuf> packets, int vehicle) {
        for (ByteBuf b : packets) {
            b.markReaderIndex();
            if (Types.readVarInt(b) == SetPassengers.ID && Types.readVarInt(b) == vehicle) {
                int n = Types.readVarInt(b);
                int[] ids = new int[n];
                for (int i = 0; i < n; i++) ids[i] = Types.readVarInt(b);
                return ids;
            }
            b.resetReaderIndex();
        }
        return null;
    }

    @Test
    void trackerSendsPassengersOnPairingAndChange() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = chicken(level);
        Zombie z = zombie(level);
        z.startRiding(c);
        var ch = new EmbeddedChannel();
        Player viewer = new Player(900, UUID.randomUUID(), "viewer", ch);
        viewer.applyMove(0.5, -60, 5.5, 0, 0, true);
        var tracker = new EntityTracker(c, 160);
        EntityTracker.Viewers v = new EntityTracker.Viewers() {
            @Override public int viewDistance(Player p) { return 10; }
            @Override public boolean chunkTracked(Player p, int cx, int cz) { return true; }
        };
        tracker.updatePlayer(viewer, v);
        assertArrayEquals(new int[] {z.id()}, passengersPacket(drain(ch), c.id()), "보기 시작할 때 승객 목록");
        tracker.sendChanges();   // 추적기는 빈 목록에서 시작한다(바닐라 ServerEntity) — 첫 갱신에서 한 번 더 보낸다
        assertArrayEquals(new int[] {z.id()}, passengersPacket(drain(ch), c.id()));
        tracker.sendChanges();
        assertNull(passengersPacket(drain(ch), c.id()), "바뀌지 않으면 다시 보내지 않는다");
        z.stopRiding();
        tracker.sendChanges();
        assertArrayEquals(new int[0], passengersPacket(drain(ch), c.id()), "내리면 빈 목록");
    }

    /** 승객 둘을 받는 시험용 닭: 플레이어가 첫 승객이면 그가 조종한다. */
    static final class TwoSeatChicken extends Chicken {
        TwoSeatChicken(BlockPlacementTest.TestLevel level) { super(level, level.nextEntityId()); }

        @Override protected boolean canAddPassenger(Rider r) { return passengers().size() < 2; }

        @Override public Rider getControllingPassenger() { return firstPassenger() instanceof Player p ? p : null; }
    }

    @Test
    void playerPassengerStateTransitions() {
        var level = new BlockPlacementTest.TestLevel();
        var c = new TwoSeatChicken(level);
        c.moveTo(0.5, -60, 0.5, 0, 0);
        Zombie z = zombie(level);
        assertTrue(z.startRiding(c));
        var ch = new EmbeddedChannel();
        Player p = new Player(901, UUID.randomUUID(), "rider", ch);
        List<double[]> teleports = new ArrayList<>();
        p.teleporter(teleports::add);
        p.applyMove(2.5, -60, 2.5, 0, 0, true);
        assertTrue(p.startRiding(c, false));
        assertEquals(List.of(p, z), c.passengers(), "플레이어는 맨 앞(조종자)");
        assertSame(p, c.getControllingPassenger());
        c.positionRider(p);
        assertEquals(-60 + 0.7 - 0.6, p.y(), 1e-6, "플레이어 탈것점 (0, 0.6, 0)");
        // 탄 동안 발행된 위치는 회전만 반영
        double y = p.y();
        p.publishPosition(100, 100, 100, 45.0F, 10.0F, false);
        p.drainPending();
        assertEquals(y, p.y(), 1e-9);
        assertEquals(45.0F, p.yaw());
        // 입력: 탄 동안만 받는다
        p.setPlayerInput(0.5F, 1.0F, true, false);
        assertEquals(1.0F, p.inputZza());
        assertTrue(p.inputJumping());
        // 웅크리면 rideTick 에서 내리고 웅크림을 푼다
        p.setPlayerInput(0.0F, 0.0F, false, true);
        p.rideTick();
        assertNull(p.vehicle());
        assertFalse(p.sneaking());
        assertEquals(List.of(z), c.passengers());
        assertEquals(1, teleports.size(), "내린 자리로 연결이 순간이동");
        assertEquals(-60 + 0.7, teleports.get(0)[1], 1e-6);
        assertFalse(p.canRide(c), "다시 타기 기다림");
        for (int i = 0; i < 60; i++) p.tickRiding();
        assertTrue(p.canRide(c));
        p.setPlayerInput(1.0F, 1.0F, false, false);
        assertEquals(0.0F, p.inputZza(), "안 탄 동안은 입력을 받지 않는다");
    }

    @Test
    void moveVehicleAcceptsAndRejects() {
        var level = new BlockPlacementTest.TestLevel();
        var c = new TwoSeatChicken(level);
        c.moveTo(0.5, -60, 0.5, 0, 0);
        var ch = new EmbeddedChannel();
        Player p = new Player(902, UUID.randomUUID(), "driver", ch);
        p.teleporter(t -> {});
        assertEquals(VehicleMoves.Result.IGNORED, VehicleMoves.handle(p, 1, -60, 1, 0, 0), "안 탔다");
        p.startRiding(c, false);
        assertEquals(VehicleMoves.Result.IGNORED, VehicleMoves.handle(p, 1, -60, 1, 0, 0), "연결 틱 전(lastVehicle 없음)");
        p.tickRiding();
        assertEquals(VehicleMoves.Result.ACCEPTED, VehicleMoves.handle(p, 1.0, -60, 0.5, 30.0F, 0.0F));
        assertEquals(1.0, c.x(), 1e-9);
        assertEquals(30.0F, c.yRot());
        drain(ch);
        assertEquals(VehicleMoves.Result.REJECTED, VehicleMoves.handle(p, 20.0, -60, 0.5, 0.0F, 0.0F), "틱 시작점에서 10 블록 넘게");
        var sent = drain(ch);
        assertEquals(1, sent.size());
        assertEquals(0x31, Types.readVarInt(sent.get(0)));
        assertEquals(1.0, sent.get(0).readDouble(), 1e-9, "지금 탈것 위치로 되돌린다");
        assertTrue(VehicleMoves.invalid(Double.NaN, 0, 0, 0, 0));
    }

    @Test
    void zombieJockeyRidesNearbyChicken() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = chicken(level);
        level.addFreshEntity(c);
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(1.5, -60, 1.5, 0, 0);
        // 모든 nextFloat 가 0.01: 아기(< 0.05) → 기수(nextBoolean 참) → 근처 닭(< 0.05)
        var r = new org.domi.furnace.server.block.RandomSource() {
            @Override public int nextInt(int bound) { return 0; }
            @Override public int nextInt() { return 0; }
            @Override public long nextLong() { return 0; }
            @Override public boolean nextBoolean() { return true; }
            @Override public float nextFloat() { return 0.01F; }
            @Override public double nextDouble() { return 0.5; }
            @Override public double nextGaussian() { return 0; }
        };
        z.finalizeSpawn(r, new DifficultyInstance(2, 0, 0, 0.0F));
        assertTrue(z.isBaby());
        assertSame(c, z.vehicle());
        assertTrue(c.isChickenJockey());
    }

    @Test
    void skeletonTrapSpawnsFourJockeys() {
        var level = new BlockPlacementTest.TestLevel();
        SkeletonHorse horse = new SkeletonHorse(level, level.nextEntityId());
        horse.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(horse);
        horse.setTrap(true);
        new SkeletonHorse.TrapGoal(horse).tick();
        assertFalse(horse.isTrap());
        assertTrue(horse.isTamed());
        int horses = 0, riders = 0;
        for (Entity e : level.entities().all()) {
            if (e instanceof SkeletonHorse h) {
                horses++;
                if (h.firstPassenger() instanceof Skeleton s) {
                    riders++;
                    assertFalse(s.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "철 투구");
                }
            }
        }
        assertEquals(4, horses);
        assertEquals(4, riders);
    }

    @Test
    void respawnDetachesPlayerWithoutTeleport() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = chicken(level);
        Player p = new Player(903, UUID.randomUUID(), "ghost", new EmbeddedChannel());
        List<double[]> teleports = new ArrayList<>();
        p.teleporter(teleports::add);
        assertTrue(p.startRiding(c, false));
        p.bumpSpawnGeneration();
        p.tickRiding();
        assertNull(p.vehicle());
        assertTrue(c.passengers().isEmpty());
        assertTrue(teleports.isEmpty(), "다시 태어난 자리를 덮어쓰지 않는다");
    }
}
