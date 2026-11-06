// 바닐라 GameEventDispatcher.post: 알림 반경 안 청크 구역들의 리스너를 돌며, BY_DISTANCE 리스너는 모아 가까운 순으로 처리한다.
// 차이: 다른 region 이 가진 청크의 리스너는 보지 않는다(소유권 — region 경계 근처에서만 바닐라와 다를 수 있다).
package org.domi.furnace.server.world.gameevent;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.world.ChunkColumn;

public final class GameEventDispatcher {

    private GameEventDispatcher() {}

    /** GameEvent.ListenerInfo: 이벤트 지점과 리스너 위치 거리 제곱으로 정렬(같으면 넣은 순서 — 안정 정렬). */
    private record ListenerInfo(GameEvent event, Vec3 source, GameEventContext context, GameEventListener recipient, double distanceToRecipient) {}

    public static void post(ChunkLevel level, GameEvent event, Vec3 pos, GameEventContext ctx) {
        int r = event.notificationRadius;
        int bx = (int) Math.floor(pos.x()), by = (int) Math.floor(pos.y()), bz = (int) Math.floor(pos.z());
        int x0 = (bx - r) >> 4, y0 = (by - r) >> 4, z0 = (bz - r) >> 4;
        int x1 = (bx + r) >> 4, y1 = (by + r) >> 4, z1 = (bz + r) >> 4;
        List<ListenerInfo> byDistance = new ArrayList<>();
        GameEventListenerRegistry.ListenerVisitor visitor = (l, lp) -> {
            if (l.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                double dx = pos.x() - lp.x(), dy = pos.y() - lp.y(), dz = pos.z() - lp.z();
                byDistance.add(new ListenerInfo(event, pos, ctx, l, dx * dx + dy * dy + dz * dz));
            } else {
                l.handleGameEvent(level, event, ctx, pos);
            }
        };
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                ChunkColumn col = level.listenerChunk(cx, cz);
                if (col == null) continue;
                for (int sy = y0; sy <= y1; sy++) {
                    GameEventListenerRegistry reg = col.listenerRegistry(sy, false);
                    if (reg != null) reg.visitInRangeListeners(level, event, pos, ctx, visitor);
                }
            }
        }
        if (!byDistance.isEmpty()) {
            byDistance.sort((a, b) -> Double.compare(a.distanceToRecipient, b.distanceToRecipient));
            for (ListenerInfo i : byDistance) i.recipient.handleGameEvent(level, i.event, i.context, i.source);
        }
    }

    /** LevelChunk.addGameEventListener: 블록 엔티티 리스너를 그 칸 구역에. */
    public static void registerBlockListener(ChunkColumn col, int blockY, GameEventListener l) {
        col.listenerRegistry(blockY >> 4, true).register(l);
    }

    public static void unregisterBlockListener(ChunkColumn col, int blockY, GameEventListener l) {
        GameEventListenerRegistry reg = col.listenerRegistry(blockY >> 4, false);
        if (reg != null) reg.unregister(l);
    }
}
