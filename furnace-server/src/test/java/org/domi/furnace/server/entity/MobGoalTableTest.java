// 몹 목표 표: registerGoals 가 넣은 (우선순위, 목표 종류[대상 종류]) 목록이 바닐라 registerGoals 순서와 같은지 본다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.entity.ai.GoalSelector;
import org.domi.furnace.server.entity.ai.WrappedGoal;
import org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobGoalTableTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static List<String> table(GoalSelector sel) {
        List<String> out = new ArrayList<>();
        for (WrappedGoal w : sel.getAvailableGoals()) {
            var g = w.getGoal();
            String name = g.getClass().getSimpleName();
            if (g instanceof NearestAttackableTargetGoal n) name += "<" + n.targetType().getSimpleName() + ">";
            out.add(w.getPriority() + " " + name);
        }
        return out;
    }

    private static final BlockPlacementTest.TestLevel LEVEL = new BlockPlacementTest.TestLevel();

    private static void check(Mob m, List<String> goals, List<String> targets) {
        if (goals != null) assertEquals(goals, table(m.goalSelector), m.getClass().getSimpleName() + " goalSelector");
        assertEquals(targets, table(m.targetSelector), m.getClass().getSimpleName() + " targetSelector");
    }

    private static int id() { return LEVEL.nextEntityId(); }

    @Test
    void zombieFamily() {
        List<String> zombieTargets = List.of("1 HurtByTargetGoal", "2 NearestAttackableTargetGoal<Player>", "3 NearestAttackableTargetGoal<PathfinderMob>",
                "3 NearestAttackableTargetGoal<IronGolem>", "5 NearestAttackableTargetGoal<Turtle>");
        List<String> zombieGoals = List.of("4 ZombieAttackTurtleEggGoal", "8 LookAtPlayerGoal", "8 RandomLookAroundGoal", "2 ZombieAttackGoal",
                "6 MoveThroughVillageGoal", "7 WaterAvoidingRandomStrollGoal");
        check(new Zombie(LEVEL, id()), zombieGoals, zombieTargets);
        check(new Husk(LEVEL, id()), zombieGoals, zombieTargets);
        check(new ZombieVillager(LEVEL, id()), zombieGoals, zombieTargets);
        check(new Drowned(LEVEL, id()),
                List.of("4 ZombieAttackTurtleEggGoal", "8 LookAtPlayerGoal", "8 RandomLookAroundGoal", "1 DrownedGoToWaterGoal", "2 DrownedTridentAttackGoal",
                        "2 DrownedAttackGoal", "5 DrownedGoToBeachGoal", "6 DrownedSwimUpGoal", "7 RandomStrollGoal"),
                List.of("1 HurtByTargetGoal", "2 NearestAttackableTargetGoal<Player>", "3 NearestAttackableTargetGoal<PathfinderMob>",
                        "3 NearestAttackableTargetGoal<IronGolem>", "3 NearestAttackableTargetGoal<Axolotl>", "5 NearestAttackableTargetGoal<Turtle>"));
    }

    @Test
    void m4Mobs() {
        check(new Spider(LEVEL, id()),
                List.of("1 FloatGoal", "2 AvoidEntityGoal", "3 LeapAtTargetGoal", "4 SpiderAttackGoal", "5 WaterAvoidingRandomStrollGoal", "6 LookAtPlayerGoal",
                        "6 RandomLookAroundGoal"),
                List.of("1 HurtByTargetGoal", "2 SpiderTargetGoal<Player>", "3 SpiderTargetGoal<IronGolem>"));
        check(new Skeleton(LEVEL, id()), null,
                List.of("1 HurtByTargetGoal", "2 NearestAttackableTargetGoal<Player>", "3 NearestAttackableTargetGoal<IronGolem>",
                        "3 NearestAttackableTargetGoal<Turtle>"));
        check(new Slime(LEVEL, id()), null, List.of("1 NearestAttackableTargetGoal<Player>", "3 NearestAttackableTargetGoal<IronGolem>"));
        check(new Wolf(LEVEL, id()),
                List.of("1 FloatGoal", "1 TamablePanicGoal", "2 SitWhenOrderedToDoGoal", "3 WolfAvoidEntityGoal", "4 LeapAtTargetGoal", "5 MeleeAttackGoal",
                        "6 FollowOwnerGoal", "7 BreedGoal", "8 WaterAvoidingRandomStrollGoal", "9 BegGoal", "10 LookAtPlayerGoal", "10 RandomLookAroundGoal"),
                List.of("1 OwnerHurtByTargetGoal", "2 OwnerHurtTargetGoal", "3 HurtByTargetGoal", "4 NearestAttackableTargetGoal<Player>",
                        "5 NonTameRandomTargetGoal<Animal>", "6 NonTameRandomTargetGoal<Turtle>", "7 NearestAttackableTargetGoal<AbstractSkeleton>",
                        "8 ResetUniversalAngerTargetGoal"));
        var rabbit = new Rabbit(LEVEL, id());
        check(rabbit,
                List.of("1 FloatGoal", "1 ClimbOnTopOfPowderSnowGoalStub", "1 RabbitPanicGoal", "2 BreedGoal", "3 TemptGoal", "4 RabbitAvoidEntityGoal",
                        "4 RabbitAvoidEntityGoal", "4 RabbitAvoidEntityGoal", "5 RaidGardenGoal", "6 WaterAvoidingRandomStrollGoal", "11 LookAtPlayerGoal"),
                List.of());
        rabbit.setVariant(Rabbit.TYPE_EVIL);
        check(rabbit, null, List.of("1 HurtByTargetGoal", "2 NearestAttackableTargetGoal<Player>", "2 NearestAttackableTargetGoal<Wolf>"));
        check(new Cat(LEVEL, id()), null, List.of("1 CatNonTameTarget<Rabbit>", "1 NonTameRandomTargetGoal<Turtle>"));
        check(new Ocelot(LEVEL, id()), null, List.of("1 NearestAttackableTargetGoal<Chicken>", "1 NearestAttackableTargetGoal<Turtle>"));
        check(new PolarBear(LEVEL, id()), null,
                List.of("1 PolarBearHurtByTargetGoal", "2 PolarBearAttackPlayersGoal<Player>", "3 NearestAttackableTargetGoal<Player>",
                        "4 NearestAttackableTargetGoal<Fox>", "5 ResetUniversalAngerTargetGoal"));
        check(new Enderman(LEVEL, id()), null,
                List.of("1 EndermanLookForPlayerGoal<Player>", "2 HurtByTargetGoal", "3 NearestAttackableTargetGoal<Endermite>", "4 ResetUniversalAngerTargetGoal"));
    }

    @Test
    void ironGolem() {
        check(new IronGolem(LEVEL, id()),
                List.of("1 MeleeAttackGoal", "2 MoveTowardsTargetGoal", "2 MoveBackToVillageGoal", "4 GolemRandomStrollInVillageGoal", "5 OfferFlowerGoal",
                        "7 LookAtPlayerGoal", "8 RandomLookAroundGoal"),
                List.of("1 DefendVillageTargetGoal", "2 HurtByTargetGoal", "3 NearestAttackableTargetGoal<Player>", "3 NearestAttackableTargetGoal<Mob>",
                        "4 ResetUniversalAngerTargetGoal"));
    }

    @Test
    void witch() {
        check(new Witch(LEVEL, id()),
                List.of("4 LongDistancePatrolGoal", "1 ObtainRaidLeaderBannerGoal", "3 PathfindToRaidGoal", "4 RaiderMoveThroughVillageGoal", "5 RaiderCelebration",
                        "1 FloatGoal", "2 RangedAttackGoal", "2 WaterAvoidingRandomStrollGoal", "3 LookAtPlayerGoal", "3 RandomLookAroundGoal"),
                List.of("1 HurtByTargetGoal", "2 NearestHealableRaiderTargetGoal<Raider>", "3 NearestAttackableWitchTargetGoal<Player>"));
    }
}
