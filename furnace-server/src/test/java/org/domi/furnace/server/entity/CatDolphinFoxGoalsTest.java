// 고양이(침대 눕기·블록 앉기·주인 곁 쉬기)·돌고래(아이템 놀이·보트 따라가기)·여우(마을 산책)·가루눈 오르기·집행자 문 부수기 목표 시험.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import org.domi.furnace.server.entity.ai.goal.StrollThroughVillageGoal;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CatDolphinFoxGoalsTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    private static PlayerSleepingTest.Lvl floor() {
        var level = new PlayerSleepingTest.Lvl();
        for (int x = -8; x < 16; x++) for (int z = -8; z < 16; z++) level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:grass_block"), 3);
        return level;
    }

    private static int bed(String part) { return REG.stateIdOrAir("minecraft:red_bed", Map.of("facing", "south", "part", part, "occupied", "false")); }

    private static Cat tameCat(PlayerSleepingTest.Lvl level, double x, double y, double z) {
        var cat = new Cat(level, level.nextEntityId());
        cat.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(cat);
        cat.setTame(true, false);
        return cat;
    }

    // ------------------------------------------------------------------ 목표 표

    @Test
    void goalTables() {
        var level = floor();
        var cat = MobGoalTableTest.table(new Cat(level, level.nextEntityId()).goalSelector);
        for (String g : List.of("3 CatRelaxOnOwnerGoal", "5 CatLieOnBedGoal", "7 CatSitOnBlockGoal")) assertTrue(cat.contains(g), g + " in " + cat);
        var dolphin = MobGoalTableTest.table(new Dolphin(level, level.nextEntityId()).goalSelector);
        for (String g : List.of("8 PlayWithItemsGoal", "8 FollowBoatGoal")) assertTrue(dolphin.contains(g), g + " in " + dolphin);
        var fox = MobGoalTableTest.table(new Fox(level, level.nextEntityId()).goalSelector);
        for (String g : List.of("0 ClimbOnTopOfPowderSnowGoal", "9 FoxStrollThroughVillageGoal")) assertTrue(fox.contains(g), g + " in " + fox);
        assertTrue(MobGoalTableTest.table(new Vindicator(level, level.nextEntityId()).goalSelector).contains("1 VindicatorBreakDoorGoal"));
    }

    // ------------------------------------------------------------------ 고양이

    // 바닐라 CatLieOnBedGoal 은 verticalSearchStart=-2 라 탐색 순서가 -2,3,-3,4,... 이다.
    // 곧 고양이 발 높이 기준 (y-3)·(y+2)·... 칸만 보고, 발 바로 밑(y-1)·같은 높이 칸은 건너뛴다. 그래서 시험은 침대보다 3칸 위에서 시작한다.
    private static final double CAT_Y = -57.0;

    @Test
    void catLieOnBedWalksToBedAndLies() {
        var level = floor();
        level.setBlock(new Pos(5, -60, 5), bed("foot"), 3);
        var cat = tameCat(level, 5.5, CAT_Y, 5.5);
        var goal = new Cat.CatLieOnBedGoal(cat, 1.1, 8);
        assertTrue(goal.canUse(), "침대가 가까이 있다");
        assertFalse(goal.canUse(), "다음 시작 시도는 40틱(→20) 뒤");
        cat.moveTo(5.5, -59.4, 5.5, 0, 0);   // 침대 위에 도착했다고 본다
        goal.start();
        assertFalse(cat.isInSittingPose());
        goal.tick();
        assertTrue(cat.isLying(), "침대 칸에 닿으면 눕는다");
        goal.stop();
        assertFalse(cat.isLying());
    }

    @Test
    void catLieOnBedSkipsBedAtOwnHeight() {
        var level = floor();
        level.setBlock(new Pos(5, -60, 5), bed("foot"), 3);
        var cat = tameCat(level, 5.5, -59.4, 5.5);
        assertFalse(new Cat.CatLieOnBedGoal(cat, 1.1, 8).canUse(), "verticalSearchStart=-2 라 발 밑·같은 높이 침대는 찾지 않는다(바닐라 그대로)");
    }

    @Test
    void catLieOnBedRequiresTameStandingAndFreeBed() {
        var level = floor();
        level.setBlock(new Pos(5, -60, 5), bed("foot"), 3);
        assertTrue(new Cat.CatLieOnBedGoal(tameCat(level, 5.5, CAT_Y, 5.5), 1.1, 8).canUse(), "대조: 조건이 맞으면 쓴다");
        var wild = new Cat(level, level.nextEntityId());
        wild.moveTo(5.5, CAT_Y, 5.5, 0, 0);
        level.addFreshEntity(wild);
        assertFalse(new Cat.CatLieOnBedGoal(wild, 1.1, 8).canUse(), "길들지 않았다");
        var sitting = tameCat(level, 5.5, CAT_Y, 5.5);
        sitting.setOrderedToSit(true);
        assertFalse(new Cat.CatLieOnBedGoal(sitting, 1.1, 8).canUse(), "앉으라는 명령");
        var lying = tameCat(level, 5.5, CAT_Y, 5.5);
        lying.setLying(true);
        assertFalse(new Cat.CatLieOnBedGoal(lying, 1.1, 8).canUse(), "이미 누웠다");
        var blocked = floor();
        blocked.setBlock(new Pos(5, -60, 5), bed("foot"), 3);
        blocked.setBlock(new Pos(5, -59, 5), B.defaultState("minecraft:stone"), 3);
        assertFalse(new Cat.CatLieOnBedGoal(tameCat(blocked, 5.5, CAT_Y, 5.5), 1.1, 8).canUse(), "침대 위가 막혔다");
    }

    @Test
    void catSitOnBlockTargets() {
        var level = floor();
        var cat = tameCat(level, 5.5, -60, 5.5);
        var goal = new Cat.CatSitOnBlockGoal(cat, 0.8);
        Pos p = new Pos(3, -60, 3);
        level.setBlock(p, B.defaultState("minecraft:chest"), 3);
        assertTrue(goal.isValidTarget(level, p), "닫힌 상자");
        if (level.getBlockEntity(p) instanceof org.domi.furnace.server.block.entity.OpenersBlockEntity o) {
            o.openCount = 1;
            assertFalse(goal.isValidTarget(level, p), "열려 있는 상자");
        }
        level.setBlock(p, REG.stateIdOrAir("minecraft:furnace", Map.of("facing", "north", "lit", "false")), 3);
        assertFalse(goal.isValidTarget(level, p), "꺼진 화로");
        level.setBlock(p, REG.stateIdOrAir("minecraft:furnace", Map.of("facing", "north", "lit", "true")), 3);
        assertTrue(goal.isValidTarget(level, p), "불 붙은 화로");
        level.setBlock(p, bed("foot"), 3);
        assertTrue(goal.isValidTarget(level, p), "침대 발");
        level.setBlock(p, bed("head"), 3);
        assertFalse(goal.isValidTarget(level, p), "침대 머리");
        level.setBlock(p, B.defaultState("minecraft:stone"), 3);
        assertFalse(goal.isValidTarget(level, p), "그냥 돌");
        level.setBlock(p, B.defaultState("minecraft:chest"), 3);
        level.setBlock(p.above(), B.defaultState("minecraft:stone"), 3);
        assertFalse(goal.isValidTarget(level, p), "위가 막힌 상자");
    }

    @Test
    void catSitOnBlockSitsWhenReached() {
        var level = floor();
        level.setBlock(new Pos(5, -60, 5), B.defaultState("minecraft:chest"), 3);
        var cat = tameCat(level, 5.5, -59, 5.5);
        var goal = new Cat.CatSitOnBlockGoal(cat, 0.8);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertTrue(cat.isInSittingPose(), "도착하면 앉는다");
        goal.stop();
        assertFalse(cat.isInSittingPose());
        cat.setOrderedToSit(true);
        assertFalse(new Cat.CatSitOnBlockGoal(cat, 0.8).canUse(), "앉으라는 명령이 있으면 안 쓴다");
    }

    private static Player sleepingOwner(PlayerSleepingTest.Lvl level, Cat cat) {
        PlayerSleepingTest.placeBed(level);
        level.dayTime = 13000;
        var p = PlayerSleepingTest.player(level, 1.5, -60, 0.5);
        assertNull(PlayerSleeping.startSleepInBed(p, level, PlayerSleepingTest.HEAD));
        cat.setOwnerUUID(p.uuid());
        return p;
    }

    @Test
    void catRelaxOnOwnerLiesAfterWaitingOnBed() {
        var level = floor();
        var cat = tameCat(level, 0.5, -60, 0.5);
        var owner = sleepingOwner(level, cat);
        var goal = new Cat.CatRelaxOnOwnerGoal(cat);
        assertTrue(goal.canUse(), "주인이 자고 있다");
        assertTrue(goal.canContinueToUse());
        goal.start();
        int ticks = 0;
        while (!cat.isLying() && ticks < 100) {
            goal.tick();
            ticks++;
            if (!cat.isLying()) assertTrue(cat.isRelaxStateOne(), "눕기 전에는 쉬는 자세 1");
        }
        assertEquals(9, ticks, "adjustedTickDelay(16)=8 을 넘긴 아홉째 틱에 눕는다");
        assertFalse(cat.isRelaxStateOne());
        goal.stop();
        assertFalse(cat.isLying());
        owner.sleepCounter(0);
    }

    @Test
    void catRelaxOnOwnerNeedsSleepingOwnerAndFreeSpot() {
        var level = floor();
        var cat = tameCat(level, 0.5, -60, 0.5);
        PlayerSleepingTest.placeBed(level);
        level.dayTime = 13000;
        var p = PlayerSleepingTest.player(level, 1.5, -60, 0.5);
        cat.setOwnerUUID(p.uuid());
        assertFalse(new Cat.CatRelaxOnOwnerGoal(cat).canUse(), "주인이 안 잔다");
        assertNull(PlayerSleeping.startSleepInBed(p, level, PlayerSleepingTest.HEAD));
        var other = tameCat(level, 0.5, -60, 0.5);
        other.setLying(true);
        assertFalse(new Cat.CatRelaxOnOwnerGoal(cat).canUse(), "다른 고양이가 이미 누워 있다");
        other.setLying(false);
        assertTrue(new Cat.CatRelaxOnOwnerGoal(cat).canUse());
        cat.setOrderedToSit(true);
        assertFalse(new Cat.CatRelaxOnOwnerGoal(cat).canUse(), "앉으라는 명령");
    }

    @Test
    void catMorningGiftOnlyAtDawnAfterFullSleep() {
        var level = floor();
        var gifts = level.giftTables;
        var cat = tameCat(level, 0.5, -60, 0.5);
        var owner = sleepingOwner(level, cat);
        long dawn = -1;
        for (long t = 0; t < 24000 && dawn < 0; t++) {
            level.dayTime = t;
            float f = B.timeOfDay(level);
            if (f > 0.77F && f < 0.8F) dawn = t;
        }
        assertTrue(dawn >= 0);
        var goal = new Cat.CatRelaxOnOwnerGoal(cat);
        // 한밤: 잠이 충분해도 선물 없음
        level.dayTime = 13000;
        owner.sleepCounter(100);
        for (int i = 0; i < 30; i++) {
            assertTrue(goal.canUse());
            goal.start();
            goal.stop();
        }
        assertTrue(gifts.isEmpty());
        // 새벽이어도 잠이 모자라면 없음
        level.dayTime = dawn;
        owner.sleepCounter(99);
        for (int i = 0; i < 30; i++) {
            assertTrue(goal.canUse());
            goal.start();
            goal.stop();
        }
        assertTrue(gifts.isEmpty());
        // 새벽 + 충분한 잠: 확률 0.7 이므로 여러 번 시도하면 나온다
        owner.sleepCounter(100);
        for (int i = 0; i < 60 && gifts.isEmpty(); i++) {
            assertTrue(goal.canUse());
            goal.start();
            goal.stop();
        }
        assertFalse(gifts.isEmpty(), "새벽 선물");
        assertEquals("minecraft:gameplay/cat_morning_gift", gifts.get(0));
    }

    // ------------------------------------------------------------------ 돌고래

    @Test
    void dolphinPlayWithItemsDropsHeldItemAndCoolsDown() {
        var level = floor();
        var dolphin = new Dolphin(level, level.nextEntityId());
        dolphin.moveTo(5.5, -60, 5.5, 0, 0);
        level.addFreshEntity(dolphin);
        assertTrue(dolphin.canPickUpLoot());
        var goal = dolphin.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(g -> g.getClass().getSimpleName().equals("PlayWithItemsGoal")).findFirst().orElseThrow();
        assertFalse(goal.canUse(), "아이템도 든 것도 없다");
        dolphin.setItemSlot(EquipmentSlot.MAINHAND, B.stack("minecraft:cod", 1));
        assertTrue(goal.canUse(), "든 것이 있다");
        goal.start();
        goal.tick();
        assertTrue(dolphin.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "틱에서 던진다");
        var thrown = level.getEntitiesOfClass(ItemEntity.class, dolphin.boundingBox().inflate(4.0), e -> true);
        assertEquals(1, thrown.size());
        assertTrue(thrown.get(0).hasPickUpDelay(), "던진 직후엔 줍기 지연 40");
        assertEquals("minecraft:cod", org.domi.furnace.server.item.ItemTools.itemName(thrown.get(0).getItem()));
        goal.stop();
        dolphin.setItemSlot(EquipmentSlot.MAINHAND, B.stack("minecraft:cod", 1));
        goal.start();
        goal.stop();
        assertFalse(goal.canUse(), "쿨다운 중에는 시작하지 않는다(tickCount 0 < 쿨다운이면)");
    }

    @Test
    void dolphinHoldsOnlyOneItemInMainHand() {
        var level = floor();
        var dolphin = new Dolphin(level, level.nextEntityId());
        assertTrue(dolphin.canHoldItem(B.stack("minecraft:cod", 1)));
        assertFalse(dolphin.canHoldItem(B.stack("minecraft:iron_helmet", 1)), "방어구 칸은 안 된다");
        dolphin.setItemSlot(EquipmentSlot.MAINHAND, B.stack("minecraft:cod", 1));
        assertFalse(dolphin.canHoldItem(B.stack("minecraft:salmon", 1)), "이미 들었다");
        var ie = new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, B.stack("minecraft:salmon", 1));
        level.addFreshEntity(ie);
        dolphin.pickUpItem(ie);
        assertFalse(ie.isRemoved(), "손이 차 있으면 줍지 않는다");
        dolphin.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        dolphin.pickUpItem(ie);
        assertTrue(ie.isRemoved());
        assertEquals("minecraft:salmon", org.domi.furnace.server.item.ItemTools.itemName(dolphin.getItemBySlot(EquipmentSlot.MAINHAND)));
        assertEquals(2.0F, dolphin.getEquipmentDropChance(EquipmentSlot.MAINHAND), "죽으면 확실히 떨군다");
        assertFalse(dolphin.isPersistenceRequired(), "바닐라 돌고래는 유지 몹이 되지 않는다");
    }

    @Test
    void dolphinFollowsPlayerRowingBoat() {
        var level = floor();
        for (int x = -4; x < 12; x++) for (int z = -4; z < 12; z++) level.setBlock(new Pos(x, -60, z), B.defaultState("minecraft:water"), 3);
        var boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -59.5, 0.5, 0, 0);
        level.addFreshEntity(boat);
        var player = new Player(970, UUID.randomUUID(), "rower", new EmbeddedChannel());
        player.teleporter(t -> {});
        assertTrue(boat.interact(player, 0));
        var dolphin = new Dolphin(level, level.nextEntityId());
        dolphin.moveTo(2.5, -59.5, 2.5, 0, 0);
        level.addFreshEntity(dolphin);
        var goal = dolphin.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(g -> g.getClass().getSimpleName().equals("FollowBoatGoal")).findFirst().orElseThrow();
        assertTrue(goal.isInterruptable());
        assertTrue(goal.requiresUpdateEveryTick());
        player.setPlayerInput(0, 0, false, false);
        assertFalse(goal.canUse(), "노를 젓지 않는다");
        player.setPlayerInput(0, 1, false, false);
        assertTrue(goal.canUse(), "앞으로 간다");
        goal.start();
        assertTrue(goal.canContinueToUse());
        player.setPlayerInput(0, 0, false, false);
        assertFalse(goal.canContinueToUse(), "멈추면 그만");
        goal.stop();
        assertFalse(goal.canContinueToUse());
    }

    // ------------------------------------------------------------------ 가루눈 오르기

    @Test
    void climbOnTopOfPowderSnow() {
        var level = floor();
        var rabbit = new Rabbit(level, level.nextEntityId());
        rabbit.moveTo(5.5, -60, 5.5, 0, 0);
        level.addFreshEntity(rabbit);
        var goal = new ClimbOnTopOfPowderSnowGoal(rabbit);
        assertEquals(java.util.EnumSet.of(org.domi.furnace.server.entity.ai.Goal.Flag.JUMP), goal.getFlags());
        assertTrue(goal.requiresUpdateEveryTick());
        assertFalse(goal.canUse(), "가루눈 안이 아니다");
        rabbit.setIsInPowderSnow(true);
        assertTrue(goal.canUse(), "가루눈 속이고 위가 비었다");
        level.setBlock(new Pos(5, -59, 5), B.defaultState("minecraft:stone"), 3);
        assertFalse(goal.canUse(), "위가 단단한 블록이다");
        level.setBlock(new Pos(5, -59, 5), B.defaultState("minecraft:powder_snow"), 3);
        assertTrue(goal.canUse(), "위가 가루눈이면 계속 오른다");
        var cow = new Cow(level, level.nextEntityId());
        cow.moveTo(8.5, -60, 8.5, 0, 0);
        cow.setIsInPowderSnow(true);
        assertFalse(new ClimbOnTopOfPowderSnowGoal(cow).canUse(), "가루눈을 걸을 수 없는 종류");
        goal.tick();
    }

    // ------------------------------------------------------------------ 마을 산책

    @Test
    void strollThroughVillageNeedsNightAndVillage() {
        var level = floor();
        level.dayTime = 1000;
        var fox = new Fox(level, level.nextEntityId());
        fox.moveTo(5.5, -60, 5.5, 0, 0);
        level.addFreshEntity(fox);
        var goal = new StrollThroughVillageGoal(fox, 1);
        assertFalse(goal.canUse(), "낮에는 안 한다");
        level.dayTime = 18000;
        for (int i = 0; i < 20; i++) assertFalse(goal.canUse(), "마을이 없다");
        assertEquals(java.util.EnumSet.of(org.domi.furnace.server.entity.ai.Goal.Flag.MOVE), goal.getFlags());
        assertFalse(goal.canContinueToUse(), "목표가 없다");
        goal.tick();
    }

    @Test
    void foxStrollGoalIgnoredWhileSleepingOrTargeting() {
        var level = floor();
        level.dayTime = 18000;
        var fox = new Fox(level, level.nextEntityId());
        fox.moveTo(5.5, -60, 5.5, 0, 0);
        level.addFreshEntity(fox);
        var goal = fox.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(g -> g.getClass().getSimpleName().equals("FoxStrollThroughVillageGoal")).findFirst().orElseThrow();
        assertFalse(goal.canUse(), "마을이 없으면 시작하지 않는다");
        assertFalse(goal.canContinueToUse());
    }
}
