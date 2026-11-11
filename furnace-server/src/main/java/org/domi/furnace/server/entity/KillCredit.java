// 바닐라 awardKillScore 경로 — LivingEntity.die / ServerPlayer.die 에서 kill credit 에게 처치 점수·통계·발전 과제 트리거를 준다.
// 순서는 바닐라 그대로: Entity.awardKillScore(죽은 쪽이 플레이어면 entity_killed_player) → ServerPlayer.awardKillScore 본문.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.loot.LootDamageSource;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.scores.ObjectiveCriteria;
import org.domi.furnace.server.scores.ScoreHolder;

public final class KillCredit {

    private KillCredit() {}

    /** 전리품 조건이 보는 엔티티(바닐라 EntityPredicate 가 읽는 값 전부). 그 엔티티 소유 스레드에서 지금 값을 떠 둔다. */
    public static LootEntity lootOf(Rider r) {
        if (r == null) return null;
        return snapshot(LootEntities.of(r));
    }

    /**
     * 다른 스레드(플레이어 주인)에서 나중에 볼 수 있게 값을 떠 둔 사본. 종류별 값(typeSpecific)·슬롯 범위는 원본에 묻는다
     * (가정: 처치 직후 한 틱 안에는 바뀌지 않는다). NBT 는 nbt 조건이 데이터 팩에 있을 때만 미리 복사한다.
     */
    public static LootEntity snapshot(LootEntity e) { return snapshot(e, 0); }

    // 가정/위험: depth 0 에서 vehicle/passengers/targetedEntity 를 지금 스레드(죽는·맞는 쪽 소유)에서 읽는다. 관계 엔티티가 다른 region 이면
    // 값이 한 틱 어긋나거나 읽기 경쟁이 있을 수 있다(읽는 값은 위치·플래그·장비 정도라 크래시는 없다고 가정).
    private static LootEntity snapshot(LootEntity e, int depth) {
        if (e == null) return null;
        String type = e.type();
        boolean fire = e.isOnFire(), crouch = e.isCrouching(), sprint = e.isSprinting(), swim = e.isSwimming(), baby = e.isBaby(), ground = e.onGround(), flying = e.isFlying();
        double[] pos = e.position() == null ? null : e.position().clone();
        float luck = e.luck(), fall = e.fallDistance();
        java.util.Map<String, LootStack> eq = new java.util.HashMap<>();
        for (String slot : new String[] {"mainhand", "offhand", "head", "chest", "legs", "feet", "body"}) eq.put(slot, e.equipment(slot));
        var effects = new java.util.LinkedHashMap<>(e.effects());
        double[] stepping = clone(e.steppingOnPos()), affected = clone(e.movementAffectedByPos()), vel = clone(e.velocity());
        String team = e.team();
        int ticks = e.tickCount();
        var nbt = org.domi.furnace.server.loot.EntityPredicate.nbtUsed ? e.nbt() : null;
        // 종류별 값은 소유 스레드에서 미리 읽어 둔다(변종은 저장 NBT 를 거치므로 다른 스레드에서 읽으면 경쟁). 플레이어 기록 키는 잠금 있는 원본에 묻는다.
        java.util.Map<String, Object> specific = new java.util.HashMap<>();
        boolean variants = org.domi.furnace.server.loot.EntityPredicate.variantUsed;
        for (String k : SPECIFIC_KEYS) {
            if (!variants && k.equals("variant")) continue;
            Object v = e.typeSpecific(k);
            if (v != null) specific.put(k, v);
        }
        // 시선 끝의 엔티티(player looking_at)는 레이캐스트라 처음 물을 때 한 번만 센다(소유 스레드가 아니면 null — PlayerView.lookingAt)
        final boolean lazyLooking = depth == 0 && e.isPlayer() && org.domi.furnace.server.loot.EntityPredicate.lookingAtUsed;
        final Object[] looked = new Object[2];   // [0] 계산했나, [1] 값
        java.util.Map<String, Object> profile = e.gameProfile();
        LootEntity vehicle = depth == 0 ? snapshot(e.vehicle(), 1) : null;
        LootEntity target = depth == 0 ? snapshot(e.targetedEntity(), 1) : null;
        java.util.List<LootEntity> passengers = new java.util.ArrayList<>();
        if (depth == 0) for (LootEntity p : e.passengers()) passengers.add(snapshot(p, 1));
        return new LootEntity() {
            public String type() { return type; }
            public boolean isOnFire() { return fire; }
            public boolean isCrouching() { return crouch; }
            public boolean isSprinting() { return sprint; }
            public boolean isSwimming() { return swim; }
            public boolean isBaby() { return baby; }
            public boolean isFlying() { return flying; }
            public boolean onGround() { return ground; }
            public double[] position() { return pos; }
            public float luck() { return luck; }
            public LootStack equipment(String slot) { return eq.containsKey(slot) ? eq.get(slot) : e.equipment(slot); }
            public Object typeSpecific(String key) {
                if (key.startsWith("recipe:") || key.startsWith("advancement:") || key.startsWith("stat:")) return e.typeSpecific(key);
                if (lazyLooking && key.equals("looking_at")) {
                    synchronized (looked) {
                        if (looked[0] == null) {
                            looked[0] = Boolean.TRUE;
                            looked[1] = e.typeSpecific("looking_at") instanceof LootEntity hit ? snapshot(hit, 1) : null;
                        }
                        return looked[1];
                    }
                }
                return specific.get(key);
            }
            public java.util.Map<String, Object> gameProfile() { return profile; }
            public java.util.Map<String, Effect> effects() { return effects; }
            public org.domi.furnace.server.net.io.nbt.NbtCompound nbt() { return nbt; }
            public LootEntity vehicle() { return vehicle; }
            public java.util.List<LootEntity> passengers() { return passengers; }
            public LootEntity targetedEntity() { return target; }
            public double[] steppingOnPos() { return stepping; }
            public double[] movementAffectedByPos() { return affected; }
            public double[] velocity() { return vel; }
            public float fallDistance() { return fall; }
            public String team() { return team; }
            public java.util.List<LootStack> slotItems(String range) { return e.slotItems(range); }
            public int tickCount() { return ticks; }
        };
    }

