// 몹 양동이로 잡기(Bucketable.bucketMobPickup): 물 양동이로 우클릭하면 몹이 bucket_entity_data 에 담긴 채운 양동이가 된다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobBucketPickupTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Player player(GameMode mode) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        return p;
    }

    private static ItemStack stack(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", name), n); }

    private static <T extends Mob> T spawn(BlockPlacementTest.TestLevel level, T mob) {
        mob.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(mob);
        return mob;
    }

    private static NbtCompound tagOf(ItemStack bucket) {
        assertInstanceOf(NbtCompound.class, ItemTools.component(bucket, "minecraft:bucket_entity_data"));
        return (NbtCompound) ItemTools.component(bucket, "minecraft:bucket_entity_data");
    }

    private static boolean pick(Player p, Mob mob) {
        return mob.interact(p, 0, p.inventory().get(p.inventory().heldWindowSlot()), null);
    }

    @Test
    void waterBucketCatchesEachFishSpecies() {
        var level = new BlockPlacementTest.TestLevel();
        String[][] cases = {{"cod", "cod_bucket"}, {"salmon", "salmon_bucket"}, {"pufferfish", "pufferfish_bucket"}, {"tropical_fish", "tropical_fish_bucket"}};
        for (String[] c : cases) {
            var p = player(GameMode.SURVIVAL);
            int held = p.inventory().heldWindowSlot();
            p.inventory().set(held, stack("water_bucket", 1));
            Mob fish = spawn(level, (Mob) Entities.create(level, "minecraft:" + c[0]));
            float health = fish.health();
            assertTrue(pick(p, fish), c[0]);
            assertTrue(fish.isRemoved(), c[0] + " 은 양동이에 담겨 사라진다");
            ItemStack out = p.inventory().get(held);
            assertEquals("minecraft:" + c[1], ItemTools.itemName(out), c[0]);
            NbtCompound tag = tagOf(out);
            assertEquals(health, tag.getFloat("Health"), 0.0F, "Health 는 늘 담긴다");
            assertFalse(tag.contains("NoAI"), "꺼진 플래그는 담지 않는다");
            assertFalse(tag.contains("Silent"));
        }
    }

    @Test
    void namedFishKeepsNameFlagsAndHealth() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        int held = p.inventory().heldWindowSlot();
        p.inventory().set(held, stack("water_bucket", 1));
        Cod cod = spawn(level, new Cod(level, level.nextEntityId()));
        cod.setCustomName(Text.text("Bob"));
        cod.setNoAi(true);
        cod.setSilent(true);
        cod.setNoGravity(true);
        cod.setGlowingTag(true);
        cod.setInvulnerable(true);
        cod.setHealth(2.0F);
        assertTrue(pick(p, cod));
        ItemStack out = p.inventory().get(held);
        NbtCompound tag = tagOf(out);
        for (String k : new String[] {"NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable"}) assertTrue(tag.getBoolean(k), k);
        assertEquals(2.0F, tag.getFloat("Health"), 0.0F);
        assertInstanceOf(String.class, ItemTools.component(out, "minecraft:custom_name"));
        assertTrue(((String) ItemTools.component(out, "minecraft:custom_name")).contains("Bob"));
    }

    @Test
    void tropicalFishSavesBucketVariantTag() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        int held = p.inventory().heldWindowSlot();
        p.inventory().set(held, stack("water_bucket", 1));
        TropicalFish fish = spawn(level, new TropicalFish(level, level.nextEntityId()));
        fish.setVariant(0x0B0E0101);
        assertTrue(pick(p, fish));
        assertEquals(0x0B0E0101, tagOf(p.inventory().get(held)).getInt("BucketVariantTag"));
    }

    @Test
    void axolotlSavesVariantAgeAndHuntingCooldown() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        int held = p.inventory().heldWindowSlot();
        p.inventory().set(held, stack("water_bucket", 1));
        Axolotl ax = spawn(level, new Axolotl(level, level.nextEntityId()));
        ax.setVariant(2);
        ax.setAge(-4000);
        ax.axolotlBrain().setMemoryWithExpiry(org.domi.furnace.server.entity.ai.brain.MemoryModuleType.HAS_HUNTING_COOLDOWN, true, 1234L);
        assertTrue(pick(p, ax));
        ItemStack out = p.inventory().get(held);
        assertEquals("minecraft:axolotl_bucket", ItemTools.itemName(out));
        NbtCompound tag = tagOf(out);
        assertEquals(2, tag.getInt("Variant"));
        assertEquals(-4000, tag.getInt("Age"));
        assertEquals(1234L, tag.getLong("HuntingCooldown"));
    }

    @Test
    void axolotlWithoutCooldownOmitsHuntingCooldown() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(p.inventory().heldWindowSlot(), stack("water_bucket", 1));
        Axolotl ax = spawn(level, new Axolotl(level, level.nextEntityId()));
        assertTrue(pick(p, ax));
        assertFalse(tagOf(p.inventory().get(p.inventory().heldWindowSlot())).contains("HuntingCooldown"));
    }

    @Test
    void tadpoleSavesAge() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        int held = p.inventory().heldWindowSlot();
        p.inventory().set(held, stack("water_bucket", 1));
        Tadpole t = spawn(level, new Tadpole(level, level.nextEntityId()));
        for (int i = 0; i < 7; i++) t.tick();
        assertTrue(pick(p, t));
        ItemStack out = p.inventory().get(held);
        assertEquals("minecraft:tadpole_bucket", ItemTools.itemName(out));
        assertEquals(7, tagOf(out).getInt("Age"));
    }

    @Test
    void nonWaterBucketOrDeadMobDoesNothing() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        int held = p.inventory().heldWindowSlot();
        Cod cod = spawn(level, new Cod(level, level.nextEntityId()));
        p.inventory().set(held, stack("bucket", 1));
        assertFalse(pick(p, cod), "빈 양동이로는 못 잡는다");
        assertFalse(cod.isRemoved());
        p.inventory().set(held, stack("water_bucket", 1));
        cod.setHealth(0.0F);
        assertFalse(pick(p, cod), "죽은 몹은 못 잡는다");
        assertEquals("minecraft:water_bucket", ItemTools.itemName(p.inventory().get(held)));
    }

    @Test
    void creativeKeepsWaterBucketAndGivesFilledOne() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.CREATIVE);
        int held = p.inventory().heldWindowSlot();
        p.inventory().set(held, stack("water_bucket", 1));
        Cod cod = spawn(level, new Cod(level, level.nextEntityId()));
        assertTrue(pick(p, cod));
        assertTrue(cod.isRemoved());
        assertEquals("minecraft:water_bucket", ItemTools.itemName(p.inventory().get(held)), "크리에이티브는 물 양동이를 쓰지 않는다");
        boolean found = false;
        for (int s = 0; s < 46; s++) found |= ItemTools.itemName(p.inventory().get(s)).equals("minecraft:cod_bucket");
        assertTrue(found, "채운 양동이는 인벤토리에");
    }

    @Test
    void stackOfWaterBucketsKeepsRestAndPutsFilledInInventory() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        int held = p.inventory().heldWindowSlot();
        p.inventory().set(held, stack("water_bucket", 3));
        Cod cod = spawn(level, new Cod(level, level.nextEntityId()));
        assertTrue(pick(p, cod));
        assertEquals(2, p.inventory().get(held).count());
        assertEquals("minecraft:water_bucket", ItemTools.itemName(p.inventory().get(held)));
        boolean found = false;
        for (int s = 0; s < 46; s++) found |= ItemTools.itemName(p.inventory().get(s)).equals("minecraft:cod_bucket");
        assertTrue(found);
    }

    @Test
    void fromBucketMobsAreNotRemovedWhenFarAway() {
        var level = new BlockPlacementTest.TestLevel();
        Cod wild = spawn(level, new Cod(level, level.nextEntityId()));
        assertTrue(wild.removeWhenFarAway(1.0E6));
        wild.setFromBucket(true);
        assertFalse(wild.removeWhenFarAway(1.0E6));
        assertTrue(wild.isPersistentForCaps(), "양동이 출신은 몹 상한에서도 빠진다");
        Axolotl ax = spawn(level, new Axolotl(level, level.nextEntityId()));
        ax.setFromBucket(true);
        assertTrue(ax.isPersistentForCaps());
    }
}
