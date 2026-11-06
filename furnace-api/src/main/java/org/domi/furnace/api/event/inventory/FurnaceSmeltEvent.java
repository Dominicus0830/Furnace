// 화로가 재료 하나를 구워 결과 칸에 넣기 직전 — 화로의 region 스레드에서 불린다.
// 취소하면 재료도 그대로, 결과도 생기지 않는다(진행도는 처음부터). setResult 로 나올 아이템을 바꿀 수 있다 — 결과 칸의 아이템과 합칠 수 없으면 굽지 않는다.
package org.domi.furnace.api.event.inventory;

import java.util.Objects;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.block.BlockEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

public final class FurnaceSmeltEvent extends BlockEvent implements Cancellable {

    private final String blockType;
    private final ItemStack source;
    private ItemStack result;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public FurnaceSmeltEvent(String world, BlockPos pos, String blockType, ItemStack source, ItemStack result) {
        super(world, pos);
        this.blockType = blockType;
        this.source = source;
        this.result = result;
    }

    public String blockType() { return blockType; }

    /** 구울 재료(재료 칸 전체). */
    public ItemStack source() { return source; }

    public ItemStack result() { return result; }

    /** EMPTY 면 재료만 줄고 아무것도 나오지 않는다. 서버가 모르는 아이템이면 원래 결과. */
    public void setResult(ItemStack result) { this.result = Objects.requireNonNull(result, "result"); }
}
