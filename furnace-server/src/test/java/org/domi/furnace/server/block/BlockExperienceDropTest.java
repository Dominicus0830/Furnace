// T-141 C(PR #370 검토에서 옮김): 블록 경험치(expDrop 리팩터)가 바닐라 tryDropExperience·스포너 난수 순서와 양을 그대로 따르는지,
// Paper dropResources(…, dropExperience=false) 는 경험치도 난수도 쓰지 않는지.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockExperienceDropTest {

    static final long SEED = 370L;

    @BeforeAll
    static void install() {
        BlockPlacementTest.install();
        ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt"));
    }

    /** 난수를 고정하고 popExperience 를 기록한다. */
    static final class Lvl extends BlockPlacementTest.TestLevel {
        final RandomSource rnd = new LegacyRandomSource(SEED);
        final List<Integer> xp = new ArrayList<>();
        @Override public RandomSource random() { return rnd; }
        @Override public void popExperience(Pos pos, int amount) { xp.add(amount); }
    }

    static int state(String name) { return B.defaultState("minecraft:" + name); }

    static ItemStack stack(String name) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), 1); }

    static ItemStack silk(String name) {
        return ItemTools.with(stack(name), "minecraft:enchantments", Map.of("levels", Map.of("minecraft:silk_touch", 1)));
    }

    /** 같은 칸에 놓고 dropResources 를 n 번 — 기록된 경험치 목록. */
    static List<Integer> drop(String block, ItemStack tool, boolean dropExperience, int n, Lvl level) {
        Pos pos = new Pos(5, -60, 5);
        for (int i = 0; i < n; i++) {
            level.setBlock(pos, state(block), 3);
            int s = level.getBlockState(pos);
            level.dropResources(s, pos, level.getBlockEntity(pos), null, tool, dropExperience);
        }
        return level.xp;
    }

    /** 바닐라 Block.tryDropExperience(UniformInt[min,max]): 레벨 난수 nextInt(max-min+1)+min, 0 보다 클 때만 pop. */
    static List<Integer> vanillaUniform(int min, int max, int n, boolean silk) {
        var r = new LegacyRandomSource(SEED);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int v = min == max ? min : r.nextInt(max - min + 1) + min;
            if (silk) v = 0;
            if (v > 0) out.add(v);
        }
        return out;
    }

    @Test
    void diamondOreMatchesVanillaSequence() {
        assertEquals(vanillaUniform(3, 7, 20, false), drop("diamond_ore", stack("iron_pickaxe"), true, 20, new Lvl()));
    }

    @Test
    void redstoneOreMatchesVanillaSequence() {
        assertEquals(vanillaUniform(1, 5, 20, false), drop("redstone_ore", stack("iron_pickaxe"), true, 20, new Lvl()));
    }

    @Test
    void sculkConstantFive() {
        assertEquals(vanillaUniform(5, 5, 3, false), drop("sculk_catalyst", ItemStack.EMPTY, true, 3, new Lvl()));
        assertEquals(vanillaUniform(5, 5, 3, false), drop("sculk_shrieker", ItemStack.EMPTY, true, 3, new Lvl()));
        assertEquals(vanillaUniform(5, 5, 3, false), drop("sculk_sensor", ItemStack.EMPTY, true, 3, new Lvl()));
    }

    @Test
    void spawnerMatchesVanillaSequence() {
        var r = new LegacyRandomSource(SEED);
        List<Integer> want = new ArrayList<>();
        for (int i = 0; i < 10; i++) want.add(15 + r.nextInt(15) + r.nextInt(15));
        assertEquals(want, drop("spawner", ItemStack.EMPTY, true, 10, new Lvl()));
    }

    @Test
    void silkTouchGivesNothingButStillDrawsSample() {
        Lvl level = new Lvl();
        assertEquals(List.of(), drop("diamond_ore", silk("iron_pickaxe"), true, 5, level));
        var r = new LegacyRandomSource(SEED);
        for (int i = 0; i < 5; i++) r.nextInt(5);
        assertEquals(r.nextInt(), level.rnd.nextInt(), "바닐라는 표본을 뽑은 뒤 섬세한 손길로 0 — 난수는 소비된다");
    }

    @Test
    void dropExperienceFalseSkipsXpAndRandom() {
        for (String b : List.of("diamond_ore", "redstone_ore", "sculk_catalyst", "spawner")) {
            Lvl level = new Lvl();
            assertEquals(List.of(), drop(b, stack("iron_pickaxe"), false, 3, level), b);
            assertEquals(new LegacyRandomSource(SEED).nextInt(), level.rnd.nextInt(), b + ": 경험치를 안 주면 난수도 쓰지 않는다");
        }
    }
}
