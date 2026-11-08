// gravity-block-portal-dupe: 엔드 포털로 넘어간 중력 블록은 바닐라(기본 true)에서는 옛 엔티티가 그 틱에 마저 돌아 제자리에도 흔적(블록 또는 아이템)을 남기고,
// false 면 넘어간 복사본만 남는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.EndPortalBlock;
import org.domi.furnace.server.config.OperatorOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GravityBlockPortalDupeTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @AfterEach
    void reset() { OperatorOptions.install(null); }

    private static void dupe(boolean on) {
        var d = OperatorOptions.DEFAULT;
        OperatorOptions.install(new OperatorOptions(d.strictChatOrder(), d.unsignedChatAsSystem(), d.serverBrand(), d.announceSleepStatus(),
                d.confirmInterrupt(), d.interruptConfirmSeconds(), d.motdFormat(), on));
    }

    /**
     * 모래 하나를 포털 칸에 착지하는 틱으로 떨어뜨리고 40틱 뒤, 두 차원에 남은 모래의 수(놓인 블록 + 떨어진 아이템 + 아직 떨어지는 엔티티)를 센다.
     * 옛 엔티티가 마저 돌면 포털 칸에 못 놓아 아이템을 떨구고(그 아이템도 포털로 엔드에 간다) 복사본 하나가 더 생긴다 — 바닐라 복제.
     */
    private static int sandAfterPortal() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) over.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:stone"), 3);
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        FallingBlockEntity sand = FallingBlockEntity.fall(over, new Pos(2, -59, 2), B.defaultState("sand"));
        sand.moveTo(2.5, -59.0, 2.5, 0.0F, 0.0F);
        sand.setDeltaMovement(0.0, -1.0, 0.0);   // 한 틱에 포털 칸을 지나 바닥에 닿는 빠르기 — 포털에 들어간 틱에 착지한다
        for (int i = 0; i < 40; i++) {
            over.tickEntities(1);
            end.tickEntities(1);
        }
        assertTrue(sand.isRemoved());
        int n = 0;
        for (DimensionTestLevel lv : new DimensionTestLevel[] {over, end}) {
            n += (int) lv.entities().tickSnapshot().stream().filter(e -> (e instanceof FallingBlockEntity || e instanceof ItemEntity) && !e.isRemoved()).count();
            for (int x = -2; x <= 102; x++) for (int y = -61; y <= 52; y++) for (int z = -2; z <= 4; z++) {
                if (lv == over && x > 4) break;
                if (lv == end && x < 98) continue;
                if (B.is(lv.getBlockState(new Pos(x, y, z)), "sand")) n++;
            }
        }
        return n;
    }

    @Test
    void defaultKeepsVanillaDuplication() {
        assertTrue(OperatorOptions.current().gravityBlockPortalDupe());
        assertEquals(2, sandAfterPortal(), "복사본 + 옛 엔티티가 떨군 아이템 = 2 (바닐라 복제)");
    }

    @Test
    void disabledLeavesOnlyTheCopy() {
        dupe(false);
        assertEquals(1, sandAfterPortal(), "넘어간 복사본 하나만 남는다");
    }
}
