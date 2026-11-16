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

    /** 쓰기는 플레이어 소유 스레드에서(Player.runOnOwner) — 접속이 끊겼으면 IllegalStateException 으로 완료. 읽기는 PlayerInventory 가 내부 동기화돼 있어 즉시. */
    @Override
    public CompletableFuture<Void> set(int slot, ItemStack item) {
        if (slot < 0 || slot >= SIZE) return RegionFuture.wrap(CompletableFuture.failedFuture(new IndexOutOfBoundsException("slot " + slot)));
        if (p.isDisconnected()) return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalStateException("not connected")));
        org.domi.furnace.server.entity.ItemStack converted;
        try {
            converted = ApiConvert.item(item, items());
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        RegionFuture<Void> f = new RegionFuture<>();
        p.runOnOwner(() -> {
            try {
                p.inventory().set(PlayerInventory.windowSlot(slot), converted);
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /**
     * 읽기·fn·쓰기를 플레이어 소유 스레드의 한 작업에서 하고, 쓰기는 서버 칸의 **객체 동일성 비교-교환**(PlayerInventory.replace)으로 한다.
     * 서버 PlayerInventory.add(줍기·/give·화살 줍기 …)는 소유 스레드가 아닌 곳에서도 불리고 runOnOwner 로 직렬화되지 않아, 읽기와 쓰기를
     * 따로 하면 그 사이에 합쳐진 개수를 stale 값으로 덮어 아이템을 잃을 수 있다. 서버 ItemStack 은 불변이라(쓸 때마다 새 객체) 동일성이 바뀌었으면 칸이 바뀐 것이다.
     * 비교가 실패하면 다시 읽어 fn 을 다시 부른다(그래서 fn 은 여러 번 불릴 수 있다).
     */
    @Override
    public CompletableFuture<Boolean> update(int slot, java.util.function.UnaryOperator<ItemStack> fn) {
        if (slot < 0 || slot >= SIZE) return RegionFuture.wrap(CompletableFuture.failedFuture(new IndexOutOfBoundsException("slot " + slot)));
        if (p.isDisconnected()) return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalStateException("not connected")));
        RegionFuture<Boolean> f = new RegionFuture<>();
        p.runOnOwner(() -> {
            try {
                int w = PlayerInventory.windowSlot(slot);
                for (int attempt = 0; attempt < 64; attempt++) {
                    org.domi.furnace.server.entity.ItemStack raw = p.inventory().get(w);
                    ItemStack cur = ApiConvert.item(raw, items());
                    ItemStack next = fn.apply(cur);
                    if (next == null || next == cur || next.equals(cur)) {
                        f.complete(false);
                        return;
                    }
                    if (p.inventory().replace(w, raw, ApiConvert.item(next, items()))) {
                        f.complete(true);
                        return;
                    }
                }
                f.completeExceptionally(new IllegalStateException("inventory slot " + slot + " kept changing"));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    @Override
    public CompletableFuture<List<ItemStack>> contents() {
        List<ItemStack> out = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) out.add(ApiConvert.item(p.inventory().get(PlayerInventory.windowSlot(i)), items()));
        return CompletableFuture.completedFuture(out);
    }
}
