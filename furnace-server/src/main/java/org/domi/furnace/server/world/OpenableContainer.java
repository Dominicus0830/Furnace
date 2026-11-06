// 열고 닫을 때 블록 엔티티의 여는 수를 올리고 내리는 용기의 바탕. 블록 엔티티의 여는 수·소리는 그 블록의 region 스레드에서만 만지므로,
// 지금 스레드가 그 region 이면 바로, 아니면 월드의 runAt 으로 그 region 에 보내 돌린다(MenuBlockContext 를 걸어서).
package org.domi.furnace.server.world;

import org.domi.furnace.server.block.entity.OpenersBlockEntity;
import org.domi.furnace.server.menu.Container;
import org.domi.furnace.server.menu.MenuPlayer;

public abstract class OpenableContainer implements Container {

    /** 다른 region 의 청크에서 작업을 돌릴 길(월드의 runAt). */
    public interface RegionRunner { void runAt(double blockX, double blockZ, java.util.function.Consumer<Region> task); }

    private final RegionRunner runner;

    protected OpenableContainer(RegionRunner runner) { this.runner = runner; }

    /** 여는 수를 세는 블록 엔티티. */
    public abstract OpenersBlockEntity opener();

    protected void withRegion(java.util.function.Consumer<Region> task) {
        OpenersBlockEntity be = opener();
        var p = be.pos();
        ChunkPos cp = ChunkPos.ofBlock(p.x(), p.z());
        Region cur = MenuBlockContext.current();
        if (cur != null && cur.chunkLoaded(cp)) {
            task.accept(cur);
        } else if (runner != null) {
            runner.runAt(p.x(), p.z(), r -> MenuBlockContext.runIn(r, () -> {
                if (r.chunkLoaded(cp) && !be.isRemoved()) task.accept(r);
            }));
        }
    }

    @Override public void startOpen(MenuPlayer player) { withRegion(r -> opener().startOpen(r.level())); }

    @Override public void stopOpen(MenuPlayer player) { withRegion(r -> opener().stopOpen(r.level())); }

    /** Container.stillValidBlockEntity: 그 블록엔티티가 살아 있고 블록 중심에서 8칸 이내. */
    @Override public boolean stillValid(MenuPlayer player) {
        var p = opener().pos();
        return !opener().isRemoved() && player.distanceToSqr(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5) <= 64.0;
    }
}
