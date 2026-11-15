// LivingEntity.pushEntities 의 후보 수집기(EntitySelector.pushableBy). 레벨마다 하나를 재사용해 틱당 할당이 없다.
// 엔티티는 같은 region(레벨) 것만 읽고 쓴다 — 레벨은 한 번에 한 region 스레드만 돌린다.
// 플레이어는 차원 전체를 읽는다. 쓰기는 상한 횟수(numCollisions) 하나뿐이고, 그 플레이어를 가진 region 스레드일 때만 센다(countPlayer).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.function.Consumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.scores.PlayerTeam;
import org.domi.furnace.server.scores.PlayerTeam.CollisionRule;
import org.domi.furnace.server.scores.Scoreboard;

public final class PushScratch {

    private final ArrayList<Entity> entities = new ArrayList<>();
    private final ArrayList<Player> players = new ArrayList<>();
    private Rider self;
    private boolean teams;
    private PlayerTeam myTeam;
    private CollisionRule myRule = CollisionRule.ALWAYS;

    private final Consumer<Entity> entityVisitor = e -> {
        if (e == self || e.isSpectator() || !e.isPushable()) return;
        if (!teams || teamAllows(e.getTeam())) entities.add(e);
    };

    private final Consumer<Player> playerVisitor = p -> {
        if (p == self || !p.isPushableByEntities()) return;   // Player.isPushable = LivingEntity.isPushable
        if (!teams || teamAllows(p.team())) players.add(p);
    };

    /** EntitySelector.pushableBy 의 팀 충돌 규칙(서버 쪽: 항상 검사). */
    private boolean teamAllows(PlayerTeam other) {
        CollisionRule r2 = other == null ? CollisionRule.ALWAYS : other.collisionRule();
        if (r2 == CollisionRule.NEVER) return false;
        boolean allied = myTeam != null && myTeam.isAlliedTo(other);
        if ((myRule == CollisionRule.PUSH_OWN_TEAM || r2 == CollisionRule.PUSH_OWN_TEAM) && allied) return false;
        return myRule != CollisionRule.PUSH_OTHER_TEAMS && r2 != CollisionRule.PUSH_OTHER_TEAMS || allied;
    }

    private static PlayerTeam teamOf(Rider r) { return r instanceof Player p ? p.team() : ((Entity) r).getTeam(); }

    /** 상자에 걸친 밀 수 있는 엔티티·플레이어를 모은다(자기 자신 제외). 팀 규칙이 never 면 아무도 모으지 않는다. */
    public void collect(ChunkLevel level, Rider self, AABB box) {
        entities.clear();
        players.clear();
        this.self = self;
        Scoreboard board = level.scoreboard();
        teams = board != null && board.hasTeams();
        if (teams) {
            myTeam = teamOf(self);
            myRule = myTeam == null ? CollisionRule.ALWAYS : myTeam.collisionRule();
            if (myRule == CollisionRule.NEVER) return;
        }
        if (!level.entities().isEmpty()) level.entities().forEachInNoCopy(box, entityVisitor);
        level.forEachPlayerIn(box, playerVisitor);
    }

    public int size() { return entities.size() + players.size(); }

    /** 탈것에 타지 않은 것의 수(cramming 판정). */
    public int countNonPassengers() {
        int n = 0;
        for (int i = 0, c = entities.size(); i < c; i++) if (!entities.get(i).isPassenger()) n++;
        for (int i = 0, c = players.size(); i < c; i++) if (players.get(i).vehicle() == null) n++;
        return n;
    }

    /** 생물 pusher 가 후보마다 doPush(후보)(기본 후보.push(pusher)). 플레이어 후보는 클라이언트 권위라 pusher 쪽 몫만 받는다. */
    public void pushAllBy(LivingEntity pusher) {
        for (int i = 0, c = entities.size(); i < c; i++) pusher.doPush(entities.get(i));
        for (int i = 0, c = players.size(); i < c; i++) pusher.doPush(players.get(i));
    }

