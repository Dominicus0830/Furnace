// 소유 스레드에서만 만질 수 있는 칸(블록 엔티티·엔티티 소지품)의 플러그인 Inventory 핸들. 모든 읽기·쓰기는 Runner 가
// 그 칸을 가진 region 스레드에서 살아 있는 칸을 다시 찾아 실행한다 — 그 사이 칸이 사라졌으면 IllegalStateException 으로 완료.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.domi.furnace.api.item.ItemStack;

final class OwnedInventory implements org.domi.furnace.api.inventory.Inventory {

    /** 살아 있는 칸(소유 스레드에서만 쓴다). */
    interface Slots {
        int size();

        org.domi.furnace.server.entity.ItemStack get(int slot);

        void set(int slot, org.domi.furnace.server.entity.ItemStack stack);
    }

    /** 소유 스레드에서 살아 있는 칸을 찾아 op 를 돌린다. 찾지 못하면 IllegalStateException 으로 완료. */
    interface Runner {
        <T> CompletableFuture<T> run(Function<Slots, T> op);
    }

    private final PluginHost host;
    private final String type;
    private final int size;
    private final Runner runner;

    OwnedInventory(PluginHost host, String type, int size, Runner runner) {
        this.host = host;
        this.type = type;
        this.size = size;
        this.runner = runner;
    }

    private org.domi.furnace.server.world.ItemRegistry items() { return host.core().world().itemRegistry(); }

    @Override
    public String type() { return type; }

    @Override
    public int size() { return size; }

    @Override
    public CompletableFuture<ItemStack> get(int slot) {
        if (slot < 0 || slot >= size) return RegionFuture.wrap(CompletableFuture.failedFuture(new IndexOutOfBoundsException("slot " + slot)));
        return RegionFuture.wrap(runner.run(s -> ApiConvert.item(s.get(slot), items())));
    }

    @Override
    public CompletableFuture<Void> set(int slot, ItemStack item) {
        if (slot < 0 || slot >= size) return RegionFuture.wrap(CompletableFuture.failedFuture(new IndexOutOfBoundsException("slot " + slot)));
        org.domi.furnace.server.entity.ItemStack converted;
        try {
            converted = ApiConvert.item(item, items());
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        return RegionFuture.wrap(runner.run(s -> {
            s.set(slot, converted);
            return null;
        }));
    }

    @Override
    public CompletableFuture<List<ItemStack>> contents() {
        return RegionFuture.wrap(runner.run(s -> {
            List<ItemStack> out = new ArrayList<>(s.size());
            for (int i = 0; i < s.size(); i++) out.add(ApiConvert.item(s.get(i), items()));
            return out;
        }));
    }
}