    private static final String[] SPECIFIC_KEYS = {"size", "is_captain", "has_raid", "blocks_set_on_fire", "variant", "entity_struck",
            "in_open_water", "gamemode", "level"};

    private static double[] clone(double[] a) { return a == null ? null : a.clone(); }

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
        // ServerPlayer.awardKillScore 는 killed != this 일 때만 super(Entity.awardKillScore) 와 본문을 한다 — 자살은 아무것도 없다
        if (killer == killed) return;
        // 값은 지금(죽는 쪽 스레드에서) 떠 두고, 플레이어 상태는 그 플레이어 주인 스레드에서 바꾼다
        // Entity.awardKillScore: 죽은 쪽이 플레이어면 그 플레이어에게 entity_killed_player(entity = killer)
        if (killed instanceof Player victim && killer instanceof Rider kr) {
            LootEntity killerLoot = lootOf(kr);
            victim.runOnOwner(() -> {
                var adv = victim.advancements();
                if (adv != null) adv.triggerKill("minecraft:entity_killed_player", killerLoot, source);
            });
        }
        if (!(killer instanceof Player p)) return;
        boolean killedIsPlayer = killed instanceof Player;
        String killedName = ScoreHolder.of(killed).scoreboardName();
        LootEntity killedLoot = lootOf(killed);
        p.runOnOwner(() -> {
            // ServerPlayer.awardKillScore 본문. increaseScore(deathScore) 는 바닐라 deathScore 가 늘 0 이라 값이 바뀌지 않는다.
            var board = p.scoreboard();
            if (board != null) board.incrementCriteria(p, ObjectiveCriteria.KILL_COUNT_ALL);
            if (killedIsPlayer) {
                p.awardStat("minecraft:player_kills", 1);
                if (board != null) board.incrementCriteria(p, ObjectiveCriteria.KILL_COUNT_PLAYERS);
            } else {
                p.awardStat("minecraft:mob_kills", 1);
            }
            if (board != null) {
                ScoreHolder self = ScoreHolder.of(p), other = ScoreHolder.forNameOnly(killedName);
                board.handleTeamKill(self, killedName, ObjectiveCriteria.TEAM_KILL);
                board.handleTeamKill(other, self.scoreboardName(), ObjectiveCriteria.KILLED_BY_TEAM);
            }
            var adv = p.advancements();
            if (adv != null) adv.triggerKill("minecraft:player_killed_entity", killedLoot, source);
        });
    }
}
