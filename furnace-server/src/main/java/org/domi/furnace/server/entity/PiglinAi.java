// 바닐라 PiglinAi: 피글린 브레인 구성과 규칙 — 금 아이템 감상·물물교환, 금 갑옷이 없는 플레이어 공격, 호글린 사냥/도피, 석궁 사격, 축하.
// TODO(탑승): 아기 피글린이 아기 호글린을 타는 활동(RIDE)은 탑승 규칙이 생기면. 상자 열기·금 블록 캐기 자극(angerNearbyPiglins)은 블록·탈것 쪽에서 부른다.
package org.domi.furnace.server.entity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;

public final class PiglinAi {
    public static final int REPELLENT_DETECTION_RANGE_HORIZONTAL = 8;
    public static final int REPELLENT_DETECTION_RANGE_VERTICAL = 4;
    public static final String BARTERING_ITEM = "minecraft:gold_ingot";
    private static final int PLAYER_ANGER_RANGE = 16;
    private static final int ANGER_DURATION = 600;
    private static final int ADMIRE_DURATION = 119;
    private static final int MAX_DISTANCE_TO_WALK_TO_ITEM = 9;
    private static final int MAX_TIME_TO_WALK_TO_ITEM = 200;
    private static final int HOW_LONG_TIME_TO_DISABLE_ADMIRE_WALKING_IF_CANT_REACH_ITEM = 200;
    private static final int CELEBRATION_TIME = 300;
    public static final UniformInt TIME_BETWEEN_HUNTS = UniformInt.of(30 * 20, 120 * 20);
    private static final int BABY_FLEE_DURATION_AFTER_GETTING_HIT = 100;
    private static final int HIT_BY_PLAYER_MEMORY_TIMEOUT = 400;
    private static final int MAX_WALK_DISTANCE_TO_START_RIDING = 8;
    private static final UniformInt RIDE_START_INTERVAL = UniformInt.of(10 * 20, 40 * 20);
    private static final UniformInt RIDE_DURATION = UniformInt.of(10 * 20, 30 * 20);
    private static final UniformInt RETREAT_DURATION = UniformInt.of(5 * 20, 20 * 20);
    private static final int MELEE_ATTACK_COOLDOWN = 20;
    private static final int EAT_COOLDOWN = 200;
    private static final int DESIRED_DISTANCE_FROM_ENTITY_WHEN_AVOIDING = 12;
    private static final int MAX_LOOK_DIST = 8;
    private static final int MAX_LOOK_DIST_FOR_PLAYER_HOLDING_LOVED_ITEM = 14;
    private static final int INTERACTION_RANGE = 8;
    private static final int MIN_DESIRED_DIST_FROM_TARGET_WHEN_HOLDING_CROSSBOW = 5;
    private static final float SPEED_WHEN_STROLLING = 0.6F;
    private static final float SPEED_WHEN_AVOIDING = 1.0F;
    private static final float SPEED_WHEN_CELEBRATING = 0.6F;
    private static final float SPEED_MULTIPLIER_WHEN_BACKING_UP = 0.75F;
    private static final UniformInt AVOID_ZOMBIFIED_DURATION = UniformInt.of(5 * 20, 7 * 20);
    private static final UniformInt BABY_AVOID_NEMESIS_DURATION = UniformInt.of(5 * 20, 7 * 20);

    private PiglinAi() {}

    public static Brain<Piglin> makeBrain(Piglin piglin, Brain<Piglin> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initAdmireItemActivity(brain);
        initFightActivity(piglin, brain);
        initCelebrateActivity(brain);
        initRetreatActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    public static void initMemories(Piglin piglin, org.domi.furnace.server.block.RandomSource random) {
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.HUNTED_RECENTLY, true, TIME_BETWEEN_HUNTS.sample(random));
    }

    private static Map.Entry<BehaviorControl<? super Piglin>, Integer> w(BehaviorControl<? super Piglin> b, int weight) { return Map.entry(b, weight); }

