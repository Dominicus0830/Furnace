// A09-01 재현(동작): 신호기 위에 슬래브·계단·카펫·눈 한 겹이 있어도 바닐라는 빛줄기가 이어지고(getLightBlock < 15) 색 없는 틴티드 유리는 빛줄기를 끊는다.
package org.domi.furnace.server.audit;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BeaconBlockEntity;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A09BeaconBeamBehaviorReproTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<Player> players = new ArrayList<>();
        long time;
        @Override public long gameTime() { return time; }
        @Override public void forEachPlayerIn(AABB box, Consumer<Player> c) {
            for (Player p : players) if (p.boundingBox().intersects(box)) c.accept(p);
        }
    }

    /** 철 블록 3×3 받침(1단) 위 신호기. 그 위 한 칸(y+1)에 above 를 놓고 여러 틱 돌린 뒤, 근처 플레이어가 속도 효과를 받았는가. */
    private static boolean speedGranted(String above) {
        Lvl l = new Lvl();
        Pos beacon = new Pos(0, -60, 0);
        int iron = B.defaultState(B.id("minecraft:iron_block"));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) l.setBlock(new Pos(x, -61, z), iron, 2);
        int beaconState = B.defaultState(B.id("minecraft:beacon"));
        l.setBlock(beacon, beaconState, 2);
        if (above != null) l.setBlock(beacon.above(), B.defaultState(B.id(above)), 2);
        Player p = new Player(1, UUID.randomUUID(), "P", new ConcurrentEmbeddedChannel());
        p.publishPosition(3.5, -60, 0.5, 0, 0, true);
        p.drainPending();
        l.players.add(p);
        BeaconBlockEntity be = new BeaconBlockEntity(beacon, beaconState);
        be.primaryPower = "minecraft:speed";
        // 빛줄기 검사는 한 틱에 10칸씩, 효과는 80틱마다
        for (int i = 0; i < 4; i++) {
            l.time = 80L * (i + 1);
            be.tick(l);
        }
        return p.vitals().getEffect(MobEffect.SPEED) != null;
    }

    @Test
    void controlOpenSkyGrantsEffect() {
        assertTrue(speedGranted(null), "대조: 위가 트여 있으면 효과를 받는다");
    }

    @Test
    void slabAboveBeaconStillGrantsEffectInVanilla() {
        assertTrue(speedGranted("minecraft:oak_slab"), "바닐라: 슬래브(getLightBlock 0)는 빛줄기를 끊지 않는다");
    }

    @Test
    void stairsAboveBeaconStillGrantsEffectInVanilla() {
        assertTrue(speedGranted("minecraft:stone_stairs"), "바닐라: 계단은 빛줄기를 끊지 않는다");
    }

    @Test
    void carpetAboveBeaconStillGrantsEffectInVanilla() {
        assertTrue(speedGranted("minecraft:white_carpet"), "바닐라: 카펫은 빛줄기를 끊지 않는다");
    }

    @Test
    void snowLayerAboveBeaconStillGrantsEffectInVanilla() {
        assertTrue(speedGranted("minecraft:snow"), "바닐라: 눈 한 겹(getLightBlock 0)은 빛줄기를 끊지 않는다");
    }

    @Test
    void tintedGlassAboveBeaconBlocksBeamInVanilla() {
        assertFalse(speedGranted("minecraft:tinted_glass"), "바닐라: 틴티드 유리(getLightBlock 15)는 빛줄기를 막는다");
    }

    @Test
    void solidStoneAboveBeaconBlocksBeam() {
        assertFalse(speedGranted("minecraft:stone"), "대조: 돌은 막는다");
    }
}
