// 철퇴(MaceItem)·밀도·관통·돌풍·가시 시험. 기대값은 바닐라 1.21.1 디컴파일을 손으로 계산한 것이다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.entity.damage.CombatRules;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.SetEntityMotion;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaceEnchantTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final java.util.Map<Player, EmbeddedChannel> CHANNELS = new java.util.IdentityHashMap<>();

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static ItemStack item(String name) { return new ItemStack(ItemContext.id("minecraft:item", name), 1, null); }

    private static ItemStack mace(String... enchants) {
        return EnchantTools.update(item("minecraft:mace"), m -> {
            for (int i = 0; i < enchants.length; i += 2) m.put(enchants[i], Integer.parseInt(enchants[i + 1]));
        });
    }

    private static Player player(int id, double x, float yaw, PlayerVitalsTest.TestEnv env) {
        var ch = new ConcurrentEmbeddedChannel();
        var p = new Player(id, UUID.randomUUID(), "P" + id, ch);
        CHANNELS.put(p, ch);
        p.vitals().env(env);
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        return p;
    }

    private static PlayerMelee.World world(ZombieCombatTest.ArenaLevel level, Player... ps) {
        return new PlayerMelee.World() {
            public org.domi.furnace.server.block.ChunkLevel level() { return level; }
            public Iterable<Player> players() { return List.of(ps); }
        };
    }

    private static void ready(Player a, ItemStack weapon) {
        a.inventory().set(a.inventory().heldWindowSlot(), weapon);
        a.tickAttackStrength();
        a.attackStrengthTicker = 200;
    }

    @Test
    void baseBonusFollowsThreePieceFormula() {
        assertEquals(8.0F, MaceSmash.baseBonus(2.0F), 1e-6, "≤3: 4 × 2");
        assertEquals(12.0F, MaceSmash.baseBonus(3.0F), 1e-6);
        assertEquals(16.0F, MaceSmash.baseBonus(5.0F), 1e-6, "12 + 2 × (5 - 3)");
        assertEquals(22.0F, MaceSmash.baseBonus(8.0F), 1e-6);
        assertEquals(24.0F, MaceSmash.baseBonus(10.0F), 1e-6, "22 + 10 - 8");
    }

    @Test
    void densityAddsHalfPerBlockPerLevel() {
        var a = player(1, 0.5, -90, new PlayerVitalsTest.TestEnv());
        a.vitals().setFallDistance(5.0F);
        assertEquals(16.0F, MaceSmash.attackDamageBonus(a, mace()), 1e-5);
        assertEquals(16.0F + 1.5F * 5.0F, MaceSmash.attackDamageBonus(a, mace("minecraft:density", "3")), 1e-5, "밀도 III = 1.5/칸");
        a.vitals().setFallDistance(1.5F);
        assertEquals(0.0F, MaceSmash.attackDamageBonus(a, mace("minecraft:density", "3")), "낙하 1.5 이하는 스매시가 아니다");
        a.vitals().setFallDistance(9.0F);
        a.setFallFlying(true);
        assertEquals(0.0F, MaceSmash.attackDamageBonus(a, mace()), "겉날개 비행 중이면 스매시 아님");
        a.setFallFlying(false);
        a.vitals().setFallDistance(5.0F);
        assertEquals(0.0F, MaceSmash.attackDamageBonus(a, item("minecraft:iron_sword")), "철퇴가 아니면 0");
    }

    @Test
    void smashAttackDealsFallBonusAndSetsImpulseContext() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        ready(a, mace());
        a.vitals().setFallDistance(2.0F);
        var r = PlayerMelee.attack(world(level, a, v), a, v);
        assertTrue(r.hurt());
        assertEquals(1, r.weaponDamage(), "철퇴 내구 1");
        assertEquals(20.0F - (6.0F + 8.0F), v.vitals().health(), 1e-4, "공격력 1 + 5, 낙하 2 → +8");
        assertEquals(0.0F, a.vitals().fallDistance(), "postHurtEnemy 가 낙하 거리를 지운다");
        assertTrue(a.vitals().isIgnoringFallDamageFromCurrentImpulse());
        assertArrayEquals(new double[] {0.5, -60, 0.5}, a.vitals().currentImpulsePos(), 1e-9);
    }

    @Test
    void noSmashBelowThreshold() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        ready(a, mace("minecraft:density", "5"));
        a.vitals().setFallDistance(1.5F);
        PlayerMelee.attack(world(level, a, v), a, v);
        assertEquals(14.0F, v.vitals().health(), 1e-4, "스매시 없음: 6 피해");
        assertFalse(a.vitals().isIgnoringFallDamageFromCurrentImpulse());
        assertEquals(1.5F, a.vitals().fallDistance(), "철퇴 postHurtEnemy 도 스매시 조건일 때만 지운다");
    }

    @Test
    void densityStacksOnFallBonus() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        ready(a, mace("minecraft:density", "3"));
        a.vitals().setFallDistance(5.0F);
        assertTrue(PlayerMelee.attack(world(level, a, v), a, v).hurt());
        assertTrue(v.vitals().health() <= 0.0F, "6 + 16 + 1.5 × 5 = 29.5 ≥ 20");
        // 밀도 없이 같은 낙하면 22 로도 죽는다 — 정확한 합은 attackDamageBonus 시험이 본다
    }

    @Test
    void shockwavePushesBystanderAwayFromTarget() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        var o = player(3, 3.5, 90, env);   // 대상에서 +x 로 2.0
        ready(a, mace());
        a.vitals().setFallDistance(2.0F);
        drain(o);
        PlayerMelee.attack(world(level, a, v, o), a, v);
        int[] m = lastMotion(o);
        assertNotNull(m, "충격파 속도 패킷");
        double power = (3.5 - 2.0) * (double) 0.7F;
        assertEquals((short) SetEntityMotion.encode(power), m[0]);
        assertEquals((short) SetEntityMotion.encode((double) 0.7F), m[1]);
        assertEquals(0, m[2]);
    }

    @Test
    void shockwaveDoublesWhenFallIsAboveFive() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        var o = player(3, 3.5, 90, env);
        ready(a, mace());
        a.vitals().setFallDistance(6.0F);
        drain(o);
        PlayerMelee.attack(world(level, a, v, o), a, v);
        int[] m = lastMotion(o);
        assertNotNull(m);
        double power = (3.5 - 2.0) * (double) 0.7F * 2;
        assertEquals((short) SetEntityMotion.encode(power), m[0]);
    }

    @Test
    void breachReducesArmorEffectiveness() {
        // 방어력 8, 강인함 2, 피해 6: g = clamp(8 - 6/2.5, 1.6, 20) = 5.6, h = 5.6/25
        float h = 5.6F / 25.0F;
        assertEquals(6.0F * (1.0F - h), CombatRules.getDamageAfterAbsorb(6.0F, 8.0F, 2.0F, null), 1e-5);
        assertEquals(6.0F * (1.0F - h), CombatRules.getDamageAfterAbsorb(6.0F, 8.0F, 2.0F, mace()), 1e-5);
        assertEquals(6.0F * (1.0F - (h - 0.15F)), CombatRules.getDamageAfterAbsorb(6.0F, 8.0F, 2.0F, mace("minecraft:breach", "1")), 1e-5, "관통 I = -0.15");
        assertEquals(6.0F, CombatRules.getDamageAfterAbsorb(6.0F, 8.0F, 2.0F, mace("minecraft:breach", "3")), 1e-6, "0.224 - 0.45 < 0 → 0 으로 자른다");
        assertEquals(6.0F, CombatRules.getDamageAfterAbsorb(6.0F, 8.0F, 2.0F, mace("minecraft:breach", "4")), 1e-6, "관통 IV 는 방어력 효과를 0 으로 자른다");
    }

    @Test
    void breachAppliesThroughMeleeAgainstArmoredPlayer() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        v.inventory().set(6, item("minecraft:diamond_chestplate"));   // 방어력 8, 강인함 2
        ready(a, mace("minecraft:breach", "4"));
        PlayerMelee.attack(world(level, a, v), a, v);
        assertEquals(14.0F, v.vitals().health(), 1e-4, "관통 IV: 6 피해가 그대로");
    }

    @Test
    void fallDamageIsIgnoredBelowImpulseHeight() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(1, 0.5, 0, env);
        var v = p.vitals();
        // 충격 높이가 2칸 위: 거리는 min(10, 2) → 안전 거리 3 아래라 피해 0
        v.setFallDistance(10.0F);
        v.setCurrentImpulsePos(new double[] {0.5, -58, 0.5});
        v.setIgnoreFallDamageFromCurrentImpulse(true);
        v.currentMoveY(-60);
        v.onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
        assertEquals(20.0F, v.health());
        // 충격 높이가 착지 높이보다 아래: 면제
        v.setFallDistance(10.0F);
        v.setCurrentImpulsePos(new double[] {0.5, -70, 0.5});
        v.setIgnoreFallDamageFromCurrentImpulse(true);
        v.onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
        assertEquals(20.0F, v.health());
        // 충격 높이가 10칸 위: 평소처럼 ceil(10 - 3) = 7, 피해를 받으면 맥락 초기화
        v.setFallDistance(10.0F);
        v.setCurrentImpulsePos(new double[] {0.5, -50, 0.5});
        v.setIgnoreFallDamageFromCurrentImpulse(true);
        v.onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
        assertEquals(13.0F, v.health());
        assertFalse(v.isIgnoringFallDamageFromCurrentImpulse());
        assertNull(v.currentImpulsePos());
    }

    @Test
    void expiredGraceStillExemptsLanding_andAirExpiryKeepsContext() {
        var p = player(1, 0.5, 0, new PlayerVitalsTest.TestEnv());
        var v = p.vitals();
        v.setFallDistance(10.0F);
        v.setCurrentImpulsePos(new double[] {0.5, -58, 0.5});
        v.setIgnoreFallDamageFromCurrentImpulse(true);
        for (int i = 0; i < 45; i++) v.tick(DRY);
        v.currentMoveY(-59);
        v.onMove(0, -1, 0, true, false, false, false, false, false, null, "minecraft:air", false);   // 공중 이동
        assertTrue(v.isIgnoringFallDamageFromCurrentImpulse(), "공중에서 유예가 끝나도 지우지 않는다");
        assertNotNull(v.currentImpulsePos());
        v.currentMoveY(-60);
        v.onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
        assertEquals(20.0F, v.health(), "유예 0 에서 착지해도 min(거리, top-y)=2 → 피해 없음");
    }

    @Test
    void impulseContextExpiresAfterGraceTicks() {
        var p = player(1, 0.5, 0, new PlayerVitalsTest.TestEnv());
        var v = p.vitals();
        v.setCurrentImpulsePos(new double[] {0, 0, 0});
        v.setIgnoreFallDamageFromCurrentImpulse(true);
        for (int i = 0; i < 39; i++) v.tick(DRY);
        v.tryResetCurrentImpulseContext();
        assertTrue(v.isIgnoringFallDamageFromCurrentImpulse(), "유예 40틱 안");
        v.tick(DRY);
        v.tryResetCurrentImpulseContext();
        assertFalse(v.isIgnoringFallDamageFromCurrentImpulse());
        assertNull(v.currentImpulsePos());
    }

    @Test
    void windBurstExplodesAtAttackerAndResetsImpulse() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        level.player = a;
        ready(a, mace("minecraft:wind_burst", "2"));
        a.vitals().setFallDistance(4.0F);
        PlayerMelee.attack(world(level, a, v), a, v);
        // 스매시가 맥락을 켠 뒤 돌풍 폭발(원인 없음)이 맞은 자리로 덮어쓰고 면제를 끈다
        assertFalse(a.vitals().isIgnoringFallDamageFromCurrentImpulse());
        assertArrayEquals(new double[] {0.5, -60, 0.5}, a.vitals().currentImpulsePos(), 1e-9);
    }

    @Test
    void windBurstNeedsFallOfOneAndAHalf() {
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, new PlayerVitalsTest.TestEnv());
        level.player = a;
        a.vitals().setFallDistance(1.0F);
        EnchantEffects.windBurst(level, a, mace("minecraft:wind_burst", "1"));
        assertNull(a.vitals().currentImpulsePos(), "낙하 1.5 미만이면 폭발 없음");
        a.vitals().setFallDistance(1.5F);
        EnchantEffects.windBurst(level, a, mace("minecraft:wind_burst", "1"));
        assertNotNull(a.vitals().currentImpulsePos());
    }

    // ------------------------------------------------------------------ 가시

    private static long seedWithFirstFloor(boolean below, float bound) {
        for (long s = 1; ; s++) {
            if ((new LegacyRandomSource(s).nextFloat() < bound) == below) return s;
        }
    }

    @Test
    void thornsReflectsRandomBetweenOneAndFiveAndCostsTwoDurability() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        v.inventory().set(6, EnchantTools.update(item("minecraft:iron_chestplate"), m -> m.put("minecraft:thorns", 3)));
        long levelSeed = seedWithFirstFloor(true, 0.45F), attackerSeed = 12345L;
        ((LegacyRandomSource) level.random()).setSeed(levelSeed);
        ((LegacyRandomSource) a.random()).setSeed(attackerSeed);
        ready(a, item("minecraft:diamond_sword"));
        PlayerMelee.attack(world(level, a, v), a, v);
        var refLevel = new LegacyRandomSource(levelSeed);
        refLevel.nextFloat();
        float amount = new LegacyRandomSource(attackerSeed).nextFloat() * 4.0F + 1.0F;
        assertEquals(20.0F - amount, a.vitals().health(), 1e-4, "때린 쪽이 randomBetween(1, 5) 피해");
        assertEquals(1 + 2, ItemTools.damage(v.inventory().get(6)), "맞아서 방어구 내구 1(hurtArmor) + 가시 2");
        assertEquals(refLevel.nextFloat(), level.random().nextFloat(), "레벨 난수는 확률 굴림에 한 번만 쓴다");
    }

    @Test
    void thornsMissLeavesRandomAndDurabilityUntouched() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        v.inventory().set(6, EnchantTools.update(item("minecraft:iron_chestplate"), m -> m.put("minecraft:thorns", 3)));
        long levelSeed = seedWithFirstFloor(false, 0.45F);
        ((LegacyRandomSource) level.random()).setSeed(levelSeed);
        ((LegacyRandomSource) a.random()).setSeed(777L);
        ready(a, item("minecraft:diamond_sword"));
        PlayerMelee.attack(world(level, a, v), a, v);
        assertEquals(20.0F, a.vitals().health());
        assertEquals(1, ItemTools.damage(v.inventory().get(6)), "hurtArmor 1 만");
        assertEquals(new LegacyRandomSource(777L).nextFloat(), a.random().nextFloat(), "때린 쪽 난수는 건드리지 않는다");
    }

    @Test
    void thornsChanceUsesPerLevelFloatSum() {
        // 레벨 1: 0.15F. 첫 난수가 0.15F 보다 작으면 반사
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        v.inventory().set(6, EnchantTools.update(item("minecraft:iron_chestplate"), m -> m.put("minecraft:thorns", 1)));
        long s = seedWithFirstFloor(true, 0.15F);
        ((LegacyRandomSource) level.random()).setSeed(s);
        ready(a, item("minecraft:diamond_sword"));
        PlayerMelee.attack(world(level, a, v), a, v);
        assertTrue(a.vitals().health() < 20.0F);
        assertEquals(3, ItemTools.damage(v.inventory().get(6)));
    }

    @Test
    void thornsBreaksLowDurabilityArmor() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        var worn = ItemTools.withDamage(EnchantTools.update(item("minecraft:iron_chestplate"), m -> m.put("minecraft:thorns", 3)), ItemTools.maxDamage(item("minecraft:iron_chestplate")) - 1);
        v.inventory().set(6, worn);
        ((LegacyRandomSource) level.random()).setSeed(seedWithFirstFloor(true, 0.45F));
        ready(a, item("minecraft:diamond_sword"));
        PlayerMelee.attack(world(level, a, v), a, v);
        assertTrue(v.inventory().get(6).isEmpty(), "남은 내구 1 에서 2 를 깎으면 부서진다");
    }

    private static void drain(Player p) { lastMotion(p); }

    private static int[] lastMotion(Player p) {
        var ch = CHANNELS.get(p);
        ch.runPendingTasks();
        int[] r = null;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            io.netty.buffer.ByteBuf bb = (io.netty.buffer.ByteBuf) o;
            if (org.domi.furnace.server.net.io.Types.readVarInt(bb) == SetEntityMotion.ID) {
                org.domi.furnace.server.net.io.Types.readVarInt(bb);
                r = new int[] {bb.readShort(), bb.readShort(), bb.readShort()};
            }
            bb.release();
        }
        return r;
    }
}
