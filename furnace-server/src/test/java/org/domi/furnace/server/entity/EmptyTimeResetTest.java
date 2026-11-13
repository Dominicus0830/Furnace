// 바닐라 1.21.1: resetEmptyTime 을 부르는 곳은 Entity.changeDimension 하나뿐이다(바이트코드 bsr → aqu.h()V). 새 엔티티를 만들었을 때만
// 떠난·닿은 레벨 둘 다 부르고, 명령 teleportTo(레벨, …)·플레이어 경로·목적지 없음은 부르지 않는다(PR #279 검토 R2·R5).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.EndPortalBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EmptyTimeResetTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static DimensionTestLevel[] overAndEnd() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        return new DimensionTestLevel[] {over, end};
    }

    private static Pig pigAt(DimensionTestLevel lvl, double x, double z) {
        Pig pig = new Pig(lvl, lvl.nextEntityId());
        pig.moveTo(x, -59.8, z, 0, 0);
        lvl.addFreshEntity(pig);
        return pig;
    }

    @Test
    void singleThreadPortalResetsBothLevels() {
        var l = overAndEnd();
        pigAt(l[0], 2.5, 2.5);
        l[0].tickEntities(6);
        assertNotNull(l[1].entities().tickSnapshot().stream().filter(Pig.class::isInstance).findFirst().orElse(null), "돼지가 엔드에");
        assertEquals(1, l[0].emptyResets, "떠난 레벨");
        assertEquals(1, l[1].emptyResets, "닿은 레벨");
    }

    @Test
    void serverPortalResetsBothLevelsOnlyWhenArrived() {
        var l = overAndEnd();
        var travel = new PassengerPortalTest.SyncTravel(l[0], l[1]);
        l[0].travel = travel;
        pigAt(l[0], 2.5, 2.5);
        l[0].tickEntities(6);
        assertEquals(1, travel.travels);
        assertEquals(1, l[0].emptyResets);
        assertEquals(1, l[1].emptyResets);

        var l2 = overAndEnd();
        var nowhere = new PassengerPortalTest.SyncTravel(l2[0], l2[1]);
        nowhere.noDestination = true;
        l2[0].travel = nowhere;
        pigAt(l2[0], 2.5, 2.5);
        l2[0].tickEntities(6);
        assertEquals(1, nowhere.travels);
        assertEquals(0, l2[0].emptyResets, "목적지가 없으면(되살림) 바닐라는 changeDimension 을 부르지 않는다");
        assertEquals(0, l2[1].emptyResets);
    }

    @Test
    void commandTeleportToOtherLevelDoesNotReset() {
        var l = overAndEnd();
        l[0].travel = new PassengerPortalTest.SyncTravel(l[0], l[1]);
        Pig pig = pigAt(l[0], 6.5, 6.5);
        assertTrue(pig.teleportToDimension(EndPortalBlock.THE_END, 1.5, -59, 1.5, 0, 0, null));
        assertTrue(pig.isRemoved());
        assertEquals(0, l[0].emptyResets, "바닐라 Entity.teleportTo(레벨, …) 는 resetEmptyTime 을 부르지 않는다");
        assertEquals(0, l[1].emptyResets);
    }
}
