// 플레이어 생존 상태 — 게임 모드·능력·체력·흡수·허기·경험치·공기·낙하 거리·무적 틱·사망/리스폰.
// 바닐라 ServerPlayer/Player/LivingEntity 의 해당 부분을 옮겼다. 모든 메서드는 이 객체로 동기화돼
// region 스레드(틱·낙하), 명령 스레드(/gamemode·/kill·/xp), netty 스레드(저장·리스폰)에서 안전하게 부를 수 있다.
// 블록을 봐야 하는 판정(물·사다리·착지 블록)은 소유 region 이 계산해 인자로 넘긴다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
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

        /**
         * 사망 직전(플러그인 PlayerDeathEvent) — 메시지(null = 알리지 않음)·떨어뜨릴 아이템·경험치·인벤토리/경험치 유지를 바꿀 수 있다.
         * drops 는 죽는 순간 인벤토리에서 모은 목록(소멸 저주 제외), droppedExp 는 바닐라 기본 경험치량, maxHealth 는 취소 때 되살릴 체력의 기본값이다.
         * 결과가 cancelled 면 죽지 않는다.
         */
        default DeathOutcome death(Player player, Text message, boolean keepInventory, List<ItemStack> drops, int droppedExp, float maxHealth) {
            return DeathOutcome.unchanged(message, keepInventory, drops, droppedExp);
        }

        /** 플러그인 이벤트 입구(피해·텔레포트). 기본은 아무것도 안 한다. */
        default org.domi.furnace.server.plugin.GameEvents gameEvents() { return org.domi.furnace.server.plugin.GameEvents.NONE; }

        /** 게임 모드가 바뀌기 직전(플러그인 PlayerGameModeChangeEvent) — false 면 바꾸지 않는다. */
        default boolean allowGameModeChange(Player player, GameMode mode) { return true; }

        /** 월드 보더 밖 피해량(0 = 없음). */
        default float borderDamage(double x, double y, double z) { return 0f; }

        /** 그 플레이어 기준 보더 밖 피해 — 플러그인이 enforce 한 개인 보더가 있으면 그것으로(PlayerViews). */
        default float borderDamage(Player player, double x, double y, double z) { return borderDamage(x, y, z); }

        Env DEFAULT = new Env() {
            public Difficulty difficulty() { return Difficulty.EASY; }
            public boolean gameRule(String name) { return !name.equals("keepInventory") && !name.equals("doImmediateRespawn"); }
            public boolean pvp() { return true; }
            public void broadcastSystemMessage(Text message) {}
            public void broadcastAll(Packet packet) {}
            public void broadcastToTrackers(int entityId, Packet packet) {}
        };
    }

    /**
     * 사망 처리 결과. drops 는 이 목록대로 던지고(keepInventory 와 무관), keepInventory 가 false 면 인벤토리를 비우되 itemsToKeep 와 같은 칸은 남긴다.
     * newExp·newLevel·newTotalExp 는 keepLevel 이 false 일 때 리스폰 경험치(바닐라 기본은 전부 0). dropExperience 가 false 면 구슬이 없다.
     * cancelled 면 죽지 않는다(체력이 0 이하면 reviveHealth 로) — 나머지 값은 보지 않는다.
     */
    public record DeathOutcome(Text message, boolean keepInventory, boolean keepLevel, List<ItemStack> drops, int droppedExp,
                               int newExp, int newLevel, int newTotalExp, List<ItemStack> itemsToKeep, boolean dropExperience,
                               boolean cancelled, float reviveHealth) {
        public DeathOutcome {
            if (itemsToKeep == null) itemsToKeep = List.of();
        }

        /** 플러그인이 아무것도 바꾸지 않은 기본 결과: keepLevel 은 keepInventory 와 같다. */
        public static DeathOutcome unchanged(Text message, boolean keepInventory, List<ItemStack> drops, int droppedExp) {
            return new DeathOutcome(message, keepInventory, keepInventory, drops, droppedExp, 0, 0, 0, List.of(), true, false, 0f);
        }
    }

    /** 죽을 때 소유 연결이 할 일(드롭 던지기·인벤토리 비우기 등). netty 이벤트 루프로 넘겨 실행된다. */
    public interface DeathListener {
        /**
         * @param drops     이벤트가 정한 떨어뜨릴 목록
         * @param originals 죽는 순간 인벤토리에서 뜬 스택(소멸 저주 제외) — 목록의 원래 스택은 이 객체 그대로다. 목록을 뜬 뒤 이벤트 루프가 처리한
         *                  패킷(버리기·창 클릭)이 인벤토리에서 꺼낸 아이템이 두 번 나오지 않게, 연결 쪽이 지금 인벤토리와 신원으로 맞춰 본다.
         * @param itemsToKeep keepInventory 가 false 일 때 비우지 않고 남길 아이템(Paper getItemsToKeep — 값으로 칸과 맞춘다, 남으면 새로 넣는다)
         */
        void onDeath(boolean keepInventory, List<ItemStack> drops, List<ItemStack> originals, List<ItemStack> itemsToKeep);
    }

    /**
     * 틱마다 region 이 블록을 보고 계산해 넘기는 주변 상태. inWall 은 Entity.isInWall(눈 높이 질식 블록), inPowderSnow 는 이번 틱 서버 move 의
     * checkInsideBlocks 가 가루눈에 들었는가(Entity.isInPowderSnow), groundNotAir 는 getBlockStateOnLegacy(0.2 아래 칸)가 공기가 아닌가(tryAddFrost).
     */
    public record Surroundings(boolean chunkLoaded, boolean inWater, boolean eyeInWater, boolean eyeInBubbleColumn,
                               String climbable, boolean stuckInBlock, int minBuildHeight, boolean inWall, boolean inPowderSnow, boolean groundNotAir) {
        public static final Surroundings NONE = new Surroundings(false, false, false, false, null, false, -64);

        public Surroundings(boolean chunkLoaded, boolean inWater, boolean eyeInWater, boolean eyeInBubbleColumn,
                            String climbable, boolean stuckInBlock, int minBuildHeight) {
            this(chunkLoaded, inWater, eyeInWater, eyeInBubbleColumn, climbable, stuckInBlock, minBuildHeight, false, false, false);
        }
    }

    private final Player player;
    private volatile Env env = Env.DEFAULT;
    private volatile DeathListener deathListener;

    private volatile GameMode gameMode = GameMode.SURVIVAL;
    private GameMode previousGameMode;
    private final Abilities abilities = new Abilities();

    private volatile float health = MAX_HEALTH;
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
    private CombatTracker combat = newCombat();

    /** 전투 진입/종료를 클라이언트에 알리는 추적기(바닐라 ServerPlayer.onEnterCombat/onLeaveCombat). */
    private CombatTracker newCombat() {
        CombatTracker c = new CombatTracker();
        c.hooks(new CombatTracker.Hooks() {
            public void enterCombat() { player.send(new org.domi.furnace.server.net.protocol.play.PlayerCombatEnter()); }
            public void leaveCombat(int duration) { player.send(new org.domi.furnace.server.net.protocol.play.PlayerCombatEnd(duration)); }
        });
        return c;
    }
    private boolean inWater;
    private String lastClimbable;              // 매달린 순간의 FallLocation 이름(칸을 다시 볼 수 없을 때만 쓴다)
    private org.domi.furnace.server.block.Pos lastClimbablePos;   // 바닐라 lastClimbablePos: 마지막으로 매달린 칸
    private boolean removedAfterDeath;

    private float lastSentHealth = -1.0e8f;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    private int lastSentExp = -99999999;
    /** 플러그인 레벨 변화 이벤트의 기준 레벨(-1 = 아직 없음). */
    private int levelEventLast = -1;
    // 바닐라 ServerPlayer.lastRecorded*: 점수판 읽기 전용 기준(health·food·air·armor·xp·level) 갱신용
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE, lastRecordedAirLevel = Integer.MIN_VALUE, lastRecordedArmor = Integer.MIN_VALUE,
            lastRecordedLevel = Integer.MIN_VALUE, lastRecordedExperience = Integer.MIN_VALUE;
    // 클라이언트가 아는 엔티티 데이터 — 새 엔티티는 바닐라 정의 기본값(체력 1.0, 공기 300)에서 시작한다.
    private float lastDataHealth = 1.0f;
    private int lastDataAir = MAX_AIR;
    /** Entity.ticksFrozen(DATA_TICKS_FROZEN): 가루눈 속에서 오르고 140 이면 완전히 언다. Player.syncLivingFlags 가 동기화한다. */
    private int ticksFrozen;
    /** tryAddFrost 가 이동 속도에 다는 얼음 수정자(없으면 null) — Player.syncAttributes 가 속성 맵에 맞춘다. */
    private volatile org.domi.furnace.server.attribute.AttributeModifier frostModifier;

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
        ticksFrozen = tag.getIntOr0("TicksFrozen");
        fallDistance = tag.getFloat("FallDistance");
        ignoreFallFromImpulse = tag.getBoolean("ignore_fall_damage_from_current_explosion");
        impulseGrace = tag.getIntOr0("current_impulse_context_reset_grace_time");
        spawnExtraParticlesOnFall = tag.getBoolean("spawn_extra_particles_on_fall");
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
        if (ticksFrozen > 0) tag.putInt("TicksFrozen", ticksFrozen);
        else tag.tags().remove("TicksFrozen");
        tag.putFloat("FallDistance", fallDistance);
        tag.putBoolean("ignore_fall_damage_from_current_explosion", ignoreFallFromImpulse);
        tag.putInt("current_impulse_context_reset_grace_time", impulseGrace);
        tag.putBoolean("spawn_extra_particles_on_fall", spawnExtraParticlesOnFall);
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

    public GameMode gameMode() { return gameMode; }

    public synchronized GameMode previousGameMode() { return previousGameMode; }

    public synchronized boolean isSpectator() { return gameMode == GameMode.SPECTATOR; }

    /** 마지막 환경 조사에서 몸이 물속이었나(Entity.isInWater). */
    public synchronized boolean isInWater() { return inWater; }

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
        if (mode == GameMode.SPECTATOR) player.removeEntitiesOnShoulder();   // ServerPlayer.setGameMode: 관전자가 되면 어깨의 몹을 내려놓는다
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

    public boolean isDeadOrDying() { return health <= 0.0f; }

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

    /** 철퇴가 땅 위의 대상을 때리면 켜지는 착지 먼지 기둥 입자 표시(바닐라 ServerPlayer.spawnExtraParticlesOnFall). 착지 때 한 번 쓰고 꺼진다. */
    public synchronized void setSpawnExtraParticlesOnFall(boolean v) { spawnExtraParticlesOnFall = v; }

    public synchronized boolean spawnExtraParticlesOnFall() { return spawnExtraParticlesOnFall; }

    private boolean spawnExtraParticlesOnFall;

    /** 이동 표본 처리 직전에 그 표본의 y 를 알린다(Region.processMoves). */
    public synchronized void currentMoveY(double y) { moveY = y; }

    /** 이번 이동의 착지 블록이 위를 향한 점적석 끝(석순 tip)인가 — 이름만으로는 모르므로 Region 이 상태를 보고 알려 준다. */
    public synchronized void landingStalagmite(boolean v) { landingStalagmite = v; }

    /** 지난 onMove 에서 블록 fallOn 이 불렸나(땅에 닿았고 낙하 거리가 0 보다 컸다) — Region 이 블록 쪽 fallOn(거북 알)을 부른다. */
    public synchronized boolean landedOnLastMove() { return landed; }

    private boolean landingStalagmite, landed;
    private DamageType fallDamageType = DamageType.FALL;

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

    private double safeFallBase = 3.0, fallMultiplierBase = 1.0;
    private java.util.List<org.domi.furnace.server.attribute.AttributeModifier> safeFallExtra = java.util.List.of(), fallMultiplierExtra = java.util.List.of();

    /** 속성 맵에서 넘어온 낙하 입력(소유 region 스레드): 기본값과 효과 밖 수정자. 효과 수정자(점프 강화 +1×(단계+1))는 지금 효과로 더한다. */
    public synchronized void fallInputs(double safeBase, java.util.List<org.domi.furnace.server.attribute.AttributeModifier> safeExtra,
            double multBase, java.util.List<org.domi.furnace.server.attribute.AttributeModifier> multExtra) {
        this.safeFallBase = safeBase;
        this.safeFallExtra = java.util.List.copyOf(safeExtra);
        this.fallMultiplierBase = multBase;
        this.fallMultiplierExtra = java.util.List.copyOf(multExtra);
    }

    private double fallAttribute(org.domi.furnace.server.attribute.Attribute attr, double base, java.util.List<org.domi.furnace.server.attribute.AttributeModifier> extra) {
        if (effects.isEmpty() && extra.isEmpty()) return attr.sanitize(base);
        java.util.Map<String, org.domi.furnace.server.attribute.AttributeModifier> mods = PlayerAttributes.effectModifiers(attr, new java.util.ArrayList<>(effects.values()));
        for (var m : extra) mods.put(m.id(), m);
        return PlayerAttributes.compute(attr, base, mods.values());
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
    /** 방패로 막은 피해였는가(막았으면 hurt_animation 이 가지 않는다). */
    private volatile boolean lastHurtBlocked;

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
     * hurt(type, amount, attacker, shield) 와 같되 플러그인 EntityDamageEvent 의 원인 사슬에만 쓰는 직접 엔티티(폭발한 TNT)를 준다.
     * 피해 계산·패킷·사망 메시지는 그대로(attacker 기준).
     */
    public boolean hurtVia(DamageType type, float amount, Targetable attacker, Shields.Source shield, Entity eventDirect) {
        Rider a = attacker instanceof Rider r ? r : null;
        return hurt(type, amount, attacker, shield, a, a, null, eventDirect);
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
                indicateDamage(-d[0], -d[1]);
            } else {
                double[] p = source.sourcePosition();
                double dx = p == null ? 0.0 : p[0] - player.x(), dz = p == null ? 0.0 : p[2] - player.z();
                player.knockback(0.4, dx, dz);
                indicateDamage(dx, dz);
            }
        }
        return r;
    }

    /** 바닐라 ServerPlayer.indicateDamage: 방패로 막지 않았으면 피격 방향(도)을 hurt_animation 으로 보낸다(화면 기울임). */
    private void indicateDamage(double dx, double dz) {
        if (lastHurtBlocked) return;
        float hurtDir = (float) (Math.atan2(dz, dx) * 180.0F / (float) Math.PI - player.yaw());
        player.send(new org.domi.furnace.server.net.protocol.play.HurtAnimation(player.entityId(), hurtDir));
    }

    private boolean hurt(DamageType type, float amount, Targetable attacker, Shields.Source shield, Rider causing, Rider direct, double[] pos) {
        return hurt(type, amount, attacker, shield, causing, direct, pos, null);
    }

    /** eventDirect: 원인 사슬에만 쓰는 직접 엔티티(null 이면 direct). */
    private boolean hurt(DamageType type, float amount, Targetable attacker, Shields.Source shield, Rider causing, Rider direct, double[] pos, Entity eventDirect) {
        lastHurtFresh = false;
        float evAmount = env.gameEvents().playerDamage(player, type.key(), amount, causing, eventDirect != null ? eventDirect : direct);   // 플러그인 EntityDamageEvent
        if (evAmount < 0.0F) return false;
        amount = evAmount;
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
            if (attacker instanceof Player ap && !player.canHarmPlayer(ap, env.pvp())) return false;
            if (abilities.invulnerable && !type.bypassesInvulnerability()) return false;
            if (health <= 0.0f) return false;
            player.removeEntitiesOnShoulder();   // Player.hurt: 피해를 받으면 어깨의 몹을 내려놓는다
            // LivingEntity.hurt: 자고 있으면 깬다(Player.stopSleeping = stopSleepInBed(true, true))
            if (player.isSleeping()) PlayerSleeping.stopSleepInBed(player, player.currentLevel, true, true);
            if (attacker != null) player.setLastHurtByMob(attacker);
            if (causing instanceof Player cp) player.markHurtByPlayer(cp);
            if (type.scalesWithDifficulty(causing instanceof LivingEntity)) {   // Player.hurt: 난이도 배율(scaling)
                Difficulty d = env.difficulty();
                if (d == Difficulty.PEACEFUL) amount = 0.0f;
                if (d == Difficulty.EASY) amount = Math.min(amount / 2.0f + 1.0f, amount);
                if (d == Difficulty.HARD) amount = amount * 3.0f / 2.0f;
                float mul = org.domi.furnace.server.world.DifficultyModifiers.playerDamage();   // 난이도 프로필(기본 1 = 바닐라)
                if (mul != 1.0f) amount *= mul;
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
            // LivingEntity.hurt(btn.a 오프셋 180~214): #damages_helmet(떨어지는 모루·블록·종유석) 피해는 머리 칸이 비어 있지 않으면 투구를 깎고 0.75 배
            if (type.isInTag("damages_helmet") && !player.inventory().get(5).isEmpty()) {
                player.hurtHelmet(amount);
                amount *= 0.75F;
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
        flushStats();
        lastHurtFresh = newHurt;
        lastHurtBlocked = blocked;
        if (newHurt && !type.noImpact() && (!blocked || amount > 0.0f)) player.markHurt();   // 완전히 막으면 markHurt 없음 → 속도 패킷 0건
        if (newHurt) {   // 막았으면 damage_event 대신 엔티티 이벤트 29(방패 소리)
            Packet ev = blocked ? new EntityEvent(player.entityId(), (byte) 29)
                    : causing == null && direct == null && pos == null ? DamageEvent.environmental(player.entityId(), type.registryId())
                    : new DamageEvent(player.entityId(), type.registryId(), DamageSource.idOf(causing), DamageSource.idOf(direct), pos);
            player.send(ev);
            env.broadcastToTrackers(player.entityId(), ev);
        }
        if (died && !checkTotemDeathProtection(type)) die(type, causing, direct);
        if (!blocked || amount > 0.0f) {   // 바닐라 LivingEntity.hurt 끝: 효과마다 onMobHurt(벌레 들끓음)
            var lv = player.currentLevel;
            if (lv != null && hasAnyEffect()) {
                var t = org.domi.furnace.server.effect.MobEffectTriggers.Target.of(lv, player);
                for (MobEffectInstance e : activeEffects()) org.domi.furnace.server.effect.MobEffectTriggers.onMobHurt(t, e.effect(), e.amplifier());
            }
        }
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
        if (!type.bypassesArmor()) player.hurtArmor(amount);   // getDamageAfterArmorAbsorb: 방어구 내구도를 먼저 깎는다(깨지면 빠진 뒤 방어력을 읽는다 — 바닐라 순서)
        float afterAbsorb;
        var damageEvents = env.gameEvents();
        if (amount > 0.0f && damageEvents.wantsDamageCalc()) {   // 플러그인 EntityDamageCalculateEvent: 단계마다 바닐라 식 + 플러그인이 바꾼 값
            var calc = damageEvents.playerDamageCalc(player, type.key(), causing, direct, amount, absorption, new StageRules(type, direct));
            float beforeAbsorb = calc.input(org.domi.furnace.api.event.entity.DamageStage.ABSORPTION);
            afterAbsorb = calc.output(org.domi.furnace.api.event.entity.DamageStage.ABSORPTION);
            if (calc.applies(org.domi.furnace.api.event.entity.DamageStage.RESISTANCE)) {   // 저항 효과가 줄인 양(getDamageAfterMagicAbsorb)
                queueDamageStat("minecraft:damage_resisted", calc.input(org.domi.furnace.api.event.entity.DamageStage.RESISTANCE)
                        - calc.output(org.domi.furnace.api.event.entity.DamageStage.RESISTANCE));
            }
            setAbsorptionLocked(absorption - (beforeAbsorb - afterAbsorb));
            queueDamageStat("minecraft:damage_absorbed", beforeAbsorb - afterAbsorb);
        } else {
            if (!type.bypassesArmor()) {   // CombatRules.getDamageAfterAbsorb
                float armor = player.armorValue(), toughness = player.armorToughness();
                amount = CombatRules.getDamageAfterAbsorb(amount, armor, toughness, EnchantEffects.weaponOf(direct));
            }
            amount = getDamageAfterMagicAbsorb(type, amount);
            afterAbsorb = Math.max(amount - absorption, 0.0f);
            setAbsorptionLocked(absorption - (amount - afterAbsorb));
            queueDamageStat("minecraft:damage_absorbed", amount - afterAbsorb);   // Player.actuallyHurt: 흡수 체력이 막은 양
        }
        if (afterAbsorb != 0.0f) {
            if (!abilities.invulnerable) food.addExhaustion(type.exhaustion());
            combat.recordDamage(type, afterAbsorb, fallLocation(), fallDistance, tickCount, health > 0, DamageSource.nameOf(causing), DamageSource.nameOf(direct),
                    causing instanceof Player || causing instanceof LivingEntity);
            setHealthRaw(health - afterAbsorb);
            if (afterAbsorb < 3.4028235E37F) queueStat("minecraft:damage_taken", Math.round(afterAbsorb * 10.0f));
            var lv = player.currentLevel;   // 바닐라 Player.actuallyHurt 끝의 gameEvent(ENTITY_DAMAGE)
            if (lv != null) lv.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DAMAGE,
                    new Vec3(player.x(), player.y(), player.z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(player));
        }
    }

    /** 피해 계산 단계 이벤트에 넘기는 바닐라 식 — 플레이어의 지금 방어력·효과·방어구로 계산한다. 엔티티 고유 경감(ENTITY)은 없다. */
    private final class StageRules implements org.domi.furnace.api.event.entity.DamageRules {
        private final DamageType type;
        private final ItemStack weapon;
        private final MobEffectInstance resistance;
        private final float protection;

        StageRules(DamageType type, Rider direct) {
            this.type = type;
            this.weapon = EnchantEffects.weaponOf(direct);
            this.resistance = effects.get(MobEffect.RESISTANCE);
            PlayerInventory inv = player.inventory();
            this.protection = CombatRules.damageProtection(type.key(), inv.get(5), inv.get(6), inv.get(7), inv.get(8));
        }

        @Override
        public boolean applies(org.domi.furnace.api.event.entity.DamageStage stage) {
            return switch (stage) {
                case ARMOR -> !type.bypassesArmor();
                case RESISTANCE -> resistance != null && !type.bypassesEffects() && !type.bypassesResistance();
                case ENCHANTMENT -> protection > 0.0f && !type.bypassesEffects() && !type.bypassesEnchantments();
                case ENTITY -> false;
                case ABSORPTION -> true;
            };
        }

        @Override
        public float apply(org.domi.furnace.api.event.entity.DamageStage stage, float input) {
            return switch (stage) {
                case ARMOR -> CombatRules.getDamageAfterAbsorb(input, player.armorValue(), player.armorToughness(), weapon);
                case RESISTANCE -> resistance == null ? input : CombatRules.afterResistance(input, resistance.amplifier());
                case ENCHANTMENT -> protection > 0.0f ? CombatRules.getDamageAfterMagicAbsorb(input, protection) : input;
                case ENTITY, ABSORPTION -> input;
            };
        }
    }

    /** LivingEntity.getDamageAfterMagicAbsorb: bypasses_effects → 그대로, 저항 효과, 0 이하면 0, bypasses_enchantments → 그대로, 보호 인챈트. */
    private float getDamageAfterMagicAbsorb(DamageType type, float amount) {
        if (type.bypassesEffects()) return amount;
        MobEffectInstance resistance = effects.get(MobEffect.RESISTANCE);
        if (resistance != null && !type.bypassesResistance()) {
            float before = amount;
            amount = CombatRules.afterResistance(amount, resistance.amplifier());
            queueDamageStat("minecraft:damage_resisted", before - amount);   // ServerPlayer 가 맞으면 본인에게
        }
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

    /** 블록은 주인 region 스레드에서만 읽는다(아니면 -1). */
    private int ownerBlockAt(int x, int y, int z) {
        var r = player.ownerRegion;
        return r != null && r.ownsPlayer(player) ? r.getBlock(x, y, z) : -1;
    }
    private volatile org.domi.furnace.server.world.PlayerProbe.Blocks blockLookup = this::ownerBlockAt;

    /** 낙하 위치 이름을 정할 때 매달렸던 칸의 블록을 읽는 곳(시험용). null 이면 주인 region. */
    void setBlockLookup(org.domi.furnace.server.world.PlayerProbe.Blocks lookup) { blockLookup = lookup == null ? this::ownerBlockAt : lookup; }

    /** 바닐라 FallLocation.getCurrentFallLocation: 마지막으로 매달린 블록, 없고 물속이면 water. */
    private String fallLocation() {
        if (lastClimbablePos != null) {
            // 바닐라처럼 그 칸의 지금 블록으로 이름을 정한다(사다리가 부서졌으면 other_climbable). 못 읽으면 매달린 순간의 이름.
            var p = lastClimbablePos;
            int st = blockLookup.stateAt(p.x(), p.y(), p.z());
            return st >= 0 ? LivingEntity.fallLocationOf(st) : lastClimbable;
        }
        return inWater ? "water" : null;
    }

    /**
     * 바닐라 ServerPlayer.die: 사망 메시지·사망 화면, 인벤토리 처리, 엔티티 이벤트 3, 마지막 사망 위치.
     * Paper 처럼 PlayerDeathEvent 를 맨 앞(바닐라 첫 줄 gameEvent(ENTITY_DIE) 보다 먼저)에 묻고, 취소되면 아무것도 하지 않고 되살린다.
     */
    private void die(DamageType type, Rider causing, Rider direct) {
        // 주인 스레드가 아니면 밀린 작업(entity_die·구슬)이 다음 주인 틱에 돌므로, 죽은 순간의 위치·레벨·경험치량·피해 정보는 지금 떠서 넘긴다(그 사이 리스폰해도 안전).
        final Death death = new Death(type.key(), causing, direct, baseExperienceReward(), player.currentLevel, player.x(), player.y(), player.z());
        boolean showMessages = env.gameRule("showDeathMessages");
        Text name = player.displayName();
        Text message;
        boolean spectator;
        float maxHealth;
        synchronized (this) {
            Targetable credit = player.getKillCredit();
            message = showMessages ? combat.getDeathMessage(name, credit == null ? null : credit.displayName()) : null;
            spectator = gameMode == GameMode.SPECTATOR;
            maxHealth = maxHealthLocked();
        }
        // CraftBukkit/Paper ServerPlayer.die: keepInventory 는 게임룰 또는 관전자. 그렇지 않으면 인벤토리 전체가 떨어뜨릴 목록으로 들어간다.
        boolean keepInventory = env.gameRule("keepInventory") || spectator;
        List<ItemStack> loot = keepInventory ? new ArrayList<>() : collectInventoryLoot();
        DeathOutcome outcome = env.death(player, message, keepInventory, loot, death.experience, maxHealth);
        if (outcome.cancelled()) {
            // Paper: 처리기가 이미 체력을 올렸으면 그대로, 아니면 reviveHealth. 사망 메시지·사망 화면·드롭·통계·entity_die 모두 없다.
            // 체력 패킷은 틱 끝에 한 번 나가므로 클라이언트는 0 을 보지 않는다(Paper 가 체력 패킷을 미뤄 두는 것과 같은 결과).
            synchronized (this) {
                if (health <= 0.0f) setHealthRaw(outcome.reviveHealth());
            }
            return;
        }
        this.death = death;
        // 바닐라 ServerPlayer.die 첫 줄: gameEvent(ENTITY_DIE) — Paper 는 취소 검사 뒤로 옮겼다. 스컬크 촉매가 듣고 경험치를 가져갈 수 있다.
        onOwnerThread(() -> {
            if (death.level != null) death.level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DIE,
                    new Vec3(death.x, death.y, death.z), org.domi.furnace.server.world.gameevent.GameEventContext.of(player));
        });
        onOwnerThread(player::clearFire);   // Player.die / ServerPlayer.die: clearFire + 불 플래그 끔
        death.experience = outcome.dropExperience() ? outcome.droppedExp() : 0;   // 플러그인이 바꾼 경험치량(바닐라 expToDrop), shouldDropExperience=false 면 없음
        synchronized (this) {
            combat.recheckStatus(tickCount, false);
            lastDeathDimension = "minecraft:overworld";
            lastDeathPos = new int[]{(int) Math.floor(player.x()), (int) Math.floor(player.y()), (int) Math.floor(player.z())};
            keepLevelOnRespawn = outcome.keepLevel();
            newExp = outcome.newExp();
            newLevel = outcome.newLevel();
            newTotalExp = outcome.newTotalExp();
        }
        if (outcome.message() != null) {
            player.send(new PlayerCombatKill(player.entityId(), player.localize(outcome.message())));
            env.broadcastDeathMessage(player, outcome.message());
        } else {
            player.send(new PlayerCombatKill(player.entityId(), Text.empty()));
        }
        player.removeEntitiesOnShoulder();   // ServerPlayer.die: 어깨의 몹을 내려놓는다
        DeathListener l = deathListener;
        if (l != null) l.onDeath(outcome.keepInventory(), outcome.drops(), loot, outcome.itemsToKeep());
        onOwnerThread(() -> dropDeathExperience(death));   // dropAllDeathLoot 의 dropExperience(전리품 다음). 관전자는 기본 0 이라 구슬이 없다.
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
    private int newExp, newLevel, newTotalExp;

    /**
     * Inventory.getContents 순서(주 인벤토리 → 갑옷 발·다리·가슴·머리 → 왼손)의 비어 있지 않은 칸 — 소멸 저주 아이템(PREVENT_EQUIPMENT_DROP)은 뺀다.
     * 인벤토리를 건드리지는 않는다(비우는 건 이벤트 뒤 keepInventory 가 false 일 때 소유 연결이 한다).
     */
    private List<ItemStack> collectInventoryLoot() {
        List<ItemStack> loot = new ArrayList<>();
        PlayerInventory inv = player.inventory();
        if (inv == null) return loot;
        ItemStack[] snap = inv.snapshot();   // 한 번에 뜬다 — 칸마다 읽으면 그 사이 옮겨진 아이템이 두 번 잡힐 수 있다
        for (int i = 0; i <= 40; i++) {
            ItemStack s = snap[PlayerInventory.windowSlot(i)];
            if (s == null || s.isEmpty()) continue;
            if (org.domi.furnace.server.item.ItemTools.enchantmentLevel(s, "minecraft:vanishing_curse") > 0) continue;
            loot.add(s);
        }
        return loot;
    }

    // ------------------------------------------------------------------ 사망 경험치(바닐라 Player.getBaseExperienceReward·dropExperience)

    /** 한 번의 사망: 죽은 순간의 값(피해 종류·원인, 경험치량, 자리)과 촉매가 경험치를 가져갔는지. 다음 사망이 새로 덮는다. */
    public static final class Death {
        private final String type;
        private final Rider causing, direct;
        private volatile int experience;
        private final org.domi.furnace.server.block.ChunkLevel level;
        private final double x, y, z;
        private volatile boolean consumed;

        Death(String type, Rider causing, Rider direct, int experience, org.domi.furnace.server.block.ChunkLevel level, double x, double y, double z) {
            this.type = type;
            this.causing = causing;
            this.direct = direct;
            this.experience = experience;
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** getLastDamageSource 의 종류(DamageType 키). */
        public String type() { return type; }

        public Rider causing() { return causing; }

        public Rider direct() { return direct; }

        /** getExperienceReward — 죽은 순간의 값. */
        public int experience() { return experience; }

        /** skipDropExperience: 스컬크 촉매가 경험치를 가져갔다. */
        public void skipDropExperience() { consumed = true; }

        public boolean wasExperienceConsumed() { return consumed; }
    }

    private volatile Death death;

    /** 지금 죽어 있는 사망 기록(없으면 null). */
    public Death death() { return death; }

    public boolean wasExperienceConsumed() {
        var d = death;
        return d != null && d.consumed;
    }

    /**
     * Player.getBaseExperienceReward: keepInventory 게임룰이 켜졌거나 관전자면 0, 아니면 레벨×7(최대 100).
     * 바닐라처럼 int 곱셈(아주 큰 레벨은 넘쳐 음수가 될 수 있다 — 그때도 같다). EnchantmentHelper.processMobExperience 는
     * 1.21.1 데이터에서 쓰는 인챈트가 없어 원래 값 그대로다(데이터 팩 mob_experience 효과는 가정: 반영 안 함).
     */
    public int baseExperienceReward() {
        if (env.gameRule("keepInventory")) return 0;
        synchronized (this) {
            if (gameMode == GameMode.SPECTATOR) return 0;
            int i = xpLevel * 7;
            return i > 100 ? 100 : i;
        }
    }

    /** LivingEntity.dropExperience(isAlwaysExperienceDropper = true): 이미 촉매가 가져가지 않았으면 죽은 자리에 구슬로 낸다. */
    private static void dropDeathExperience(Death d) {
        if (d.consumed || d.level == null || d.experience <= 0) return;
        ExperienceOrb.award(d.level, d.x, d.y, d.z, d.experience);
    }

    /** 플레이어 소유 스레드에서 실행(region 에 붙은 적 없는 시험 플레이어는 바로). 순서는 보존된다. */
    private void onOwnerThread(Runnable task) {
        if (player.ownerRegion == null) task.run();
        else player.runOnOwner(task);
    }

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
        DamageType fallType;
        synchronized (this) {
            landedFallDistance = 0.0f;
            fallDamageType = DamageType.FALL;
            landed = false;
            if (wasOnGround && !onGround && dy > 0.0) {   // jumpFromGround: jump 통계 → 허기 소모
                queueStat("minecraft:jump", 1);
                causeFoodExhaustionLocked(sprinting ? 0.2f : 0.05f);
            }
            if (waterOnPath && fallDistance != 0.0f && dx * dx + dy * dy + dz * dz >= 1.0) fallDistance = 0.0f;
            if (landingBlock != null) fallDamage = checkFallDamage(dy, onGround, inWaterNow, landingBlock, sneaking);
            fallType = fallDamageType;
        }
        // 바닐라 fallOn → causeFallDamage → hurt 는 낙하 거리를 초기화하기 전에 일어난다(전투 기록이 낙하 거리를 본다).
        flushStats();   // jump·fall_one_cm 은 피해(damage_taken)보다 먼저
        if (fallDamage > 0) hurt(fallType, fallDamage);
        synchronized (this) {
            if (landingBlock != null && onGround) {
                fallDistance = 0.0f;
                lastClimbable = null;
                lastClimbablePos = null;
            }
            if (dy > 0.0) fallDistance = 0.0f;
            checkMovementStatistics(dx, dy, dz, onGround, sprinting, sneaking, inWaterNow, eyeInWater, climbable);
        }
        flushStats();
    }

    // ------------------------------------------------------------------ 통계(잠금 안에서 모았다가 밖에서 올린다)

    private final java.util.ArrayList<Object[]> pendingStats = new java.util.ArrayList<>(2);

    /** 잠금 안에서 올릴 통계를 모은다(awardStat 은 점수판까지 건드리므로 잠금 밖에서 올린다). */
    private void queueStat(String stat, int amount) { pendingStats.add(new Object[] {stat, amount}); }

    /** 모은 통계를 순서대로 올린다(잠금 밖). */
    private void flushStats() {
        Object[][] out;
        synchronized (this) {
            if (pendingStats.isEmpty()) return;
            out = pendingStats.toArray(new Object[0][]);
            pendingStats.clear();
        }
        for (Object[] o : out) player.awardStat((String) o[0], (Integer) o[1]);
    }

    /** 바닐라의 "0 보다 크고 3.4028235E37 보다 작을 때만 올리는" 피해 통계: Math.round(값 * 10). */
    private void queueDamageStat(String stat, float amount) {
        if (amount > 0.0f && amount < 3.4028235E37F) queueStat(stat, Math.round(amount * 10.0f));
    }

    private void causeFoodExhaustionLocked(float amount) {
        if (!abilities.invulnerable) food.addExhaustion(amount);
    }

    /**
     * 바닐라 ServerPlayer.checkMovementStatistics(aqv.b): 탈것을 타고 있으면 아무것도 안 하고, 아니면 한 분기만 탄다 —
     * 헤엄 → 눈이 물속 → 물 안 → 오르는 중 → 땅 위 → 겉날개 → 그 밖(공중). 분기마다 통계와 허기 소모를 함께 낸다.
     * 거리는 3차원이면 sqrt(dx²+dy²+dz²), 수평이면 sqrt(dx²+dz²) 를 float 으로 곱한 뒤 Math.round(float).
     */
    private void checkMovementStatistics(double dx, double dy, double dz, boolean onGround, boolean sprinting, boolean sneaking,
                                         boolean inWaterNow, boolean eyeInWater, String climbable) {
        if (player.vehicle() != null) return;
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) return;
        int cm3 = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0f);
        int cmH = Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0f);
        if (player.swimming) {
            if (cm3 > 0) {
                queueStat("minecraft:swim_one_cm", cm3);
                causeFoodExhaustionLocked(0.01f * cm3 * 0.01f);
            }
        } else if (eyeInWater) {
            if (cm3 > 0) {
                queueStat("minecraft:walk_under_water_one_cm", cm3);
                causeFoodExhaustionLocked(0.01f * cm3 * 0.01f);
            }
        } else if (inWaterNow) {
            if (cmH > 0) {
                queueStat("minecraft:walk_on_water_one_cm", cmH);
                causeFoodExhaustionLocked(0.01f * cmH * 0.01f);
            }
        } else if (climbable != null) {
            if (dy > 0.0) queueStat("minecraft:climb_one_cm", (int) Math.round(dy * 100.0));   // 오르기: 위로만, 소모 없음
        } else if (onGround) {
            if (cmH > 0) {
                if (sprinting) {
                    queueStat("minecraft:sprint_one_cm", cmH);
                    causeFoodExhaustionLocked(0.1f * cmH * 0.01f);
                } else if (isCrouching(sneaking)) {
                    queueStat("minecraft:crouch_one_cm", cmH);
                    causeFoodExhaustionLocked(0.0f * cmH * 0.01f);
                } else {
                    queueStat("minecraft:walk_one_cm", cmH);
                    causeFoodExhaustionLocked(0.0f * cmH * 0.01f);
                }
            }
        } else if (player.isFallFlying()) {
            queueStat("minecraft:aviate_one_cm", cm3);
        } else if (cmH > 25) {
            queueStat("minecraft:fly_one_cm", cmH);
        }
    }

    /** Entity.isCrouching = 자세가 웅크림: 날고 있거나 겉날개·수영·기어가기 자세가 아닌 웅크림(서 있을 틈이 없어 눌린 경우 포함). */
    private boolean isCrouching(boolean sneaking) {
        if (player.isFallFlying() || player.isSwimPose()) return false;
        return sneaking && !abilities.flying || player.forcedCrouch;
    }

    /**
     * 바닐라 ServerPlayer.rideTick → checkRidingStatistics(aqv.q): 탈것을 타고 있으면 한 틱 사이 내 위치 변화(3차원)를
     * 탈것 종류의 통계로 올린다(수레·배·돼지·말류·스트라이더). 소유 region 스레드가 rideTick 전후 위치로 부른다.
     */
    public void checkRidingStatistics(double dx, double dy, double dz) {
        Entity v = player.vehicle();
        if (v == null || dx == 0.0 && dy == 0.0 && dz == 0.0) return;
        int cm = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0f);
        String stat = v instanceof AbstractMinecart ? "minecraft:minecart_one_cm"
                : v instanceof Boat ? "minecraft:boat_one_cm"
                : v instanceof Pig ? "minecraft:pig_one_cm"
                : v instanceof AbstractHorse ? "minecraft:horse_one_cm"
                : v instanceof Strider ? "minecraft:strider_one_cm" : null;
        if (stat != null) player.awardStat(stat, cm);
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
            if (fallDistance > 0.0f) {
                landedFallDistance = fallDistance;   // 블록별 fallOn(경작지 밟기) 용 — 비행 가능해도 부른다
                landed = true;
                damage = fallOn(landingBlock, fallDistance, sneaking);
            }
            // 초기화는 피해를 준 뒤(onMove)
        } else if (dy < 0.0) {
            fallDistance -= (float) dy;
        }
        return damage;
    }

    private float landedFallDistance;

    /** 마지막 onMove 에서 내려앉으며 Block.fallOn 에 넘긴 낙하 거리(내려앉지 않았으면 0). */
    public synchronized float landedFallDistance() { return landedFallDistance; }

    /** 블록별 Block.fallOn → Player/LivingEntity.causeFallDamage. 비행 가능하면 피해 없음. */
    private float fallOn(String block, float distance, boolean sneaking) {
        float multiplier = fallMultiplier(block, sneaking);
        if (landingStalagmite && "minecraft:pointed_dripstone".equals(block)) {   // PointedDripstoneBlock.fallOn: 석순 끝은 거리 + 2, 배수 2
            distance += 2.0F;
            multiplier = 2.0F;
            fallDamageType = DamageType.byKey("stalagmite");
        }
        if (multiplier < 0) return 0;
        return fallDamage(distance, multiplier);
    }

    /** Player/ServerPlayer.causeFallDamage + LivingEntity.calculateFallDamage: 비행 가능이면 0, 충격 높이 규칙, 점프 강화로 안전 거리가 는다. */
    private float fallDamage(float distance, float multiplier) {
        if (abilities.mayfly) return 0;
        // Player.causeFallDamage: 충격 높이 규칙보다 먼저, 잘리지 않은 낙하 거리로(비행 가능이면 안 오른다)
        if (distance >= 2.0f) queueStat("minecraft:fall_one_cm", (int) Math.round((double) distance * 100.0));
        boolean impulse = false;
        if (ignoreFallFromImpulse && impulsePos != null) {   // ServerPlayer.causeFallDamage: 충격 높이보다 위에서 내려앉으면 면제, 아니면 그 높이까지만 센다
            double top = impulsePos[1];
            tryResetCurrentImpulseContext();
            if (top < impulseY()) return 0;
            distance = Math.min(distance, (float) (top - impulseY()));
            impulse = true;
        }
        // LivingEntity.calculateFallDamage: ceil((거리 − safe_fall_distance) × 배율 × fall_damage_multiplier) — 점프 강화는 safe_fall_distance 수정자
        double safe = fallAttribute(org.domi.furnace.server.attribute.Attribute.SAFE_FALL_DISTANCE, safeFallBase, safeFallExtra);
        double attrMult = fallAttribute(org.domi.furnace.server.attribute.Attribute.FALL_DAMAGE_MULTIPLIER, fallMultiplierBase, fallMultiplierExtra);
        float over = distance - (float) safe;   // 바닐라: float f = (float) safe_fall_distance; (distance − f) × …
        float dmg = (float) Math.ceil((double) (over * multiplier) * attrMult);
        if (impulse && dmg > 0) resetCurrentImpulseContext();
        return dmg;
    }

    /**
     * 탈것이 내려앉으며 승객인 플레이어에게 넘기는 낙하(Entity.causeFallDamage → ServerPlayer/Player/LivingEntity.causeFallDamage).
     * 피해를 주었으면 그 양, 아니면 0(소유 region 스레드).
     */
    public float causePassengerFallDamage(float distance, float multiplier, String damageKey) {
        float dmg;
        synchronized (this) { dmg = fallDamage(distance, multiplier); }
        flushStats();
        if (dmg > 0) hurt(DamageType.byKey(damageKey), dmg);
        return Math.max(dmg, 0);
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
        boolean freezeHurt;
        java.util.List<Packet> toSelf = new java.util.ArrayList<>(3);
        java.util.List<Packet> toTrackers = new java.util.ArrayList<>(2);
        // 인벤토리는 이 잠금 밖에서 읽는다(PlayerInventory.get 은 자체 잠금, ItemStack 은 불변). 가루눈 밖이어도 TicksFrozen 이 140 을 넘게
        // 읽혀 왔으면(바닐라 load 는 자르지 않는다) 40틱 피해 판정이 canFreeze 를 보므로 늘 검사한다.
        boolean freezeImmuneWear = wearsFreezeImmune();
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
                    lastClimbablePos = new org.domi.furnace.server.block.Pos(
                            (int) Math.floor(player.x()), (int) Math.floor(player.y()), (int) Math.floor(player.z()));
                    fallDistance = 0.0f;   // travel → handleOnClimbable → resetFallDistance
                }
                if (s.stuckInBlock()) fallDistance = 0.0f;   // 거미줄·가루눈·달콤한 열매 덤불: makeStuckInBlock
            }
            if (player.y() < s.minBuildHeight() - 64) after.add(() -> hurt(DamageType.OUT_OF_WORLD, 4.0f));
            if (alive) {
                // btn.aw: 질식 블록 속이면 in_wall 1, 아니면(플레이어만) 월드 보더 밖 피해 — 둘은 같은 틱에 겹치지 않는다
                if (s.inWall() && gameMode != GameMode.SPECTATOR) {
                    after.add(() -> hurt(DamageType.IN_WALL, 1.0f));
                } else {
                    float border = env.borderDamage(player, player.x(), player.y(), player.z());
                    if (border > 0) after.add(() -> hurt(DamageType.OUTSIDE_BORDER, border));
                }
                // 숨: MobEffectUtil.hasWaterBreathing(물숨쉬기·전도체의 힘) 이나 무적 능력이면 줄지 않는다(물속에서는 차오르지도 않는다 — 바닐라 그대로)
                if (s.chunkLoaded() && s.eyeInWater() && !s.eyeInBubbleColumn()) {
                    if (!abilities.invulnerable && !effects.containsKey(MobEffect.WATER_BREATHING) && !effects.containsKey(MobEffect.CONDUIT_POWER)) {
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
            freezeHurt = tickFreezingLocked(s, freezeImmuneWear);   // aiStep: travel 바로 뒤
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
        // 냉각 피해는 aiStep 이라 baseTick 끝의 효과 틱(btn.aw 765 eo) 피해보다 뒤다
        if (freezeHurt) hurt(DamageType.byKey("freeze"), 1.0f);
        applyPendingStarve();
        final int epoch;
        int levelFrom = -1, levelTo = -1;
        synchronized (this) {
            collectChanges(toSelf, toTrackers);
            epoch = respawnEpoch;
            // 플러그인 PlayerLevelChangeEvent: CraftBukkit 처럼 틱마다 지난번 레벨과 비교(처음 틱은 기준만 잡는다)
            if (levelEventLast == -1) levelEventLast = xpLevel;
            if (levelEventLast != xpLevel) {
                levelFrom = levelEventLast;
                levelTo = xpLevel;
                levelEventLast = xpLevel;
            }
        }
        if (levelFrom != levelTo) env.gameEvents().playerLevelChange(player, levelFrom, levelTo);
        // 모은 뒤 리스폰이 끼면(연결 스레드) 낡은 체력 0 등이 Respawn 패킷 뒤에 가서 클라가 죽은 채로 남는다 — 쓰는 순간 세대를 비교해 버린다
        for (Packet p : toSelf) player.send(p, () -> respawnEpoch == epoch);
        for (Packet p : toTrackers) env.broadcastToTrackers(player.entityId(), p);
    }

    /**
     * LivingEntity.aiStep 의 "freezing" 구간(btn.m_ 오프셋 678~807): 죽어 가지 않으면 가루눈 속이고 얼 수 있을 때 +1(최대 140), 아니면 −2(최소 0).
     * 얼음 속도 수정자(removeFrost → tryAddFrost)는 발밑(0.2 아래)이 공기가 아니고 얼음 틱이 있을 때 −0.05 × 언 비율. 40틱마다 완전히 얼었으면 freeze 1.
     */
    /** 냉각 피해(freeze 1)를 줄 틱이면 참 — 호출자가 효과 피해 뒤에 입힌다. */
    private boolean tickFreezingLocked(Surroundings s, boolean freezeImmuneWear) {
        boolean canFreeze = gameMode != GameMode.SPECTATOR && !freezeImmuneWear;   // LivingEntity.canFreeze(플레이어 종류는 면역 태그에 없다)
        if (health > 0.0f) {
            if (s.inPowderSnow() && canFreeze) ticksFrozen = Math.min(Entity.TICKS_REQUIRED_TO_FREEZE, ticksFrozen + 1);
            else ticksFrozen = Math.max(0, ticksFrozen - 2);
        }
        if (s.groundNotAir() && ticksFrozen > 0) {
            float f = -0.05F * ((float) Math.min(ticksFrozen, Entity.TICKS_REQUIRED_TO_FREEZE) / (float) Entity.TICKS_REQUIRED_TO_FREEZE);
            frostModifier = new org.domi.furnace.server.attribute.AttributeModifier(LivingEntity.POWDER_SNOW_SPEED_MODIFIER, f,
                    org.domi.furnace.server.attribute.AttributeModifier.ADD_VALUE);
        } else {
            frostModifier = null;
        }
        return tickCount % 40 == 0 && ticksFrozen >= Entity.TICKS_REQUIRED_TO_FREEZE && canFreeze;
    }

    /** LivingEntity.canFreeze 의 방어구 항: 머리·몸통·다리·발에 #freeze_immune_wearables(가죽)를 하나라도 입었다. */
    private boolean wearsFreezeImmune() {
        for (EquipmentSlot slot : LivingEntity.FREEZE_ARMOR_SLOTS) {
            if (org.domi.furnace.server.item.ItemTags.is(player.equipment(slot), "minecraft:freeze_immune_wearables")) return true;
        }
        return false;
    }

    /** 얼음 틱(DATA_TICKS_FROZEN). */
    public synchronized int ticksFrozen() { return ticksFrozen; }

    /** 지금 이동 속도에 달아야 할 얼음 수정자(없으면 null). */
    public org.domi.furnace.server.attribute.AttributeModifier frostModifier() { return frostModifier; }

    /** Entity.baseTick: 불이 붙어 있는 동안 얼음 틱이 있으면 0 으로 녹인다. 녹였으면 참(호출자가 레벨 이벤트 1009 를 낸다). */
    public synchronized boolean thawByFire() {
        if (ticksFrozen <= 0) return false;
        ticksFrozen = 0;
        return true;
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
        boolean keepXp = keepLevelOnRespawn;   // Paper ServerPlayer.reset 은 keepLevel 만 본다(관전자는 죽을 때 keepLevel=true 로 미리 채워진다)
        keepLevelOnRespawn = false;
        // death 기록은 지우지 않는다: 주인 스레드로 밀린 entity_die·구슬 작업이 리스폰 뒤에 돌 수 있다. 다음 사망이 새 기록으로 덮는다.
        effects.clear();
        health = MAX_HEALTH;
        absorption = 0;
        food = new FoodData();
        air = MAX_AIR;
        ticksFrozen = 0;
        frostModifier = null;
        fallDistance = 0;
        invulnerableTime = 0;
        hurtTime = 0;
        deathTime = 0;
        lastHurt = 0;
        spawnInvulnerableTime = SPAWN_INVULNERABLE_TICKS;
        player.clearHurtRecords();
        combat = newCombat();
        inWater = false;
        lastClimbable = null;
        lastClimbablePos = null;
        removedAfterDeath = false;
        abilities.flying = false;
        abilities.flyingSpeed = 0.05f;
        abilities.walkingSpeed = 0.1f;
        abilities.applyGameMode(gameMode);
        if (!keepXp) {
            // Paper ServerPlayer.reset: 레벨·총량은 PlayerDeathEvent 의 newLevel·newTotalExp(기본 0), 진행도 0, 그 뒤 newExp 만큼 더한다.
            xpLevel = Math.max(0, newLevel);
            xpTotal = Math.max(0, newTotalExp);
            xpProgress = 0;
            score = 0;
            if (newExp != 0) giveExperiencePoints(newExp);
        }
        newExp = 0;
        newLevel = 0;
        newTotalExp = 0;
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

    public synchronized boolean hasAnyEffect() { return !effects.isEmpty(); }

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

    /** 바닐라 updateSynchronizedMobEffectParticles 의 입자 목록(효과가 없으면 빈 목록). */
    public synchronized java.util.List<org.domi.furnace.server.net.protocol.play.ParticleOptions> effectParticles() {
        return MobEffectInstance.visibleParticles(effects.values());
    }

    /** 모든 효과가 환경 효과인가. 효과가 없으면 null — 바닐라 removeEffectParticles 는 환경 표시를 그대로 둔다. */
    public synchronized Boolean effectsAmbient() { return effects.isEmpty() ? null : MobEffectInstance.allAmbient(effects.values()); }

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
