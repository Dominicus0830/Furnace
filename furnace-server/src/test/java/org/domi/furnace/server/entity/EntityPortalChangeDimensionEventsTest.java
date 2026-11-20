// 차원문으로 건너는 플레이어가 아닌 엔티티의 Paper Entity#changeDimension 이벤트(EntityTeleportEvent·EntityPortalExitEvent, 관문이면 그 칸):
// 듣는 처리기가 있으면 목적지를 먼저 정해 돌아와(엔티티를 따라가는 대기열 — 다음 틱) 떠나기 전에 부르고, 취소하면 그대로 남고, 바꾼 도착 위치·속도로 간다.
// 듣는 처리기가 없으면 예전처럼 곧바로 건넌다. 서버 경로는 같은 스레드에서 끝내는 대역 이동(SyncTravel)으로 본다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.EndPortalBlock;
import org.domi.furnace.server.plugin.GameEvents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityPortalChangeDimensionEventsTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    /** 목적지를 같은 스레드에서 바로 정해 arrive 한다(이동 시작 횟수를 센다). */
    static final class SyncTravel implements LevelServices.DimensionTravel {
        final DimensionTestLevel source, target;
        int travels;

        SyncTravel(DimensionTestLevel source, DimensionTestLevel target) {
            this.source = source;
            this.target = target;
        }

        @Override public boolean isLevelEnabled(String dimension) { return true; }

        @Override
        public void travel(PortalTravel plan, BiConsumer<ChunkLevel, DimensionTransition> arrive) {
            travels++;
            ChunkLevel lvl = plan.targetDimension().equals(target.dimension) ? target : source;
            arrive.accept(lvl, plan.complete().apply(lvl));
        }

        @Override public void runAtSource(int blockX, int blockZ, Consumer<ChunkLevel> task) { task.accept(source); }
    }

    /** 이벤트를 듣는 대역: 받은 것을 적고, answer 로 답한다. */
    static final class Listening implements GameEvents {
        final List<Object[]> calls = new ArrayList<>();   // {to, gateway, inPortal}
        java.util.function.UnaryOperator<Arrival> answer = a -> a;

        @Override public boolean listensEntityChangeDimension() { return true; }

        @Override
        public Arrival entityChangeDimension(Entity e, Arrival to, Pos gateway, boolean inPortal) {
            calls.add(new Object[] {to, gateway, inPortal});
            return answer.apply(to);
        }
    }

    private static <T extends Entity> T only(DimensionTestLevel level, Class<T> c) {
        return level.entities().tickSnapshot().stream().filter(c::isInstance).map(c::cast).filter(e -> !e.isRemoved()).findFirst().orElse(null);
    }

    private record Setup(DimensionTestLevel over, DimensionTestLevel end, SyncTravel travel, Pig pig) {}

    private static Setup pigOnEndPortal(GameEvents events) {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        var travel = new SyncTravel(over, end);
        over.travel = travel;
        over.events = events;
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.goalSelector().removeAllGoals(g -> true);   // 걸어 다니지 않게(NoAI 몹은 move 하지 않아 포털에 들어가지 못한다)
        pig.moveTo(2.5, -59.8, 2.5, 30, 10);
        over.addFreshEntity(pig);
        return new Setup(over, end, travel, pig);
    }

    @Test
    void withoutListenersThePigCrossesAtOnce() {
        var s = pigOnEndPortal(GameEvents.NONE);
        s.over.tickEntities(3);
        assertEquals(1, s.travel.travels);
        assertTrue(s.pig.isRemoved());
        assertNotNull(only(s.end, Pig.class));
    }

    @Test
    void listenerSeesTheDestinationBeforeThePigLeaves() {
        var ev = new Listening();
        var s = pigOnEndPortal(ev);
        s.over.tickEntities(3);
        assertEquals(1, s.travel.travels, "목적지(엔드 단상)를 먼저 정했다");
        assertFalse(s.pig.isRemoved(), "이벤트 전에는 떠나지 않는다");
        assertTrue(ev.calls.isEmpty());
        s.over.runEntityTasks();   // 다음 틱: 엔티티를 따라가는 대기열로 돌아온다
        assertEquals(1, ev.calls.size());
        var to = (GameEvents.Arrival) ev.calls.get(0)[0];
        assertEquals(EndPortalBlock.THE_END, to.dimension());
        assertEquals(100.5, to.x(), 1e-9, "엔드 단상(100, 49, 0) 가운데");
        assertEquals(s.pig.xRot(), to.pitch(), "pitch 는 지금 xRot");
        assertNull(ev.calls.get(0)[1], "관문이 아니다");
        assertEquals(true, ev.calls.get(0)[2], "포털 처리 중 — EntityPortalExitEvent 도 부른다");
        assertTrue(s.pig.isRemoved());
        Pig arrived = only(s.end, Pig.class);
        assertNotNull(arrived);
        assertEquals(s.pig.uuid(), arrived.uuid());
        assertEquals(100.5, arrived.x(), 1e-9);
    }

    @Test
    void cancelKeepsThePigInThePortal() {
        var ev = new Listening();
        ev.answer = a -> null;
        var s = pigOnEndPortal(ev);
        s.over.tickEntities(3);
        s.over.runEntityTasks();
        assertEquals(1, ev.calls.size());
        assertFalse(s.pig.isRemoved(), "취소하면 남는다(Paper changeDimension 이 null)");
        assertEquals(1, s.travel.travels, "떠나는 이동은 시작하지 않았다");
        assertNull(only(s.end, Pig.class));
    }

    @Test
    void redirectedArrivalAndVelocityAreUsed() {
        var ev = new Listening();
        ev.answer = a -> new GameEvents.Arrival(a.dimension(), 120.5, 60, 4.5, 45f, 0f, 0.0, 0.5, 0.0);
        var s = pigOnEndPortal(ev);
        s.over.tickEntities(3);
        s.over.runEntityTasks();
        Pig arrived = only(s.end, Pig.class);
        assertNotNull(arrived);
        assertEquals(120.5, arrived.x(), 1e-9);
        assertEquals(60.0, arrived.y(), 1e-9);
        assertEquals(4.5, arrived.z(), 1e-9);
        assertEquals(45f, arrived.yRot(), "도착 yRot 은 바뀐 yaw");
        assertEquals(s.pig.xRot(), arrived.xRot(), "xRot 은 바닐라 placement 대로 떠날 때 값");
        assertEquals(0.5, arrived.vy(), 1e-9, "EntityPortalExitEvent 의 after 속도");
        assertEquals(2, s.travel.travels);
    }

    @Test
    void pigKilledBeforeReturningDoesNotLeave() {
        var ev = new Listening();
        var s = pigOnEndPortal(ev);
        s.over.tickEntities(3);
        s.pig.discard();
        s.over.runEntityTasks();
        assertTrue(ev.calls.isEmpty(), "사라진 엔티티는 이벤트도 없다");
        assertNull(only(s.end, Pig.class));
    }

    @Test
    void endGatewayEntryIsReportedOnlyForAGatewayBlockEntity() {
        var level = new DimensionTestLevel(EndPortalBlock.THE_END);
        Pos at = new Pos(1, -60, 1);
        level.setBlock(at, B.defaultState("minecraft:end_gateway"), 3);
        assertInstanceOf(org.domi.furnace.server.block.entity.EndGatewayBlockEntity.class, level.getBlockEntity(at));
        Pig pig = new Pig(level, level.nextEntityId());
        pig.moveTo(1.5, -60, 1.5, 0, 0);
        level.addFreshEntity(pig);
        assertNull(pig.endGatewayEntry(), "포털 처리 중이 아니다");
        pig.portalProcess = new PortalProcessor((Portal) B.behavior(B.defaultState("minecraft:end_gateway")), at);
        assertEquals(at, pig.endGatewayEntry());
        pig.portalProcess = new PortalProcessor((Portal) B.behavior(B.defaultState("minecraft:end_portal")), at);
        assertNull(pig.endGatewayEntry(), "엔드 포털은 관문 이벤트가 아니다");
    }

    @Test
    void redirectedTransitionMovesOnlyAnArrivalTicket() {
        var lvl = new DimensionTestLevel(EndPortalBlock.THE_END);
        var end = DimensionTransition.portal(lvl, 100.5, 49, 0.5, 0, 0, 0, 90f, 0f);
        assertEquals(new Pos(7, 50, -3), end.redirected(lvl, 7.2, 50, -2.5, 0, 0, 0, 0f, 0f).ticketPos(), "PLACE_PORTAL_TICKET 은 도착 칸");
        var found = new DimensionTransition(lvl, 3.5, 70, 3.5, 0, 0, 0, 0f, 0f, true, new Pos(3, 70, 2), false);
        assertEquals(new Pos(3, 70, 2), found.redirected(lvl, 50, 70, 50, 0, 0, 0, 0f, 0f).ticketPos(), "찾은 지옥문 칸 티켓은 그대로");
        assertTrue(found.redirected(lvl, 50, 70, 50, 0, 0, 0, 0f, 0f).portalSound(), "도착 뒤 소리도 그대로");
    }
}
