// 플레이어 생존 상태 — 게임 모드·능력·체력·흡수·허기·경험치·공기·낙하 거리·무적 틱·사망/리스폰.
// 바닐라 ServerPlayer/Player/LivingEntity 의 해당 부분을 옮겼다. 모든 메서드는 이 객체로 동기화돼
// region 스레드(틱·낙하), 명령 스레드(/gamemode·/kill·/xp), netty 스레드(저장·리스폰)에서 안전하게 부를 수 있다.
// 블록을 봐야 하는 판정(물·사다리·착지 블록)은 소유 region 이 계산해 인자로 넘긴다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.entity.damage.CombatRules;
import org.domi.furnace.server.entity.damage.CombatTracker;
import org.domi.furnace.server.entity.damage.DamageSource;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.play.DamageEvent;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.PlayerCombatKill;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.protocol.play.SetExperience;
import org.domi.furnace.server.net.protocol.play.SetHealth;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;

public final class PlayerVitals {

    public static final float MAX_HEALTH = 20.0f;
    public static final int MAX_AIR = 300;
    /** 바닐라 Attributes.SAFE_FALL_DISTANCE 기본값. */
    private static final float SAFE_FALL_DISTANCE = 3.0f;
    private static final int SPAWN_INVULNERABLE_TICKS = 60;

    /** 생존 상태가 서버에 묻거나 시키는 것. 스레드 안전해야 한다. */
    public interface Env {
        Difficulty difficulty();

        boolean gameRule(String name);

        /** 서버 설정 pvp(전용 서버에서 스폰 무적이 낙하 피해를 막지 않는 조건). */
        boolean pvp();

        /** 접속자 전원에게 시스템 메시지(콘솔 기록 포함) — 바닐라 PlayerList.broadcastSystemMessage. */
        void broadcastSystemMessage(Text message);

        /** 바닐라 ServerPlayer.die 의 사망 메시지 전달(팀 deathMessageVisibility 반영). 기본은 전원. */
        default void broadcastDeathMessage(Player player, Text message) { broadcastSystemMessage(message); }

        /** 접속자 전원에게 패킷. */
        void broadcastAll(Packet packet);

        /** 이 엔티티를 추적(화면에 보고) 중인 다른 플레이어에게 패킷. */
        void broadcastToTrackers(int entityId, Packet packet);

        /** 사망 직전(플러그인 PlayerDeathEvent) — 메시지(null = 알리지 않음)·인벤토리·경험치 유지를 바꿀 수 있다. */
        default DeathOutcome death(Player player, Text message, boolean keepInventory) {
            return new DeathOutcome(message, keepInventory, keepInventory);
        }

        /** 게임 모드가 바뀌기 직전(플러그인 PlayerGameModeChangeEvent) — false 면 바꾸지 않는다. */
        default boolean allowGameModeChange(Player player, GameMode mode) { return true; }

        /** 월드 보더 밖 피해량(0 = 없음). */
        default float borderDamage(double x, double y, double z) { return 0f; }

        Env DEFAULT = new Env() {
            public Difficulty difficulty() { return Difficulty.EASY; }
            public boolean gameRule(String name) { return !name.equals("keepInventory") && !name.equals("doImmediateRespawn"); }
            public boolean pvp() { return true; }
            public void broadcastSystemMessage(Text message) {}
            public void broadcastAll(Packet packet) {}
            public void broadcastToTrackers(int entityId, Packet packet) {}
        };
    }

    /** 사망 처리 결과. */
    public record DeathOutcome(Text message, boolean keepInventory, boolean keepLevel) {}

    /** 죽을 때 소유 연결이 할 일(인벤토리 비우기 등). netty 이벤트 루프로 넘겨 실행된다. */
    public interface DeathListener {
        void onDeath(boolean keepInventory);
    }

    /** 틱마다 region 이 블록을 보고 계산해 넘기는 주변 상태. */
    public record Surroundings(boolean chunkLoaded, boolean inWater, boolean eyeInWater, boolean eyeInBubbleColumn,
                               String climbable, boolean stuckInBlock, int minBuildHeight) {
        public static final Surroundings NONE = new Surroundings(false, false, false, false, null, false, -64);
    }

    private final Player player;
    private volatile Env env = Env.DEFAULT;
    private volatile DeathListener deathListener;

    private GameMode gameMode = GameMode.SURVIVAL;
    private GameMode previousGameMode;
    private final Abilities abilities = new Abilities();

    private float health = MAX_HEALTH;
    private float absorption;
    private final java.util.Map<MobEffect, MobEffectInstance> effects = new java.util.LinkedHashMap<>();
    private final java.util.List<Object[]> pendingEffectHurts = new java.util.ArrayList<>();
    private final java.util.List<Packet> pendingEffectPackets = new java.util.ArrayList<>();
    private float lastDataAbsorption;
    private FoodData food = new FoodData();
    private int xpLevel, xpTotal, xpSeed, score;
    private float xpProgress;
    private int air = MAX_AIR;
    private float fallDistance;
    // 현재 충격(폭발·철퇴) 맥락: 같은 충격 아래로 떨어질 때 낙하 피해를 면제한다(바닐라 Player.ignoreFallDamageFromCurrentImpulse 등)
    private boolean ignoreFallFromImpulse;
    private int impulseGrace;
    private double[] impulsePos;
    /** 지금 처리 중인 이동 표본의 y(위치 패킷이 쌓여 있어도 그 시점 높이로 비교). NaN 이면 플레이어 현재 y. */
    private double moveY = Double.NaN;
    private int invulnerableTime, hurtTime, deathTime, tickCount;

    /** 틱마다 올라가는 시계(같은 틱 안의 밀침을 쌓는 기준). */
    public synchronized int tickCount() { return tickCount; }
    private int spawnInvulnerableTime = SPAWN_INVULNERABLE_TICKS;
    private float lastHurt;
    private boolean invulnerableFlag;          // 엔티티 NBT "Invulnerable"
    private CombatTracker combat = new CombatTracker();
    private boolean inWater;
    private String lastClimbable;              // 바닐라 lastClimbablePos 의 FallLocation 이름
    private boolean removedAfterDeath;

    private float lastSentHealth = -1.0e8f;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    private int lastSentExp = -99999999;
    // 바닐라 ServerPlayer.lastRecorded*: 점수판 읽기 전용 기준(health·food·air·armor·xp·level) 갱신용
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE, lastRecordedAirLevel = Integer.MIN_VALUE, lastRecordedArmor = Integer.MIN_VALUE,
            lastRecordedLevel = Integer.MIN_VALUE, lastRecordedExperience = Integer.MIN_VALUE;
    // 클라이언트가 아는 엔티티 데이터 — 새 엔티티는 바닐라 정의 기본값(체력 1.0, 공기 300)에서 시작한다.
    private float lastDataHealth = 1.0f;
    private int lastDataAir = MAX_AIR;

    private int[] respawnPos;
    private float respawnAngle;
    private boolean respawnForced;
    private String respawnDimension = "minecraft:overworld";
    private String lastDeathDimension;
    private int[] lastDeathPos;
    /** ServerPlayer.raidOmenPosition(저장 키 raid_omen_position, BlockPos 코덱 = 정수 배열 [x, y, z]). */
    private org.domi.furnace.server.block.Pos raidOmenPosition;

    public synchronized org.domi.furnace.server.block.Pos raidOmenPosition() { return raidOmenPosition; }

    public synchronized void raidOmenPosition(org.domi.furnace.server.block.Pos p) { raidOmenPosition = p; }

    public PlayerVitals(Player player) {
        this.player = player;
        this.xpSeed = java.util.concurrent.ThreadLocalRandom.current().nextInt();
    }

    public void env(Env env) { this.env = env; }

    public Env env() { return env; }

    public void deathListener(DeathListener l) { this.deathListener = l; }

    // ------------------------------------------------------------------ 저장

    /** 바닐라 LivingEntity/Player/ServerPlayer.readAdditionalSaveData 중 이 객체가 다루는 키. 게임 모드는 {@link #loadGameTypes}. */
    private WardenSpawnTracker wardenSpawnTracker = new WardenSpawnTracker();

    public WardenSpawnTracker wardenSpawnTracker() { return wardenSpawnTracker; }

