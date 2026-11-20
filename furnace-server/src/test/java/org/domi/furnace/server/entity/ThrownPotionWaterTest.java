// 물 물약(ThrownPotion.onHitBlock/dowseFire): 맞은 칸 바깥쪽·그 반대쪽·수평 네 칸의 불·양초·모닥불을 끈다. 터뜨림은 부딪힘 처리 뒤(onHit).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThrownPotionWaterTest {

    private static final Pos AT = new Pos(3, -60, 3);

    @BeforeEach
    void install() throws Exception {
        assumeTrue(org.domi.furnace.server.recipe.TestRecipes.manager() != null);
    }

    private static int state(String name) { return B.defaultState(B.id("minecraft:" + name)); }

    private static ThrownPotion potion(ProjectileBlockHitTest.Level level, String potionId) {
        var p = new ThrownPotion(level, level.nextEntityId());
        p.setItem(ItemTools.with(new ItemStack(ItemContext.id("minecraft:item", "splash_potion"), 1), "minecraft:potion_contents", Map.of("potion", potionId)));
        level.addFreshEntity(p);
        return p;
    }

    private static Projectile.Hit hit(int face) { return new Projectile.Hit(null, AT, face, AT.x() + 0.5, AT.y() + 1.0, AT.z() + 0.5); }

    @Test
    void waterPotionDousesFireCandleAndCampfireAroundHitFace() {
        var level = new ProjectileBlockHitTest.Level();
        Pos front = AT.relative(Dir.UP);   // 윗면을 맞힘
        Pos fireAt = front.relative(Dir.WEST), farFire = front.relative(Dir.NORTH, 2);
        level.setBlock(fireAt.relative(Dir.DOWN), state("stone"), 3);   // 불이 붙어 있을 받침
        level.setBlock(farFire.relative(Dir.DOWN), state("stone"), 3);
        level.setBlock(fireAt, state("fire"), 3);
        level.setBlock(front.relative(Dir.NORTH), B.prop("lit").with(state("candle"), true), 3);
        level.setBlock(front.relative(Dir.EAST), B.prop("lit").with(state("campfire"), true), 3);
        level.setBlock(AT, B.prop("lit").with(state("campfire"), true), 3);   // 맞은 칸 자신 = 면 맞은편 칸 → 꺼진다
        level.setBlock(farFire, state("fire"), 3);                             // 수평 두 칸 밖
        level.setBlock(front.relative(Dir.EAST, 2), B.prop("lit").with(state("candle"), true), 3);   // 수평 두 칸 밖
        potion(level, "minecraft:water").onHitBlock(hit(Dir.UP));
        assertFalse(B.isTag(level.getBlockState(fireAt), "minecraft:fire"), "불은 지워진다");
        assertFalse(B.prop("lit").is(level.getBlockState(front.relative(Dir.NORTH))), "양초가 꺼진다");
        assertFalse(B.prop("lit").is(level.getBlockState(front.relative(Dir.EAST))), "모닥불이 꺼진다");
        assertFalse(B.prop("lit").is(level.getBlockState(AT)), "맞은 칸의 모닥불도 꺼진다");
        assertTrue(B.isTag(level.getBlockState(farFire), "minecraft:fire"), "범위 밖의 불은 그대로");
        assertTrue(B.prop("lit").is(level.getBlockState(front.relative(Dir.EAST, 2))), "범위 밖의 양초는 그대로");
    }

    @Test
    void nonWaterPotionDoesNotDouse() {
        var level = new ProjectileBlockHitTest.Level();
        Pos front = AT.relative(Dir.UP);
        level.setBlock(front.relative(Dir.DOWN), state("stone"), 3);
        level.setBlock(front, state("fire"), 3);
        potion(level, "minecraft:awkward").onHitBlock(hit(Dir.UP));
        assertTrue(B.isTag(level.getBlockState(front), "minecraft:fire"));
    }

    @Test
    void splashHappensInOnHitAndDiscardsThePotion() {
        var level = new ProjectileBlockHitTest.Level();
        Pos front = AT.relative(Dir.UP);
        level.setBlock(AT, state("stone"), 3);
        level.setBlock(front, state("fire"), 3);
        var p = potion(level, "minecraft:water");
        p.onHit(hit(Dir.UP));
        assertFalse(B.isTag(level.getBlockState(front), "minecraft:fire"));
        assertTrue(p.isRemoved(), "터뜨린 뒤 사라진다");
    }
}
