// 놓인 블록 엔티티의 살아 있는 Bukkit PersistentDataContainer(Paper Block#getState(false) 의 TileState) — 호출마다 그 칸을 가진 region 스레드에서
// 블록 엔티티 영속 데이터(PublicBukkitValues)를 곧바로 읽고 쓴다(update 불필요). 소유 스레드가 아니면 읽기는 ThreadOwnershipException, 쓰기는 예약.
package org.domi.furnace.compat.paper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.world.BlockPos;

public final class BlockEntityPdc extends LivePdc {

    private final String dimension;
    private final BlockPos pos;
    /** 블록 엔티티가 사라진 뒤의 값(Paper: 떨어져 나간 블록 엔티티의 PDC — 고쳐도 블록에 남지 않는다). */
    private ItemPdc detached;

    public BlockEntityPdc(String dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    @Override
    protected boolean editNow(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        return Compat.furnace().world(dimension).editBlockEntityPersistentDataNow(pos, edit);
    }

    @Override
    protected CompletableFuture<Boolean> editLater(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        return Compat.furnace().world(dimension).editBlockEntityPersistentData(pos, edit);
    }

    @Override
    protected <T> T whenGone(Function<ItemPdc, T> op, boolean write) {
        if (write) {
            org.domi.furnace.compat.nms.NmsCaller.warnOnce("TileState.pdc.gone",
                    "플러그인 {} 이(가) 블록 엔티티가 사라진 칸의 getState(false) 영속 데이터를 고쳤다 — 블록에는 남지 않는다(Paper 와 같다).");
        }
        if (detached == null) detached = new ItemPdc(null);
        return op.apply(detached);
    }
}