    public synchronized void load(NbtCompound tag) {
        if (tag.tags().get("warden_spawn_tracker") instanceof NbtCompound w) wardenSpawnTracker = WardenSpawnTracker.load(w);
        if (tag.containsNumber("Health")) health = Math.clamp(tag.getFloat("Health"), 0f, MAX_HEALTH + 4.0f * 255);
        absorption = Math.max(0f, tag.getFloat("AbsorptionAmount"));
        effects.clear();
        if (tag.contains("active_effects")) {
            for (Object o : tag.getList("active_effects").items()) {
                if (o instanceof NbtCompound c) {
                    MobEffectInstance e = MobEffectInstance.load(c);
                    if (e != null) effects.put(e.effect(), e);
                }
            }
        }
        health = Math.min(health, maxHealthLocked());
        absorption = Math.min(absorption, maxAbsorptionLocked());
        hurtTime = tag.getShort("HurtTime");
        deathTime = tag.getShort("DeathTime");
        if (tag.containsNumber("Air")) air = tag.getShort("Air");
        fallDistance = tag.getFloat("FallDistance");
        ignoreFallFromImpulse = tag.getBoolean("ignore_fall_damage_from_current_explosion");
        impulseGrace = tag.getIntOr0("current_impulse_context_reset_grace_time");
        impulsePos = null;
        var ipos = tag.contains("current_explosion_impact_pos") ? tag.getList("current_explosion_impact_pos") : null;
        if (ipos != null && ipos.size() >= 3) {
            impulsePos = new double[3];
            for (int i = 0; i < 3; i++) impulsePos[i] = ipos.get(i) instanceof Number n ? n.doubleValue() : 0.0;
        }
        if (tag.getBoolean("FallFlying")) {   // LivingEntity.readAdditionalSaveData — 서버 속도는 Motion 에서
            player.setFallFlying(true);
            var motion = tag.getList("Motion");
            if (motion != null && motion.size() >= 3) {
                double[] mv = new double[3];
                for (int i = 0; i < 3; i++) {
                    double d = motion.get(i) instanceof Number n ? n.doubleValue() : 0.0;
                    mv[i] = Math.abs(d) > 10.0 ? 0.0 : d;
                }
                player.flyVx = mv[0]; player.flyVy = mv[1]; player.flyVz = mv[2];
            }
        }
        invulnerableFlag = tag.getBoolean("Invulnerable");
        xpProgress = tag.getFloat("XpP");
        xpLevel = tag.getIntOr0("XpLevel");
        xpTotal = tag.getIntOr0("XpTotal");
        xpSeed = tag.getIntOr0("XpSeed");
        if (xpSeed == 0) xpSeed = java.util.concurrent.ThreadLocalRandom.current().nextInt();
        score = tag.getIntOr0("Score");
        food.load(tag);
        abilities.load(tag);
        if (tag.containsNumber("SpawnX") && tag.containsNumber("SpawnY") && tag.containsNumber("SpawnZ")) {
            respawnPos = new int[]{tag.getIntOr0("SpawnX"), tag.getIntOr0("SpawnY"), tag.getIntOr0("SpawnZ")};
            respawnForced = tag.getBoolean("SpawnForced");
            respawnAngle = tag.getFloat("SpawnAngle");
            if (tag.tags().get("SpawnDimension") instanceof String d) respawnDimension = d;
        }
        if (tag.tags().get("LastDeathLocation") instanceof NbtCompound ld
                && ld.tags().get("dimension") instanceof String dim && ld.tags().get("pos") instanceof int[] pos && pos.length == 3) {
            lastDeathDimension = dim;
            lastDeathPos = pos.clone();
        }
        raidOmenPosition = tag.tags().get("raid_omen_position") instanceof int[] ro && ro.length == 3
                ? new org.domi.furnace.server.block.Pos(ro[0], ro[1], ro[2]) : null;
    }

    /**
     * 바닐라 ServerPlayer.loadGameTypes + calculateGameModeForNewPlayer: force-gamemode 면 서버 기본값, 아니면 저장값,
     * 저장값이 없으면 서버 기본값. 이전 모드는 저장값 그대로.
     */
    public synchronized void loadGameTypes(NbtCompound tag, GameMode defaultMode, boolean forceGameMode) {
        GameMode saved = tag != null && tag.containsNumber("playerGameType") ? readMode(tag.getIntOr0("playerGameType")) : null;
        GameMode prev = tag != null && tag.containsNumber("previousPlayerGameType") ? readMode(tag.getIntOr0("previousPlayerGameType")) : null;
        GameMode mode = forceGameMode ? defaultMode : saved != null ? saved : defaultMode;
        previousGameMode = prev;
        gameMode = mode;
        abilities.applyGameMode(mode);
    }

    private static GameMode readMode(int id) { return id == -1 ? null : GameMode.byId(id); }