    private static void initCoreActivity(Brain<Piglin> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new LookAtTargetSink(45, 90), new MoveToTargetSink(), InteractWithDoor.create(), babyAvoidNemesis(), avoidZombified(),
                StopHoldingItemIfNoLongerAdmiring.create(), StartAdmiringItemIfSeen.<Piglin>create(ADMIRE_DURATION),
                StartCelebratingIfTargetDead.<Piglin>create(CELEBRATION_TIME, PiglinAi::wantsToDance), StopBeingAngryIfTargetDead.<Piglin>create()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Piglin> brain) {
        brain.addActivity(Activity.IDLE, 10, List.of(
                SetEntityLookTarget.create(PiglinAi::isPlayerHoldingLovedItem, MAX_LOOK_DIST_FOR_PLAYER_HOLDING_LOVED_ITEM),
                StartAttacking.<Piglin>create(AbstractPiglin::isAdult, PiglinAi::findNearestValidAttackTarget),
                OneShot.sequence(OneShot.triggerIf((Piglin p) -> p.canHunt()), StartHuntingHoglin.create()),
                avoidRepellent(), createIdleLookBehaviors(), createIdleMovementBehaviors(), SetLookAndInteract.create(Targetable::isPlayer, 4)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initFightActivity(Piglin piglin, Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.FIGHT, 10, List.of(
                StopAttackingIfTargetInvalid.<Piglin>create(t -> !isNearestValidAttackTarget(piglin, t)),
                OneShot.sequence(OneShot.triggerIf((Piglin p) -> hasCrossbow(p)), BackUpIfTooClose.create(MIN_DESIRED_DIST_FROM_TARGET_WHEN_HOLDING_CROSSBOW, SPEED_MULTIPLIER_WHEN_BACKING_UP)),
                SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F), MeleeAttack.create(MELEE_ATTACK_COOLDOWN), new CrossbowAttack<Piglin>(), RememberIfHoglinWasKilled.create(),
                EraseMemoryIf.create((Piglin p) -> isNearZombified(p), MemoryModuleType.ATTACK_TARGET)), MemoryModuleType.ATTACK_TARGET);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initCelebrateActivity(Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.CELEBRATE, 10, List.of(
                avoidRepellent(), SetEntityLookTarget.create(PiglinAi::isPlayerHoldingLovedItem, MAX_LOOK_DIST_FOR_PLAYER_HOLDING_LOVED_ITEM),
                StartAttacking.<Piglin>create(AbstractPiglin::isAdult, PiglinAi::findNearestValidAttackTarget),
                OneShot.sequence(OneShot.triggerIf((Piglin p) -> !p.isDancing()), GoToTargetLocation.<Piglin>create(MemoryModuleType.CELEBRATE_LOCATION, 2, 1.0F)),
                OneShot.sequence(OneShot.triggerIf((Piglin p) -> p.isDancing()), GoToTargetLocation.<Piglin>create(MemoryModuleType.CELEBRATE_LOCATION, 4, SPEED_WHEN_CELEBRATING)),
                new RunOne<Piglin>(List.<Map.Entry<? extends BehaviorControl<? super Piglin>, Integer>>of(w(SetEntityLookTarget.create(t -> isType(t, EntityType.PIGLIN), 8.0F), 1),
                        w(RandomStroll.stroll(SPEED_WHEN_CELEBRATING, 2, 1), 1), w(new DoNothing(10, 20), 1)))), MemoryModuleType.CELEBRATE_LOCATION);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initAdmireItemActivity(Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.ADMIRE_ITEM, 10, List.of(GoToWantedItem.<Piglin>create(PiglinAi::isNotHoldingLovedItemInOffHand, 1.0F, true, MAX_DISTANCE_TO_WALK_TO_ITEM),
                StopAdmiringIfItemTooFarAway.create(MAX_DISTANCE_TO_WALK_TO_ITEM),
                StopAdmiringIfTiredOfTryingToReachItem.create(MAX_TIME_TO_WALK_TO_ITEM, HOW_LONG_TIME_TO_DISABLE_ADMIRE_WALKING_IF_CANT_REACH_ITEM)), MemoryModuleType.ADMIRING_ITEM);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initRetreatActivity(Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.AVOID, 10, List.of(
                SetWalkTargetAwayFrom.entity(MemoryModuleType.AVOID_TARGET, SPEED_WHEN_AVOIDING, DESIRED_DISTANCE_FROM_ENTITY_WHEN_AVOIDING, true), createIdleLookBehaviors(),
                createIdleMovementBehaviors(), EraseMemoryIf.create((Piglin p) -> wantsToStopFleeing(p), MemoryModuleType.AVOID_TARGET)), MemoryModuleType.AVOID_TARGET);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RunOne<Piglin> createIdleLookBehaviors() {
        return new RunOne<Piglin>(List.<Map.Entry<? extends BehaviorControl<? super Piglin>, Integer>>of(w(SetEntityLookTarget.createPlayers(MAX_LOOK_DIST), 1),
                w(SetEntityLookTarget.create(t -> isType(t, EntityType.PIGLIN), MAX_LOOK_DIST), 1), w(SetEntityLookTarget.create(t -> true, MAX_LOOK_DIST), 1), w(new DoNothing(30, 60), 1)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RunOne<Piglin> createIdleMovementBehaviors() {
        return new RunOne<Piglin>(List.<Map.Entry<? extends BehaviorControl<? super Piglin>, Integer>>of(w(RandomStroll.stroll(SPEED_WHEN_STROLLING), 2),
                w(InteractWith.of(EntityType.PIGLIN, 8, MemoryModuleType.INTERACTION_TARGET, SPEED_WHEN_STROLLING, 2), 2),
                w(OneShot.sequence(OneShot.triggerIf((Piglin p) -> !isNearRepellent(p)), SetWalkTargetFromLookTarget.create(SPEED_WHEN_STROLLING, 3)), 2), w(new DoNothing(30, 60), 1)));
    }

    private static OneShot<PathfinderMob> avoidRepellent() { return SetWalkTargetAwayFrom.pos(MemoryModuleType.NEAREST_REPELLENT, 1.0F, 8, false); }

    private static OneShot<Piglin> babyAvoidNemesis() {
        return CopyMemoryWithExpiry.create(Piglin::isBaby, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.AVOID_TARGET, BABY_AVOID_NEMESIS_DURATION);
    }

    private static OneShot<Piglin> avoidZombified() {
        return CopyMemoryWithExpiry.create(PiglinAi::isNearZombified, MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, MemoryModuleType.AVOID_TARGET, AVOID_ZOMBIFIED_DURATION);
    }

    public static void updateActivity(Piglin piglin) {
        Brain<Piglin> brain = piglin.piglinBrain();
        Activity before = brain.getActiveNonCoreActivity().orElse(null);
        brain.setActiveActivityToFirstValid(List.of(Activity.ADMIRE_ITEM, Activity.FIGHT, Activity.AVOID, Activity.CELEBRATE, Activity.IDLE));
        Activity after = brain.getActiveNonCoreActivity().orElse(null);
        if (before != after) getSoundForCurrentActivity(piglin).ifPresent(piglin::makeSound);
        piglin.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
        if (!brain.hasMemoryValue(MemoryModuleType.CELEBRATE_LOCATION)) brain.eraseMemory(MemoryModuleType.DANCING);
        piglin.setDancing(brain.hasMemoryValue(MemoryModuleType.DANCING));
    }

    // ------------------------------------------------------------------ 줍기·감상·물물교환

    public static void pickUpItem(Piglin piglin, ItemEntity itemEntity) {
        stopWalking(piglin);
        ItemStack stack;
        if (ItemTools.itemName(itemEntity.getItem()).equals("minecraft:gold_nugget")) {
            itemEntity.broadcastTake(piglin, itemEntity.getItem().count());
            stack = itemEntity.getItem();
            itemEntity.discard();
        } else {
            itemEntity.broadcastTake(piglin, 1);
            stack = removeOneItemFromItemEntity(itemEntity);
        }
        if (isLovedItem(stack)) {
            piglin.getBrain().eraseMemory(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM);
            holdInOffhand(piglin, stack);
            admireGoldItem(piglin);
        } else if (isFood(stack) && !hasEatenRecently(piglin)) {
            eat(piglin);
        } else {
            boolean equipped = !piglin.equipItemIfPossible(stack).isEmpty();
            if (!equipped) putInInventory(piglin, stack);
        }
    }

    private static void holdInOffhand(Piglin piglin, ItemStack stack) {
        if (isHoldingItemInOffHand(piglin)) piglin.spawnAtLocation(piglin.getItemBySlot(EquipmentSlot.OFFHAND));
        piglin.holdInOffHand(stack);
    }

    private static ItemStack removeOneItemFromItemEntity(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        ItemStack one = stack.withCount(1);
        ItemStack rest = stack.withCount(stack.count() - 1);
        if (rest.isEmpty()) itemEntity.discard();
        else itemEntity.setItem(rest);
        return one;
    }

    public static void stopHoldingOffHandItem(Piglin piglin, boolean shouldBarter) {
        ItemStack off = piglin.getItemBySlot(EquipmentSlot.OFFHAND);
        piglin.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (piglin.isAdult()) {
            boolean bartering = isBarterCurrency(off);
            if (shouldBarter && bartering) {
                throwItems(piglin, getBarterResponseItems(piglin));
            } else if (!bartering) {
                boolean equipped = !piglin.equipItemIfPossible(off).isEmpty();
                if (!equipped) putInInventory(piglin, off);
            }
        } else {
            boolean equipped = !piglin.equipItemIfPossible(off).isEmpty();
            if (!equipped) {
                ItemStack main = piglin.getItemBySlot(EquipmentSlot.MAINHAND);
                if (isLovedItem(main)) putInInventory(piglin, main);
                else throwItems(piglin, Collections.singletonList(main));
                piglin.holdInMainHand(off);
            }
        }
    }

    public static void cancelAdmiring(Piglin piglin) {
        if (isAdmiringItem(piglin) && !piglin.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            piglin.spawnAtLocation(piglin.getItemBySlot(EquipmentSlot.OFFHAND));
            piglin.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private static void putInInventory(Piglin piglin, ItemStack stack) {
        ItemStack left = piglin.addToInventory(stack);
        throwItemsTowardRandomPos(piglin, Collections.singletonList(left));
    }

    private static void throwItems(Piglin piglin, List<ItemStack> items) {
        Optional<Player> player = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        if (player.isPresent()) throwItemsTowardPlayer(piglin, player.get(), items);
        else throwItemsTowardRandomPos(piglin, items);
    }

    private static void throwItemsTowardRandomPos(Piglin piglin, List<ItemStack> items) { throwItemsTowardPos(piglin, items, getRandomNearbyPos(piglin)); }

    private static void throwItemsTowardPlayer(Piglin piglin, Player player, List<ItemStack> items) { throwItemsTowardPos(piglin, items, new double[] {player.x(), player.y(), player.z()}); }

    private static void throwItemsTowardPos(Piglin piglin, List<ItemStack> items, double[] pos) {
        if (!items.isEmpty()) {
            piglin.swing(1);
            for (ItemStack s : items) if (!s.isEmpty()) BehaviorUtils.throwItem(piglin, s, new double[] {pos[0], pos[1] + 1.0, pos[2]});
        }
    }

    private static List<ItemStack> getBarterResponseItems(Piglin piglin) {
        return piglin.level().entityDrops("minecraft:gameplay/piglin_bartering", piglin.lootEntity(), piglin.x(), piglin.y(), piglin.z(), "generic", null, null, 0L);
    }

    private static boolean wantsToDance(Piglin piglin, Targetable target) {
        if (!(target instanceof Entity e) || e.type() != EntityType.HOGLIN) return false;
        return org.domi.furnace.server.block.RandomSource.create(piglin.level().gameTime()).nextFloat() < 0.1F;
    }

    public static boolean wantsToPickup(Piglin piglin, ItemStack stack) {
        if (piglin.isBaby() && ItemTags.is(stack, "minecraft:ignored_by_piglin_babies")) return false;
        if (ItemTags.is(stack, "minecraft:piglin_repellents")) return false;
        if (isAdmiringDisabled(piglin) && piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return false;
        if (isBarterCurrency(stack)) return isNotHoldingLovedItemInOffHand(piglin);
        boolean canAddToInventory = piglin.canAddToInventory(stack);
        if (ItemTools.itemName(stack).equals("minecraft:gold_nugget")) return canAddToInventory;
        if (isFood(stack)) return !hasEatenRecently(piglin) && canAddToInventory;
        return !isLovedItem(stack) ? piglin.wantsToReplace(stack) : isNotHoldingLovedItemInOffHand(piglin) && canAddToInventory;
    }

    public static boolean isLovedItem(ItemStack stack) { return ItemTags.is(stack, "minecraft:piglin_loved"); }

    private static boolean isBarterCurrency(ItemStack stack) { return ItemTools.itemName(stack).equals(BARTERING_ITEM); }

    private static boolean isFood(ItemStack stack) { return ItemTags.is(stack, "minecraft:piglin_food"); }

    /** PiglinAi.isWearingGold: 금 방어구를 하나라도 입었다. */
    public static boolean wearsGoldArmor(Targetable entity) {
        if (entity instanceof LivingEntity le) {
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
                if (isGoldArmor(ItemTools.itemName(le.getItemBySlot(slot)))) return true;
            }
        } else if (entity instanceof Player p) {
            var inv = p.inventory();
            for (int slot = 5; slot <= 8; slot++) if (isGoldArmor(ItemTools.itemName(inv.get(slot)))) return true;
        }
        return false;
    }

    private static boolean isGoldArmor(String n) {
        return n.startsWith("minecraft:golden_") && (n.endsWith("_helmet") || n.endsWith("_chestplate") || n.endsWith("_leggings") || n.endsWith("_boots"));
    }

    public static boolean isPlayerHoldingLovedItem(Targetable entity) {
        if (!(entity instanceof Player p)) return false;
        var inv = p.inventory();
        return isLovedItem(inv.get(inv.heldWindowSlot())) || isLovedItem(inv.get(45));
    }

    public static boolean isZombified(EntityType type) { return type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.ZOGLIN; }

    private static boolean isType(Targetable t, EntityType type) { return t instanceof Entity e && e.type() == type; }

    public static boolean mobInteract(Piglin piglin, Player player, int hand, ItemStack held) {
        if (canAdmire(piglin, held)) {
            ItemStack one = held.withCount(1);
            if (!player.vitals().isCreative()) {
                var inv = player.inventory();
                int ws = hand == 0 ? inv.heldWindowSlot() : 45;
                inv.set(ws, held.withCount(held.count() - 1));
            }
            holdInOffhand(piglin, one);
            admireGoldItem(piglin);
            stopWalking(piglin);
            return true;
        }
        return false;
    }

    public static boolean canAdmire(Piglin piglin, ItemStack stack) { return !hasEatenRecently(piglin) && !isAdmiringItem(piglin) && piglin.isAdult() && isBarterCurrency(stack); }

    // ------------------------------------------------------------------ 분노·도피

    public static void wasHurtBy(Piglin piglin, Targetable attacker) {
        if (attacker instanceof Piglin) return;
        if (isHoldingItemInOffHand(piglin)) stopHoldingOffHandItem(piglin, false);
        Brain<Piglin> brain = piglin.piglinBrain();
        brain.eraseMemory(MemoryModuleType.CELEBRATE_LOCATION);
        brain.eraseMemory(MemoryModuleType.DANCING);
        brain.eraseMemory(MemoryModuleType.ADMIRING_ITEM);
        if (attacker instanceof Player) brain.setMemoryWithExpiry(MemoryModuleType.ADMIRING_DISABLED, true, HIT_BY_PLAYER_MEMORY_TIMEOUT);
        getAvoidTarget(piglin).ifPresent(avoid -> {
            if (!sameType(avoid, attacker)) brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
        });
        if (piglin.isBaby()) {
            brain.setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, attacker, BABY_FLEE_DURATION_AFTER_GETTING_HIT);
            if (Sensor.isEntityAttackableIgnoringLineOfSight(piglin, attacker)) broadcastAngerTarget(piglin, attacker);
        } else if (isType(attacker, EntityType.HOGLIN) && hoglinsOutnumberPiglins(piglin)) {
            setAvoidTargetAndDontHuntForAWhile(piglin, attacker);
            broadcastRetreat(piglin, attacker);
        } else {
            maybeRetaliate(piglin, attacker);
        }
    }

    private static boolean sameType(Targetable a, Targetable b) {
        if (a instanceof Entity x && b instanceof Entity y) return x.type() == y.type();
        return a.isPlayer() && b.isPlayer();
    }

    public static void maybeRetaliate(AbstractPiglin piglin, Targetable attacker) {
        if (!piglin.getBrain().isActive(Activity.AVOID)) {
            if (Sensor.isEntityAttackableIgnoringLineOfSight(piglin, attacker)) {
                if (!BehaviorUtils.isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(piglin, attacker, 4.0)) {
                    if (attacker.isPlayer() && piglin.level().gameRule("universalAnger")) {
                        setAngerTargetToNearestTargetablePlayerIfFound(piglin, attacker);
                        broadcastUniversalAnger(piglin);
                    } else {
                        setAngerTarget(piglin, attacker);
                        broadcastAngerTarget(piglin, attacker);
                    }
                }
            }
        }
    }

    public static Optional<SoundName> noop() { return Optional.empty(); }

    private record SoundName(String n) {}

    public static Optional<String> getSoundForCurrentActivity(Piglin piglin) { return piglin.piglinBrain().getActiveNonCoreActivity().map(a -> getSoundForActivity(piglin, a)); }

    private static String getSoundForActivity(Piglin piglin, Activity activity) {
        if (activity == Activity.FIGHT) return "entity.piglin.angry";
        if (piglin.isConverting()) return "entity.piglin.retreat";
        if (activity == Activity.AVOID && isNearAvoidTarget(piglin)) return "entity.piglin.retreat";
        if (activity == Activity.ADMIRE_ITEM) return "entity.piglin.admiring_item";
        if (activity == Activity.CELEBRATE) return "entity.piglin.celebrate";
        if (seesPlayerHoldingLovedItem(piglin)) return "entity.piglin.jealous";
        return isNearRepellent(piglin) ? "entity.piglin.retreat" : "entity.piglin.ambient";
    }

    private static boolean isNearAvoidTarget(Piglin piglin) {
        var avoid = piglin.piglinBrain().getMemory(MemoryModuleType.AVOID_TARGET);
        if (avoid.isEmpty()) return false;
        return BehaviorUtils.distSqr(piglin, avoid.get()) < 144.0;
    }

    public static void angerNearbyPiglins(org.domi.furnace.server.block.ChunkLevel level, Player player, boolean requireLineOfSight) {
        var box = player.boundingBox().inflate(PLAYER_ANGER_RANGE);
        for (Piglin p : level.getEntitiesOfClass(Piglin.class, box, x -> true)) {
            if (isIdle(p) && (!requireLineOfSight || BehaviorUtils.canSee(p, player))) {
                if (level.gameRule("universalAnger")) setAngerTargetToNearestTargetablePlayerIfFound(p, player);
                else setAngerTarget(p, player);
            }
        }
    }

    private static List<AbstractPiglin> getVisibleAdultPiglins(AbstractPiglin piglin) {
        return piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS).map(l -> l.stream().map(m -> (AbstractPiglin) m).toList()).orElse(List.of());
    }

    private static List<AbstractPiglin> getAdultPiglins(AbstractPiglin piglin) {
        return piglin.getBrain().getMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS).map(l -> l.stream().map(m -> (AbstractPiglin) m).toList()).orElse(List.of());
    }

    private static Optional<Targetable> getAvoidTarget(Piglin piglin) {
        return piglin.piglinBrain().hasMemoryValue(MemoryModuleType.AVOID_TARGET) ? piglin.piglinBrain().getMemory(MemoryModuleType.AVOID_TARGET) : Optional.empty();
    }

    public static Optional<Player> getNearestVisibleTargetablePlayer(AbstractPiglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER) ? piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER) : Optional.empty();
    }

    private static void broadcastUniversalAnger(AbstractPiglin piglin) {
        getAdultPiglins(piglin).forEach(p -> getNearestVisibleTargetablePlayer(p).ifPresent(pl -> setAngerTarget(p, pl)));
    }

    public static void setAngerTarget(AbstractPiglin piglin, Targetable target) {
        if (Sensor.isEntityAttackableIgnoringLineOfSight(piglin, target)) {
            piglin.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, uuidOf(target), ANGER_DURATION);
            if (isType(target, EntityType.HOGLIN) && piglin.canHunt()) dontKillAnyMoreHoglinsForAWhile(piglin);
            if (target.isPlayer() && piglin.level().gameRule("universalAnger")) piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.UNIVERSAL_ANGER, true, ANGER_DURATION);
        }
    }

