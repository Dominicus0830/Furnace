// 겉날개 비행: 시작 조건, 틱별 속도(바닐라 LivingEntity.travel 식), 내구도 간격, 로켓 가속, 충돌 피해, 자세 패킷.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElytraFlightTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static ItemStack item(String n) { return new ItemStack(ItemContext.id("minecraft:item", n), 1); }

    private final PlayerVitalsTest.TestEnv env = new PlayerVitalsTest.TestEnv();

    private Player player(boolean elytra) {
        var p = new Player(11, UUID.randomUUID(), "E", new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        if (elytra) p.inventory().set(ElytraFlight.CHEST_SLOT, item("minecraft:elytra"));
        p.applyMove(0.5, -50.0, 0.5, 0.0F, 0.0F, false);
        return p;
    }

    @Test
    void startConditions() {
        assertFalse(ElytraFlight.tryToStartFallFlying(player(false)), "겉날개 없음");
        var ground = player(true);
        ground.applyMove(0.5, -60.0, 0.5, 0, 0, true);
        assertFalse(ElytraFlight.tryToStartFallFlying(ground), "땅 위");
        var worn = player(true);
        worn.inventory().set(ElytraFlight.CHEST_SLOT, ItemTools.withDamage(item("minecraft:elytra"), 431));
        assertFalse(ElytraFlight.tryToStartFallFlying(worn), "내구도 1 남음(isFlyEnabled 거짓)");
        var water = player(true);
        water.wasTouchingWater = true;
        assertFalse(ElytraFlight.tryToStartFallFlying(water), "물속");
        var ok = player(true);
        ok.inventory().set(ElytraFlight.CHEST_SLOT, ItemTools.withDamage(item("minecraft:elytra"), 430));
        assertTrue(ElytraFlight.tryToStartFallFlying(ok));
        assertTrue(ok.isFallFlying());
        assertFalse(ElytraFlight.tryToStartFallFlying(ok), "이미 비행 중");
    }

    @Test
    void levitationBlocksStart() {
        var p = player(true);
        p.vitals().addEffect(new MobEffectInstance(MobEffect.LEVITATION, 100, 0));
        assertFalse(ElytraFlight.tryToStartFallFlying(p));
    }

    /** 기대값은 디컴파일 식을 float 반올림까지 따로 옮긴 계산(파이썬)에서 얻었다. */
    @Test
    void glideTickValues() {
        double[][] exp0 = {{0.0, -0.017640000343322755, 0.0017820000171661376}, {0.0, -0.03319848094894409, 0.005117904096885681},
                {0.0, -0.046921061110181135, 0.009806709802936502}};
        check(0, 0, new double[3], exp0);
        double[][] exp1 = {{0.14658077945316234, -0.12237750323505726, 0.29891922483837213}, {0.09986111511650275, -0.1426627103401145, 0.2970793925310117},
                {0.05864115550167051, -0.161051250963825, 0.29503284021923254}};
        check(30, 45, new double[] {0.2, -0.1, 0.3}, exp1);
        double[][] exp2 = {{-0.9725858913244683, 0.22472933322918606, 0.08910000085830688}, {-0.9458601515117607, 0.2471873894908457, 0.07938810152950287},
                {-0.9198172528526368, 0.26747329010929893, 0.07073479914418061}};
        check(-30, 90, new double[] {-1.0, 0.2, 0.1}, exp2);
    }

    private static void check(float xRot, float yRot, double[] v, double[][] exp) {
        double[] l = org.domi.furnace.server.block.Clip.viewVector(xRot, yRot);
        for (double[] e : exp) {
            v = ElytraFlight.glide(v[0], v[1], v[2], l[0], l[1], l[2], xRot, ElytraFlight.GRAVITY);
            assertArrayEquals(e, v, 1.0E-12);
        }
    }

    @Test
    void durabilityEveryTwentyTicksAndLandingStops() {
        var p = player(true);
        assertTrue(ElytraFlight.tryToStartFallFlying(p));
        for (int i = 0; i < 19; i++) ElytraFlight.tick(p, null);
        assertEquals(0, ItemTools.damage(p.inventory().get(ElytraFlight.CHEST_SLOT)));
        ElytraFlight.tick(p, null);
        assertEquals(1, ItemTools.damage(p.inventory().get(ElytraFlight.CHEST_SLOT)), "20 틱째");
        for (int i = 0; i < 20; i++) ElytraFlight.tick(p, null);
        assertEquals(2, ItemTools.damage(p.inventory().get(ElytraFlight.CHEST_SLOT)), "40 틱째");
        assertEquals(40, p.fallFlyTicks);
        p.applyMove(0.5, -60.0, 0.5, 0, 0, true);
        ElytraFlight.tick(p, null);
        assertFalse(p.isFallFlying(), "착지하면 해제");
        assertEquals(0, p.fallFlyTicks);
    }

    @Test
    void creativeDoesNotWearElytra() {
        var p = player(true);
        p.vitals().loadGameTypes(null, GameMode.CREATIVE, false);
        ElytraFlight.tryToStartFallFlying(p);
        for (int i = 0; i < 40; i++) ElytraFlight.tick(p, null);
        assertEquals(0, ItemTools.damage(p.inventory().get(ElytraFlight.CHEST_SLOT)));
    }

    @Test
    void crashIntoWallDealsKineticDamage() {
        var level = new BlockPlacementTest.TestLevel();
        for (int y = -52; y <= -47; y++) for (int x = -2; x <= 2; x++) level.setBlock(new Pos(x, y, 10), B.defaultState(B.id("minecraft:stone")), 3);
        var p = player(true);
        p.applyMove(0.5, -50.0, 9.5, 0.0F, 0.0F, false);
        assertTrue(ElytraFlight.tryToStartFallFlying(p));
        p.flyVx = 0.0; p.flyVy = 0.0; p.flyVz = 1.5;
        for (int i = 0; i < 100; i++) p.vitals().tick(PlayerVitals.Surroundings.NONE);   // 스폰 무적 시간 지나기
        float hp = p.vitals().health();
        ElytraFlight.travel(p, level);
        assertEquals(0.0, p.flyVz, "벽에 막힌 축은 0");
        // (1.5 − 0) × 10 − 3 = 12
        assertEquals(hp - 12.0F, p.vitals().health(), 1.0E-4F);
        assertEquals(12.0F, ElytraFlight.crashDamage(1.5, 0.0));
        assertTrue(ElytraFlight.crashDamage(0.3, 0.0) <= 0.0F, "느리면 피해 없음");
    }

    @Test
    void rocketBoostsAttachedPlayer() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(true);
        p.applyMove(0.5, -40.0, 0.5, 0.0F, 0.0F, false);
        assertTrue(ElytraFlight.tryToStartFallFlying(p));
        p.flyVx = 0.1; p.flyVy = -0.2; p.flyVz = 0.5;
        p.currentLevel = level;
        var rocket = new FireworkRocketEntity(level, level.nextEntityId(), item("minecraft:firework_rocket"), p);
        level.addFreshEntity(rocket);
        assertSame(p, rocket.attachedTo());
        rocket.tick();
        double[] l = org.domi.furnace.server.block.Clip.viewVector(0.0F, 0.0F);
        double[] e = FireworkRocketEntity.boost(0.1, -0.2, 0.5, l[0], l[1], l[2]);
        // 0.1 + (0·0.1 + (0 − 0.1)·0.5) = 0.05, z: 0.5 + (0.1 + (1.5 − 0.5)·0.5) = 1.1
        assertEquals(0.05, e[0], 1e-12);
        assertEquals(1.1, e[2], 1e-12);
        assertArrayEquals(e, new double[] {p.flyVx, p.flyVy, p.flyVz}, 1e-12);
        assertEquals(p.flyVz, rocket.vz(), 1e-12, "로켓 속도 = 플레이어 속도");
        // 오른손: yaw + 80° 시선의 절반만큼 옆
        double[] h = org.domi.furnace.server.block.Clip.viewVector(0.0F, 80.0F);
        assertEquals(0.5 + h[0] * 0.5, rocket.x(), 1e-9);
    }

    private static byte[] bytes(SetEntityData d) {
        ByteBuf buf = Unpooled.buffer();
        d.write(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    @Test
    void poseAndFlagBroadcast() {
        var p = player(true);
        p.syncLivingFlags();
        int before = env.trackerPackets.size();
        assertTrue(ElytraFlight.tryToStartFallFlying(p));
        assertEquals(1, p.pose());
        assertEquals(0.6F, p.bbHeight());
        assertEquals(p.y() + 0.4, p.eyeY(), 1e-9);
        p.syncLivingFlags();
        var d = (SetEntityData) env.trackerPackets.get(before);
        // 엔티티 11, [0, byte, 0x80], [6, pose(21), 1 FALL_FLYING], 끝
        assertArrayEquals(new byte[] {11, 0, 0, (byte) 0x80, 6, 21, 1, (byte) 0xFF}, bytes(d));
        // 실패한 시작 명령: stopFallFlying 은 같은 값이어도 플래그를 다시 보낸다
        p.applyMove(0.5, -60.0, 0.5, 0, 0, true);
        ElytraFlight.tick(p, null);
        p.syncLivingFlags();
        int n = env.trackerPackets.size();
        ElytraFlight.handleStartCommand(p);
        p.syncLivingFlags();
        assertEquals(n + 1, env.trackerPackets.size());
        assertArrayEquals(new byte[] {11, 0, 0, 0, (byte) 0xFF}, bytes((SetEntityData) env.trackerPackets.get(n)));
    }

    @Test
    void creativeFlightAndRemovedElytraStop() {
        var p = player(true);
        assertTrue(ElytraFlight.tryToStartFallFlying(p));
        p.vitals().loadGameTypes(null, GameMode.CREATIVE, false);
        p.vitals().setFlying(true);   // Player.travel: abilities.flying 이면 travel 뒤 플래그 7 끔
        ElytraFlight.tick(p, null);
        assertFalse(p.isFallFlying());
        assertEquals(0, p.fallFlyTicks);
        var q = player(true);
        assertTrue(ElytraFlight.tryToStartFallFlying(q));
        ElytraFlight.tick(q, null);
        q.inventory().set(ElytraFlight.CHEST_SLOT, ItemStack.EMPTY);
        ElytraFlight.tick(q, null);
        assertFalse(q.isFallFlying(), "겉날개를 벗으면 해제");
    }

    @Test
    void unbreakingConsumesOneLevelRandomFloatPerWear() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(false);
        p.applyMove(0.5, -20.0, 0.5, 0.0F, 0.0F, false);
        p.inventory().set(ElytraFlight.CHEST_SLOT,
                org.domi.furnace.server.item.EnchantTools.update(item("minecraft:elytra"), m -> m.put("minecraft:unbreaking", 3)));
        assertTrue(ElytraFlight.tryToStartFallFlying(p));
        ((org.domi.furnace.server.block.LegacyRandomSource) level.random()).setSeed(1234L);
        var ref = org.domi.furnace.server.block.RandomSource.create(1234L);
        int expected = 0;
        for (int k = 0; k < 10; k++) if (!(ref.nextFloat() < 3.0F / 4.0F)) expected++;   // 비갑옷: L/(L+1)
        for (int t = 0; t < 200; t++) {
            p.applyMove(0.5, -20.0, 0.5, 0.0F, 0.0F, false);
            ElytraFlight.tick(p, level);
        }
        assertTrue(p.isFallFlying());
        assertEquals(expected, ItemTools.damage(p.inventory().get(ElytraFlight.CHEST_SLOT)));
    }
}
