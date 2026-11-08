// 생존 상태가 바닐라 수치대로 움직이는가: 낙하 피해(블록별 배율·물·위로 이동·비행), 무적 틱, 공허·익사, 굶주림·자연 회복,
// 경험치 계산, /kill, 사망 메시지, NBT 저장·게임 모드 결정.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;

class PlayerVitalsTest {

    /** 테스트 서버 쪽 정보: 난이도·게임룰을 바꿀 수 있고 방송된 메시지를 모은다. */
    static final class TestEnv implements PlayerVitals.Env {
        Difficulty difficulty = Difficulty.EASY;
        java.util.Map<String, Boolean> rules = new java.util.HashMap<>();
        List<Text> broadcasts = new ArrayList<>();

        public Difficulty difficulty() { return difficulty; }
        public boolean gameRule(String name) { return rules.getOrDefault(name, !name.equals("keepInventory") && !name.equals("doImmediateRespawn")); }
        boolean pvp = true;
        public boolean pvp() { return pvp; }
        public void broadcastSystemMessage(Text message) { broadcasts.add(message); }
        public void broadcastAll(Packet packet) {}
        List<Packet> trackerPackets = new ArrayList<>();
        public void broadcastToTrackers(int entityId, Packet packet) { trackerPackets.add(packet); }
        org.domi.furnace.server.plugin.GameEvents events = org.domi.furnace.server.plugin.GameEvents.NONE;
        public org.domi.furnace.server.plugin.GameEvents gameEvents() { return events; }
        /** 플러그인 PlayerDeathEvent 취소 흉내(되살릴 체력). null 이면 취소하지 않는다. */
        Float cancelDeathWith;
        public PlayerVitals.DeathOutcome death(Player player, Text message, boolean keepInventory, List<ItemStack> drops, int droppedExp, float maxHealth) {
            if (cancelDeathWith == null) return PlayerVitals.DeathOutcome.unchanged(message, keepInventory, drops, droppedExp);
            return new PlayerVitals.DeathOutcome(message, keepInventory, keepInventory, drops, droppedExp, 0, 0, 0, List.of(), true, true, cancelDeathWith);
        }
    }

    private final EmbeddedChannel channel = new ConcurrentEmbeddedChannel();
    private final Player player = new Player(7, UUID.randomUUID(), "Steve", channel);
    private final PlayerVitals v = player.vitals();
    private final TestEnv env = new TestEnv();

    PlayerVitalsTest() {
        v.env(env);
        v.loadGameTypes(null, GameMode.SURVIVAL, false);
    }