    /** 바닐라 target.getUUID() — Furnace 플레이어는 Entity 가 아니라서 따로 읽는다. */
    private static java.util.UUID uuidOf(Targetable target) {
        if (target instanceof Entity e) return e.uuid();
        if (target instanceof Player p) return p.uuid();
        throw new IllegalArgumentException("UUID 를 모르는 분노 대상: " + target);
    }

    private static void setAngerTargetToNearestTargetablePlayerIfFound(AbstractPiglin piglin, Targetable fallback) {
        Optional<Player> p = getNearestVisibleTargetablePlayer(piglin);
        if (p.isPresent()) setAngerTarget(piglin, p.get());
        else setAngerTarget(piglin, fallback);
    }

    public static void broadcastAngerTarget(AbstractPiglin piglin, Targetable target) {
        getAdultPiglins(piglin).forEach(p -> {
            if (!isType(target, EntityType.HOGLIN) || p.canHunt() && target instanceof Hoglin h && h.canBeHunted()) setAngerTargetIfCloserThanCurrentTarget(p, target);
        });
    }

    private static void setAngerTargetIfCloserThanCurrentTarget(AbstractPiglin piglin, Targetable target) {
        Optional<Targetable> current = getAngerTarget(piglin);
        Targetable nearest = BehaviorUtils.getNearestTarget(piglin, current, target);
        if (current.isEmpty() || current.get() != nearest) setAngerTarget(piglin, nearest);
    }

