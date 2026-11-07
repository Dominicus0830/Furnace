// M5 주민 바닐라 맞춤: mobInteract(불만·대화 통계·스폰 알), 불만 카운터, 살해 목격자(visible_mobs), 변환(이름·무적·귀속 저주·치료 finalizeSpawn),
// 번식 그룹 데이터, villager_plantable_seeds 태그, 번식/AbstractVillager 길찾기 가중치.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.stats.PlayerStats;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VillagerParityTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 어려움 난이도 월드. */
    private static final class Hard extends BlockPlacementTest.TestLevel {
        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public int difficulty() { return 3; }
            };
        }
    }

    private static Player player(java.nio.file.Path dir) {
        var p = new Player(9, UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        p.stats(new PlayerStats(dir, p.uuid()));
        return p;
    }

    private static Villager villager(BlockPlacementTest.TestLevel level, String prof) {
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(0.5, -60, 0.5, 0, 0);
        v.setProfession(prof);
        return v;
    }

    private static int talked(Player p) { return p.stats().get("minecraft:talked_to_villager"); }

    @Test
    void emptyTradesShakeHeadAndTalkStatOnlyForMainHand(@TempDir java.nio.file.Path dir) {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(dir);
        var nitwit = villager(level, "nitwit");
        assertEquals(Villager.TradeInteract.CONSUME, nitwit.mobInteract(p, false, false), "거래 없음 = 소비");
        assertEquals(0, nitwit.getUnhappyCounter(), "보조 손은 불만 표시도 대화 통계도 없다");
        assertEquals(0, talked(p));
        assertEquals(Villager.TradeInteract.CONSUME, nitwit.mobInteract(p, true, false));
        assertEquals(40, nitwit.getUnhappyCounter(), "주 손 + 거래 없음 = 고개 젓기 40틱");
        assertEquals(1, talked(p));
    }

    @Test
    void babyShakesHeadWithoutStat(@TempDir java.nio.file.Path dir) {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(dir);
        var baby = villager(level, "farmer");
        baby.setAge(Villager.BABY_START_AGE);
        assertEquals(Villager.TradeInteract.CONSUME, baby.mobInteract(p, true, false));
        assertEquals(40, baby.getUnhappyCounter());
        assertEquals(0, talked(p), "아기는 대화 통계가 오르지 않는다");
    }

    @Test
    void tradingVillagerOpensAndCountsTalk(@TempDir java.nio.file.Path dir) {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(dir);
        var farmer = villager(level, "farmer");
        assertFalse(farmer.offers().isEmpty());
        assertEquals(Villager.TradeInteract.OPEN_TRADE, farmer.mobInteract(p, true, false));
        assertEquals(0, farmer.getUnhappyCounter());
        assertEquals(1, talked(p));
        assertEquals(Villager.TradeInteract.OPEN_TRADE, farmer.mobInteract(p, false, false), "보조 손도 거래는 연다(통계만 없다)");
        assertEquals(1, talked(p));
    }

    @Test
    void villagerSpawnEggPassesButOtherEggsDoNot(@TempDir java.nio.file.Path dir) {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(dir);
        var farmer = villager(level, "farmer");
        assertEquals(Villager.TradeInteract.PASS, farmer.mobInteract(p, true, true), "주민 스폰 알을 들면 넘긴다");
        assertEquals(0, talked(p));
        farmer.setTradingPlayer(new org.domi.furnace.server.menu.MenuPlayer() {
            public boolean isCreative() { return false; }
            public boolean isSpectator() { return false; }
            public boolean isDeadOrDying() { return false; }
            public boolean hasDisconnected() { return false; }
            public void drop(ItemStack s, boolean b) { }
            public void placeItemBackInInventory(ItemStack s) { }
            public ItemStack addToInventory(ItemStack s) { return s; }
            public ItemStack inventoryItem(int i) { return ItemStack.EMPTY; }
            public void setInventoryItem(int i, ItemStack s) { }
            public org.domi.furnace.server.menu.EquipSlot equipmentSlotFor(ItemStack s) { return null; }
            public boolean hasBindingCurse(ItemStack s) { return false; }
            public double distanceToSqr(double x, double y, double z) { return 0; }
        });
        assertEquals(Villager.TradeInteract.PASS, farmer.mobInteract(p, true, false), "이미 거래 중이면 넘긴다");
    }

    @Test
    void unhappyCounterTicksDown() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer");
        v.setUnhappy();
        assertEquals(40, v.getUnhappyCounter());
        v.tick();
        assertEquals(39, v.getUnhappyCounter());
    }

    @Test
    void murderWitnessesAreVisibleMobsOnly() {
        var level = new BlockPlacementTest.TestLevel();
        var victim = villager(level, "farmer");
        var seen = villager(level, "librarian");
        var behindWall = villager(level, "cleric");
        var killer = new Zombie(level, level.nextEntityId());
        killer.moveTo(1.5, -60, 0.5, 0, 0);
        victim.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of((Targetable) seen, behindWall));
        victim.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(victim, List.of((Targetable) seen)));
        victim.hurtBy(Entity.Damage.GENERIC_KILL, 1000f, killer);
        assertTrue(victim.isDeadOrDying());
        assertTrue(seen.getGossips().getGossipEntries().containsKey(killer.uuid()), "보이는 주민은 살해를 듣는다");
        assertFalse(behindWall.getGossips().getGossipEntries().containsKey(killer.uuid()), "보이지 않는(근처 목록에만 있는) 주민은 못 듣는다");
    }

    @Test
    void villagersNeverDespawnAndDefaultGroupHasNoBabies() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "none");
        assertFalse(v.removeWhenFarAway(1.0E9));
        // AbstractVillager.finalizeSpawn: 그룹 데이터가 없으면 새끼를 내지 않는 데이터
        var g = v.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(v.blockPosition()), null);
        assertFalse(g.shouldSpawnBaby);
        assertEquals(1, g.groupSize);
    }

    @Test
    void firePathMalusLikeAbstractVillager() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "none");
        assertEquals(16.0F, v.getPathfindingMalus(PathType.DANGER_FIRE));
        assertEquals(-1.0F, v.getPathfindingMalus(PathType.DAMAGE_FIRE));
    }

    @Test
    void savedDataDoesNotCarryWilling() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer");
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        v.saveWithoutId(tag);
        assertFalse(tag.contains("Willing"));
    }

    @Test
    void plantableSeedsFollowTheItemTag() {
        // 설치된 레지스트리의 villager_plantable_seeds(바닐라 기본 6종)
        for (String seed : List.of("minecraft:wheat_seeds", "minecraft:potato", "minecraft:carrot", "minecraft:beetroot_seeds", "minecraft:torchflower_seeds",
                "minecraft:pitcher_pod")) assertTrue(Villager.isPlantableSeed(B.stack(seed, 1)), seed);
        assertFalse(Villager.isPlantableSeed(B.stack("minecraft:wheat", 1)));
        assertFalse(Villager.isPlantableSeed(ItemStack.EMPTY));
    }

    @Test
    void zombieInfectionKeepsNameInvulnerableAndGossip() {
        var level = new Hard();
        var v = villager(level, "librarian");
        v.setCustomName(Text.text("Bob"));
        v.setCustomNameVisible(true);
        v.setInvulnerable(true);
        UUID rep = new UUID(7, 8);
        v.onReputationEvent(Villager.ReputationEvent.TRADE, rep);
        level.addFreshEntity(v);
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(1.5, -60, 0.5, 0, 0);
        assertFalse(z.killedEntity(v), "변환했으면 드롭 없음");
        assertTrue(v.isRemoved());
        var found = level.getEntitiesOfClass(ZombieVillager.class, z.boundingBox().inflate(10.0, 10.0, 10.0), q -> true);
        assertEquals(1, found.size(), "좀비 주민이 생겼다");
        ZombieVillager zv = found.get(0);
        assertEquals("Bob", zv.getCustomName().plainText());
        assertTrue(zv.isCustomNameVisible());
        assertTrue(zv.isInvulnerable());
        assertEquals("librarian", Villager.PROFESSIONS.get(zv.villagerData()[1]));
    }

    private static ItemStack cursed(String item) {
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        tag.putString("id", item);
        tag.putInt("count", 1);
        var comps = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        var ench = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        var levels = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        levels.putInt("minecraft:binding_curse", 1);
        ench.putCompound("levels", levels);
        comps.putCompound("minecraft:enchantments", ench);
        tag.putCompound("components", comps);
        return ItemStackNbt.read(tag);
    }

    @Test
    void cureKeepsBindingCurseGearInInventoryAndDropsOnlyOverOneChance() {
        var level = new BlockPlacementTest.TestLevel();
        var zv = new ZombieVillager(level, level.nextEntityId());
        zv.moveTo(0.5, -60, 0.5, 0, 0);
        zv.setVillagerData("plains", "librarian", 3);
        zv.setItemSlot(EquipmentSlot.HEAD, cursed("minecraft:diamond_helmet"));
        zv.setItemSlot(EquipmentSlot.FEET, B.stack("minecraft:iron_boots", 1));
        zv.setItemSlot(EquipmentSlot.CHEST, B.stack("minecraft:iron_chestplate", 1));
        zv.setDropChance(EquipmentSlot.CHEST, 2.0F);   // 1.0 을 넘는 것만 바닥에 떨군다
        zv.setCustomName(Text.text("Zed"));
        level.addFreshEntity(zv);
        var tag = zv.saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        tag.putInt("ConversionTime", 1);   // 다음 틱에 치료가 끝난다
        zv.load(tag);
        zv.tick();
        assertTrue(zv.isRemoved(), "치료가 끝나 좀비 주민이 사라졌다");
        var vs = level.getEntitiesOfClass(Villager.class, zv.boundingBox().inflate(10.0, 10.0, 10.0), x -> true);
        assertEquals(1, vs.size());
        var v = vs.get(0);
        assertEquals("librarian", v.profession());
        assertEquals("Zed", v.getCustomName().plainText(), "이름을 넘긴다");
        assertTrue(B.holds(v.getInventory().get(EquipmentSlot.HEAD.index()), "minecraft:diamond_helmet"), "귀속 저주 장비는 주민 가방의 (종류별 번호) 칸에");
        var drops = level.getEntitiesOfClass(ItemEntity.class, zv.boundingBox().inflate(10.0, 10.0, 10.0), x -> true);
        assertEquals(1, drops.size(), "드롭 확률 1.0 초과인 가슴 갑옷만 떨어진다");
    }

    @Test
    void tradeTitleIsJustTheNameAndBabyVoiceUsesRandom() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer");
        assertEquals("entity.minecraft.villager.farmer", ((org.domi.furnace.api.text.Content.Translatable) v.tradeTitle().content()).key());
        v.setCustomName(Text.text("Bob"));
        assertEquals("Bob", v.tradeTitle().plainText());
        assertEquals(0, villager(level, "none").getUnhappyCounter());
    }

    @Test
    void wanderingTraderLikeAbstractVillager() {
        var level = new BlockPlacementTest.TestLevel();
        var t = new WanderingTrader(level, level.nextEntityId());
        assertEquals(16.0F, t.getPathfindingMalus(PathType.DANGER_FIRE));
        assertEquals(-1.0F, t.getPathfindingMalus(PathType.DAMAGE_FIRE));
    }
}
