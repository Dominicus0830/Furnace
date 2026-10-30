package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Axolotl;
import org.domi.furnace.server.entity.Cod;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Tadpole;
import org.domi.furnace.server.entity.TropicalFish;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 몹 양동이 쏟기(MobBucketItem): 물을 붓고 bucket_entity_data 로 몹을 되살린다. 소리·게임 이벤트·난수 소비까지 바닐라 순서로. */
class MobBucketUseTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 소리·게임 이벤트를 기록하는 시험 레벨. */
    static class RecLevel extends BlockPlacementTest.TestLevel {
        final List<String> sounds = new ArrayList<>(), events = new ArrayList<>();

        @Override
        public void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch) {
            sounds.add(sound + "/" + source);
            super.playSound(except, x, y, z, sound, source, volume, pitch);
        }

        @Override
        public void gameEventFrom(String event, Pos pos, Object entity, int state) {
            events.add(event);
            super.gameEventFrom(event, pos, entity, state);
        }
    }

    private static ItemStack stack(String item, int n) { return new ItemStack(ItemContext.id("minecraft:item", item), n); }

    private static ItemStack bucket(String item, NbtCompound tag) { return ItemTools.with(stack(item, 1), "minecraft:bucket_entity_data", tag); }

    private static <T> T only(RecLevel level, Class<T> type) {
        var list = level.entities().tickSnapshot().stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, list.size(), type.getSimpleName());
        return list.get(0);
    }

    @Test
    void codBucketPoursWaterAndRestoresTheMobWithItsData() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("NoAI", true);
        tag.putBoolean("Silent", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Glowing", true);
        tag.putBoolean("Invulnerable", true);
        tag.putFloat("Health", 1.5F);
        ItemStack b = ItemTools.with(bucket("cod_bucket", tag), "minecraft:custom_name", Text.text("Bob").toJson());
        u.hands[0] = b;
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertEquals("minecraft:water[level=0]", Blocks.get().describe(level.getBlockState(new Pos(0, -60, 0))));
        assertEquals("minecraft:bucket", ItemTools.itemName(u.hands[0]));
        assertTrue(u.stats.contains("minecraft:used:minecraft:cod_bucket"));

        Cod cod = only(level, Cod.class);
        assertTrue(cod.fromBucket());
        assertTrue(cod.isNoAi());
        assertTrue(cod.isSilent());
        assertTrue(cod.isNoGravity());
        assertTrue(cod.hasGlowingTag());
        assertTrue(cod.isInvulnerable());
        assertEquals(1.5F, cod.health(), 0.0F);
        assertEquals("Bob", cod.getCustomName().getString());
        assertEquals(0.5, cod.x(), 1e-9);
        assertEquals(-60.0, cod.y(), 1e-9, "물 칸 바닥에 맞춰 내린다");
        assertFalse(cod.removeWhenFarAway(1.0E6));
    }

    @Test
    void emptySoundIsNeutralAndOnlyEntityPlaceEventIsPosted() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        u.hands[0] = bucket("salmon_bucket", new NbtCompound());
        ItemUse.useItem(level, u, 0);
        assertTrue(level.sounds.contains("minecraft:item.bucket.empty_fish/neutral"), level.sounds.toString());
        assertFalse(level.sounds.stream().anyMatch(s -> s.startsWith("minecraft:item.bucket.empty/")), "물 양동이 소리는 대체된다");
        assertTrue(level.events.contains("minecraft:entity_place"));
        assertFalse(level.events.contains("minecraft:fluid_place"), "MobBucketItem.playEmptySound 는 fluid_place 를 내지 않는다");

        var level2 = new RecLevel();
        u.hands[0] = bucket("axolotl_bucket", new NbtCompound());
        ItemUse.useItem(level2, u, 0);
        assertTrue(level2.sounds.contains("minecraft:item.bucket.empty_axolotl/neutral"));
        var level3 = new RecLevel();
        u.hands[0] = bucket("tadpole_bucket", new NbtCompound());
        ItemUse.useItem(level3, u, 0);
        assertTrue(level3.sounds.contains("minecraft:item.bucket.empty_tadpole/neutral"));
    }

    @Test
    void tropicalFishRestoresBucketVariantTagAndOnlyWhenInt() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        NbtCompound tag = new NbtCompound();
        tag.putInt("BucketVariantTag", 0x0B0E0101);
        u.hands[0] = bucket("tropical_fish_bucket", tag);
        ItemUse.useItem(level, u, 0);
        assertEquals(0x0B0E0101, only(level, TropicalFish.class).getVariant());

        // 정수가 아닌 BucketVariantTag(바닐라: contains(key, 3) 가 아님)는 무시 — finalizeSpawn 이 정한 변종이 남는다
        var level2 = new RecLevel();
        NbtCompound bad = new NbtCompound();
        bad.putString("BucketVariantTag", "x");
        u.hands[0] = bucket("tropical_fish_bucket", bad);
        ItemUse.useItem(level2, u, 0);
        assertNotEquals(0x0B0E0101, only(level2, TropicalFish.class).getVariant());
    }

    @Test
    void axolotlRestoresVariantAgeAndHuntingCooldown() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        NbtCompound tag = new NbtCompound();
        tag.putInt("Variant", 4);
        tag.putInt("Age", -5000);
        tag.putLong("HuntingCooldown", 777L);
        u.hands[0] = bucket("axolotl_bucket", tag);
        ItemUse.useItem(level, u, 0);
        Axolotl ax = only(level, Axolotl.class);
        assertEquals(4, ax.variant());
        assertEquals(-5000, ax.getAge());
        assertTrue(ax.fromBucket());
        assertTrue(ax.axolotlBrain().hasMemoryValue(org.domi.furnace.server.entity.ai.brain.MemoryModuleType.HAS_HUNTING_COOLDOWN));
        assertEquals(777L, ax.axolotlBrain().getTimeUntilExpiry(org.domi.furnace.server.entity.ai.brain.MemoryModuleType.HAS_HUNTING_COOLDOWN));
        assertTrue(ax.isPersistentForCaps());
    }

    @Test
    void tadpoleRestoresAge() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        NbtCompound tag = new NbtCompound();
        tag.putInt("Age", 321);
        u.hands[0] = bucket("tadpole_bucket", tag);
        ItemUse.useItem(level, u, 0);
        Tadpole t = only(level, Tadpole.class);
        t.tick();
        assertEquals(322, t.saveAsPassenger().getInt("Age"));
    }

    @Test
    void levelRandomIsConsumedLikeVanillaFinalizeSpawn() {
        // 대구: 방향 nextFloat 1회 + Mob.finalizeSpawn(삼각분포 nextDouble 2회 + 왼손잡이 nextFloat 1회). 아홀로틀은 BUCKET 이라 finalizeSpawn 없이 방향 1회뿐.
        for (String kind : new String[] {"cod_bucket", "axolotl_bucket"}) {
            var level = new RecLevel();
            var seeded = (LegacyRandomSource) level.random();
            seeded.setSeed(12345L);
            var ref = new LegacyRandomSource(12345L);
            ref.nextFloat();
            if (kind.equals("cod_bucket")) {
                ref.nextDouble();
                ref.nextDouble();
                ref.nextFloat();
            }
            var u = new ItemUseTest.User();
            u.hands[0] = bucket(kind, new NbtCompound());
            ItemUse.useItem(level, u, 0);
            assertEquals(ref.nextLong(), level.random().nextLong(), kind);
        }
    }

    @Test
    void creativeBucketIsKeptAndMobStillAppears() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        u.creative = true;
        u.hands[0] = bucket("cod_bucket", new NbtCompound());
        ItemUse.useItem(level, u, 0);
        assertEquals("minecraft:cod_bucket", ItemTools.itemName(u.hands[0]));
        only(level, Cod.class);
    }

    @Test
    void netherEvaporatesWaterButTheMobStillSpawns() {
        var level = new RecLevel() {
            @Override
            protected LevelServices services() {
                return new LevelServices.Delegating(LevelServices.DEFAULT) {
                    @Override public boolean ultraWarm() { return true; }
                };
            }
        };
        var u = new ItemUseTest.User();
        u.hands[0] = bucket("cod_bucket", new NbtCompound());
        ItemUse.useItem(level, u, 0);
        assertEquals("minecraft:air", Blocks.get().describe(level.getBlockState(new Pos(0, -60, 0))));
        only(level, Cod.class);
        assertEquals("minecraft:bucket", ItemTools.itemName(u.hands[0]));
    }

    @Test
    void roundTripPickupThenPourKeepsTheMob() {
        var level = new RecLevel();
        Cod cod = new Cod(level, level.nextEntityId());
        cod.moveTo(0.5, -60, 0.5, 0, 0);
        cod.setCustomName(Text.text("Cody"));
        cod.setNoAi(true);
        cod.setHealth(2.0F);
        ItemStack b = cod.saveToBucketTag(cod.getBucketItemStack());
        var u = new ItemUseTest.User();
        u.hands[0] = b;
        ItemUse.useItem(level, u, 0);
        Cod back = only(level, Cod.class);
        assertEquals("Cody", back.getCustomName().getString());
        assertTrue(back.isNoAi());
        assertEquals(2.0F, back.health(), 0.0F);
        assertTrue(back.isAlive());
    }
}
