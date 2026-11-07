// M18: 유리병으로 엔더 드래곤의 숨결 구름을 뜬다(BottleItem.use) — 구름이 0.5 줄고, 드래곤이 만든 구름만 된다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.AreaEffectCloud;
import org.domi.furnace.server.entity.EnderDragon;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonBreathBottleTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class RecLevel extends BlockPlacementTest.TestLevel {
        final List<String> sounds = new ArrayList<>();

        @Override
        public void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch) {
            sounds.add(sound + "/" + source + "/" + (except == null));
            super.playSound(except, x, y, z, sound, source, volume, pitch);
        }
    }

    private static ItemStack bottle(int n) { return new ItemStack(ItemContext.id("minecraft:item", "glass_bottle"), n); }

    private static AreaEffectCloud cloud(RecLevel level, double x, double y, double z, boolean byDragon) {
        var c = new AreaEffectCloud(level, level.nextEntityId());
        c.setPos(x, y, z);
        c.setRadius(3.0F);
        c.setDuration(600);
        if (byDragon) c.setOwner(new EnderDragon(level, level.nextEntityIds(9)));
        level.addFreshEntity(c);
        return c;
    }

    @Test
    void dragonCloudNearbyGivesDragonBreathAndShrinksCloud() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        u.xRot = 0;
        var c = cloud(level, 1.5, -60, 0.5, true);
        u.hands[0] = bottle(2);
        var r = ItemUse.useItem(level, u, 0);
        assertTrue(r.consumesAction(), r.toString());
        assertEquals(2.5F, c.getRadius());
        assertEquals(1, u.hands[0].count());
        assertEquals("minecraft:dragon_breath", ItemTools.itemName(u.added.get(0)));
        assertTrue(u.stats.contains("minecraft:used:minecraft:glass_bottle"));
        assertTrue(level.sounds.contains("minecraft:item.bottle.fill_dragonbreath/neutral/true"), level.sounds.toString());
    }

    @Test
    void cloudNotMadeByTheDragonIsIgnored() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        u.xRot = 0;
        var c = cloud(level, 1.5, -60, 0.5, false);
        u.hands[0] = bottle(1);
        ItemUse.useItem(level, u, 0);
        assertEquals(3.0F, c.getRadius());
        assertTrue(u.added.isEmpty());
        assertEquals(1, u.hands[0].count());
    }

    @Test
    void cloudBeyondTwoBlocksOfTheBodyIsIgnored() {
        var level = new RecLevel();
        var u = new ItemUseTest.User();
        u.xRot = 0;
        var c = cloud(level, 8.0, -60, 0.5, true);
        u.hands[0] = bottle(1);
        ItemUse.useItem(level, u, 0);
        assertEquals(3.0F, c.getRadius());
    }
}
