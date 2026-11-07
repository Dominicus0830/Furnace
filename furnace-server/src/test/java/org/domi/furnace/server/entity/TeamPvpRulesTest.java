// M16 팀·PvP 규칙: Entity.isAlliedTo(같은 팀·길들인 동물 주인·일러저 친구), 타게팅 조건·목표 지속 조건, canHarmPlayer, 늑대·화살의 PvP 거름.
// 기대값은 바닐라 1.21.1 바이트코드(btn.s=isAlliedTo, cef.a=TargetingConditions.test, cmx.a=canHarmPlayer, cgh.a=Wolf.wantsToAttack, cnd.l=AbstractArrow.tick)에서 읽었다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.entity.ai.goal.TargetGoal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.scores.PlayerTeam;
import org.domi.furnace.server.scores.ServerScoreboard;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TeamPvpRulesTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Arena extends BlockPlacementTest.TestLevel {
        final ServerScoreboard board = new ServerScoreboard(List::of, null);
        final List<Player> players = new ArrayList<>();
        boolean pvp = true;

        Arena() { wallOffArena(); }

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public boolean pvp() { return pvp; }
                @Override public org.domi.furnace.server.scores.Scoreboard scoreboard() { return board; }
                @Override public void forEachPlayer(Consumer<Player> c) { players.forEach(c); }
            };
        }
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static Player player(Arena level, String name, double x) {
        var p = new Player(level.players.size() + 7, UUID.randomUUID(), name, new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, 0.5, 90, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);   // 접속 직후 무적 시간 지나기
        p.scoreboard(level.board);
        level.players.add(p);
        return p;
    }

    private static <T extends Entity> T spawn(Arena level, String type, double x) {
        @SuppressWarnings("unchecked") T e = (T) Entities.create(level, type);
        e.moveTo(x, -60, 0.5, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    private static PlayerTeam team(Arena level, String name, boolean friendlyFire, String... holders) {
        PlayerTeam t = level.board.addPlayerTeam(name);
        t.allowFriendlyFire(friendlyFire);
        for (String h : holders) level.board.addPlayerToTeam(h, t);
        return t;
    }

    @Test
    void sameTeamIsAlliedAndNoTeamIsNot() {
        var level = new Arena();
        Cow a = spawn(level, "minecraft:cow", 0.5), b = spawn(level, "minecraft:cow", 2.5), c = spawn(level, "minecraft:cow", 4.5);
        assertFalse(a.isAlliedTo(b), "팀이 없으면 아군이 아니다");
        team(level, "red", true, a.uuid().toString(), b.uuid().toString());
        team(level, "blue", true, c.uuid().toString());
        assertTrue(a.isAlliedTo(b));
        assertFalse(a.isAlliedTo(c), "다른 팀");
        assertFalse(c.isAlliedTo(null));
        assertFalse(level.board.getPlayersTeam(a.uuid().toString()) == null);
    }

    @Test
    void combatTargetingSkipsTeammatesButNotOthers() {
        var level = new Arena();
        Zombie z = spawn(level, "minecraft:zombie", 0.5);
        Cow mate = spawn(level, "minecraft:cow", 2.5), other = spawn(level, "minecraft:cow", 4.5);
        team(level, "red", true, z.uuid().toString(), mate.uuid().toString());
        var cond = TargetingConditions.forCombat().ignoreLineOfSight();
        assertFalse(cond.test(z, mate), "같은 팀은 겨냥하지 않는다");
        assertTrue(cond.test(z, other));
        assertTrue(TargetingConditions.forNonCombat().ignoreLineOfSight().test(z, mate), "비전투 조건은 팀을 보지 않는다");
    }

    @Test
    void targetGoalStopsWhenTargetJoinsMobsTeam() {
        var level = new Arena();
        Zombie z = spawn(level, "minecraft:zombie", 0.5);
        Cow cow = spawn(level, "minecraft:cow", 3.5);
        var goal = new TargetGoal(z, false) {
            @Override public boolean canUse() { return false; }
        };
        z.setTarget(cow);
        assertTrue(goal.canContinueToUse());
        team(level, "red", true, z.uuid().toString(), cow.uuid().toString());
        assertFalse(goal.canContinueToUse(), "같은 팀이 되면 추격을 멈춘다");
    }

    @Test
    void tamedAnimalIsAlliedToOwnerAndOwnersTeam() {
        var level = new Arena();
        Player owner = player(level, "Owner", -10);
        Wolf wolf = spawn(level, "minecraft:wolf", 0.5);
        Cow cow = spawn(level, "minecraft:cow", 2.5), stranger = spawn(level, "minecraft:cow", 4.5);
        wolf.setOwnerUUID(owner.uuid());
        wolf.setTame(true, false);
        team(level, "red", true, owner.name(), cow.uuid().toString());
        assertTrue(wolf.isAlliedTo(owner), "주인");
        assertTrue(wolf.isAlliedTo(cow), "주인의 팀원");
        assertFalse(wolf.isAlliedTo(stranger));
        wolf.setTame(false, false);
        assertFalse(wolf.isAlliedTo(owner), "길들이지 않았으면 팀 규칙뿐");
    }

    @Test
    void illagerFriendsAreAlliedOnlyWhenNeitherHasATeam() {
        var level = new Arena();
        AbstractIllager pillager = spawn(level, "minecraft:pillager", 0.5);
        Entity vindicator = spawn(level, "minecraft:vindicator", 2.5);
        Cow cow = spawn(level, "minecraft:cow", 4.5);
        assertTrue(pillager.isAlliedTo((Targetable) vindicator), "illager_friends 태그, 팀 없음");
        assertFalse(pillager.isAlliedTo(cow));
        team(level, "red", true, pillager.uuid().toString());
        assertFalse(pillager.isAlliedTo((Targetable) vindicator), "한쪽이라도 팀이 있으면 태그 동맹은 풀린다");
        team(level, "blue", true, vindicator.uuid().toString());
        assertFalse(pillager.isAlliedTo((Targetable) vindicator));
    }

    @Test
    void canHarmPlayerFollowsPvpAndFriendlyFire() {
        var level = new Arena();
        Player a = player(level, "A", 0.5), b = player(level, "B", 2.5), c = player(level, "C", 4.5);
        assertTrue(a.canHarmPlayer(b, true), "팀 없음");
        assertFalse(a.canHarmPlayer(b, false), "pvp 꺼짐");
        team(level, "red", false, "A", "B");
        assertFalse(a.canHarmPlayer(b, true), "같은 팀, 아군 피해 꺼짐");
        assertTrue(a.canHarmPlayer(c, true), "다른 팀");
        team(level, "green", true, "C");
        assertTrue(c.canHarmPlayer(a, true));
        level.board.getPlayerTeam("red").allowFriendlyFire(true);
        assertTrue(a.canHarmPlayer(b, true), "아군 피해 켜짐");
    }

    @Test
    void victimIgnoresTeammateHitsWhenFriendlyFireIsOff() {
        var level = new Arena();
        Player a = player(level, "A", 0.5), b = player(level, "B", 2.5);
        team(level, "red", false, "A", "B");
        float before = b.vitals().health();
        assertFalse(b.hurtByVia(new Entity.Damage("player_attack", false, false, false), 4.0F, a, null));
        assertEquals(before, b.vitals().health());
        level.board.getPlayerTeam("red").allowFriendlyFire(true);
        assertTrue(b.hurtByVia(new Entity.Damage("player_attack", false, false, false), 4.0F, a, null));
        assertTrue(b.vitals().health() < before);
    }

    @Test
    void wolfDoesNotJoinOwnersFightAgainstAPlayerItCannotHarm() {
        var level = new Arena();
        Player owner = player(level, "Owner", -10), foe = player(level, "Foe", 2.5);
        Wolf wolf = spawn(level, "minecraft:wolf", 0.5);
        Cow cow = spawn(level, "minecraft:cow", 4.5);
        assertTrue(wolf.wantsToAttack(foe, owner));
        assertTrue(wolf.wantsToAttack(cow, owner));
        team(level, "red", false, "Owner", "Foe");
        assertFalse(wolf.wantsToAttack(foe, owner), "같은 팀, 아군 피해 꺼짐");
        assertTrue(wolf.wantsToAttack(cow, owner), "몹은 그대로");
        level.board.getPlayerTeam("red").allowFriendlyFire(true);
        assertTrue(wolf.wantsToAttack(foe, owner));
        level.pvp = false;
        assertFalse(wolf.wantsToAttack(foe, owner), "pvp 꺼짐");
        assertTrue(wolf.wantsToAttack(cow, owner));
    }

    @Test
    void wolfLeavesTamedHorsesAlone() {
        var level = new Arena();
        Player owner = player(level, "Owner", -10);
        Wolf wolf = spawn(level, "minecraft:wolf", 0.5);
        Horse horse = spawn(level, "minecraft:horse", 2.5);
        assertTrue(wolf.wantsToAttack(horse, owner));
        horse.setTamed(true);
        assertFalse(wolf.wantsToAttack(horse, owner));
    }

    /** 쏜 사람이 victim 쪽으로 화살을 쏘고 3틱 돌린다. 화살을 돌려준다(통과했으면 victim 을 지나 날아간다). */
    private static Arrow shootAt(Arena level, Player shooter, Player victim) {
        var arrow = new Arrow(level, level.nextEntityId(), victim.x() - 3.0, -59, 0.5);
        arrow.setOwner(shooter);
        arrow.setDeltaMovement(3.0, 0, 0);
        level.addFreshEntity(arrow);
        for (int i = 0; i < 3; i++) arrow.tick();
        return arrow;
    }

    @Test
    void arrowHitsEnemyPlayerButPassesThroughProtectedOnes() {
        var level = new Arena();
        Player shooter = player(level, "Shooter", -20), victim = player(level, "Victim", 5.5);
        float before = victim.vitals().health();
        shootAt(level, shooter, victim);
        assertTrue(victim.vitals().health() < before, "팀 없음·pvp 켜짐 — 맞는다");

        var level2 = new Arena();
        Player s2 = player(level2, "Shooter", -20), v2 = player(level2, "Victim", 5.5);
        team(level2, "red", false, "Shooter", "Victim");
        Arrow a2 = shootAt(level2, s2, v2);
        assertEquals(before, v2.vitals().health(), "같은 팀, 아군 피해 꺼짐");
        assertTrue(a2.x() > v2.x() + 1.0, "화살이 사람을 통과해 날아간다: x=" + a2.x());

        var level3 = new Arena();
        Player s3 = player(level3, "Shooter", -20), v3 = player(level3, "Victim", 5.5);
        level3.pvp = false;
        Arrow a3 = shootAt(level3, s3, v3);
        assertEquals(before, v3.vitals().health(), "pvp 꺼짐");
        assertTrue(a3.x() > v3.x() + 1.0, "화살이 사람을 통과해 날아간다: x=" + a3.x());
    }
}
