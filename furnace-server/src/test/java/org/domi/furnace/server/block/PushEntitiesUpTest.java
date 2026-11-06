package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.types.FarmBlock;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 바닐라 Block.pushEntitiesUp — 손 계산. 바닥 윗면 y=-60, 시험 칸은 (0,-60,0). */
class PushEntitiesUpTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Pos AT = new Pos(0, -60, 0);

    static class PLevel extends BlockPlacementTest.TestLevel {
        Player player;
        final LevelServices svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
        };

        @Override protected LevelServices services() { return svc; }
    }

    private static ItemEntity item(PLevel l, double x, double y, double z) {
        ItemEntity e = new ItemEntity(l, l.nextEntityId(), x, y, z, B.stack("minecraft:dirt", 1), 0, 0, 0);
        assertTrue(l.addFreshEntity(e));
        return e;
    }

    @Test
    void joinOnlySecond_farmlandToDirt_isTopSlab() {
        Shape farm = B.behavior(B.defaultState("minecraft:farmland")).getCollisionShape(B.defaultState("minecraft:farmland"), null, AT, null);
        Shape s = Shape.joinOnlySecond(farm, Shape.BLOCK);
        assertArrayEquals(new double[] {0, 15 / 16.0, 0, 1, 1, 1}, s.bounds(), 1e-12);
        assertTrue(Shape.joinOnlySecond(Shape.BLOCK, Shape.BLOCK).isEmpty());
        assertTrue(Shape.joinOnlySecond(Shape.BLOCK, farm).isEmpty(), "아래로 줄어들면 새로 생긴 부분이 없다");
        assertSame(Shape.BLOCK, Shape.joinOnlySecond(Shape.EMPTY, Shape.BLOCK));
    }

    @Test
    void farmlandToDirt_liftsItemAndCowOntoDirt() {
        PLevel l = new PLevel();
        int farm = B.defaultState("minecraft:farmland");
        l.setBlock(AT, farm, 3);
        ItemEntity it = item(l, 0.5, -60.0 + 15 / 16.0, 0.5);
        Cow cow = new Cow(l, l.nextEntityId());
        cow.moveTo(0.5, -60.0 + 15 / 16.0, 0.5, 30, 0);
        l.addFreshEntity(cow);
        ItemEntity above = item(l, 0.5, -58.5, 0.5);   // 새 부분 경계 상자와 안 겹친다
        FarmBlock.turnToDirt(null, farm, l, AT);
        assertEquals("minecraft:dirt", Blocks.get().describe(l.getBlockState(AT)));
        assertEquals(-59.0, it.y(), 1e-9, "상자를 1 올려 -1 만큼 내리면 흙 윗면에 닿는다 → +1/16");
        assertEquals(-59.0, cow.y(), 1e-9);
        assertEquals(30.0F, cow.yRot(), 0.0F, "회전은 그대로");
        assertEquals(-58.5, above.y(), 1e-9);
    }

    @Test
    void snowLayer_oneToTwo_liftsItemTwoSixteenths() {
        PLevel l = new PLevel();
        Prop layers = B.prop("layers");
        int one = B.defaultState("minecraft:snow");
        l.setBlock(AT, one, 3);
        ItemEntity it = item(l, 0.5, -60.0, 0.5);   // 한 겹 눈은 충돌 모양이 없어 칸 바닥에 서 있다
        int two = layers.withInt(one, 2);
        assertEquals(two, l.pushEntitiesUp(one, two, AT));
        assertEquals(-60.0 + 2 / 16.0, it.y(), 1e-9);
    }

    @Test
    void endPortalFrameEye_onlyLiftsEntitiesOverTheEye() {
        PLevel l = new PLevel();
        int frame = B.defaultState("minecraft:end_portal_frame");
        l.setBlock(AT, frame, 3);
        ItemEntity mid = item(l, 0.5, -60.0 + 13 / 16.0, 0.5);
        ItemEntity edge = item(l, 0.1, -60.0 + 13 / 16.0, 0.1);   // 상자 [-0.025, 0.225] — 눈 [4/16, 12/16] 밖
        int eye = org.domi.furnace.server.block.types.EndPortalFrameBlock.eyeProp().with(frame, true);
        l.pushEntitiesUp(frame, eye, AT);
        assertEquals(-59.0, mid.y(), 1e-9);
        assertEquals(-60.0 + 13 / 16.0, edge.y(), 1e-9);
    }

    @Test
    void player_getsRelativeTeleport_spectatorIgnored() {
        PLevel l = new PLevel();
        int farm = B.defaultState("minecraft:farmland");
        l.setBlock(AT, farm, 3);
        Player p = new Player(900_321, UUID.randomUUID(), "P", new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(0.5, -60.0 + 15 / 16.0, 0.5, 0, 0, true);
        List<double[]> tps = new ArrayList<>();
        p.teleporter(tps::add);
        l.player = p;
        l.pushEntitiesUp(farm, B.defaultState("minecraft:dirt"), AT);
        assertEquals(1, tps.size());
        double[] t = tps.get(0);
        assertEquals(8, t.length, "상대 텔레포트(RelativeMovement.ALL)");
        assertEquals(-59.0, t[1], 1e-9);
        assertArrayEquals(new double[] {0.0, 1 / 16.0, 0.0}, new double[] {t[5], t[6], t[7]}, 1e-9);

        tps.clear();
        p.vitals().setGameMode(GameMode.SPECTATOR);
        l.pushEntitiesUp(farm, B.defaultState("minecraft:dirt"), AT);
        assertTrue(tps.isEmpty(), "관전자는 밀지 않는다(getEntities 의 NO_SPECTATORS)");
    }
}