    public synchronized void save(NbtCompound tag) {
        tag.putFloat("Health", health);
        tag.putShort("HurtTime", (short) hurtTime);
        tag.putInt("HurtByTimestamp", 0);
        tag.putShort("DeathTime", (short) deathTime);
        tag.putFloat("AbsorptionAmount", absorption);
        if (!effects.isEmpty()) {
            org.domi.furnace.server.net.io.nbt.NbtList list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
            for (MobEffectInstance e : effects.values()) list.add(e.save());
            tag.putList("active_effects", list);
        } else {
            tag.tags().remove("active_effects");
        }
        tag.putShort("Air", (short) air);
        tag.putFloat("FallDistance", fallDistance);
        tag.putBoolean("ignore_fall_damage_from_current_explosion", ignoreFallFromImpulse);
        tag.putInt("current_impulse_context_reset_grace_time", impulseGrace);
        if (impulsePos != null) {
            var ip = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_DOUBLE);
            for (double d : impulsePos) ip.add(d);
            tag.putList("current_explosion_impact_pos", ip);
        } else {
            tag.tags().remove("current_explosion_impact_pos");
        }
        tag.putBoolean("FallFlying", player.isFallFlying());
        tag.putBoolean("Invulnerable", invulnerableFlag);
        tag.putFloat("XpP", xpProgress);
        tag.putInt("XpLevel", xpLevel);
        tag.putInt("XpTotal", xpTotal);
        tag.putInt("XpSeed", xpSeed);
        tag.putInt("Score", score);
        food.save(tag);
        abilities.save(tag);
        tag.putInt("playerGameType", gameMode.id());
        if (previousGameMode != null) tag.putInt("previousPlayerGameType", previousGameMode.id());
        else tag.tags().remove("previousPlayerGameType");
        if (respawnPos != null) {
            tag.putInt("SpawnX", respawnPos[0]);
            tag.putInt("SpawnY", respawnPos[1]);
            tag.putInt("SpawnZ", respawnPos[2]);
            tag.putBoolean("SpawnForced", respawnForced);
            tag.putFloat("SpawnAngle", respawnAngle);
            tag.putString("SpawnDimension", respawnDimension);
        } else {
            for (String k : new String[]{"SpawnX", "SpawnY", "SpawnZ", "SpawnForced", "SpawnAngle", "SpawnDimension"}) tag.tags().remove(k);
        }
        if (lastDeathPos != null) {
            NbtCompound ld = new NbtCompound();
            ld.putString("dimension", lastDeathDimension);
            ld.putIntArray("pos", lastDeathPos.clone());
            tag.putCompound("LastDeathLocation", ld);
        } else {
            tag.tags().remove("LastDeathLocation");
        }
        if (raidOmenPosition != null) {
            tag.putIntArray("raid_omen_position", new int[] {raidOmenPosition.x(), raidOmenPosition.y(), raidOmenPosition.z()});
        } else {
            tag.tags().remove("raid_omen_position");
        }
    }

    // ------------------------------------------------------------------ 게임 모드

    public synchronized GameMode gameMode() { return gameMode; }

    public synchronized GameMode previousGameMode() { return previousGameMode; }

    public synchronized boolean isSpectator() { return gameMode == GameMode.SPECTATOR; }

    public synchronized boolean isFlying() { return abilities.flying; }

    /** Abilities.mayfly. */
    public synchronized boolean mayfly() { return abilities.mayfly; }

    public synchronized boolean isCreative() { return gameMode == GameMode.CREATIVE; }

    public synchronized boolean mayBuild() { return abilities.mayBuild; }

    public synchronized boolean instabuild() { return abilities.instabuild; }

    public synchronized org.domi.furnace.server.net.protocol.play.PlayerAbilities abilitiesPacket() { return abilities.packet(); }

    /**
     * 바닐라 ServerPlayer.setGameMode: 같으면 false. 바꾸면 능력 → 탭 목록(전원) → 게임 이벤트 3 → 능력 순으로 알린다.
     */
    public boolean setGameMode(GameMode mode) {
        synchronized (this) {
            if (mode == gameMode) return false;
        }
        if (!env.allowGameModeChange(player, mode)) return false;
        Packet abilitiesPkt;
        synchronized (this) {
            if (mode == gameMode) return false;
            previousGameMode = gameMode;
            gameMode = mode;
            abilities.applyGameMode(mode);
            abilitiesPkt = abilities.packet();
        }
        player.send(abilitiesPkt);
        env.broadcastAll(PlayerInfoUpdate.updateGameMode(player.uuid(), mode.id()));
        player.send(new GameEvent(GameEvent.CHANGE_GAME_MODE, mode.id()));
        player.send(abilitiesPkt);
        return true;
    }

    /** 클라이언트가 비행 시작/끝을 알릴 때(serverbound player_abilities): 비행 가능할 때만 반영. */
    public synchronized void setFlying(boolean flying) {
        abilities.flying = flying && abilities.mayfly;
    }

    // ------------------------------------------------------------------ 체력·피해

    public synchronized float health() { return health; }

    public synchronized boolean isDeadOrDying() { return health <= 0.0f; }

    public synchronized boolean removedAfterDeath() { return removedAfterDeath; }

    public synchronized int foodLevel() { return food.foodLevel(); }

    public synchronized float saturation() { return food.saturationLevel(); }

    public synchronized float exhaustion() { return food.exhaustionLevel(); }

    public synchronized int air() { return air; }

    public synchronized float fallDistance() { return fallDistance; }

    // ------------------------------------------------------------------ 현재 충격 맥락(Player.setIgnoreFallDamageFromCurrentImpulse 등)

    /** setIgnoreFallDamageFromCurrentImpulse: 켜면 유예 40틱, 끄면 0. */
    public synchronized void setIgnoreFallDamageFromCurrentImpulse(boolean v) {
        ignoreFallFromImpulse = v;
        impulseGrace = v ? 40 : 0;
    }

    /** 이동 표본 처리 직전에 그 표본의 y 를 알린다(Region.processMoves). */
    public synchronized void currentMoveY(double y) { moveY = y; }

    private double impulseY() { return Double.isNaN(moveY) ? player.y() : moveY; }

    /** 테스트·플러그인용: 낙하 거리를 직접 정한다(Entity.fallDistance 는 공개 필드). */
    public synchronized void setFallDistance(float d) { fallDistance = d; }

    public synchronized boolean isIgnoringFallDamageFromCurrentImpulse() { return ignoreFallFromImpulse; }

    /** 현재 충격(폭발)을 일으킨 쪽의 값(fall_after_explosion 의 cause). 없으면 null. */
    public synchronized org.domi.furnace.server.loot.LootEntity currentImpulseCause() { return impulseCause; }

    private org.domi.furnace.server.loot.LootEntity impulseCause;

    public synchronized double[] currentImpulsePos() { return impulsePos == null ? null : impulsePos.clone(); }

    public synchronized void setCurrentImpulsePos(double[] p) { impulsePos = p == null ? null : p.clone(); }

    /** resetCurrentImpulseContext. */
    public synchronized void resetCurrentImpulseContext() {
        impulseCause = null;
        impulseGrace = 0;
        impulsePos = null;
        ignoreFallFromImpulse = false;
    }

    /** tryResetCurrentImpulseContext: 유예가 끝났을 때만 초기화. */
    public synchronized void tryResetCurrentImpulseContext() {
        if (impulseGrace == 0) resetCurrentImpulseContext();
    }

    /** Entity.onExplosionHit(source) 의 ServerPlayer 쪽: 맞은 자리를 기억하고, 원인이 윈드 차지일 때만 낙하 피해를 면제한다. */
    public synchronized void onExplosionHit(Entity source) {
        impulsePos = new double[] {player.x(), player.y(), player.z()};
        impulseCause = source == null ? null : KillCredit.lootOf(source);
        setIgnoreFallDamageFromCurrentImpulse(source instanceof WindCharge);
    }

    public synchronized void resetFallDistance() { fallDistance = 0.0f; }

    /** Entity.checkSlowFallDistance: y 속도가 -0.5 보다 크면 낙하 거리를 1 로 줄인다(겉날개 비행). */
    public synchronized void checkSlowFallDistance(double vy) {
        if (vy > -0.5 && fallDistance > 1.0f) fallDistance = 1.0f;
    }

    private void setHealthRaw(float h) { health = Math.clamp(h, 0.0f, maxHealthLocked()); }

    // 최대 체력 속성의 입력: 기본값과 효과 밖 수정자(장비·/attribute)는 Player.syncAttributes 가 넘기고, 효과 수정자는 지금 효과에서 바로 합성한다
    // (바닐라는 효과를 걸자마자 수정자를 단다 — 체력 강화로 최대 체력이 곧바로 오른다).
    private volatile double maxHealthBase = MAX_HEALTH;
    private volatile java.util.List<org.domi.furnace.server.attribute.AttributeModifier> maxHealthExtra = java.util.List.of();
    private volatile double oxygenBonus;

    /** 속성 맵에서 넘어온 입력(소유 region 스레드). 최대 체력이 줄었으면 체력을 자른다(LivingEntity.onAttributeUpdated). */
    public synchronized void attributeInputs(double maxHealthBase, java.util.List<org.domi.furnace.server.attribute.AttributeModifier> extra, double oxygenBonus) {
        this.maxHealthBase = maxHealthBase;
        this.maxHealthExtra = java.util.List.copyOf(extra);
        this.oxygenBonus = oxygenBonus;
        if (health > maxHealthLocked()) health = maxHealthLocked();
    }

    /** 최대 체력 = 속성 generic.max_health 값(기본 20 + 체력 강화 4 × (단계+1) + 그 밖의 수정자), float 로 줄인다. */
    private float maxHealthLocked() {
        if (effects.isEmpty() && maxHealthExtra.isEmpty()) return (float) org.domi.furnace.server.attribute.Attribute.MAX_HEALTH.sanitize(maxHealthBase);
        java.util.Map<String, org.domi.furnace.server.attribute.AttributeModifier> mods =
                PlayerAttributes.effectModifiers(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH, new java.util.ArrayList<>(effects.values()));
        for (var m : maxHealthExtra) mods.put(m.id(), m);
        return (float) PlayerAttributes.compute(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH, maxHealthBase, mods.values());
    }

    public synchronized float maxHealth() { return maxHealthLocked(); }

    /**
     * LivingEntity.decreaseAirSupply: 산소 보너스 b 가 0 보다 크면 난수(nextDouble)가 1/(b+1) 이상일 때 공기를 안 깎는다.
     * b 가 0 이하면 난수를 쓰지 않고 1 깎는다(단락 평가 그대로). 난수는 플레이어 난수.
     */
    private int decreaseAirSupply(int current) {
        double b = oxygenBonus;
        return b > 0.0 && player.random().nextDouble() >= 1.0 / (b + 1.0) ? current : current - 1;
    }

    /** 최대 흡수량: 흡수 효과 4 × (단계+1). */
    private float maxAbsorptionLocked() {
        MobEffectInstance ab = effects.get(MobEffect.ABSORPTION);
        return ab == null ? 0.0f : 4.0f * (ab.amplifier() + 1);
    }

    private void setAbsorptionLocked(float a) { absorption = Math.clamp(a, 0.0f, maxAbsorptionLocked()); }

    public synchronized float absorption() { return absorption; }

    /** 바닐라 LivingEntity.heal: 살아 있을 때만. */
    public synchronized void heal(float amount) {
        if (health > 0.0f) setHealthRaw(health + amount);
    }

    /** 바닐라 Player.canEat(ignoreHunger): 무적 능력이거나 배고픔 무시거나 배고플 때. */
    public synchronized boolean canEat(boolean ignoreHunger) { return abilities.invulnerable || ignoreHunger || food.foodLevel() < 20; }

    /** 바닐라 FoodData.eat(nutrition, saturationModifier). */
    /** 1.21 FoodProperties: saturation 은 이미 최종 포화량. */
    public synchronized void eatFood(int nutrition, float saturation) { food.add(nutrition, saturation); }

    public synchronized void eat(int nutrition, float saturationModifier) { food.eat(nutrition, saturationModifier); }

    /** 바닐라 Player.causeFoodExhaustion: 무적 능력이면 무시. */
    public synchronized void causeFoodExhaustion(float amount) {
        if (!abilities.invulnerable) food.addExhaustion(amount);
    }

    /** 바닐라 Player.isInvulnerableTo(+ Entity): 무적 플래그·게임룰(익사·낙하·불·동결 피해). */
    private boolean isInvulnerableTo(DamageType type) {
        if (invulnerableFlag && !type.bypassesInvulnerability()) return true;
        if (effects.containsKey(MobEffect.FIRE_RESISTANCE) && type.isFire()) return true;   // LivingEntity.hurt: 화염 저항
        if (type.isDrowning()) return !env.gameRule("drowningDamage");
        if (type.isFall()) return !env.gameRule("fallDamage");
        if (type.isFire()) return !env.gameRule("fireDamage");
        if (type.isFreezing()) return !env.gameRule("freezeDamage");
        return false;
    }

    /** /kill — 바닐라 LivingEntity.kill: generic_kill 로 Float.MAX_VALUE 피해. */
    public boolean kill() {
        return hurt(DamageType.GENERIC_KILL, Float.MAX_VALUE);
    }

    /** 마지막 hurt 가 무적 시간 밖의 새 피해였나(LivingEntity.hurt 의 넉백 조건) — 플레이어 근접 공격이 읽는다. */
    public volatile boolean lastHurtFresh;

    /** 바닐라 ServerPlayer.hurt → Player.hurt → LivingEntity.hurt(원인 엔티티 없는 피해). */
    public boolean hurt(DamageType type, float amount) { return hurt(type, amount, null); }

    /** 몹이 때린 피해(원인 엔티티 있음): 난이도 배율·방어구 경감·damage_event 의 원인 id. */
    public boolean hurt(DamageType type, float amount, Targetable attacker) {
        return hurt(type, amount, attacker, player.shieldSource(type.key(), attacker, null));
    }

    /** 방패 막기(LivingEntity.hurt 의 isDamageSourceBlocked 분기)까지 보는 피해. shield 는 원인 종류·위치. */
    public boolean hurt(DamageType type, float amount, Targetable attacker, Shields.Source shield) {
        Rider a = attacker instanceof Rider r ? r : null;
        return hurt(type, amount, attacker, shield, a, a, null);
    }

    /**
     * 원인·직접 엔티티와 위치를 다 가진 피해(바닐라 DamageSource 그대로, /damage 가 쓴다). 방패 막기 위치는 명시 위치,
     * 없으면 직접 엔티티(플레이어가 직접 때렸으면 그 플레이어).
     */
    public boolean hurt(DamageSource source, float amount) {
        DamageType type = source.type();
        Targetable attacker = source.attacker();
        Shields.Source shield;
        if (source.position() != null) {
            double[] p = source.position();
            shield = new Shields.Source(type.key(), true, p[0], p[2], attacker, false, false, null);
        } else {
            Targetable shieldAttacker = source.direct() instanceof Player dp ? dp : attacker;
            shield = player.shieldSource(type.key(), shieldAttacker, source.directEntity());
        }
        boolean r = hurt(type, amount, attacker, shield, source.causing(), source.direct(), source.position());
        // LivingEntity.hurt: 새 피해이고 no_knockback 이 아니면 원인 위치 쪽에서 0.4 민다(위치가 없으면 (0, 0)).
        // 직접 엔티티가 투사체면 -calculateHorizontalHurtKnockbackDirection.
        if (lastHurtFresh && !type.noKnockback()) {
            if (source.directEntity() instanceof Projectile pr) {
                double[] d = pr.hurtKnockbackDirection(player);
                player.knockback(0.4, -d[0], -d[1]);
            } else {
                double[] p = source.sourcePosition();
                player.knockback(0.4, p == null ? 0.0 : p[0] - player.x(), p == null ? 0.0 : p[2] - player.z());
            }
        }
        return r;
    }

    private boolean hurt(DamageType type, float amount, Targetable attacker, Shields.Source shield, Rider causing, Rider direct, double[] pos) {
        lastHurtFresh = false;
        boolean died;
        boolean newHurt;
        boolean blocked = false;
        float blockedAmount = 0.0f;
        float dealt = 0.0f;   // 발전 과제 dealt: 막기 전 피해(난이도 배율 뒤)
        synchronized (this) {
            if (isInvulnerableTo(type)) return false;
            boolean fallOnPvpServer = env.pvp() && type.isFall();
            if (!fallOnPvpServer && spawnInvulnerableTime > 0 && !type.bypassesInvulnerability()) return false;
            // ServerPlayer.hurt: 때린 쪽(화살이면 주인)이 플레이어인데 canHarmPlayer 가 거짓(pvp 꺼짐·같은 팀이고 아군 피해 꺼짐)이면 무시
            if (attacker != null && attacker.isPlayer() && attacker != player && !env.pvp()) return false;
            if (attacker instanceof Player ap && ap != player) {
                var mine = player.team();   // 바닐라 Player.canHarmPlayer: 맞은 쪽 팀이 때린 쪽과 같은 팀이고 아군 피해 꺼짐
                if (mine != null && mine.isAlliedTo(ap.team()) && !mine.isAllowFriendlyFire()) return false;
            }
            if (abilities.invulnerable && !type.bypassesInvulnerability()) return false;
            if (health <= 0.0f) return false;
            // LivingEntity.hurt: 자고 있으면 깬다(Player.stopSleeping = stopSleepInBed(true, true))
            if (player.isSleeping()) PlayerSleeping.stopSleepInBed(player, player.currentLevel, true, true);
            if (attacker != null) player.setLastHurtByMob(attacker);
            if (causing instanceof Player cp) player.markHurtByPlayer(cp);
            if (type.scalesWithDifficulty(causing instanceof LivingEntity)) {   // Player.hurt: 난이도 배율(scaling)
                Difficulty d = env.difficulty();
                if (d == Difficulty.PEACEFUL) amount = 0.0f;
                if (d == Difficulty.EASY) amount = Math.min(amount / 2.0f + 1.0f, amount);
                if (d == Difficulty.HARD) amount = amount * 3.0f / 2.0f;
            }
            if (amount == 0.0f) return false;
            dealt = amount;
            if (amount > 0.0f && shield != null
                    && Shields.isDamageSourceBlocked(player.isBlocking(), player.yaw(), player.x(), player.z(), shield)) {
                Shields.hurtCurrentlyUsedShield(player, amount, shield.level());
                blockedAmount = amount;
                amount = 0.0f;
                if (shield.directLiving() && shield.attacker() != null) Shields.blockUsingShield(player, shield.attacker());
                blocked = true;
            }
            if (invulnerableTime > 10) {
                if (amount <= lastHurt) return false;
                actuallyHurt(type, amount - lastHurt, causing, direct);
                lastHurt = amount;
                newHurt = false;
            } else {
                lastHurt = amount;
                invulnerableTime = 20;
                actuallyHurt(type, amount, causing, direct);
                hurtTime = 10;
                newHurt = true;
            }
            died = health <= 0.0f;
        }
        lastHurtFresh = newHurt;
        if (newHurt && !type.noImpact() && (!blocked || amount > 0.0f)) player.markHurt();   // 완전히 막으면 markHurt 없음 → 속도 패킷 0건
        if (newHurt) {   // 막았으면 damage_event 대신 엔티티 이벤트 29(방패 소리)
            Packet ev = blocked ? new EntityEvent(player.entityId(), (byte) 29)
                    : causing == null && direct == null && pos == null ? DamageEvent.environmental(player.entityId(), type.registryId())
                    : new DamageEvent(player.entityId(), type.registryId(), DamageSource.idOf(causing), DamageSource.idOf(direct), pos);
            player.send(ev);
            env.broadcastToTrackers(player.entityId(), ev);
        }
        if (died && !checkTotemDeathProtection(type)) die(type, causing, direct);
        if (blockedAmount > 0.0f && blockedAmount < 3.4028235E37F) player.awardStat("minecraft:damage_blocked_by_shield", Math.round(blockedAmount * 10.0f));
        {   // 바닐라 LivingEntity.hurt 끝: 맞은 쪽이 플레이어 → entity_hurt_player, 때린 쪽(원인)이 플레이어 → player_hurt_entity
            final float fDealt = dealt, fTaken = amount;
            final boolean fBlocked = blocked;
            var padv = player.advancements();
            var dmg = padv != null && (padv.hasListeners("minecraft:entity_hurt_player")
                    || causing instanceof Player cp0 && cp0.advancements() != null && cp0.advancements().hasListeners("minecraft:player_hurt_entity"))
                    ? KillCredit.source(type.key(), causing, direct) : null;
            if (dmg != null && padv != null) player.triggerAdvancement(a -> a.triggerEntityHurtPlayer(dmg, fDealt, fTaken, fBlocked));
            if (dmg != null && causing instanceof Player ap) {
                var victim = KillCredit.lootOf(player);
                ap.triggerAdvancement(a -> a.triggerHurtEntity(victim, dmg, fDealt, fTaken, fBlocked));
            }
        }
        return !blocked;
    }

    /** 바닐라 Player.actuallyHurt: 방어구(내구도 소모 후 CombatRules) → 저항 효과 → 보호 인챈트 → 흡수 → 소모 → 기록 → 체력. 몹과 같은 CombatRules. */
    private void actuallyHurt(DamageType type, float amount, Rider causing, Rider direct) {
        if (!type.bypassesArmor()) {   // getDamageAfterArmorAbsorb: 방어구 내구도를 깎고 CombatRules.getDamageAfterAbsorb
            player.hurtArmor(amount);   // 바닐라 순서: 내구도를 먼저 깎고(깨지면 빠진 뒤) 방어력을 읽는다
            float armor = player.armorValue(), toughness = player.armorToughness();
            amount = CombatRules.getDamageAfterAbsorb(amount, armor, toughness, EnchantEffects.weaponOf(direct));
        }
        amount = getDamageAfterMagicAbsorb(type, amount);
        float afterAbsorb = Math.max(amount - absorption, 0.0f);
        setAbsorptionLocked(absorption - (amount - afterAbsorb));
        if (afterAbsorb != 0.0f) {
            if (!abilities.invulnerable) food.addExhaustion(type.exhaustion());
            combat.recordDamage(type, afterAbsorb, fallLocation(), fallDistance, tickCount, health > 0, DamageSource.nameOf(causing), DamageSource.nameOf(direct));
            setHealthRaw(health - afterAbsorb);
        }
    }

    /** LivingEntity.getDamageAfterMagicAbsorb: bypasses_effects → 그대로, 저항 효과, 0 이하면 0, bypasses_enchantments → 그대로, 보호 인챈트. */
    private float getDamageAfterMagicAbsorb(DamageType type, float amount) {
        if (type.bypassesEffects()) return amount;
        MobEffectInstance resistance = effects.get(MobEffect.RESISTANCE);
        if (resistance != null && !type.bypassesResistance()) amount = CombatRules.afterResistance(amount, resistance.amplifier());
        if (amount <= 0.0f) return 0.0f;
        if (type.bypassesEnchantments()) return amount;
        PlayerInventory inv = player.inventory();
        float prot = CombatRules.damageProtection(type.key(), inv.get(5), inv.get(6), inv.get(7), inv.get(8));
        if (prot > 0.0f) amount = CombatRules.getDamageAfterMagicAbsorb(amount, prot);
        return amount;
    }

    /**
     * LivingEntity.checkTotemDeathProtection(1.21.1, ServerPlayer 분기 포함): bypasses_invulnerability 가 아니면 주 손 → 보조 손에서
     * 불사의 토템을 하나 줄이고, used:totem_of_undying 통계·used_totem 발전과제·item_interact_finish 게임 이벤트,
     * 체력 1·효과 전부 제거·재생 II 900·흡수 II 100·화염 저항 800, 엔티티 이벤트 35(입자·item.totem.use 소리는 클라이언트).
     */
    boolean checkTotemDeathProtection(DamageType type) {
        if (type.bypassesInvulnerability()) return false;
        PlayerInventory inv = player.inventory();
        ItemStack used = null;
        for (int ws : new int[] {inv.heldWindowSlot(), 45}) {
            ItemStack s = inv.get(ws);
            if (!s.isEmpty() && "minecraft:totem_of_undying".equals(org.domi.furnace.server.item.ItemTools.itemName(s))) {
                used = s;   // 바닐라: 줄이기 전 스택 복사(개수 그대로)
                inv.set(ws, s.withCount(s.count() - 1));
                break;
            }
        }
        if (used == null) return false;
        player.awardStat("minecraft:used:minecraft:totem_of_undying", 1);
        var adv = player.advancements();
        if (adv != null) adv.trigger("minecraft:used_totem", org.domi.furnace.server.ServerCore.lootStackOf(used));
        var level = player.currentLevel;
        if (level != null) level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ITEM_INTERACT_FINISH,
                new Vec3(player.x(), player.y(), player.z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(player));
        synchronized (this) { setHealthRaw(1.0f); }
        removeAllEffects();
        addEffect(new MobEffectInstance(MobEffect.REGENERATION, 900, 1));
        addEffect(new MobEffectInstance(MobEffect.ABSORPTION, 100, 1));
        addEffect(new MobEffectInstance(MobEffect.FIRE_RESISTANCE, 800, 0));
        Packet ev = new EntityEvent(player.entityId(), (byte) 35);
        player.send(ev);
        env.broadcastToTrackers(player.entityId(), ev);
        return true;
    }

    /** 이 플레이어를 보는 사람 모두에게(본인 제외) 패킷. */
    public void broadcastToTrackers(Packet p) { env.broadcastToTrackers(player.entityId(), p); }

    /** 바닐라 FallLocation.getCurrentFallLocation: 마지막으로 매달린 블록, 없고 물속이면 water. */
    private String fallLocation() {
        if (lastClimbable != null) return lastClimbable;
        return inWater ? "water" : null;
    }

    /** 바닐라 ServerPlayer.die: 사망 메시지·사망 화면, 인벤토리 처리, 엔티티 이벤트 3, 마지막 사망 위치. */
    private void die(DamageType type, Rider causing, Rider direct) {
        boolean showMessages = env.gameRule("showDeathMessages");
        boolean keepInventory = env.gameRule("keepInventory");
        Text name = player.displayName();
        Text message;
        boolean spectator;
        synchronized (this) {
            Targetable credit = player.getKillCredit();
            message = showMessages ? combat.getDeathMessage(name, credit == null ? null : credit.displayName()) : null;
            spectator = gameMode == GameMode.SPECTATOR;
            combat.recheckStatus(tickCount, false);
            lastDeathDimension = "minecraft:overworld";
            lastDeathPos = new int[]{(int) Math.floor(player.x()), (int) Math.floor(player.y()), (int) Math.floor(player.z())};
        }
        DeathOutcome outcome = env.death(player, message, keepInventory);
        synchronized (this) {
            keepLevelOnRespawn = outcome.keepLevel();
        }
        if (outcome.message() != null) {
            player.send(new PlayerCombatKill(player.entityId(), player.localize(outcome.message())));
            env.broadcastDeathMessage(player, outcome.message());
        } else {
            player.send(new PlayerCombatKill(player.entityId(), Text.empty()));
        }
        DeathListener l = deathListener;
        if (l != null && !spectator) l.onDeath(outcome.keepInventory());
        var board = player.scoreboard();   // 바닐라 ServerPlayer.die: deathCount 기준 +1
        if (board != null) board.incrementCriteria(player, org.domi.furnace.server.scores.ObjectiveCriteria.DEATH_COUNT);
        Targetable killer = player.getKillCredit();
        if (killer instanceof Rider kr) {   // killed_by:<종류> 통계 → killer.awardKillScore(this, deathScore, source)
            player.awardStat("minecraft:killed_by:" + kr.typeName(), 1);
            KillCredit.award(killer, player, KillCredit.source(type.key(), causing, direct));
        }
        // awardStat(DEATHS), resetStat(TIME_SINCE_DEATH), resetStat(TIME_SINCE_REST)
        player.awardStat("minecraft:deaths", 1);
        player.resetStat("minecraft:time_since_death");
        player.resetStat("minecraft:time_since_rest");
        Packet ev = new EntityEvent(player.entityId(), (byte) 3);
        player.send(ev);
        env.broadcastToTrackers(player.entityId(), ev);
    }

    private boolean keepLevelOnRespawn;

    /** 플러그인 API setHealth(CraftBukkit 과 같다): 0 이하로 내려 살아 있던 플레이어는 일반 사망 처리. */
    public void setHealth(float h) {
        boolean kill;
        synchronized (this) {
            boolean alive = health > 0.0f;
            setHealthRaw(h);
            kill = alive && health <= 0.0f;
        }
        if (kill) die(DamageType.GENERIC, null, null);
    }

    public synchronized void setFoodLevel(int food) { this.food.foodLevel(Math.clamp(food, 0, 20)); }

    // ------------------------------------------------------------------ 이동(낙하·소모)

    /**
     * 수락된 위치 패킷 하나마다 — 바닐라 handleMovePlayer 순서: 점프 소모 → move(경로에 물이 있으면 낙하 초기화)
     * → doCheckFallDamage → 위로 움직였으면 낙하 초기화 → 이동 거리 소모.
     *
     * @param wasOnGround  이 이동 전 서버가 알던 onGround
     * @param landingBlock 발밑 블록 이름(getOnPosLegacy), 청크가 없으면 null(낙하 판정 건너뜀)
     * @param inWaterNow   이동 후 몸이 물에 닿아 있는가
     * @param waterOnPath  이동 경로(이전 → 새 위치)에 물이 있는가(Entity.move 의 낙하 중 물 레이캐스트)
     */
    public void onMove(double dx, double dy, double dz, boolean wasOnGround, boolean onGround, boolean sprinting, boolean sneaking,
                       boolean inWaterNow, boolean eyeInWater, String climbable, String landingBlock, boolean waterOnPath) {
        float fallDamage = 0;
        synchronized (this) {
            if (wasOnGround && !onGround && dy > 0.0) causeFoodExhaustionLocked(sprinting ? 0.2f : 0.05f);   // jumpFromGround
            if (waterOnPath && fallDistance != 0.0f && dx * dx + dy * dy + dz * dz >= 1.0) fallDistance = 0.0f;
            if (landingBlock != null) fallDamage = checkFallDamage(dy, onGround, inWaterNow, landingBlock, sneaking);
        }
        // 바닐라 fallOn → causeFallDamage → hurt 는 낙하 거리를 초기화하기 전에 일어난다(전투 기록이 낙하 거리를 본다).
        if (fallDamage > 0) hurt(DamageType.FALL, fallDamage);
        synchronized (this) {
            if (landingBlock != null && onGround) {
                fallDistance = 0.0f;
                lastClimbable = null;
            }
            if (dy > 0.0) fallDistance = 0.0f;
            checkMovementStatistics(dx, dy, dz, onGround, sprinting, sneaking, inWaterNow, eyeInWater, climbable);
        }
    }

    private void causeFoodExhaustionLocked(float amount) {
        if (!abilities.invulnerable) food.addExhaustion(amount);
    }

    /** 바닐라 ServerPlayer.checkMovementStatistics 의 허기 소모 부분(수영 자세 판정은 아직 없어 물속 이동으로 본다). */
    private void checkMovementStatistics(double dx, double dy, double dz, boolean onGround, boolean sprinting, boolean sneaking,
                                         boolean inWaterNow, boolean eyeInWater, String climbable) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) return;
        if (eyeInWater) {
            int cm = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0f);
            if (cm > 0) causeFoodExhaustionLocked(0.01f * cm * 0.01f);
        } else if (inWaterNow) {
            int cm = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0f);
            if (cm > 0) causeFoodExhaustionLocked(0.01f * cm * 0.01f);
        } else if (climbable != null) {
            // 오르기: 통계만, 소모 없음
        } else if (onGround) {
            int cm = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0f);
            if (cm > 0) causeFoodExhaustionLocked((sprinting ? 0.1f : 0.0f) * cm * 0.01f);
        }
    }

    /**
     * 바닐라 LivingEntity/Entity.checkFallDamage: 물에 닿으면 낙하 거리 초기화, 착지하면 블록별 fallOn 으로 피해 후 초기화,
     * 떨어지는 중이면 누적. 반환값은 줄 낙하 피해(없으면 0).
     */
    private float checkFallDamage(double dy, boolean onGround, boolean inWaterNow, String landingBlock, boolean sneaking) {
        if (inWaterNow) {
            inWater = true;
            fallDistance = 0.0f;
        }
        float damage = 0;
        if (onGround) {
            if (fallDistance > 0.0f) damage = fallOn(landingBlock, fallDistance, sneaking);
            // 초기화는 피해를 준 뒤(onMove)
        } else if (dy < 0.0) {
            fallDistance -= (float) dy;
        }
        return damage;
    }

    /** 블록별 Block.fallOn → Player/LivingEntity.causeFallDamage. 비행 가능하면 피해 없음. */
    private float fallOn(String block, float distance, boolean sneaking) {
        float multiplier = fallMultiplier(block, sneaking);
        if (multiplier < 0 || abilities.mayfly) return 0;
        MobEffectInstance jump = effects.get(MobEffect.JUMP_BOOST);
        boolean impulse = false;
        if (ignoreFallFromImpulse && impulsePos != null) {   // ServerPlayer.causeFallDamage: 충격 높이보다 위에서 내려앉으면 면제, 아니면 그 높이까지만 센다
            double top = impulsePos[1];
            tryResetCurrentImpulseContext();
            if (top < impulseY()) return 0;
            distance = Math.min(distance, (float) (top - impulseY()));
            impulse = true;
        }
        float over = distance - SAFE_FALL_DISTANCE - (jump == null ? 0.0f : jump.amplifier() + 1);
        float dmg = (float) Math.ceil((double) (over * multiplier) * 1.0);
        if (impulse && dmg > 0) resetCurrentImpulseContext();
        return dmg;
    }

    /** -1 = 피해 없음(가루눈). */
    static float fallMultiplier(String block, boolean sneaking) {
        if (block == null) return 1.0f;
        if (block.equals("minecraft:hay_block") || block.equals("minecraft:honey_block")) return 0.2f;
        if (block.equals("minecraft:slime_block")) return sneaking ? 1.0f : 0.0f;
        if (block.equals("minecraft:powder_snow")) return -1;
        if (block.endsWith("_bed")) return 0.5f;
        return 1.0f;
    }

    // ------------------------------------------------------------------ 틱

    /**
     * 매 틱(소유 region): 무적 틱 감소 → 물·사다리 상태 → 공허·보더·익사 → 사망 틱 → 평화 회복 → 허기 → 변경 전송.
     * 순서는 바닐라 ServerPlayer.tick + doTick(Player.tick → LivingEntity.baseTick → aiStep → FoodData.tick).
     */
    public void tick(Surroundings s) {
        player.combatTick();
        java.util.List<Runnable> after = new java.util.ArrayList<>(2);
        java.util.List<Packet> toSelf = new java.util.ArrayList<>(3);
        java.util.List<Packet> toTrackers = new java.util.ArrayList<>(2);
        synchronized (this) {
            tickCount++;
            if (impulseGrace > 0) impulseGrace--;
            if (invulnerableTime > 0) invulnerableTime--;
            if (spawnInvulnerableTime > 0) spawnInvulnerableTime--;
            boolean alive = health > 0.0f;
            if (s.chunkLoaded()) {
                inWater = s.inWater() && gameMode != GameMode.SPECTATOR;
                if (inWater) fallDistance = 0.0f;
                if (s.climbable() != null && gameMode != GameMode.SPECTATOR) {
                    lastClimbable = s.climbable();
                    fallDistance = 0.0f;   // travel → handleOnClimbable → resetFallDistance
                }
                if (s.stuckInBlock()) fallDistance = 0.0f;   // 거미줄·가루눈·달콤한 열매 덤불: makeStuckInBlock
            }
            if (player.y() < s.minBuildHeight() - 64) after.add(() -> hurt(DamageType.OUT_OF_WORLD, 4.0f));
            if (alive) {
                float border = env.borderDamage(player.x(), player.y(), player.z());
                if (border > 0) after.add(() -> hurt(DamageType.OUTSIDE_BORDER, border));
                if (s.chunkLoaded() && s.eyeInWater() && !s.eyeInBubbleColumn()) {
                    if (!abilities.invulnerable && !effects.containsKey(MobEffect.WATER_BREATHING)) {
                        air = decreaseAirSupply(air);
                        if (air == -20) {
                            air = 0;
                            after.add(() -> hurt(DamageType.DROWN, 2.0f));
                        }
                    }
                } else if (air < MAX_AIR) {
                    air = Math.min(air + 4, MAX_AIR);
                }
            }
            if (hurtTime > 0) hurtTime--;
            if (alive) tickEffectsLocked(toSelf);
            if (effects.containsKey(MobEffect.SLOW_FALLING) || effects.containsKey(MobEffect.LEVITATION)) fallDistance = 0.0f;
            if (health <= 0.0f) {
                if (++deathTime >= 20 && !removedAfterDeath) {
                    removedAfterDeath = true;
                    toSelf.add(new EntityEvent(player.entityId(), (byte) 60));
                    toTrackers.add(new EntityEvent(player.entityId(), (byte) 60));
                }
            } else {
                Difficulty difficulty = env.difficulty();
                if (difficulty == Difficulty.PEACEFUL && env.gameRule("naturalRegeneration")) {
                    if (health < maxHealthLocked() && tickCount % 20 == 0) heal(1.0f);
                    if (food.saturationLevel() < 20.0f && tickCount % 20 == 0) food.saturationLevel(food.saturationLevel() + 1.0f);
                    if (food.needsFood() && tickCount % 10 == 0) food.foodLevel(food.foodLevel() + 1);
                }
                food.tick(foodOwner);
                combat.recheckStatus(tickCount, true);
            }
        }
        for (Runnable r : after) r.run();
        if (effectsExpired) {   // 끝난 효과마다 바닐라는 onEffectRemoved 에서 effects_changed — 잠금 밖에서 한 번
            effectsExpired = false;
            notifyEffectsChanged();
        }
        runPendingEffectHurts();
        applyPendingStarve();
        final int epoch;
        synchronized (this) {
            collectChanges(toSelf, toTrackers);
            epoch = respawnEpoch;
        }
        // 모은 뒤 리스폰이 끼면(연결 스레드) 낡은 체력 0 등이 Respawn 패킷 뒤에 가서 클라가 죽은 채로 남는다 — 쓰는 순간 세대를 비교해 버린다
        for (Packet p : toSelf) player.send(p, () -> respawnEpoch == epoch);
        for (Packet p : toTrackers) env.broadcastToTrackers(player.entityId(), p);
    }

    /** 엔티티 데이터(체력·공기) 동기화, 그다음 바닐라 doTick 끝: 체력·허기·포화 0 여부가 바뀌면 SetHealth, 총 경험치가 바뀌면 SetExperience. */
    private void collectChanges(java.util.List<Packet> toSelf, java.util.List<Packet> toTrackers) {
        // 바닐라 틱 순서: 엔티티 데이터(레벨 틱의 엔티티 추적) → 체력·경험치(플레이어 doTick)
        java.util.List<SetEntityData.Item> data = new java.util.ArrayList<>(2);
        if (air != lastDataAir) {
            lastDataAir = air;
            data.add(new SetEntityData.Item(SetEntityData.AIR_SUPPLY, SetEntityData.SERIALIZER_INT, air));
        }
        if (health != lastDataHealth) {
            lastDataHealth = health;
            data.add(new SetEntityData.Item(SetEntityData.HEALTH, SetEntityData.SERIALIZER_FLOAT, health));
        }
        if (absorption != lastDataAbsorption) {
            lastDataAbsorption = absorption;
            data.add(new SetEntityData.Item(SetEntityData.PLAYER_ABSORPTION, SetEntityData.SERIALIZER_FLOAT, absorption));
        }
        if (!data.isEmpty()) {
            SetEntityData pkt = new SetEntityData(player.entityId(), java.util.List.copyOf(data));
            toSelf.add(pkt);
            toTrackers.add(pkt);
        }
        boolean saturationZero = food.saturationLevel() == 0.0f;
        if (health != lastSentHealth || lastSentFood != food.foodLevel() || saturationZero != lastFoodSaturationZero) {
            toSelf.add(new SetHealth(health, food.foodLevel(), food.saturationLevel()));
            lastSentHealth = health;
            lastSentFood = food.foodLevel();
            lastFoodSaturationZero = saturationZero;
        }
        var board = player.scoreboard();
        if (board != null) {
            if (health + absorption != lastRecordedHealthAndAbsorption) {
                lastRecordedHealthAndAbsorption = health + absorption;
                board.updateForCriteria(player, org.domi.furnace.server.scores.ObjectiveCriteria.HEALTH, (int) Math.ceil(lastRecordedHealthAndAbsorption));
            }
            if (food.foodLevel() != lastRecordedFoodLevel) {
                lastRecordedFoodLevel = food.foodLevel();
                board.updateForCriteria(player, org.domi.furnace.server.scores.ObjectiveCriteria.FOOD, lastRecordedFoodLevel);
            }
            if (air != lastRecordedAirLevel) {
                lastRecordedAirLevel = air;
                board.updateForCriteria(player, org.domi.furnace.server.scores.ObjectiveCriteria.AIR, air);
            }
            int armor = (int) player.armorValue();   // 바닐라 getArmorValue = Mth.floor(속성값)
            if (armor != lastRecordedArmor) {
                lastRecordedArmor = armor;
                board.updateForCriteria(player, org.domi.furnace.server.scores.ObjectiveCriteria.ARMOR, armor);
            }
            if (xpTotal != lastRecordedExperience) {
                lastRecordedExperience = xpTotal;
                board.updateForCriteria(player, org.domi.furnace.server.scores.ObjectiveCriteria.EXPERIENCE, xpTotal);
            }
            if (xpLevel != lastRecordedLevel) {
                lastRecordedLevel = xpLevel;
                board.updateForCriteria(player, org.domi.furnace.server.scores.ObjectiveCriteria.LEVEL, xpLevel);
            }
        }
        if (xpTotal != lastSentExp) {
            lastSentExp = xpTotal;
            toSelf.add(new SetExperience(xpProgress, xpLevel, xpTotal));
        }
    }

    private final FoodData.Owner foodOwner = new FoodData.Owner() {
        public Difficulty difficulty() { return env.difficulty(); }
        public boolean naturalRegeneration() { return env.gameRule("naturalRegeneration"); }
        public boolean isHurt() { return health > 0.0f && health < maxHealthLocked(); }
        public float health() { return health; }
        public void heal(float amount) { PlayerVitals.this.heal(amount); }
        // 틱 잠금 밖에서 피해를 줘야 사망 처리(패킷·메시지)가 잠금을 오래 쥐지 않는다 — 그래도 순서는 같은 틱 안이다.
        public void starve() { pendingStarve = true; }
    };
    private boolean pendingStarve;

    /** 굶주림 피해는 FoodData.tick 안에서 정해지고 잠금 밖에서 적용된다. */
    private void applyPendingStarve() {
        boolean s;
        synchronized (this) {
            s = pendingStarve;
            pendingStarve = false;
        }
        if (s) hurt(DamageType.STARVE, 1.0f);
    }

    // ------------------------------------------------------------------ 경험치

    public synchronized int xpLevel() { return xpLevel; }

    public synchronized int xpTotal() { return xpTotal; }

    public synchronized float xpProgress() { return xpProgress; }

    public synchronized int score() { return score; }

    /** 바닐라 Player.getXpNeededForNextLevel. */
    public synchronized int xpNeededForNextLevel() { return xpNeeded(xpLevel); }

    static int xpNeeded(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    /** 바닐라 Player.giveExperiencePoints. */
    public synchronized void giveExperiencePoints(int amount) {
        score += amount;
        xpProgress += (float) amount / (float) xpNeeded(xpLevel);
        xpTotal = Math.clamp((long) xpTotal + amount, 0, Integer.MAX_VALUE);
        while (xpProgress < 0.0f) {
            float f = xpProgress * (float) xpNeeded(xpLevel);
            if (xpLevel > 0) {
                giveLevelsLocked(-1);
                xpProgress = 1.0f + f / (float) xpNeeded(xpLevel);
            } else {
                giveLevelsLocked(-1);
                xpProgress = 0.0f;
            }
        }
        while (xpProgress >= 1.0f) {
            xpProgress = (xpProgress - 1.0f) * (float) xpNeeded(xpLevel);
            giveLevelsLocked(1);
            xpProgress = xpProgress / (float) xpNeeded(xpLevel);
        }
    }

    /** 바닐라 ServerPlayer.giveExperienceLevels(Player.giveExperienceLevels + lastSentExp = -1). */
    public synchronized void giveExperienceLevels(int levels) {
        giveLevelsLocked(levels);
        lastSentExp = -1;
    }

    private void giveLevelsLocked(int levels) {
        xpLevel += levels;
        if (xpLevel < 0) {
            xpLevel = 0;
            xpProgress = 0.0f;
            xpTotal = 0;
        }
    }

    public synchronized int enchantmentSeed() { return xpSeed; }

    /** 바닐라 Player.onEnchantmentPerformed: 레벨을 깎고(0 미만이면 0) 새 마법 부여 시드. */
    public synchronized void onEnchantmentPerformed(int levels) {
        xpLevel -= levels;
        if (xpLevel < 0) {
            xpLevel = 0;
            xpProgress = 0.0f;
            xpTotal = 0;
        }
        xpSeed = java.util.concurrent.ThreadLocalRandom.current().nextInt();
        lastSentExp = -1;
    }

    /** 바닐라 ServerPlayer.setExperienceLevels. */
    public synchronized void setExperienceLevels(int level) {
        xpLevel = level;
        lastSentExp = -1;
    }

    /** 바닐라 ServerPlayer.setExperiencePoints(진행도만): 다음 레벨 필요량 이상이면 false(명령이 거부). */
    public synchronized boolean setExperiencePoints(int points) {
        float needed = (float) xpNeeded(xpLevel);
        if (points >= needed) return false;
        float max = (needed - 1.0f) / needed;
        xpProgress = Math.clamp((float) points / needed, 0.0f, max);
        lastSentExp = -1;
        return true;
    }

    // ------------------------------------------------------------------ 스폰·리스폰

    /** /spawnpoint — 바닐라 ServerPlayer.setRespawnPosition(dim, pos, angle, forced=true, sendMessage=false). */
    public synchronized void setRespawnPosition(String dimension, int[] pos, float angle, boolean forced) {
        if (pos == null) {
            respawnPos = null;
            respawnDimension = "minecraft:overworld";
            respawnAngle = 0f;
            respawnForced = false;
            return;
        }
        respawnPos = pos.clone();
        respawnDimension = dimension;
        respawnAngle = angle;
        respawnForced = forced;
    }

    /**
     * ServerPlayer.setRespawnPosition(dim, pos, angle, forced, sendMessage): 같은 차원·같은 칸이 아니고 sendMessage 면
     * "block.minecraft.set_spawn" 시스템 메시지. pos 가 null 이면 지운다(오버월드·0·강제 아님).
     */
    public void setRespawnPosition(String dimension, int[] pos, float angle, boolean forced, boolean sendMessage) {
        boolean message;
        synchronized (this) {
            message = pos != null && sendMessage && !(java.util.Arrays.equals(pos, respawnPos) && dimension.equals(respawnDimension));
            setRespawnPosition(dimension, pos, angle, forced);
        }
        if (message && player != null) player.sendSystemMessage(Text.translatable("block.minecraft.set_spawn"), false);
    }

    public synchronized int[] respawnPos() { return respawnPos == null ? null : respawnPos.clone(); }

    public synchronized float respawnAngle() { return respawnAngle; }

    public synchronized boolean respawnForced() { return respawnForced; }

    /** 리스폰 지점의 차원(SpawnDimension). */
    public synchronized String respawnDimension() { return respawnDimension; }

    /** 능력의 무적(크리에이티브·관전) — 지옥문 대기 시간이 creative delay 가 된다. */
    public synchronized boolean invulnerableAbility() { return abilities.invulnerable; }

    /** ServerPlayer.changeDimension 끝: lastSentExp = -1, lastSentHealth = -1, lastSentFood = -1 — 다음 틱에 체력·경험치를 다시 보낸다. */
    public synchronized void resetSentInfo() {
        lastSentExp = -1;
        lastSentHealth = -1.0F;
        lastSentFood = -1;
    }

    public synchronized String lastDeathDimension() { return lastDeathDimension; }

    public synchronized int[] lastDeathPos() { return lastDeathPos == null ? null : lastDeathPos.clone(); }

    /**
     * 바닐라 PlayerList.respawn 의 새 ServerPlayer + copyFrom(keepEverything=false): 체력·허기·공기·낙하·전투 기록 초기화,
     * 사망 때 정한 경험치 유지(keepInventory 게임룰 또는 PlayerDeathEvent.keepLevel)이거나 관전자였으면 경험치·점수 유지,
     * 게임 모드·스폰 지점·마지막 사망 위치·경험치 씨앗은 유지.
     */
    public synchronized void respawn() {
        respawnEpoch++;
        boolean keepXp = keepLevelOnRespawn || gameMode == GameMode.SPECTATOR;
        keepLevelOnRespawn = false;
        effects.clear();
        health = MAX_HEALTH;
        absorption = 0;
        food = new FoodData();
        air = MAX_AIR;
        fallDistance = 0;
        invulnerableTime = 0;
        hurtTime = 0;
        deathTime = 0;
        lastHurt = 0;
        spawnInvulnerableTime = SPAWN_INVULNERABLE_TICKS;
        player.clearHurtRecords();
        combat = new CombatTracker();
        inWater = false;
        lastClimbable = null;
        removedAfterDeath = false;
        abilities.flying = false;
        abilities.flyingSpeed = 0.05f;
        abilities.walkingSpeed = 0.1f;
        abilities.applyGameMode(gameMode);
        if (!keepXp) {
            xpLevel = 0;
            xpTotal = 0;
            xpProgress = 0;
            score = 0;
        }
        lastSentHealth = -1.0e8f;
        lastSentFood = -99999999;
        lastFoodSaturationZero = true;
        lastSentExp = -1;
        lastDataHealth = 1.0f;
        lastDataAir = MAX_AIR;
        lastDataAbsorption = 0.0f;
    }

    /** 리스폰 세대: respawn() 마다 올라간다. tick 이 모은 패킷이 리스폰을 넘어 낡은 채로 나가는 걸 막는 데 쓴다. */
    private volatile int respawnEpoch;

    /** 접속 때 바로 보낼 체력·허기·경험치·엔티티 데이터(바닐라는 첫 doTick 에서 같은 값을 보낸다 — 이후 틱은 바뀔 때만). */
    public synchronized java.util.List<Packet> joinPackets() {
        java.util.List<Packet> out = new java.util.ArrayList<>(3);
        collectChanges(out, new java.util.ArrayList<>());
        return out;
    }

    /** 저장용 NBT(이 객체가 다루는 키만). 스냅샷 시점의 값이라 IO 풀에서 써도 안전하다. */
    public synchronized NbtCompound saveData() {
        NbtCompound tag = new NbtCompound();
        save(tag);
        tag.putCompound("warden_spawn_tracker", wardenSpawnTracker.save());
        return tag;
    }

    /** 리스폰·접속 직후 클라이언트에 맞출 경험치 패킷. */
    public synchronized SetExperience experiencePacket() { return new SetExperience(xpProgress, xpLevel, xpTotal); }

    // ------------------------------------------------------------------ 상태 효과(LivingEntity.activeEffects)

    private final MobEffect.Host effectHost = new MobEffect.Host() {
        public float health() { return health; }

        public float maxHealth() { return maxHealthLocked(); }

        public void heal(float amount) { PlayerVitals.this.heal(amount); }

        public boolean hurtBy(String key, float amount) {
            pendingEffectHurts.add(new Object[]{key, amount});   // 잠금 안에서는 피해 처리(사망 패킷)를 미룬다
            return true;
        }

        public boolean isPlayer() { return true; }

        public void causeFoodExhaustion(float amount) { causeFoodExhaustionLocked(amount); }

        public void eat(int nutrition, float saturationModifier) { food.eat(nutrition, saturationModifier); }

        public float absorption() { return absorption; }

        public void setAbsorption(float amount) { setAbsorptionLocked(amount); }
    };

    /** 효과 대상(마시기·비콘 등이 즉시 효과를 쓸 때). */
    public MobEffect.Host effectHost() { return effectHost; }

    public synchronized boolean hasEffect(MobEffect e) { return effects.containsKey(e); }

    public synchronized MobEffectInstance getEffect(MobEffect e) {
        MobEffectInstance i = effects.get(e);
        return i == null ? null : i.deepCopy();
    }

    public synchronized java.util.List<MobEffectInstance> activeEffects() {
        java.util.List<MobEffectInstance> out = new java.util.ArrayList<>();
        for (MobEffectInstance e : effects.values()) out.add(e.deepCopy());
        return out;
    }

    /** 접속 직후 자기 효과를 클라이언트에 맞추는 패킷(PlayerList.placeNewPlayer). */
    public synchronized java.util.List<Packet> effectPackets() {
        java.util.List<Packet> out = new java.util.ArrayList<>();
        for (MobEffectInstance e : effects.values()) out.add(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(player.entityId(), e, false));
        return out;
    }

    /** 바닐라 addEffect: 새로 걸면 패킷 + 시작 효과, 이미 있으면 update(더 센 쪽). 바뀌었으면 true. */
    public boolean addEffect(MobEffectInstance incoming) {
        boolean changed;
        synchronized (this) {
            if (health <= 0.0f) return false;
            MobEffectInstance existing = effects.get(incoming.effect());
            if (existing == null) {
                effects.put(incoming.effect(), incoming);
                pendingEffectPackets.add(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(player.entityId(), incoming, true));
                incoming.effect().onEffectStarted(effectHost, incoming.amplifier());
                changed = true;
            } else if (existing.update(incoming)) {
                pendingEffectPackets.add(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(player.entityId(), existing, true));
                changed = true;
            } else {
                changed = false;
            }
        }
        flushEffectPackets();
        runPendingEffectHurts();
        if (changed) notifyEffectsChanged();
        return changed;
    }

    /** 바닐라 onEffectAdded/Updated/Removed → EFFECTS_CHANGED.trigger(player, source). 효과를 준 쪽(source)은 아직 넘기지 않아 null. */
    private void notifyEffectsChanged() { player.triggerAdvancement(a -> a.triggerEffectsChanged(null)); }

    private volatile boolean effectsExpired;

    /** 마시기 같은 즉시 효과(근접도 1.0). */
    public void applyInstantaneousEffect(MobEffect effect, int amplifier, double proximity) {
        synchronized (this) { effect.applyInstantenousEffect(effectHost, amplifier, proximity); }
        runPendingEffectHurts();
    }

    /** 바닐라 removeEffect: 있으면 지우고 패킷. 지웠으면 true. */
    public boolean removeEffect(MobEffect effect) {
        boolean removed;
        synchronized (this) {
            MobEffectInstance e = effects.remove(effect);
            removed = e != null;
            if (removed) onEffectRemovedLocked(e);
        }
        flushEffectPackets();
        if (removed) notifyEffectsChanged();
        return removed;
    }

    /** 바닐라 removeAllEffects(우유·/effect clear): 지운 개수. */
    public int removeAllEffects() {
        int n;
        synchronized (this) {
            n = effects.size();
            for (MobEffectInstance e : new java.util.ArrayList<>(effects.values())) {
                effects.remove(e.effect());
                onEffectRemovedLocked(e);
            }
        }
        flushEffectPackets();
        if (n > 0) notifyEffectsChanged();
        return n;
    }

    private void onEffectRemovedLocked(MobEffectInstance e) {
        pendingEffectPackets.add(new org.domi.furnace.server.net.protocol.play.RemoveMobEffect(player.entityId(), e.effect().registryId()));
        if (e.effect() == MobEffect.HEALTH_BOOST && health > maxHealthLocked()) health = maxHealthLocked();
        if (e.effect() == MobEffect.ABSORPTION) setAbsorptionLocked(absorption);
    }

    /** 바닐라 tickEffects: 효과마다 적용·감소, 끝난 것은 지운다(600틱마다 갱신 패킷). */
    private void tickEffectsLocked(java.util.List<Packet> toSelf) {
        for (MobEffectInstance e : new java.util.ArrayList<>(effects.values())) {
            boolean alive = e.tick(effectHost, tickCount, () -> pendingEffectPackets.add(
                    org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(player.entityId(), e, false)), () -> { });
            if (!alive) {
                effects.remove(e.effect());
                onEffectRemovedLocked(e);
                effectsExpired = true;
            } else if (e.duration() % 600 == 0) {
                pendingEffectPackets.add(org.domi.furnace.server.net.protocol.play.UpdateMobEffect.of(player.entityId(), e, false));
            }
        }
        toSelf.addAll(pendingEffectPackets);
        pendingEffectPackets.clear();
    }

    private void flushEffectPackets() {
        java.util.List<Packet> out;
        synchronized (this) {
            if (pendingEffectPackets.isEmpty()) return;
            out = new java.util.ArrayList<>(pendingEffectPackets);
            pendingEffectPackets.clear();
        }
        for (Packet p : out) player.send(p);
    }

    private void runPendingEffectHurts() {
        java.util.List<Object[]> hurts;
        synchronized (this) {
            if (pendingEffectHurts.isEmpty()) return;
            hurts = new java.util.ArrayList<>(pendingEffectHurts);
            pendingEffectHurts.clear();
        }
        for (Object[] h : hurts) hurt(DamageType.byKey((String) h[0]), (Float) h[1]);
    }
}
