// 놓인 블록 엔티티의 살아 있는 Bukkit PersistentDataContainer(Paper Block#getState(false) 의 TileState) — 호출마다 그 칸을 가진 region 스레드에서
// 블록 엔티티 영속 데이터(PublicBukkitValues)를 곧바로 읽고 쓴다(update 불필요). 소유 스레드가 아니면 읽기는 ThreadOwnershipException, 쓰기는 예약.
// 그 칸이 다른 종류의 블록으로 바뀌었으면 떨어져 나간 것으로 본다. 같은 종류로 다시 놓인 경우는 구별하지 못한다(서버 블록 엔티티 세대 번호가 필요 — 후속).
package org.domi.furnace.compat.paper;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.world.BlockPos;

public final class BlockEntityPdc extends LivePdc {

    private final String dimension;
    private final BlockPos pos;
    /** 만들 때 그 칸의 블록 종류 — 다른 블록(다른 블록 엔티티)으로 바뀌었으면 옛 블록 엔티티가 떨어져 나간 것으로 본다. */
    private final String blockType;
    /** 블록 엔티티가 사라진 뒤의 값(Paper: 떨어져 나간 블록 엔티티의 PDC — 고쳐도 블록에 남지 않는다). */
    private ItemPdc detached;

    public BlockEntityPdc(String dimension, BlockPos pos, String blockType) {
        this.dimension = dimension;
        this.pos = pos;
        this.blockType = blockType;
    }

    /** 소유 스레드에서: 그 칸이 아직 같은 종류의 블록인가(아니면 ThreadOwnershipException). */
    private boolean sameBlock() {
        return Compat.furnace().world(dimension).blockNow(pos).map(b -> b.type().equals(blockType)).orElse(false);
    }

    @Override
    protected boolean editNow(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        if (!sameBlock()) return false;
        return Compat.furnace().world(dimension).editBlockEntityPersistentDataNow(pos, edit);
    }

    @Override
    protected CompletableFuture<Boolean> editLater(Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        // 예약된 쓰기도 도는 때(소유 스레드)에 같은 블록인지 본다
        return Compat.furnace().world(dimension).editBlockEntityPersistentData(pos, c -> {
            if (sameBlock()) edit.accept(c);
        });
    }

    @Override
    protected <T> T whenGone(Function<ItemPdc, T> op, boolean write) {
        if (write) {
            org.domi.furnace.compat.nms.NmsCaller.warnOnce("TileState.pdc.gone", "compat.tilestate-pdc-gone");
        }
        if (detached == null) detached = new ItemPdc(null);
        return op.apply(detached);
    }
}
