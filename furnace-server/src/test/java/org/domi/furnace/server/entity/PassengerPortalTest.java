// 탈것이 포털을 건너면 승객도 함께 건너가 새 탈것에 다시 탄다(바닐라 1.21.1 Entity.changeDimension: getPassengers → unRide → 승객마다
// changeDimension → 탈것을 만들어 넣고 → 승객.startRiding(새 탈것, true)). 명령 /tp 의 다른 차원 이동(teleportTo)은 승객을 데려가지 않는다.
// 단일 스레드 경로(changeDimension)와, 서버 경로(moveAcross)를 같은 스레드에서 끝내는 대역 이동 둘 다 본다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.EndPortalBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PassengerPortalTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static <T extends Entity> T only(DimensionTestLevel level, Class<T> c) {
        return level.entities().tickSnapshot().stream().filter(c::isInstance).map(c::cast).filter(e -> !e.isRemoved()).findFirst().orElse(null);
    }

    private static long alive(DimensionTestLevel level, Class<?> c) {
        return level.entities().tickSnapshot().stream().filter(c::isInstance).filter(e -> !e.isRemoved()).count();
    }

    /** 서버 경로 대역: 목적지를 같은 스레드에서 바로 정해 arrive 한다(없앨 수도 있다). 되돌리기는 원천 레벨에서. */
    static final class SyncTravel implements LevelServices.DimensionTravel {
        final DimensionTestLevel source;
        final DimensionTestLevel target;
        boolean noDestination;
        int travels;

        SyncTravel(DimensionTestLevel source, DimensionTestLevel target) {
            this.source = source;
            this.target = target;
        }

        @Override public boolean isLevelEnabled(String dimension) { return true; }

        @Override
        public void travel(PortalTravel plan, BiConsumer<ChunkLevel, DimensionTransition> arrive) {
            travels++;
            if (noDestination) {
                arrive.accept(null, null);
                return;
            }
            arrive.accept(target, plan.complete().apply(target));
        }

        @Override public void runAtSource(int blockX, int blockZ, Consumer<ChunkLevel> task) { task.accept(source); }
    }

    private static DimensionTestLevel[] overAndEnd() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        return new DimensionTestLevel[] {over, end};
    }

    /** 엔드 포털 칸 위 수레에 돼지를 태운다. */
    private static Pig pigInCart(DimensionTestLevel over, AbstractMinecart[] cartOut) {
        AbstractMinecart cart = AbstractMinecart.create(over, 2.5, -59.8, 2.5, AbstractMinecart.Type.RIDEABLE);
        over.addFreshEntity(cart);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.setNoAi(true);
        pig.moveTo(2.5, -59.8, 2.5, 0, 0);
        over.addFreshEntity(pig);
        assertTrue(pig.startRiding(cart, true));
        cartOut[0] = cart;
        return pig;
    }

    @Test
    void singleThreadPath_passengerCrossesWithCartAndRidesAgain() {
        var l = overAndEnd();
        var over = l[0];
        var end = l[1];
        AbstractMinecart[] c = new AbstractMinecart[1];
        Pig pig = pigInCart(over, c);
        java.util.UUID pigId = pig.uuid(), cartId = c[0].uuid();
        over.tickEntities(6);
        assertTrue(c[0].isRemoved() && pig.isRemoved(), "수레와 돼지 둘 다 원래 쪽에서 빠진다");
        assertEquals(Entity.RemovalReason.CHANGED_DIMENSION, pig.removalReason(), "승객도 차원 이동으로 빠진다(내려서 남지 않는다)");
        AbstractMinecart cart = only(end, AbstractMinecart.class);
        Pig p2 = only(end, Pig.class);
        assertNotNull(cart, "수레가 엔드에");
        assertNotNull(p2, "돼지도 엔드에");
        assertEquals(cartId, cart.uuid());
        assertEquals(pigId, p2.uuid());
        assertSame(cart, p2.vehicle(), "건너간 새 수레에 다시 탔다");
        assertEquals(java.util.List.of(p2), cart.passengers());
        assertEquals(0, alive(over, Pig.class), "오버월드에 남은 돼지 없음");
    }

    @Test
    void singleThreadPath_passengersOfPassengersCrossToo() {
        var l = overAndEnd();
        var over = l[0];
        var end = l[1];
        AbstractMinecart[] c = new AbstractMinecart[1];
        Pig pig = pigInCart(over, c);
        Chicken chick = new Chicken(over, over.nextEntityId());
        chick.setNoAi(true);
        chick.moveTo(2.5, -59.0, 2.5, 0, 0);
        over.addFreshEntity(chick);
        assertTrue(chick.startRiding(pig, true));
        over.tickEntities(6);
        AbstractMinecart cart = only(end, AbstractMinecart.class);
        Pig p2 = only(end, Pig.class);
        Chicken c2 = only(end, Chicken.class);
        assertNotNull(c2, "승객의 승객도 건너간다(승객의 changeDimension 이 다시 자기 승객을 데려간다)");
        assertSame(cart, p2.vehicle());
        assertSame(p2, c2.vehicle(), "승객의 승객은 건너간 승객에 다시 탄다");
        assertEquals(0, alive(over, Chicken.class));
    }

    @Test
    void serverPath_passengerCrossesWithCartAndRidesAgain() {
        var l = overAndEnd();
        var over = l[0];
        var end = l[1];
        var travel = new SyncTravel(over, end);
        over.travel = travel;
        AbstractMinecart[] c = new AbstractMinecart[1];
        Pig pig = pigInCart(over, c);
        int cartEntityId = c[0].id();
        over.tickEntities(6);
        assertEquals(1, travel.travels, "수레 하나만 이동을 시작한다(승객은 수레를 따라간다)");
        assertTrue(pig.isRemoved());
        AbstractMinecart cart = only(end, AbstractMinecart.class);
        Pig p2 = only(end, Pig.class);
        assertNotNull(cart);
        assertNotNull(p2, "돼지도 함께 도착");
        assertSame(cart, p2.vehicle(), "새 수레에 다시 탔다");
        assertTrue(p2.id() < cart.id() && cart.id() > cartEntityId, "바닐라 순서: 승객이 먼저 만들어지고 탈것이 나중");
        assertEquals(0, alive(over, Pig.class));
        assertEquals(0, alive(over, AbstractMinecart.class));
    }

    @Test
    void serverPath_noDestinationRestoresCartAndPassengerTogether() {
        var l = overAndEnd();
        var over = l[0];
        var end = l[1];
        var travel = new SyncTravel(over, end);
        travel.noDestination = true;
        over.travel = travel;
        AbstractMinecart[] c = new AbstractMinecart[1];
        Pig pig = pigInCart(over, c);
        java.util.UUID pigId = pig.uuid();
        over.tickEntities(6);
        assertEquals(1, travel.travels, "되살린 수레는 포털 대기(쿨다운)를 이어받아 다시 넘어가려 하지 않는다");
        AbstractMinecart back = only(over, AbstractMinecart.class);
        Pig p2 = only(over, Pig.class);
        assertNotNull(back, "갈 곳이 없으면 수레를 원래 자리에 되살린다");
        assertNotNull(p2, "승객도 되살린다");
        assertEquals(pigId, p2.uuid());
        assertSame(back, p2.vehicle(), "되살린 수레에 다시 태운다(바닐라는 목적지가 없으면 그대로 탄 채)");
        assertNull(only(end, Pig.class));
    }

    /** 플레이어 승객 대역: 떼기는 언제나 성공하고, 도착·되돌림 호출을 센다. 목적지 계산이 던지거나 도착 레벨이 수레 넣기에서 던질 수 있다. */
    static final class PlayerCountingTravel implements LevelServices.DimensionTravel {
        final DimensionTestLevel source;
        final DimensionTestLevel target;
        boolean throwOnTravel;
        final java.util.List<Entity> arrivedWith = new java.util.ArrayList<>(), returnedWith = new java.util.ArrayList<>();
        int departed;

        PlayerCountingTravel(DimensionTestLevel source, DimensionTestLevel target) {
            this.source = source;
            this.target = target;
        }

        @Override public boolean isLevelEnabled(String dimension) { return true; }

        @Override
        public void travel(PortalTravel plan, BiConsumer<ChunkLevel, DimensionTransition> arrive) {
            if (throwOnTravel) throw new IllegalStateException("시험: 목적지 찾기 실패");
            arrive.accept(target, plan.complete().apply(target));
        }

        @Override public void runAtSource(int blockX, int blockZ, Consumer<ChunkLevel> task) { task.accept(source); }

        @Override public boolean departPassenger(Player p) { departed++; return true; }

        @Override public void arrivePassenger(Player p, ChunkLevel t, DimensionTransition tr, Entity vehicle) { arrivedWith.add(vehicle); }

        @Override public void returnPassenger(Player p, ChunkLevel src, Entity vehicle) { returnedWith.add(vehicle); }
    }

    /** 수레에 플레이어와 돼지를 태운다. */
    private static Player playerAndPigInCart(DimensionTestLevel over) {
        AbstractMinecart[] c = new AbstractMinecart[1];
        Pig pig = pigInCart(over, c);
        Player p = new Player(over.nextEntityId(), java.util.UUID.randomUUID(), "A", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        assertTrue(p.startRiding(c[0], true));
        assertEquals(2, c[0].passengers().size(), "플레이어와 돼지 둘 다 탔다: " + pig);
        return p;
    }

    @Test
    void routeThrowing_stillReturnsDetachedPlayerAndRestoresCart() {
        var l = overAndEnd();
        var over = l[0];
        var travel = new PlayerCountingTravel(over, l[1]);
        travel.throwOnTravel = true;
        over.travel = travel;
        playerAndPigInCart(over);
        assertThrows(IllegalStateException.class, () -> over.tickEntities(6));
        assertEquals(1, travel.departed);
        assertEquals(1, travel.returnedWith.size(), "뗀 플레이어는 예외가 나도 꼭 한 번 되돌린다");
        assertTrue(travel.arrivedWith.isEmpty());
        AbstractMinecart back = only(over, AbstractMinecart.class);
        assertNotNull(back, "수레도 원래 자리에 되살린다");
        assertSame(back, travel.returnedWith.get(0), "되살린 수레에 다시 태우라고 넘긴다");
        assertSame(back, only(over, Pig.class).vehicle());
    }

    @Test
    void arrivalThrowing_stillSendsDetachedPlayerOn() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END) {
            @Override public boolean addFreshEntity(Entity e) {
                if (e instanceof AbstractMinecart) throw new IllegalStateException("시험: 수레 넣기 실패");
                return super.addFreshEntity(e);
            }
        };
        DimensionTestLevel.link(over, end);
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        var travel = new PlayerCountingTravel(over, end);
        over.travel = travel;
        playerAndPigInCart(over);
        assertThrows(IllegalStateException.class, () -> over.tickEntities(6));
        assertEquals(1, travel.arrivedWith.size(), "탈것 도착이 던져도 뗀 플레이어는 꼭 한 번 보낸다");
        assertNull(travel.arrivedWith.get(0), "탈것을 넣지 못했으니 다시 태우지 않는다");
        assertNotNull(only(end, Pig.class), "돼지 승객은 먼저 건너갔다");
    }

    @Test
    void commandTeleportToOtherDimensionLeavesPassengersBehind() {
        var l = overAndEnd();
        var over = l[0];
        var end = l[1];
        var travel = new SyncTravel(over, end);
        over.travel = travel;
        AbstractMinecart cart = AbstractMinecart.create(over, 6.5, -60, 6.5, AbstractMinecart.Type.RIDEABLE);
        over.addFreshEntity(cart);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.setNoAi(true);
        pig.moveTo(6.5, -60, 6.5, 0, 0);
        over.addFreshEntity(pig);
        assertTrue(pig.startRiding(cart, true));
        assertTrue(cart.teleportToDimension(EndPortalBlock.THE_END, 0.5, -60, 0.5, 0, 0, () -> { }));
        assertNotNull(only(end, AbstractMinecart.class), "수레만 건너간다");
        assertNull(only(end, Pig.class), "바닐라 teleportTo(다른 레벨)는 unRide 만 하고 승객을 데려가지 않는다");
        assertFalse(pig.isRemoved());
        assertNull(pig.vehicle());
    }
}