    private static Optional<Targetable> getAngerTarget(AbstractPiglin piglin) { return BehaviorUtils.getLivingEntityFromUUIDMemory(piglin, MemoryModuleType.ANGRY_AT); }

    public static void dontKillAnyMoreHoglinsForAWhile(AbstractPiglin piglin) {
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.HUNTED_RECENTLY, true, TIME_BETWEEN_HUNTS.sample(piglin.level().random()));
    }

    private static void setAvoidTargetAndDontHuntForAWhile(Piglin piglin, Targetable target) {
        var brain = piglin.piglinBrain();
        brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, target, RETREAT_DURATION.sample(piglin.level().random()));
        dontKillAnyMoreHoglinsForAWhile(piglin);
    }

    private static void broadcastRetreat(Piglin piglin, Targetable target) {
        getVisibleAdultPiglins(piglin).stream().filter(p -> p instanceof Piglin).forEach(p -> retreatFromNearestTarget((Piglin) p, target));
    }

    private static void retreatFromNearestTarget(Piglin piglin, Targetable target) {
        var brain = piglin.piglinBrain();
        Targetable t = BehaviorUtils.getNearestTarget(piglin, brain.getMemory(MemoryModuleType.AVOID_TARGET), target);
        t = BehaviorUtils.getNearestTarget(piglin, brain.getMemory(MemoryModuleType.ATTACK_TARGET), t);
        setAvoidTargetAndDontHuntForAWhile(piglin, t);
    }

    private static boolean wantsToStopFleeing(Piglin piglin) {
        var brain = piglin.piglinBrain();
        if (!brain.hasMemoryValue(MemoryModuleType.AVOID_TARGET)) return true;
        Targetable target = brain.getMemory(MemoryModuleType.AVOID_TARGET).get();
        if (isType(target, EntityType.HOGLIN)) return piglinsEqualOrOutnumberHoglins(piglin);
        if (target instanceof Entity e && isZombified(e.type())) return !brain.isMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, target);
        return false;
    }

    private static boolean piglinsEqualOrOutnumberHoglins(Piglin piglin) { return !hoglinsOutnumberPiglins(piglin); }

    private static boolean hoglinsOutnumberPiglins(Piglin piglin) {
        int piglins = piglin.piglinBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT).orElse(0) + 1;
        int hoglins = piglin.piglinBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT).orElse(0);
        return hoglins > piglins;
    }

    private static boolean isNearZombified(Piglin piglin) {
        var z = piglin.piglinBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED);
        return z.isPresent() && BehaviorUtils.distSqr(piglin, z.get()) < 36.0;
    }

    private static boolean isNearestValidAttackTarget(AbstractPiglin piglin, Targetable target) { return findNearestValidAttackTarget(piglin).filter(t -> t == target).isPresent(); }

    private static Optional<? extends Targetable> findNearestValidAttackTarget(AbstractPiglin piglin) {
        var brain = piglin.getBrain();
        if (piglin instanceof Piglin p && isNearZombified(p)) return Optional.empty();
        Optional<Targetable> angry = getAngerTarget(piglin);
        if (angry.isPresent() && Sensor.isEntityAttackableIgnoringLineOfSight(piglin, angry.get())) return angry;
        if (brain.hasMemoryValue(MemoryModuleType.UNIVERSAL_ANGER)) {
            Optional<Player> p = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
            if (p.isPresent()) return p;
        }
        Optional<Mob> nemesis = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
        if (nemesis.isPresent()) return nemesis;
        Optional<Player> noGold = brain.getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD);
        return noGold.isPresent() && Sensor.isEntityAttackable(piglin, noGold.get()) ? noGold : Optional.empty();
    }

    // ------------------------------------------------------------------ 상태 도우미

    public static boolean isIdle(AbstractPiglin piglin) { return piglin.getBrain().isActive(Activity.IDLE); }

    private static boolean hasCrossbow(Piglin piglin) { return piglin.isHolding("minecraft:crossbow"); }

    private static boolean isNearRepellent(Piglin piglin) { return piglin.piglinBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT); }

    private static boolean seesPlayerHoldingLovedItem(Piglin piglin) { return piglin.piglinBrain().hasMemoryValue(MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM); }

    private static void stopWalking(Piglin piglin) {
        piglin.piglinBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        piglin.getNavigation().stop();
    }

    private static double[] getRandomNearbyPos(Piglin piglin) {
        double[] v = LandRandomPos.getPos(piglin, 4, 2);
        return v == null ? new double[] {piglin.x(), piglin.y(), piglin.z()} : v;
    }

    private static void admireGoldItem(Piglin piglin) { piglin.piglinBrain().setMemoryWithExpiry(MemoryModuleType.ADMIRING_ITEM, true, ADMIRE_DURATION); }

    private static boolean isAdmiringItem(Piglin piglin) { return piglin.piglinBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM); }

    private static boolean isBarterCurrencyBoolean(ItemStack s) { return isBarterCurrency(s); }

    private static boolean isHoldingItemInOffHand(Piglin piglin) { return !piglin.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(); }

    private static boolean isNotHoldingLovedItemInOffHand(Piglin piglin) {
        ItemStack off = piglin.getItemBySlot(EquipmentSlot.OFFHAND);
        return off.isEmpty() || !isLovedItem(off);
    }

    private static boolean isAdmiringDisabled(Piglin piglin) { return piglin.piglinBrain().hasMemoryValue(MemoryModuleType.ADMIRING_DISABLED); }

    private static boolean hasEatenRecently(Piglin piglin) { return piglin.piglinBrain().hasMemoryValue(MemoryModuleType.ATE_RECENTLY); }

    private static void eat(Piglin piglin) { piglin.piglinBrain().setMemoryWithExpiry(MemoryModuleType.ATE_RECENTLY, true, 200L); }
}