    /** 위에서 지면까지 수직 낙하(패킷당 1블록) 후 착지. */
    private void fall(double from, double to, String landing, boolean water) {
        double y = from;
        boolean ground = true;
        while (y > to) {
            double ny = Math.max(to, y - 1);
            v.onMove(0, ny - y, 0, ground, false, false, false, false, false, null, "minecraft:air", false);
            ground = false;
            y = ny;
        }
        v.onMove(0, 0, 0, false, true, false, false, water, false, null, landing, false);   // 착지 패킷
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    @Test
    void fallDamageIsCeilOfDistanceMinusThree() {
        fall(110, 100, "minecraft:stone", false);
        assertEquals(13.0f, v.health(), "10블록 낙하 = 7 피해");
    }

    @Test
    void landingBlockChangesMultiplier() {
        fall(110, 100, "minecraft:hay_block", false);
        assertEquals(18.0f, v.health(), "건초 더미 0.2배: ceil(1.4) = 2");
        PlayerVitals bed = new Player(8, UUID.randomUUID(), "A", channel).vitals();
        bed.env(env);
        bed.loadGameTypes(null, GameMode.SURVIVAL, false);
        for (int i = 0; i < 10; i++) bed.onMove(0, -1, 0, i == 0, false, false, false, false, false, null, "minecraft:air", false);
        bed.onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:red_bed", false);
        assertEquals(16.0f, bed.health(), "침대 0.5배: ceil(3.5) = 4");
    }

    @Test
    void waterAndUpwardMovementResetFallDistance() {
        fall(110, 100, "minecraft:water", true);
        assertEquals(20.0f, v.health(), "물에 닿으면 낙하 거리 초기화");
        v.onMove(0, -5, 0, true, false, false, false, false, false, null, "minecraft:air", false);
        v.onMove(0, 0.5, 0, false, false, false, false, false, false, null, "minecraft:air", false);
        assertEquals(0.0f, v.fallDistance(), "위로 움직이면 초기화");
        v.onMove(0, -2.0, 0, false, false, false, false, false, false, null, "minecraft:air", true);
        v.onMove(0, -6.0, 0, false, false, false, false, false, false, null, "minecraft:air", true);
        assertEquals(6.0f, v.fallDistance(), "1블록 이상 이동 경로에 물이 있으면 앞서 쌓인 2를 지우고 이번 이동만 남는다");
    }

    @Test
    void flyingCapableModesTakeNoFallDamage() {
        assertTrue(v.setGameMode(GameMode.CREATIVE));
        fall(150, 100, "minecraft:stone", false);
        assertEquals(20.0f, v.health());
    }

    @Test
    void invulnerabilityFramesOnlyApplyTheExtraDamage() {
        assertTrue(v.hurt(DamageType.FALL, 5));
        assertFalse(v.hurt(DamageType.FALL, 3), "무적 틱 동안 더 약한 피해는 무시");
        assertTrue(v.hurt(DamageType.FALL, 8));
        assertEquals(12.0f, v.health(), "5 + (8-5)");
        for (int i = 0; i < 10; i++) v.tick(DRY);
        assertTrue(v.hurt(DamageType.FALL, 1), "무적 틱 10 이하로 내려가면 다시 맞는다");
    }

    @Test
    void spawnInvulnerabilityBlocksNonFallDamage() {
        assertFalse(v.hurt(DamageType.DROWN, 2), "접속 직후 60틱은 익사 피해 무시");
        for (int i = 0; i < 60; i++) v.tick(DRY);
        assertTrue(v.hurt(DamageType.DROWN, 2));
    }

    @Test
    void fireAndFreezeDamageGameRules() {
        for (int i = 0; i < 60; i++) v.tick(DRY);   // 접속 무적 지나기
        DamageType fire = DamageType.find("on_fire"), freeze = DamageType.find("freeze");
        env.rules.put("fireDamage", false);
        env.rules.put("freezeDamage", false);
        assertFalse(v.hurt(fire, 2), "fireDamage=false 면 불 피해 무시(Player.isInvulnerableTo)");
        assertFalse(v.hurt(freeze, 2), "freezeDamage=false 면 동결 피해 무시");
        assertEquals(20.0f, v.health());
        env.rules.put("fireDamage", true);
        assertTrue(v.hurt(fire, 2));
        for (int i = 0; i < 20; i++) v.tick(DRY);
        env.rules.put("freezeDamage", true);
        assertTrue(v.hurt(freeze, 2));
    }

    @Test
    void voidHurtsFourEveryTenTicks() {
        NbtCompound tag = new NbtCompound();
        tag.putInt("foodLevel", 17);   // 자연 회복이 끼지 않게
        v.load(tag);
        player.applyMove(0, -200, 0, 0, 0, false);
        for (int i = 0; i < 25; i++) v.tick(DRY);
        assertEquals(8.0f, v.health(), "틱 1·11·21 에 4씩");
    }

    @Test
    void drowningAfterAirRunsOut() {
        for (int i = 0; i < 60; i++) v.tick(DRY);   // 스폰 무적 해제
        PlayerVitals.Surroundings underwater = new PlayerVitals.Surroundings(true, true, true, false, null, false, -64);
        for (int i = 0; i < 319; i++) v.tick(underwater);
        assertEquals(20.0f, v.health(), "공기 300 → -19");
        v.tick(underwater);
        assertEquals(18.0f, v.health(), "공기 -20 에서 익사 2");
        assertEquals(0, v.air());
        v.tick(DRY);
        assertEquals(4, v.air(), "물 밖에서 틱당 4씩 회복");
    }

    @Test
    void starvationFollowsDifficulty() {
        NbtCompound tag = new NbtCompound();
        tag.putInt("foodLevel", 0);
        tag.putFloat("foodSaturationLevel", 0f);
        tag.putFloat("Health", 11f);
        v.load(tag);
        env.difficulty = Difficulty.EASY;
        for (int i = 0; i < 80; i++) v.tick(DRY);
        assertEquals(10.0f, v.health(), "쉬움: 체력 10 초과면 굶주림 1");
        for (int i = 0; i < 160; i++) v.tick(DRY);
        assertEquals(10.0f, v.health(), "쉬움: 10 에서 멈춘다");
        env.difficulty = Difficulty.NORMAL;
        for (int i = 0; i < 80; i++) v.tick(DRY);
        assertEquals(9.0f, v.health(), "보통: 1 까지 내려간다");
    }

    @Test
    void saturatedRegenerationHealsEveryTenTicks() {
        NbtCompound tag = new NbtCompound();
        tag.putFloat("Health", 10f);
        v.load(tag);   // 허기 20, 포화 5
        for (int i = 0; i < 10; i++) v.tick(DRY);
        assertEquals(10.0f + 5.0f / 6.0f, v.health(), 1e-5, "포화 5 → 5/6 회복");
        assertEquals(5.0f, v.exhaustion(), 1e-5, "회복한 만큼 소모 5");
    }

    @Test
    void sprintingAndJumpingExhaust() {
        v.onMove(10, 0, 0, true, true, true, false, false, false, null, "minecraft:stone", false);
        assertEquals(1.0f, v.exhaustion(), 1e-5, "달리기 10m = 0.1 * 1000cm * 0.01");
        v.onMove(0, 0.42, 0, true, false, false, false, false, false, null, "minecraft:air", false);
        assertEquals(1.05f, v.exhaustion(), 1e-5, "점프 0.05");
        v.onMove(0, 0.42, 0, true, false, true, false, false, false, null, "minecraft:air", false);
        assertEquals(1.25f, v.exhaustion(), 1e-5, "달리며 점프 0.2");
    }

    @Test
    void experienceMathMatchesVanilla() {
        v.giveExperiencePoints(7);
        assertEquals(1, v.xpLevel());
        assertEquals(0f, v.xpProgress(), 1e-6);
        assertEquals(7, v.xpTotal());
        v.giveExperiencePoints(-1);
        assertEquals(0, v.xpLevel());
        assertEquals(6f / 7f, v.xpProgress(), 1e-6);
        assertFalse(v.setExperiencePoints(7), "레벨 0 에서 7 이상은 거부");
        assertTrue(v.setExperiencePoints(3));
        v.giveExperienceLevels(30);
        assertEquals(112, v.xpNeededForNextLevel());
        v.setExperienceLevels(15);
        assertEquals(37, v.xpNeededForNextLevel());
    }

    @Test
    void killBypassesCreativeAndProducesGenericKillMessage() {
        v.setGameMode(GameMode.CREATIVE);
        assertTrue(v.kill());
        assertTrue(v.isDeadOrDying());
        assertEquals(1, env.broadcasts.size());
        assertEquals("death.attack.genericKill", env.broadcasts.get(0).translationKey());
        assertFalse(v.hurt(DamageType.GENERIC_KILL, 1), "이미 죽었으면 피해 없음");
    }

    /**
     * PlayerDeathEvent 취소(Paper Improve-death-events): 체력이 reviveHealth 로 돌아오고 사망 메시지·사망 화면(PlayerCombatKill)·
     * 엔티티 이벤트 3·드롭 처리·마지막 사망 위치·사망 기록이 하나도 없다. 그 뒤 다시 죽이면 평소처럼 죽는다.
     */
    @Test
    void cancelledDeathRevivesWithoutAnyDeathEffects() {
        java.util.List<List<ItemStack>> deaths = new ArrayList<>();
        v.deathListener((keep, drops, originals, itemsToKeep) -> deaths.add(drops));
        v.giveExperienceLevels(5);
        env.cancelDeathWith = 6.0f;
        v.kill();
        assertFalse(v.isDeadOrDying(), "취소 → 죽지 않는다");
        assertEquals(6.0f, v.health(), "reviveHealth");
        assertTrue(env.broadcasts.isEmpty(), "사망 메시지 없음");
        assertTrue(deaths.isEmpty(), "드롭·인벤토리 처리 없음");
        assertNull(v.death(), "사망 기록 없음(스컬크 촉매가 볼 것 없음)");
        assertNull(v.lastDeathPos(), "마지막 사망 위치 없음");
        assertTrue(env.trackerPackets.stream().noneMatch(p -> p instanceof org.domi.furnace.server.net.protocol.play.EntityEvent e && e.event() == 3),
                "엔티티 이벤트 3(죽는 모습) 없음");
        channel.runPendingTasks();
        assertFalse(drain().contains(org.domi.furnace.server.net.protocol.play.PlayerCombatKill.ID), "사망 화면(PlayerCombatKill) 없음");
        for (int i = 0; i < 25; i++) v.tick(DRY);
        assertFalse(v.removedAfterDeath(), "죽은 몸 제거 없음");
        assertEquals(5, v.xpLevel(), "경험치 그대로");

        env.cancelDeathWith = null;
        v.kill();
        assertTrue(v.isDeadOrDying(), "취소하지 않으면 평소처럼 죽는다");
        assertEquals(1, deaths.size());
        assertEquals(1, env.broadcasts.size());
    }

    @Test
    void deathMessagesUseFallVariants() {
        fall(140, 100, "minecraft:stone", false);
        assertTrue(v.isDeadOrDying());
        assertEquals("death.fell.accident.generic", env.broadcasts.get(0).translationKey(), "5블록 넘게 떨어져 죽음");

        PlayerVitals small = new Player(9, UUID.randomUUID(), "B", channel).vitals();
        small.env(env);
        small.loadGameTypes(null, GameMode.SURVIVAL, false);
        NbtCompound tag = new NbtCompound();
        tag.putFloat("Health", 1f);
        small.load(tag);
        for (int i = 0; i < 5; i++) small.onMove(0, -1, 0, i == 0, false, false, false, false, false, null, "minecraft:air", false);
        small.onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
        assertEquals("death.attack.fall", env.broadcasts.get(1).translationKey(), "짧은 낙하는 일반 낙하 메시지");

        env.rules.put("showDeathMessages", false);
        PlayerVitals quiet = new Player(10, UUID.randomUUID(), "C", channel).vitals();
        quiet.env(env);
        quiet.loadGameTypes(null, GameMode.SURVIVAL, false);
        quiet.kill();
        assertEquals(2, env.broadcasts.size(), "showDeathMessages 꺼짐 → 방송 없음");
    }

    @Test
    void deathRemovesEntityAfterTwentyTicksAndRespawnResets() {
        v.giveExperienceLevels(5);
        v.kill();
        for (int i = 0; i < 19; i++) v.tick(DRY);
        assertFalse(v.removedAfterDeath());
        v.tick(DRY);
        assertTrue(v.removedAfterDeath(), "tickDeath 20틱 후 제거");
        v.respawn();
        assertEquals(20.0f, v.health());
        assertEquals(20, v.foodLevel());
        assertEquals(0, v.xpLevel(), "keepInventory 꺼짐 → 경험치 잃음");
        assertFalse(v.removedAfterDeath());
        assertNotNull(v.lastDeathPos(), "마지막 사망 위치 기록");
        v.giveExperienceLevels(3);
        env.rules.put("keepInventory", true);
        v.kill();
        v.respawn();
        assertEquals(3, v.xpLevel(), "keepInventory → 경험치 유지");
    }

    @Test
    void nbtRoundTripAndGameModeResolution() {
        v.setGameMode(GameMode.ADVENTURE);
        v.giveExperiencePoints(20);
        v.hurt(DamageType.FALL, 3);
        v.setRespawnPosition("minecraft:overworld", new int[]{1, 70, -3}, 45f, true);
        NbtCompound tag = v.saveData();
        assertEquals(2, tag.getIntOr0("playerGameType"));
        assertEquals(0, tag.getIntOr0("previousPlayerGameType"));
        assertEquals(17f, tag.getFloat("Health"));
        assertEquals(70, tag.getIntOr0("SpawnY"));

        PlayerVitals back = new Player(11, UUID.randomUUID(), "D", channel).vitals();
        back.load(tag);
        back.loadGameTypes(tag, GameMode.SURVIVAL, false);
        assertEquals(GameMode.ADVENTURE, back.gameMode());
        assertEquals(GameMode.SURVIVAL, back.previousGameMode());
        assertEquals(17f, back.health());
        assertEquals(v.xpLevel(), back.xpLevel());
        assertArrayEquals(new int[]{1, 70, -3}, back.respawnPos());
        assertFalse(back.mayBuild(), "모험 모드는 건축 불가");

        PlayerVitals forced = new Player(12, UUID.randomUUID(), "E", channel).vitals();
        forced.loadGameTypes(tag, GameMode.CREATIVE, true);
        assertEquals(GameMode.CREATIVE, forced.gameMode(), "force-gamemode 면 서버 기본값");
        PlayerVitals fresh = new Player(13, UUID.randomUUID(), "F", channel).vitals();
        fresh.loadGameTypes(null, GameMode.SPECTATOR, false);
        assertEquals(GameMode.SPECTATOR, fresh.gameMode(), "저장값 없으면 서버 기본값");
        assertNull(fresh.previousGameMode());
        assertFalse(fresh.saveData().contains("previousPlayerGameType"));
    }

    @Test
    void healthAndExperienceAreSentOnlyWhenChanged() {
        v.joinPackets();
        channel.runPendingTasks();
        drain();
        v.tick(DRY);
        channel.runPendingTasks();
        assertTrue(drain().isEmpty(), "바뀐 게 없으면 아무것도 보내지 않는다");
        v.giveExperienceLevels(2);
        v.tick(DRY);
        channel.runPendingTasks();
        assertTrue(drain().contains(0x5C), "경험치가 바뀌면 SetExperience");
    }

    private List<Integer> drain() {
        List<Integer> ids = new ArrayList<>();
        Object o;
        while ((o = channel.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            ids.add(Types.readVarInt(b));
            b.release();
        }
        return ids;
    }
}
