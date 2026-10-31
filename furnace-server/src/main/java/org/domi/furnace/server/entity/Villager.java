// 바닐라 Villager(+AbstractVillager): 거래(Merchant)·거래표 생성(updateTrades)·경험치와 레벨업·재고 보충·마을 사람 데이터(종류·직업·레벨)·저장,
// 브레인(VillagerGoalPackages: 핵심·일·놀이·휴식·만남·한가·공황 + 시간표)과 POI(집·직업·모임 장소) 티켓, 음식·번식, 철 골렘 소환.
// 소문(GossipContainer)과 평판에 따른 특별 가격, 레이드·숨기 활동(PRE_RAID/RAID/HIDE), 생성 이유별 finalizeSpawn.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemNbt;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.menu.MenuPlayer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.Merchant;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;
import org.domi.furnace.server.trade.VillagerTrades;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.Schedule;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.world.poi.PoiType;
import org.domi.furnace.server.world.poi.PoiTypes;

public final class Villager extends AgeableMob implements Merchant, InventoryCarrier {
    /** canBeLeashed(디컴파일 y): AbstractVillager(cmf)는 못 맨다. */
    @Override
    public boolean canBeLeashed() { return false; }


    /** 바닐라 AbstractVillager 하위(주민·떠돌이 상인)인가. 대상 종류 AbstractVillager.class 대신 쓴다. */
    public static boolean isAbstractVillager(Targetable t) { return t instanceof Villager || t instanceof WanderingTrader; }

    /** AgeableMob 16 아기 여부, AbstractVillager 17 불만 카운터, Villager 18 마을 사람 데이터. */
    public static final int DATA_BABY = 16, DATA_UNHAPPY = 17, DATA_VILLAGER_DATA = 18;
    public static final List<String> TYPES = List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga");
    public static final List<String> PROFESSIONS = List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
    /** VillagerType.byBiome: 바이옴 이름으로 종류를 정한다(기본 plains). */
    public static String typeForBiome(String biome) {
        if (biome == null) return "plains";
        return switch (biome.replace("minecraft:", "")) {
            case "badlands", "desert", "eroded_badlands", "wooded_badlands" -> "desert";
            case "bamboo_jungle", "jungle", "sparse_jungle" -> "jungle";
            case "savanna_plateau", "savanna", "windswept_savanna" -> "savanna";
            case "deep_frozen_ocean", "frozen_ocean", "frozen_river", "ice_spikes", "snowy_beach", "snowy_taiga", "snowy_plains", "grove", "snowy_slopes",
                    "frozen_peaks", "jagged_peaks" -> "snow";
            case "swamp", "mangrove_swamp" -> "swamp";
            case "old_growth_spruce_taiga", "old_growth_pine_taiga", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "taiga" -> "taiga";
            default -> "plains";
        };
    }

    private static final int[] XP_PER_LEVEL = {0, 10, 70, 150, 250};

    private MenuPlayer tradingPlayer;
    /** 바닐라 offers: 처음 필요할 때(offers()) 거래표로 채운다. null = 아직 안 만들었다. */
    private MerchantOffers offers;
    private int villagerXp;
    private int foodLevel;
    /** 레벨업 대기 틱(updateMerchantTimer)과 대기가 끝나면 레벨을 올릴지(increaseProfessionLevelOnUpdate). */
    private int updateMerchantTimer;
    private boolean increaseProfessionLevelOnUpdate;
    private long lastRestockGameTime;
    private int numberOfRestocksToday;

    private final MobInventory inventory = new MobInventory(8);
    /** 마지막으로 소문을 나눈 시각, 마지막 재고 확인 하루 시각(저장 안 함), 구조물 생성 직후 직업 받기. */
    private long lastGossipTime;
    private long lastRestockCheckDayTime;
    private boolean assignProfessionWhenSpawned;
    /** 바닐라 lastTradedPlayer: 거래 직후 다음 AI 틱에 TRADE 평판 소문과 기쁨 표시(14)를 낸다. */
    private Player lastTradedPlayer;
    /** 소문과 마지막 하루 감쇠 시각(LastGossipDecay). */
    private final org.domi.furnace.server.entity.gossip.GossipContainer gossips = new org.domi.furnace.server.entity.gossip.GossipContainer();
    private long lastGossipDecayTime;
    /** finalizeSpawn 에 넘어오지 않는 바닐라 MobSpawnType 대신(부르는 쪽이 finalizeSpawn 직전에 정한다). 기본 NATURAL. */
    private org.domi.furnace.server.entity.spawn.SpawnReason finalizeReason = org.domi.furnace.server.entity.spawn.SpawnReason.NATURAL;

    public Villager(ChunkLevel level, int id) {
        super(EntityType.VILLAGER, level, id);
        if (getNavigation() instanceof org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation g) g.setCanOpenDoors(true);
        getNavigation().setCanFloat(true);
        setCanPickUpLoot(true);
        brain = makeBrain();
        registerBrainGoals(villagerBrain());
    }

    // ------------------------------------------------------------------ 브레인

