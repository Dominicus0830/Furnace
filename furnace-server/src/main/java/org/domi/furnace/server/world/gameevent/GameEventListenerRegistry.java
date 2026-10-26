// 바닐라 EuclideanGameEventListenerRegistry: 청크 구역(16³) 하나의 리스너 목록. 방문 중 등록·해제는 끝난 뒤에 반영한다.
package org.domi.furnace.server.world.gameevent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Vec3;

public final class GameEventListenerRegistry {

    @FunctionalInterface
    public interface ListenerVisitor {
        void visit(GameEventListener listener, Vec3 listenerPos);
    }

    private final List<GameEventListener> listeners = new ArrayList<>();
    private final Set<GameEventListener> listenersToRemove = new HashSet<>();
    private final List<GameEventListener> listenersToAdd = new ArrayList<>();
    private boolean processing;
    private final Runnable onEmpty;

    public GameEventListenerRegistry(Runnable onEmpty) { this.onEmpty = onEmpty; }

    public boolean isEmpty() { return listeners.isEmpty(); }

    public void register(GameEventListener l) {
        if (processing) listenersToAdd.add(l);
        else listeners.add(l);
    }

    public void unregister(GameEventListener l) {
        if (processing) listenersToRemove.add(l);
        else listeners.remove(l);
        if (listeners.isEmpty() && onEmpty != null) onEmpty.run();
    }

    public boolean visitInRangeListeners(ChunkLevel level, GameEvent event, Vec3 pos, GameEventContext ctx, ListenerVisitor visitor) {
        processing = true;
        boolean any = false;
        try {
            Iterator<GameEventListener> it = listeners.iterator();
            while (it.hasNext()) {
                GameEventListener l = it.next();
                if (listenersToRemove.remove(l)) {
                    it.remove();
                } else {
                    Optional<Vec3> lp = getPostableListenerPosition(level, pos, l);
                    if (lp.isPresent()) {
                        visitor.visit(l, lp.get());
                        any = true;
                    }
                }
            }
        } finally {
            processing = false;
        }
        if (!listenersToAdd.isEmpty()) {
            listeners.addAll(listenersToAdd);
            listenersToAdd.clear();
        }
        if (!listenersToRemove.isEmpty()) {
            listeners.removeAll(listenersToRemove);
            listenersToRemove.clear();
        }
        return any;
    }

    /** BlockPos.containing 끼리 거리 제곱이 반경 제곱 이하인가. */
    private static Optional<Vec3> getPostableListenerPosition(ChunkLevel level, Vec3 pos, GameEventListener l) {
        Optional<Vec3> lp = l.getListenerSource().getPosition(level);
        if (lp.isEmpty()) return Optional.empty();
        Vec3 v = lp.get();
        double dx = Math.floor(v.x()) - Math.floor(pos.x()), dy = Math.floor(v.y()) - Math.floor(pos.y()), dz = Math.floor(v.z()) - Math.floor(pos.z());
        double d = dx * dx + dy * dy + dz * dz;
        int r = l.getListenerRadius() * l.getListenerRadius();
        return d > r ? Optional.empty() : lp;
    }
}
