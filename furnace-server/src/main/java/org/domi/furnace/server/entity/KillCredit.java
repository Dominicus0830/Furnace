// 바닐라 awardKillScore 경로 — LivingEntity.die / ServerPlayer.die 에서 kill credit 에게 처치 점수·통계·발전 과제 트리거를 준다.
// 순서는 바닐라 그대로: Entity.awardKillScore(죽은 쪽이 플레이어면 entity_killed_player) → ServerPlayer.awardKillScore 본문.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.loot.LootDamageSource;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.scores.ObjectiveCriteria;
import org.domi.furnace.server.scores.ScoreHolder;

public final class KillCredit {

    private KillCredit() {}

    /** 전리품 조건이 보는 엔티티(플레이어·살아 있는 것·그 밖은 종류와 위치만). */
    public static LootEntity lootOf(Rider r) {
        if (r == null) return null;
        if (r instanceof Player p) return ServerCore.playerLootEntity(p);
        if (r instanceof LivingEntity l) return l.lootEntity();
        String type = r.typeName();
        return new LootEntity() {
            public String type() { return type; }
            public double[] position() { return new double[]{r.x(), r.y(), r.z()}; }
        };
    }

    /** killing_blow 가 보는 피해 원인. 태그는 damage_type 태그 표, 원인·직접 엔티티는 같은 객체면 같은 LootEntity(is_direct). */
    public static LootDamageSource source(String type, Rider causing, Rider direct) {
        LootEntity c = lootOf(causing);
        LootEntity d = direct == causing ? c : lootOf(direct);
        String key = type.indexOf(':') < 0 ? "minecraft:" + type : type;
        return new LootDamageSource() {
            public String type() { return key; }
            public boolean is(String tag) { return DamageType.keyHasTag(key, tag); }
            public LootEntity entity() { return c; }
            public LootEntity directEntity() { return d; }
        };
    }

    /**
     * killer(kill credit) 가 killed 를 죽였다. 바닐라 killed.die 안의 killCredit.awardKillScore(killed, deathScore, source).
     * killer 가 플레이어가 아니면 Entity.awardKillScore 만(죽은 쪽이 플레이어면 entity_killed_player).
     */
    public static void award(Targetable killer, Rider killed, LootDamageSource source) {
        if (killer == null || killed == null) return;
        // Entity.awardKillScore: 죽은 쪽이 플레이어면 그 플레이어에게 entity_killed_player(entity = killer)
        if (killed instanceof Player victim && killer instanceof Rider kr) {
            var adv = victim.advancements();
            if (adv != null) adv.triggerKill("minecraft:entity_killed_player", lootOf(kr), source);
        }
        if (!(killer instanceof Player p) || killer == killed) return;
        // ServerPlayer.awardKillScore 본문. increaseScore(deathScore) 는 바닐라 deathScore 가 늘 0 이라 값이 바뀌지 않는다.
        var board = p.scoreboard();
        if (board != null) board.incrementCriteria(p, ObjectiveCriteria.KILL_COUNT_ALL);
        if (killed instanceof Player) {
            p.awardStat("minecraft:player_kills", 1);
            if (board != null) board.incrementCriteria(p, ObjectiveCriteria.KILL_COUNT_PLAYERS);
        } else {
            p.awardStat("minecraft:mob_kills", 1);
        }
        if (board != null) {
            ScoreHolder self = ScoreHolder.of(p), other = ScoreHolder.of(killed);
            board.handleTeamKill(self, other.scoreboardName(), ObjectiveCriteria.TEAM_KILL);
            board.handleTeamKill(other, self.scoreboardName(), ObjectiveCriteria.KILLED_BY_TEAM);
        }
        var adv = p.advancements();
        if (adv != null) adv.triggerKill("minecraft:player_killed_entity", lootOf(killed), source);
    }
}