    private static final List<MemoryModuleType<?>> MEMORY_TYPES = List.of(
            MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT, MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.VISIBLE_VILLAGER_BABIES, MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
            MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.BREED_TARGET, MemoryModuleType.PATH,
            MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.NEAREST_BED, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_HOSTILE,
            MemoryModuleType.SECONDARY_JOB_SITE, MemoryModuleType.HIDING_PLACE, MemoryModuleType.HEARD_BELL_TIME, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.LAST_SLEPT, MemoryModuleType.LAST_WOKEN, MemoryModuleType.LAST_WORKED_AT_POI, MemoryModuleType.GOLEM_DETECTED_RECENTLY);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Villager> makeBrain() {
        return new Brain<>(MEMORY_TYPES, (List) List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS,
                SensorType.NEAREST_BED, SensorType.HURT_BY, SensorType.VILLAGER_HOSTILES, SensorType.VILLAGER_BABIES, SensorType.SECONDARY_POIS, SensorType.GOLEM_DETECTED));
    }

    @SuppressWarnings("unchecked")
    public Brain<Villager> villagerBrain() { return (Brain<Villager>) brain; }

    /** refreshBrain: 실행 중 행동을 멈추고 기억만 옮긴 새 브레인에 활동을 다시 건다(직업·나이가 바뀔 때). */
    public void refreshBrain(ChunkLevel level) {
        Brain<Villager> b = villagerBrain();
        b.stopAll(level, this);
        brain = b.copyWithoutBehaviors();
        registerBrainGoals(villagerBrain());
    }

    /** registerBrainGoals: 아기는 놀이 + 아기 시간표, 어른은 일(JOB_SITE 있을 때) + 기본 시간표. 그다음 핵심·만남·휴식·한가·공황. */
    private void registerBrainGoals(Brain<Villager> b) {
        String prof = profession();
        if (isBaby()) {
            b.setSchedule(Schedule.VILLAGER_BABY);
            b.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(0.5F));
        } else {
            b.setSchedule(Schedule.VILLAGER_DEFAULT);
            b.addActivityWithConditions(Activity.WORK, VillagerGoalPackages.getWorkPackage(prof, 0.5F),
                    java.util.Set.of(java.util.Map.entry(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));
        }
        b.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(prof, 0.5F));
        b.addActivityWithConditions(Activity.MEET, VillagerGoalPackages.getMeetPackage(prof, 0.5F),
                java.util.Set.of(java.util.Map.entry(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        b.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(prof, 0.5F));
        b.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(prof, 0.5F));
        b.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(prof, 0.5F));
        b.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(prof, 0.5F));
        b.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(prof, 0.5F));
        b.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(prof, 0.5F));
        b.setCoreActivities(java.util.Set.of(Activity.CORE));
        b.setDefaultActivity(Activity.IDLE);
        b.setActiveActivityIfPossible(Activity.IDLE);
        b.updateActivityFromSchedule(level.dayTime(), level.gameTime());
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (brain != null) refreshBrain(level);
    }

    // ------------------------------------------------------------------ 직업·POI

    /** VillagerProfession.heldJobSite: 무직·바보는 없음, 나머지는 자기 직업 블록. */
    public static java.util.function.Predicate<PoiType> heldJobSite(String prof) {
        if (prof.equals("none") || prof.equals("nitwit")) return t -> false;
        PoiType p = PoiTypes.byName(prof);
        return t -> t == p;
    }

    /** VillagerProfession.acquirableJobSite: 무직은 직업 블록 태그 전체, 바보는 없음. */
    public static java.util.function.Predicate<PoiType> acquirableJobSite(String prof) {
        if (prof.equals("none")) return PoiTypes.ACQUIRABLE_JOB_SITE;
        return heldJobSite(prof);
    }

    /** 직업 블록 → 그 블록을 쓰는 직업(레지스트리 순서로 처음). 없으면 null. */
    public static String professionForJobSite(PoiType type) {
        for (String p : PROFESSIONS) if (heldJobSite(p).test(type)) return p;
        return null;
    }

    /** VillagerProfession.requestedItems: 농부만 밀·씨앗·뼛가루. */
    public static java.util.Set<String> requestedItems(String prof) {
        return prof.equals("farmer") ? java.util.Set.of("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:bone_meal") : java.util.Set.of();
    }

    /** VillagerProfession.secondaryPoi: 농부만 경작지. */
    public static java.util.Set<String> secondaryPoi(String prof) { return prof.equals("farmer") ? java.util.Set.of("minecraft:farmland") : java.util.Set.of(); }

    public void setProfession(String prof) { setVillagerData(TYPES.get(villagerData()[0]), prof, villagerLevel()); }

    public boolean assignProfessionWhenSpawned() { return assignProfessionWhenSpawned; }

    public void setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason r) { finalizeReason = r; }

    /**
     * 바닐라 Villager.finalizeSpawn: 번식 = 무직, 명령·스폰 알·스포너·디스펜서 = 그 자리 바이옴의 종류, 구조물 = 처음 AI 틱에 직업 받기.
     * 그다음 AgeableMob/Mob.finalizeSpawn(난수는 거기서만 쓴다).
     */
    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        var reason = finalizeReason;
        finalizeReason = org.domi.furnace.server.entity.spawn.SpawnReason.NATURAL;
        if (reason == org.domi.furnace.server.entity.spawn.SpawnReason.BREEDING) setProfession("none");
        if (reason == org.domi.furnace.server.entity.spawn.SpawnReason.COMMAND || reason == org.domi.furnace.server.entity.spawn.SpawnReason.SPAWN_EGG
                || reason.isSpawner() || reason == org.domi.furnace.server.entity.spawn.SpawnReason.DISPENSER) {
            var p = blockPosition();
            setVillagerData(typeForBiome(level.biomeAt(p.x(), p.y(), p.z())), profession(), villagerLevel());
        }
        if (reason == org.domi.furnace.server.entity.spawn.SpawnReason.STRUCTURE) assignProfessionWhenSpawned = true;
        super.finalizeSpawn(r, difficulty);
    }

    public void setAssignProfessionWhenSpawned(boolean v) { assignProfessionWhenSpawned = v; }

    /** playWorkSound: 직업 소리(무직·바보는 없음). */
    public void playWorkSound() {
        String p = profession();
        if (!p.equals("none") && !p.equals("nitwit")) makeSound("entity.villager.work_" + p);
    }

    /** Villager.POI_MEMORIES 의 조건: 기억 종류별로 맞는 POI 종류. */
    private boolean poiMatches(MemoryModuleType<GlobalPos> memory, PoiType type) {
        if (memory == MemoryModuleType.HOME) return type == PoiTypes.HOME;
        if (memory == MemoryModuleType.JOB_SITE) return heldJobSite(profession()).test(type);
        if (memory == MemoryModuleType.POTENTIAL_JOB_SITE) return PoiTypes.ACQUIRABLE_JOB_SITE.test(type);
        if (memory == MemoryModuleType.MEETING_POINT) return type == PoiTypes.MEETING;
        return false;
    }

    /** releasePoi: 기억한 POI 가 그 기억에 맞는 종류면 티켓을 돌려준다. TODO(차원): 다른 차원 레벨의 POI 는 찾지 않는다. */
    public void releasePoi(MemoryModuleType<GlobalPos> memory) {
        brain.getMemory(memory).ifPresent(g -> {
            if (!g.dimension().equals(level.dimension())) return;
            PoiType t = level.poiManager().getType(g.pos());
            if (t != null && poiMatches(memory, t)) level.poiManager().release(g.pos());
        });
    }

    /** releaseAllPois: 집·직업·찾은 직업·모임 장소. */
    public void releaseAllPois() {
        releasePoi(MemoryModuleType.HOME);
        releasePoi(MemoryModuleType.JOB_SITE);
        releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        releasePoi(MemoryModuleType.MEETING_POINT);
    }

    /**
     * 바닐라 Villager.die: 죽인 엔티티가 있으면 주변 주민(NEAREST_LIVING_ENTITIES)에게 VILLAGER_KILLED 를 알리고 POI 를 돌려준다.
     * 죽인 엔티티 = 죽인 피해의 원인 엔티티(damageSourceEntity). 차이: 살아 있지 않은 원인(주인 없는 투사체 등)은 보지 않는다.
     */
    @Override
    public void die(Damage source) {
        if (!isRemoved() && !dead) {
            Targetable killer = damageSourceEntity();
            if (killer != null) tellWitnessesThatIWasMurdered(killer);
            releaseAllPois();
        }
        super.die(source);
    }

    private void tellWitnessesThatIWasMurdered(Targetable killer) {
        brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).ifPresent(list -> {
            for (Targetable t : list) if (t instanceof Villager v) v.onReputationEvent(ReputationEvent.VILLAGER_KILLED, killer);
        });
    }

    /** 바닐라 setLastHurtByMob: 때린 쪽에 VILLAGER_HURT 소문, 살아 있고 플레이어가 때렸으면 화남 표시(13). */
    @Override
    public void setLastHurtByMob(Targetable e) {
        if (e != null && level != null) {
            onReputationEvent(ReputationEvent.VILLAGER_HURT, e);
            if (isAlive() && e instanceof Player) broadcastEntityEvent((byte) 13);
        }
        super.setLastHurtByMob(e);
    }

    // ------------------------------------------------------------------ 소문·평판

    /** 바닐라 ReputationEventType. */
    public enum ReputationEvent { ZOMBIE_VILLAGER_CURED, GOLEM_KILLED, VILLAGER_HURT, VILLAGER_KILLED, TRADE }

    public org.domi.furnace.server.entity.gossip.GossipContainer getGossips() { return gossips; }

    /** 엔티티·플레이어의 UUID(소문 대상). */
    public static java.util.UUID uuidOf(Targetable t) {
        if (t instanceof Player p) return p.uuid();
        if (t instanceof Entity e) return e.uuid();
        return null;
    }

    /** onReputationEvent: 치료 = 큰 긍정 20 + 작은 긍정 25, 거래 = 거래 2, 다침 = 작은 부정 25, 죽임 = 큰 부정 25. */
    public void onReputationEvent(ReputationEvent type, Targetable target) {
        java.util.UUID u = uuidOf(target);
        if (u != null) onReputationEvent(type, u);
    }

    public void onReputationEvent(ReputationEvent type, java.util.UUID u) {
        switch (type) {
            case ZOMBIE_VILLAGER_CURED -> {
                gossips.add(u, org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.MAJOR_POSITIVE, 20);
                gossips.add(u, org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.MINOR_POSITIVE, 25);
            }
            case TRADE -> gossips.add(u, org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.TRADING, 2);
            case VILLAGER_HURT -> gossips.add(u, org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.MINOR_NEGATIVE, 25);
            case VILLAGER_KILLED -> gossips.add(u, org.domi.furnace.server.entity.gossip.GossipContainer.GossipType.MAJOR_NEGATIVE, 25);
            default -> { }
        }
    }

    /** getPlayerReputation: 그 플레이어의 모든 종류 가중 값 합. */
    public int getPlayerReputation(Player p) { return gossips.getReputation(p.uuid(), t -> true); }

    /**
     * 바닐라 updateSpecialPrices(거래 시작): 평판 × 가격 배수(내림)만큼 깎고, 마을의 영웅이면 (0.3 + 0.0625 × 증폭) × 기본 값 개수(내림, 최소 1)만큼 더 깎는다.
     */
    public void updateSpecialPrices(Player p) {
        int rep = getPlayerReputation(p);
        if (rep != 0) {
            for (MerchantOffer o : offers()) o.addToSpecialPriceDiff(-org.domi.furnace.server.entity.ai.control.Mth.floor(rep * o.priceMultiplier()));
        }
        var hero = p.vitals().getEffect(org.domi.furnace.server.effect.MobEffect.HERO_OF_THE_VILLAGE);
        if (hero != null) {
            int amp = hero.amplifier();
            for (MerchantOffer o : offers()) {
                double d = 0.3 + 0.0625 * amp;
                int k = (int) Math.floor(d * o.baseCostA().count());
                o.addToSpecialPriceDiff(-Math.max(k, 1));
            }
        }
    }

    /** resetSpecialPrices(거래 끝). */
    private void resetSpecialPrices() {
        if (offers == null) return;
        for (MerchantOffer o : offers) o.resetSpecialPriceDiff();
    }

    /** maybeDecayGossip: 처음이면 시각만 적고, 24000틱마다 하루치 감쇠. */
    private void maybeDecayGossip() {
        long now = level.gameTime();
        if (lastGossipDecayTime == 0L) lastGossipDecayTime = now;
        else if (now >= lastGossipDecayTime + 24000L) {
            gossips.decay();
            lastGossipDecayTime = now;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isRemoved()) maybeDecayGossip();
    }

    // ------------------------------------------------------------------ 잠

    @Override
    public void startSleeping(org.domi.furnace.server.block.Pos pos) {
        super.startSleeping(pos);
        brain.setMemory(MemoryModuleType.LAST_SLEPT, level.gameTime());
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        brain.setMemory(MemoryModuleType.LAST_WOKEN, level.gameTime());
    }

    // ------------------------------------------------------------------ 음식·번식

    /** FOOD_POINTS: 빵 4, 감자·당근·비트 1. */
    public static final java.util.Map<String, Integer> FOOD_POINTS = java.util.Map.of("minecraft:bread", 4, "minecraft:potato", 1, "minecraft:carrot", 1, "minecraft:beetroot", 1);
    private static final java.util.Set<String> WANTED_ITEMS = java.util.Set.of("minecraft:bread", "minecraft:potato", "minecraft:carrot", "minecraft:wheat",
            "minecraft:wheat_seeds", "minecraft:beetroot", "minecraft:beetroot_seeds", "minecraft:torchflower_seeds", "minecraft:pitcher_pod");
    private static final int BREEDING_FOOD_THRESHOLD = 12;

    @Override public MobInventory getInventory() { return inventory; }

    public int foodLevel() { return foodLevel; }

    @Override
    public boolean canBreed() { return foodLevel + countFoodPointsInInventory() >= BREEDING_FOOD_THRESHOLD && !isSleeping() && getAge() == 0; }

    private boolean hungry() { return foodLevel < BREEDING_FOOD_THRESHOLD; }

    /** eatUntilFull: 배고프면 앞 칸부터 음식을 하나씩 먹는다. */
    private void eatUntilFull() {
        if (!hungry() || countFoodPointsInInventory() == 0) return;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.get(i);
            if (s.isEmpty()) continue;
            Integer pts = FOOD_POINTS.get(ItemTools.itemName(s));
            if (pts == null) continue;
            for (int j = s.count(); j > 0; j--) {
                foodLevel += pts;
                inventory.removeItem(i, 1);
                if (!hungry()) return;
            }
        }
    }

    public void eatAndDigestFood() {
        eatUntilFull();
        foodLevel -= BREEDING_FOOD_THRESHOLD;
    }

    /** hasFarmSeeds: 가방에 심을 씨앗(villager_plantable_seeds)이 있다. */
    public boolean hasFarmSeeds() {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.get(i);
            if (!s.isEmpty() && org.domi.furnace.server.entity.ai.behavior.HarvestFarmland.PLANTABLE_SEEDS.containsKey(ItemTools.itemName(s))) return true;
        }
        return false;
    }

    /** AbstractVillager.playCelebrateSound. */
    public void playCelebrateSound() { playSound("entity.villager.celebrate", getSoundVolume(), getVoicePitch()); }

    public boolean hasExcessFood() { return countFoodPointsInInventory() >= 24; }

    public boolean wantsMoreFood() { return countFoodPointsInInventory() < 12; }

    private int countFoodPointsInInventory() {
        int n = 0;
        for (var e : FOOD_POINTS.entrySet()) n += inventory.countItem(e.getKey()) * e.getValue();
        return n;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        String n = ItemTools.itemName(stack);
        return (WANTED_ITEMS.contains(n) || requestedItems(profession()).contains(n)) && inventory.canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity ie) { InventoryCarrier.pickUpItem(this, this, ie); }

    /** getBreedOffspring: 종류는 몹 난수로 바이옴(1/2)·자신(1/4)·상대(1/4), 직업 없음(BREEDING). */
    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        double d = random.nextDouble();
        String type;
        if (d < 0.5) type = typeForBiome(level.biomeAt(blockPosition().x(), blockPosition().y(), blockPosition().z()));
        else if (d < 0.75) type = TYPES.get(villagerData()[0]);
        else type = TYPES.get(((Villager) partner).villagerData()[0]);
        Villager child = new Villager(level, level.nextEntityId());
        child.setVillagerData(type, "none", 1);
        child.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.BREEDING);
        child.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(child.blockPosition()), null);
        return child;
    }

    // ------------------------------------------------------------------ 철 골렘

    /** spawnGolemIfNeeded: 10칸 안 주민 중 골렘을 원하는 주민(최대 5)이 minVillagers 이상이면 골렘을 놓고, 놓으면 그 주민들 모두 골렘을 봤다고 기억한다. */
    public void spawnGolemIfNeeded(ChunkLevel level, long time, int minVillagers) {
        if (!wantsToSpawnGolem(time)) return;
        AABB box = boundingBox().inflate(10.0, 10.0, 10.0);
        List<Villager> all = level.getEntitiesOfClass(Villager.class, box, x -> true);
        List<Villager> want = all.stream().filter(v -> v.wantsToSpawnGolem(time)).limit(5L).toList();
        if (want.size() < minVillagers) return;
        IronGolem g = org.domi.furnace.server.entity.spawn.SpawnUtil.trySpawnMob(level, blockPosition(), 10, 8, 6,
                org.domi.furnace.server.entity.spawn.SpawnUtil.LEGACY_IRON_GOLEM,
                (l, p) -> org.domi.furnace.server.entity.spawn.SpawnUtil.place(l, new IronGolem(l, l.nextEntityId()), p));
        if (g != null) all.forEach(org.domi.furnace.server.entity.ai.sensing.GolemSensor::golemDetected);
    }

    /** wantsToSpawnGolem: 하루(24000틱) 안에 잤고 최근 골렘을 보지 못했다. */
    public boolean wantsToSpawnGolem(long time) {
        if (!golemSpawnConditionsMet(level.gameTime())) return false;
        return !brain.hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
    }

    private boolean golemSpawnConditionsMet(long time) {
        var slept = brain.getMemory(MemoryModuleType.LAST_SLEPT);
        return slept.isPresent() && time - slept.get() < 24000L;
    }

    /** gossip: 둘 다 1200틱 안에 나눈 적이 없으면 상대 소문 10개를 옮겨 오고(엔티티 난수) 시각을 맞춘 뒤 골렘 소환(5명)을 확인한다. */
    public void gossip(ChunkLevel level, Villager other, long time) {
        if ((time < lastGossipTime || time >= lastGossipTime + 1200L) && (time < other.lastGossipTime || time >= other.lastGossipTime + 1200L)) {
            gossips.transferFrom(other.gossips, random, 10);
            lastGossipTime = time;
            other.lastGossipTime = time;
            spawnGolemIfNeeded(level, time, 5);
        }
    }

    // ------------------------------------------------------------------ 소리

    @Override
    protected String getAmbientSound() {
        if (isSleeping()) return null;
        return tradingPlayer != null ? "entity.villager.trade" : "entity.villager.ambient";
    }

    @Override protected String getHurtSound(Damage source) { return "entity.villager.hurt"; }

    @Override protected String getDeathSound() { return "entity.villager.death"; }

    /** 거래 상대 플레이어(LookAndFollowTradingPlayerSink). */
    public Targetable tradingTarget() { return tradingPlayer == null ? null : tradingPlayer.entityPlayer(); }

    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {
        if (level.difficulty() != 0) {
            Witch witch = new Witch(level, level.nextEntityId());
            witch.moveTo(x(), y(), z(), yRot(), xRot());
            witch.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(witch.blockPosition()));
            witch.setNoAi(isNoAi());
            witch.persistenceRequired = true;
            level.addFreshEntity(witch);
            releaseAllPois();
            discard();
        } else {
            super.thunderHit(level, bolt);
        }
    }

    @Override
    protected void createAttributes(org.domi.furnace.server.attribute.AttributeMap map) {
        super.createAttributes(map);
        map.add(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED, 0.5).add(org.domi.furnace.server.attribute.Attribute.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_UNHAPPY, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_VILLAGER_DATA, SetEntityData.SERIALIZER_VILLAGER_DATA, new int[] {2, 0, 1});
    }

    public int[] villagerData() { return (int[]) entityData.get(DATA_VILLAGER_DATA); }

    public void setVillagerData(String type, String profession, int lvl) {
        int t = Math.max(0, TYPES.indexOf(type)), p = Math.max(0, PROFESSIONS.indexOf(profession));
        if (p != villagerData()[1]) offers = null;   // 바닐라 setVillagerData: 직업이 바뀌면 거래표를 다시 만든다
        entityData.set(DATA_VILLAGER_DATA, new int[] {t, p, lvl});
    }

    public int villagerLevel() { return villagerData()[2]; }

    public String profession() { return PROFESSIONS.get(villagerData()[1]); }

    // ------------------------------------------------------------------ 거래(Merchant)

    /** 바닐라 setTradingPlayer: 거래 중이던 플레이어가 빠지면(stopTrading) 특별 가격을 되돌린다. */
    @Override
    public void setTradingPlayer(MenuPlayer player) {
        boolean stop = tradingPlayer != null && player == null;
        this.tradingPlayer = player;
        if (stop) resetSpecialPrices();
    }

    @Override public MenuPlayer tradingPlayer() { return tradingPlayer; }

    /** 바닐라 getOffers: 아직 없으면 직업·레벨의 거래표로 만든다. */
    @Override
    public MerchantOffers offers() {
        if (offers == null) {
            offers = new MerchantOffers();
            updateTrades();
        }
        return offers;
    }

    public void setOffers(MerchantOffers o) { offers = o; }

    /** 바닐라 updateTrades: 현재 직업·레벨의 후보에서 2개를 거래 목록에 더한다. 거래표가 없는 직업(무직·바보)은 아무것도 안 한다. */
    protected void updateTrades() { updateTrades(random); }

    /** updateTrades 를 넘긴 난수로 한다(시험·재현용). 거래 목록이 없으면 만든다. */
    public void updateTrades(org.domi.furnace.server.block.RandomSource r) {
        if (offers == null) offers = new MerchantOffers();
        var table = VillagerTrades.TRADES.get(profession());
        int lvl = villagerLevel();
        if (table == null || lvl < 1 || lvl > table.length) return;
        VillagerTrades.ItemListing[] listings = table[lvl - 1];
        if (listings != null) addOffersFromItemListings(offers, listings, 2, r);
    }

    /** 바닐라 AbstractVillager.addOffersFromItemListings: 후보에서 하나씩 무작위로 뽑아(nextInt(남은 수)) 거래가 만들어지면 더한다. 모두 count 개가 될 때까지. */
    static void addOffersFromItemListings(MerchantOffers target, VillagerTrades.ItemListing[] listings, int count,
                                          org.domi.furnace.server.block.RandomSource r, VillagerTrades.TradeContext ctx) {
        java.util.ArrayList<VillagerTrades.ItemListing> pool = new java.util.ArrayList<>(java.util.Arrays.asList(listings));
        int added = 0;
        while (added < count && !pool.isEmpty()) {
            MerchantOffer offer = pool.remove(r.nextInt(pool.size())).getOffer(ctx, r);
            if (offer != null) {
                target.add(offer);
                added++;
            }
        }
    }

    private void addOffersFromItemListings(MerchantOffers target, VillagerTrades.ItemListing[] listings, int count, org.domi.furnace.server.block.RandomSource r) {
        addOffersFromItemListings(target, listings, count, r, new VillagerTrades.TradeContext(TYPES.get(villagerData()[0]), level.lootData(),
                (int) Math.floor(x()), (int) Math.floor(y()), (int) Math.floor(z()), level.explorerMaps()));
    }

    /** 바닐라 increaseMerchantCareer: 레벨을 하나 올리고 그 레벨의 거래를 더한다. */
    private void increaseMerchantCareer() {
        int[] d = villagerData();
        entityData.set(DATA_VILLAGER_DATA, new int[] {d[0], d[1], d[2] + 1});
        updateTrades();
    }

    /** 바닐라 shouldIncreaseLevel: 경험치가 지금 레벨의 다음 단계 기준에 이르렀다(레벨 1~4 만). */
    private boolean shouldIncreaseLevel() {
        int lvl = villagerLevel();
        return lvl >= 1 && lvl < 5 && villagerXp >= XP_PER_LEVEL[lvl];
    }

    /** 바닐라 needsToRestock: 한 번이라도 쓴 거래가 있다. */
    public boolean needsToRestock() {
        for (MerchantOffer o : offers()) if (o.needsRestock()) return true;
        return false;
    }

    /**
     * 바닐라 restock: 수요를 갱신하고 사용 횟수를 0 으로 되돌린다.
     * TODO(평판): updateSpecialPrices(gossip)는 평판이 없어 생략. 침대·직업 블록·시간에 따른 호출 시점(shouldRestock)은 일과(brain) 범위.
     */
    public void restock() {
        for (MerchantOffer o : offers()) o.updateDemand();
        for (MerchantOffer o : offers()) o.resetUses();
        lastRestockGameTime = level.gameTime();
        numberOfRestocksToday++;
    }

    /** allowedToRestock: 오늘 처음이거나, 두 번 미만이고 마지막 보충 뒤 2400틱이 지났다. */
    private boolean allowedToRestock() {
        return numberOfRestocksToday == 0 || numberOfRestocksToday < 2 && level.gameTime() > lastRestockGameTime + 2400L;
    }

    /** shouldRestock: 마지막 보충 뒤 12000틱이 지났거나 날이 바뀌었으면 하루 횟수를 되돌리고(못 한 수요 따라잡기), 보충할 수 있고 쓴 거래가 있나. */
    public boolean shouldRestock() {
        long due = lastRestockGameTime + 12000L;
        long now = level.gameTime();
        boolean reset = now > due;
        long day = level.dayTime();
        if (lastRestockCheckDayTime > 0L) reset |= day / 24000L > lastRestockCheckDayTime / 24000L;
        lastRestockCheckDayTime = day;
        if (reset) {
            lastRestockGameTime = now;
            resetNumberOfRestocks();
        }
        return allowedToRestock() && needsToRestock();
    }

    /** resetNumberOfRestocks: catchUpDemand(못 한 보충 수만큼 수요 갱신) 뒤 0. */
    private void resetNumberOfRestocks() {
        int missed = 2 - numberOfRestocksToday;
        if (missed > 0) for (MerchantOffer o : offers()) o.resetUses();
        for (int i = 0; i < missed; i++) for (MerchantOffer o : offers()) o.updateDemand();
        numberOfRestocksToday = 0;
    }

    /** AbstractVillager.notifyTrade: 사용 횟수 + 주변 소리 시계 되돌림 → rewardTradeXp(경험치·레벨업 대기·구슬 하나). TODO(발전 과제): villager_trade. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        resetAmbientSoundTime();
        int xp = 3 + random.nextInt(4);
        villagerXp += offer.xp();
        lastTradedPlayer = tradingPlayer == null ? null : tradingPlayer.entityPlayer();
        if (shouldIncreaseLevel()) {
            updateMerchantTimer = 40;
            increaseProfessionLevelOnUpdate = true;
            xp += 5;
        }
        // 바닐라는 나누지 않고 구슬 하나(new ExperienceOrb)를 놓는다
        if (offer.shouldRewardExp()) level.addFreshEntity(new ExperienceOrb(level, level.nextEntityId(), x(), y() + 0.5, z(), xp));
    }

    /** AbstractVillager.notifyTradeUpdated: 마지막 소리에서 20틱이 지났을 때만 예/아니요 소리(볼륨·목소리 높이). */
    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (ambientSoundTime > -getAmbientSoundInterval() + 20) {
            resetAmbientSoundTime();
            playSound(stack.isEmpty() ? "entity.villager.no" : "entity.villager.yes", getSoundVolume(), getVoicePitch());
        }
    }

    @Override public int villagerXp() { return villagerXp; }

    @Override public void overrideXp(int xp) { villagerXp = xp; }

    @Override public boolean showProgressBar() { return true; }

    @Override public String notifyTradeSound() { return "entity.villager.yes"; }

    @Override public boolean canRestock() { return true; }

    /** 거래 화면을 열 수 있나(아기가 아니고, 직업이 있거나 무직이 아니며, 거래가 있고, 누가 거래 중이 아님). */
    public boolean canTrade() {
        return isAlive() && !isBaby() && tradingPlayer == null && !offers().isEmpty() && !profession().equals("none") && !profession().equals("nitwit");
    }

    public Text tradeTitle() {
        String prof = profession();
        Text name = prof.equals("none") ? Text.translatable("entity.minecraft.villager") : Text.translatable("entity.minecraft.villager." + prof);
        return Text.translatable("merchant.title", name, Text.translatable("merchant.level." + villagerLevel()));
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        int[] d = villagerData();
        NbtCompound vd = new NbtCompound();
        vd.putString("type", "minecraft:" + TYPES.get(d[0]));
        vd.putString("profession", "minecraft:" + PROFESSIONS.get(d[1]));
        vd.putInt("level", d[2]);
        tag.putCompound("VillagerData", vd);
        tag.putInt("Xp", villagerXp);
        tag.putByte("FoodLevel", (byte) foodLevel);
        tag.putBoolean("Willing", false);
        tag.putList("Gossips", gossips.store());
        tag.putLong("LastRestock", lastRestockGameTime);
        tag.putLong("LastGossipDecay", lastGossipDecayTime);
        tag.putInt("RestocksToday", numberOfRestocksToday);
        if (assignProfessionWhenSpawned) tag.putBoolean("AssignProfessionWhenSpawned", true);
        writeInventoryToSave(tag);
        if (!offers().isEmpty()) {
            NbtList recipes = new NbtList((byte) 10);
            for (MerchantOffer o : offers) recipes.add(writeOffer(o));
            NbtCompound wrapper = new NbtCompound();
            wrapper.putList("Recipes", recipes);
            tag.putCompound("Offers", wrapper);
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("VillagerData") instanceof NbtCompound vd) {
            String type = vd.tags().get("type") instanceof String s ? s.replace("minecraft:", "") : "plains";
            String prof = vd.tags().get("profession") instanceof String s ? s.replace("minecraft:", "") : "none";
            setVillagerData(type, prof, Math.max(1, vd.getIntOr0("level")));
        }
        villagerXp = tag.getIntOr0("Xp");
        foodLevel = tag.tags().get("FoodLevel") instanceof Number n ? n.intValue() : 0;
        if (tag.tags().get("Gossips") instanceof NbtList gl) gossips.update(gl);
        lastRestockGameTime = tag.tags().get("LastRestock") instanceof Number n ? n.longValue() : 0L;
        lastGossipDecayTime = tag.tags().get("LastGossipDecay") instanceof Number n ? n.longValue() : 0L;
        numberOfRestocksToday = tag.getIntOr0("RestocksToday");
        if (tag.contains("AssignProfessionWhenSpawned")) assignProfessionWhenSpawned = tag.getBoolean("AssignProfessionWhenSpawned");
        readInventoryFromSave(tag);
        setCanPickUpLoot(true);
        refreshBrain(level);
        offers = null;
        if (tag.tags().get("Offers") instanceof NbtCompound w && w.tags().get("Recipes") instanceof NbtList list) {
            offers = new MerchantOffers();
            for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof NbtCompound c) {
                MerchantOffer o = readOffer(c);
                if (o != null) offers.add(o);
            }
        }
    }

    private static NbtCompound writeStack(ItemStack s) { return ItemStackNbt.write(s); }

    private static ItemStack readStack(NbtCompound it) { return ItemStackNbt.read(it); }

    private static NbtCompound writeCost(ItemCost c) {
        NbtCompound t = new NbtCompound();
        t.putString("id", c.item());
        t.putInt("count", c.count());
        return t;
    }

    private static ItemCost readCost(NbtCompound c) {
        return c.tags().get("id") instanceof String id ? new ItemCost(id, Math.max(1, c.tags().get("count") instanceof Number n ? n.intValue() : 1)) : null;
    }

    static NbtCompound writeOffer(MerchantOffer o) {
        NbtCompound t = new NbtCompound();
        t.putCompound("buy", writeCost(o.baseCostA()));
        o.costB().ifPresent(b -> t.putCompound("buyB", writeCost(b)));
        t.putCompound("sell", writeStack(o.result()));
        t.putInt("uses", o.uses());
        t.putInt("maxUses", o.maxUses());
        t.putBoolean("rewardExp", o.rewardExp());
        t.putInt("xp", o.xp());
        t.putFloat("priceMultiplier", o.priceMultiplier());
        t.putInt("specialPrice", o.specialPriceDiff());
        t.putInt("demand", o.demand());
        return t;
    }

    static MerchantOffer readOffer(NbtCompound t) {
        ItemCost a = t.tags().get("buy") instanceof NbtCompound b ? readCost(b) : null;
        ItemStack sell = t.tags().get("sell") instanceof NbtCompound s ? readStack(s) : ItemStack.EMPTY;
        if (a == null || sell.isEmpty()) return null;
        ItemCost b = t.tags().get("buyB") instanceof NbtCompound bb ? readCost(bb) : null;
        boolean reward = !t.contains("rewardExp") || t.getBoolean("rewardExp");
        return new MerchantOffer(a, Optional.ofNullable(b), sell, t.getIntOr0("uses"), t.contains("maxUses") ? t.getIntOr0("maxUses") : 4, reward,
                t.getIntOr0("specialPrice"), t.getIntOr0("demand"), t.contains("priceMultiplier") ? t.getFloat("priceMultiplier") : 0.0F,
                t.contains("xp") ? t.getIntOr0("xp") : 1);
    }

    /** 바닐라 customServerAiStep: 브레인 → 구조물 직업 표시 끄기 → (거래 중 아니면) 레벨업 대기 → 거래 기쁨 표시 → 레이드 중 땀(42) → 무직 거래 중단. */
    @Override
    protected void customServerAiStep() {
        villagerBrain().tick(level, this);
        if (assignProfessionWhenSpawned) assignProfessionWhenSpawned = false;
        if (tradingPlayer == null && updateMerchantTimer > 0) {
            updateMerchantTimer--;
            if (updateMerchantTimer <= 0) {
                if (increaseProfessionLevelOnUpdate) {
                    increaseMerchantCareer();
                    increaseProfessionLevelOnUpdate = false;
                }
                addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.byName("regeneration"), 200, 0));
            }
        }
        if (lastTradedPlayer != null) {
            onReputationEvent(ReputationEvent.TRADE, lastTradedPlayer);
            broadcastEntityEvent((byte) 14);
            lastTradedPlayer = null;
        }
        if (!isNoAi() && random.nextInt(100) == 0) {
            var raid = level.raids() == null ? null : level.raids().getRaidAt(blockPosition());
            if (raid != null && raid.isActive() && !raid.isOver()) broadcastEntityEvent((byte) 42);
        }
        if (profession().equals("none") && tradingPlayer != null) setTradingPlayer(null);
        super.customServerAiStep();
    }
}
