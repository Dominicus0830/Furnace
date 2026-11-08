package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.InventoryView;

/** 플레이어가 창 안을 만진 이벤트. 취소 = 결과 DENY(Bukkit 과 같다). */
public abstract class InventoryInteractEvent extends InventoryEvent implements Cancellable {

    private Result result = Result.DEFAULT;

    public InventoryInteractEvent(InventoryView transaction) { super(transaction); }

    public HumanEntity getWhoClicked() { return getView().getPlayer(); }

    public void setResult(Result newResult) { result = newResult; }

    public Result getResult() { return result; }

    @Override
    public boolean isCancelled() { return getResult() == Result.DENY; }

    @Override
    public void setCancelled(boolean toCancel) { setResult(toCancel ? Result.DENY : Result.ALLOW); }
}
