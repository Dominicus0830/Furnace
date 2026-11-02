// 플레이어 인벤토리의 API 핸들. 서버 PlayerInventory 는 내부 동기화돼 있어 어느 스레드에서든 안전하므로 Future 가 바로 완료된다.
// 클라이언트 동기화는 다음 틱의 창 broadcastChanges 가 한다(서버 코드가 인벤토리를 직접 바꿀 때와 같다).
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;

final class PlayerInventoryHandle implements org.domi.furnace.api.inventory.Inventory {

    /** Bukkit PlayerInventory 와 같은 슬롯 수: 36 + 방어구 4 + 왼손 1. */
    static final int SIZE = 41;

    private final PluginHost host;
    private final Player p;

    PlayerInventoryHandle(PluginHost host, Player p) {
        this.host = host;
        this.p = p;
    }

    private org.domi.furnace.server.world.ItemRegistry items() { return host.core().world().itemRegistry(); }

    @Override
    public String type() { return "minecraft:player"; }

    @Override
    public int size() { return SIZE; }

    @Override
    public CompletableFuture<ItemStack> get(int slot) {
        if (slot < 0 || slot >= SIZE) return CompletableFuture.failedFuture(new IndexOutOfBoundsException("slot " + slot));
        return CompletableFuture.completedFuture(ApiConvert.item(p.inventory().get(PlayerInventory.windowSlot(slot)), items()));
    }

    @Override
    public CompletableFuture<Void> set(int slot, ItemStack item) {
        if (slot < 0 || slot >= SIZE) return CompletableFuture.failedFuture(new IndexOutOfBoundsException("slot " + slot));
        try {
            p.inventory().set(PlayerInventory.windowSlot(slot), ApiConvert.item(item, items()));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<ItemStack>> contents() {
        List<ItemStack> out = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) out.add(ApiConvert.item(p.inventory().get(PlayerInventory.windowSlot(i)), items()));
        return CompletableFuture.completedFuture(out);
    }
}
