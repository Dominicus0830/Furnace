// 스트라이더: 안장·타기, 뒤틀린 균류 막대 조종·가속(내구도 1), 내릴 자리(용암 피함), 스폰 기수(좀비 피글린·새끼 스트라이더), 저장.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ItemUse;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StriderTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Strider strider(BlockPlacementTest.TestLevel level) {
        Strider s = new Strider(level, level.nextEntityId());
        s.moveTo(0.5, -60, 0.5, 0, 0);
        s.setOnGround(true);
        return s;
    }

    private static Player player() {
        Player p = new Player(952, UUID.randomUUID(), "rider", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.teleporter(t -> {});
        p.applyMove(2.5, -60, 0.5, 0.0F, 20.0F, true);
        return p;
    }

    private static ItemStack item(String name) {
        return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", name), 1);
    }

    private static void setEntityRandom(Entity e, long seed) throws Exception {
        var f = Entity.class.getDeclaredField("random");
        f.setAccessible(true);
        f.set(e, new LegacyRandomSource(seed));
    }

    @Test
    void saddleItemEquipsAdultsOnlyAndIsConsumed() {
        var level = new BlockPlacementTest.TestLevel();
        Player p = player();
        Strider baby = strider(level);
        baby.setBaby(true);
        assertFalse(baby.interact(p, 0, item("saddle"), null), "새끼에는 안장을 못 얹는다");
        assertFalse(baby.isSaddled());

        Strider s = strider(level);
        var inv = p.inventory();
        ItemStack saddles = item("saddle").withCount(2);
        inv.set(inv.heldWindowSlot(), saddles);
        assertTrue(s.interact(p, 0, saddles, null));
        assertTrue(s.isSaddled());
        assertEquals(1, inv.get(inv.heldWindowSlot()).count(), "생존 모드는 하나 쓴다");
        assertTrue(s.interact(p, 0, inv.get(inv.heldWindowSlot()), null), "안장이 있으면 안장을 들고 있어도(먹이가 아니므로) 탄다");
        assertSame(s, p.vehicle());
        assertEquals(1, inv.get(inv.heldWindowSlot()).count(), "다시 얹지 않는다");
    }

    @Test
    void ridesOnlyWithSaddleAndControlsOnlyWithWarpedFungusStick() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        Player p = player();
        s.interact(p, 0, ItemStack.EMPTY, null);
        assertNull(p.vehicle(), "안장이 없으면 타지 못한다");

        s.equipSaddle(null);
        s.interact(p, 0, item("warped_fungus"), null);
        assertNull(p.vehicle(), "먹이를 들고 있으면 타지 않고 먹인다");
        assertTrue(s.isInLove());
        assertTrue(s.interact(p, 0, ItemStack.EMPTY, null));
        assertSame(s, p.vehicle());
        assertNull(s.getControllingPassenger(), "막대가 없으면 조종하지 못한다");

        var inv = p.inventory();
        inv.set(inv.heldWindowSlot(), item("warped_fungus_on_a_stick"));
        assertSame(p, s.getControllingPassenger());
        inv.set(inv.heldWindowSlot(), item("carrot_on_a_stick"));
        assertNull(s.getControllingPassenger(), "당근 낚싯대로는 스트라이더를 조종하지 못한다");
        inv.set(inv.heldWindowSlot(), ItemStack.EMPTY);
        inv.set(45, item("warped_fungus_on_a_stick"));
        assertSame(p, s.getControllingPassenger(), "보조 손도 된다");
    }

    @Test
    void boostTotalTimeTickRiddenAndFactorFollowVanilla() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        s.equipSaddle(null);
        setEntityRandom(s, 91L);
        int total = new LegacyRandomSource(91L).nextInt(841) + 140;
        Player p = player();
        s.interact(p, 0, ItemStack.EMPTY, null);

        float base = s.getRiddenSpeed();
        assertEquals(s.attributeValue(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED) * (s.isSuffocating() ? 0.35F : 0.55F), base, 1e-6);
        assertTrue(s.boost());
        assertEquals(total, (Integer) s.entityData.get(Strider.DATA_BOOST_TIME));
        assertFalse(s.boost(), "가속 중에는 다시 못 한다");
        for (int i = 0; i < total / 2; i++) s.tickRidden(p);
        assertTrue(s.getRiddenSpeed() > base * 2.0F, "가속 중간에는 1 + 1.15·sin(≈π/2) 배");
        for (int i = total / 2; i < total + 1; i++) s.tickRidden(p);
        assertTrue(s.isBoosting());
        s.tickRidden(p);
        assertFalse(s.isBoosting(), "총 시간 + 2 번째 tickRidden 에서 끝");
        assertEquals(p.yaw(), s.yRot());
        assertEquals(p.pitch() * 0.5F, s.xRot());
    }

    @Test
    void warpedFungusStickUseBoostsRiddenStriderAndCostsOneDurability() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        s.equipSaddle(null);
        Player p = player();
        s.interact(p, 0, ItemStack.EMPTY, null);
        ItemStack stick = item("warped_fungus_on_a_stick");
        var actor = new PlayerActor(p, stick, ItemStack.EMPTY, 0);
        var r = ItemUse.useItem(level, actor, 0);
        assertTrue(r.consumesAction(), "가속: " + r);
        assertTrue(s.isBoosting());
        assertEquals(1, ItemTools.damage(actor.itemInHand(0)), "뒤틀린 균류 막대는 1 닳는다");
        var again = ItemUse.useItem(level, actor, 0);
        assertFalse(again.consumesAction(), "가속 중이면 넘긴다");

        // 돼지를 타고 있으면 뒤틀린 균류 막대는 아무 일도 하지 않는다
        var level2 = new BlockPlacementTest.TestLevel();
        Pig pig = new Pig(level2, level2.nextEntityId());
        pig.moveTo(0.5, -60, 0.5, 0, 0);
        pig.setSaddled(true);
        Player p2 = player();
        pig.interact(p2, 0, ItemStack.EMPTY, null);
        var actor2 = new PlayerActor(p2, item("warped_fungus_on_a_stick"), ItemStack.EMPTY, 0);
        assertFalse(ItemUse.useItem(level2, actor2, 0).consumesAction());
        assertEquals(0, ItemTools.damage(actor2.itemInHand(0)));
    }

    @Test
    void dismountSkipsLavaAndFloorlessSpots() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        Player p = player();   // yaw 0 → +z 쪽
        assertEquals(new Vec3(0.5, -60.0, 1.5), s.getDismountLocationForPassenger(p), "바로 앞 땅");

        int lava = B.defaultState("minecraft:lava");
        level.setBlock(new Pos(0, -61, 1), lava, 3);   // 앞(0°·±22.5°)은 바닥이 용암 → 발 디딜 곳 없음
        level.setBlock(new Pos(1, -60, 1), lava, 3);   // -45° 칸은 그 칸이 용암 → 건너뜀(바닥 돌은 맞아도)
        assertEquals(new Vec3(-0.5, -60.0, 1.5), s.getDismountLocationForPassenger(p), "+45° 쪽");
    }

    /** 부른 순서를 남기는 난수. nextInt 는 bound 별 값, nextFloat 는 고정. */
    private static final class ScriptedRandom implements RandomSource {
        final List<String> calls = new ArrayList<>();
        final int int30, int10;
        final float f;

        ScriptedRandom(int int30, int int10, float f) {
            this.int30 = int30;
            this.int10 = int10;
            this.f = f;
        }

        @Override public int nextInt() { calls.add("int"); return 0; }

        @Override public int nextInt(int bound) {
            calls.add("int" + bound);
            return bound == 30 ? int30 : bound == 10 ? int10 : 0;
        }

        @Override public long nextLong() { calls.add("long"); return 0; }

        @Override public float nextFloat() { calls.add("float"); return f; }

        @Override public double nextDouble() { calls.add("double"); return 0.5; }

        @Override public boolean nextBoolean() { calls.add("bool"); return false; }

        @Override public double nextGaussian() { calls.add("gauss"); return 0; }
    }

    @Test
    void zombifiedPiglinJockeyOnOneInThirty() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        var r = new ScriptedRandom(0, 5, 0.5F);
        var data = s.finalizeSpawn(r, new DifficultyInstance(2, 0, 0, 0.0F), null);
        assertEquals("int30", r.calls.get(0));
        assertEquals("float", r.calls.get(1), "다음은 Zombie.getSpawnAsBabyOdds(레벨 난수)");
        assertTrue(s.firstPassenger() instanceof ZombifiedPiglin, "좀비 피글린 기수");
        ZombifiedPiglin z = (ZombifiedPiglin) s.firstPassenger();
        assertSame(s, z.vehicle());
        assertFalse(z.isBaby());
        assertEquals("minecraft:warped_fungus_on_a_stick", ItemTools.itemName(z.getItemBySlot(EquipmentSlot.MAINHAND)));
        assertTrue(s.isSaddled());
        assertEquals(0.0F, data.babySpawnChance);
        assertSame(z, s.getControllingPassenger(), "AI 기수가 조종한다");
        assertEquals(s.x(), z.x(), 1e-9);
        assertFalse(s.isBaby());
    }

    @Test
    void babyZombifiedPiglinRiderDoesNotLookForChicken() {
        var level = new BlockPlacementTest.TestLevel();
        Chicken c = new Chicken(level, level.nextEntityId());
        c.moveTo(1.5, -60, 1.5, 0, 0);
        level.addFreshEntity(c);
        Strider s = strider(level);
        s.finalizeSpawn(new ScriptedRandom(0, 5, 0.01F), new DifficultyInstance(2, 0, 0, 0.0F), null);
        ZombifiedPiglin z = (ZombifiedPiglin) s.firstPassenger();
        assertTrue(z.isBaby(), "아기 확률 < 0.05");
        assertSame(s, z.vehicle(), "기수 금지 그룹이라 닭에 옮겨 타지 않는다");
        assertFalse(c.isVehicle());
    }

    @Test
    void babyStriderJockeyOnOneInTen() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        var r = new ScriptedRandom(3, 0, 0.5F);
        var data = s.finalizeSpawn(r, new DifficultyInstance(2, 0, 0, 0.0F), null);
        assertEquals(List.of("int30", "int10"), r.calls.subList(0, 2));
        assertTrue(s.firstPassenger() instanceof Strider baby && baby.isBaby(), "새끼 스트라이더 기수");
        assertFalse(s.isSaddled());
        assertEquals(0.0F, data.babySpawnChance);
    }

    @Test
    void plainSpawnUsesHalfBabyChanceGroupAndBabyIgnoresJockeyRolls() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        var data = s.finalizeSpawn(new ScriptedRandom(3, 4, 0.5F), new DifficultyInstance(2, 0, 0, 0.0F), new AgeableMob.GroupData(0.9F));
        assertFalse(s.isVehicle());
        assertEquals(0.5F, data.babySpawnChance, "들어온 그룹은 버리고 0.5 그룹");
        assertEquals(1, data.groupSize);

        Strider baby = strider(level);
        baby.setBaby(true);
        var r = new ScriptedRandom(0, 0, 0.5F);
        baby.finalizeSpawn(r, new DifficultyInstance(2, 0, 0, 0.0F), null);
        assertFalse(baby.isVehicle());
        assertFalse(r.calls.contains("int30"), "새끼는 기수 난수를 뽑지 않는다");
    }

    @Test
    void saddleIsSavedAndDroppedOnDeath() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        s.equipSaddle(null);
        NbtCompound tag = new NbtCompound();
        s.addAdditionalSaveData(tag);
        assertTrue(tag.getBoolean("Saddle"));
        Strider loaded = strider(level);
        loaded.readAdditionalSaveData(tag);
        assertTrue(loaded.isSaddled());

        level.addFreshEntity(s);
        s.dropEquipment();
        boolean saddleDropped = false;
        for (Entity e : level.entities().all()) {
            if (e instanceof ItemEntity ie && ItemTools.itemName(ie.getItem()).equals("minecraft:saddle")) saddleDropped = true;
        }
        assertTrue(saddleDropped);
    }

    @Test
    void cannotTakePassengerWhenAlreadyRidden() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        s.equipSaddle(null);
        Player p = player();
        assertTrue(s.interact(p, 0, ItemStack.EMPTY, null));
        Strider other = strider(level);
        assertFalse(other.startRiding(s), "승객이 있으면 더 받지 않는다");
    }

    @Test
    void suffocatesWhenColdOrWhenRidingASufferingStrider() {
        var level = new BlockPlacementTest.TestLevel();
        Strider s = strider(level);
        level.addFreshEntity(s);
        s.tick();
        assertTrue(s.isSuffocating(), "풀 위는 차갑다");
        assertTrue(s.attributes().get(org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED).hasModifier("minecraft:suffocating"));

        Strider baby = strider(level);
        baby.setBaby(true);
        baby.startRiding(s, true);
        s.positionRider(baby);
        level.setBlock(baby.blockPosition(), B.defaultState("minecraft:lava"), 2);
        s.setSuffocating(false);
        baby.tick();
        baby.tick();
        assertFalse(baby.isSuffocating(), "용암 속이고 탈것이 떨지 않으면 따뜻하다");
        s.setSuffocating(true);
        baby.tick();
        assertTrue(baby.isSuffocating(), "탄 스트라이더가 떨면 같이 떤다");
    }
}
