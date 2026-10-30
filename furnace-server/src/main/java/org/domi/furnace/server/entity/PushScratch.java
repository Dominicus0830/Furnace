// LivingEntity.pushEntities 의 후보 수집기(EntitySelector.pushableBy). 레벨마다 하나를 재사용해 틱당 할당이 없다.
// 엔티티는 같은 region(레벨) 것만 읽고 쓴다 — 레벨은 한 번에 한 region 스레드만 돌린다.
// 플레이어는 차원 전체를 읽기만 한다(쓰기 없음, 기존 forEachPlayerIn 선례).
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
        if (p == self || p.isSpectator() || !p.isAlive() || p.onClimbable) return;   // Player.isPushable = LivingEntity.isPushable
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

    /** 엔티티 pusher 가 후보마다 후보.push(pusher). 플레이어 후보는 클라이언트 권위라 pusher 쪽 몫만 받는다. */
    public void pushAllBy(Entity pusher) {
        for (int i = 0, c = entities.size(); i < c; i++) entities.get(i).push(pusher);
        for (int i = 0, c = players.size(); i < c; i++) pusher.pushByPlayer(players.get(i));
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
        collect(level, p, p.boundingBox());
        int n = size();
        if (n == 0) { clear(); return; }
        int rule = level.gameRuleInt("maxEntityCramming");
        if (rule > 0 && n > rule - 1 && p.random().nextInt(4) == 0) {
            if (countNonPassengers() > rule - 1) p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.CRAMMING, 6.0F);
        }
        if (!p.isSpectator()) pushAllBy(p);
        clear();
    }

    public void clear() {
        entities.clear();
        players.clear();
        self = null;
        myTeam = null;
    }
}