    /**
     * Paper Cap Entity Collisions(collisions.max-entity-collisions = max ≥ 0, T-122): 내 횟수를 max 만큼 덜고, 후보를 차례로 보며 내 횟수가 max 미만인
     * 동안만 후보·나의 횟수를 하나씩 올리고 doPush. 상한이 없으면(-1) 바닐라 {@link #pushAllBy(LivingEntity)}.
     * 차례는 엔티티 다음 플레이어다 — 바닐라 getEntities 는 둘을 구역 목록 순서로 섞으므로, 플레이어가 후보에 끼고 상한에 닿은 틱에는 누가 밀리는지가
     * 다를 수 있다(플레이어 쪽 속도는 클라이언트 권위라 보이는 것은 몹 쪽 밀림뿐).
     */
    public void pushCappedBy(LivingEntity pusher, int max) {
        pusher.numCollisions = Math.max(0, pusher.numCollisions - max);
        for (int i = 0, c = entities.size(); i < c && pusher.numCollisions < max; i++) {
            Entity e = entities.get(i);
            e.numCollisions++;
            pusher.numCollisions++;
            pusher.doPush(e);
        }
        for (int i = 0, c = players.size(); i < c && pusher.numCollisions < max; i++) {
            Player p = players.get(i);
            countPlayer(p);
            pusher.numCollisions++;
            pusher.doPush(p);
        }
    }

    /** 플레이어 pusher 의 상한 판(위와 같다). 다른 플레이어 후보는 횟수만 센다(밀림은 클라이언트 권위). */
    public void pushCappedBy(Player pusher, int max) {
        pusher.numCollisions = Math.max(0, pusher.numCollisions - max);
        for (int i = 0, c = entities.size(); i < c && pusher.numCollisions < max; i++) {
            Entity e = entities.get(i);
            e.numCollisions++;
            pusher.numCollisions++;
            e.pushByPlayer(pusher);
        }
        for (int i = 0, c = players.size(); i < c && pusher.numCollisions < max; i++) {
            countPlayer(players.get(i));
            pusher.numCollisions++;
        }
    }

    /**
     * 후보 플레이어의 상한 횟수를 하나 올린다 — 그 플레이어를 가진 region 스레드일 때만(스레드 소유권, AGENTS.md 규칙 4).
     * 후보는 차원 전체 색인에서 오므로 경계 근처에선 다른 region 플레이어일 수 있고, 그때는 그 한 번을 세지 않는다
     * (횟수는 그 플레이어가 다음 틱에 미는 몫을 줄일 뿐이라 차이는 경계에서 밀림 하나 정도). 시험 레벨은 ownerRegion 이 없다.
     */
    private static void countPlayer(Player p) {
        if (p.ownerRegion == null || p.isOnOwnerThread()) p.numCollisions++;
    }

    /** 플레이어 pusher 가 후보 엔티티마다 후보.push(플레이어). */
    public void pushAllBy(Player pusher) {
        for (int i = 0, c = entities.size(); i < c; i++) entities.get(i).pushByPlayer(pusher);
    }

    /**
     * 플레이어의 pushEntities(Region 이 부른다). 바닐라는 자기 관전 여부를 보지 않고 후보가 있으면 굴림·cramming 판정을 돈다.
     * 밀기만 관전자(noPhysics)면 막힌다.
     */
    public void pushFor(ChunkLevel level, Player p) {
        // Paper(collisions.skip-unpushable, 기본 켬): 밀 수 없는 플레이어(관전자·사다리에 매달림)는 밀기·cramming 없음
        if (!p.isPushableByEntities() && org.domi.furnace.server.config.EntityHandleSettings.current().skipUnpushableCollisions()) return;
        collect(level, p, p.boundingBox());
        int n = size();
        if (n == 0) { clear(); return; }
        int rule = level.gameRuleInt("maxEntityCramming");
        if (rule > 0 && n > rule - 1 && p.random().nextInt(4) == 0) {
            // Paper "Don't apply cramming damage to players"(collisions.allow-player-cramming-damage, 기본 false): 굴림은 그대로, 피해만 없음
            if (countNonPassengers() > rule - 1 && org.domi.furnace.server.config.EntityHandleSettings.current().allowPlayerCrammingDamage())
                p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.CRAMMING, 6.0F);
        }
        if (!p.isSpectator()) {   // 관전자는 Paper 에서 isPushable 이 거짓이라 아예 이 루프에 오지 않는다 — 상한 횟수도 세지 않는다
            int max = org.domi.furnace.server.config.EntityHandleSettings.current().maxEntityCollisions();
            if (max < 0) pushAllBy(p);
            else pushCappedBy(p, max);
        }
        clear();
    }

    public void clear() {
        entities.clear();
        players.clear();
        self = null;
        myTeam = null;
    }
}
