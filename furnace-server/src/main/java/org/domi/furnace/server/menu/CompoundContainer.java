// 바닐라 CompoundContainer — 용기 두 개를 이어 하나로(더블 상자: 첫째 0..n-1, 둘째 n..).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;

public final class CompoundContainer implements Container {

    private final Container first, second;

    public CompoundContainer(Container first, Container second) {
        this.first = first;
        this.second = second;
    }

    public Container first() { return first; }

    public Container second() { return second; }

    private boolean inFirst(int i) { return i < first.size(); }

    @Override public int size() { return first.size() + second.size(); }

    @Override public ItemStack get(int i) { return inFirst(i) ? first.get(i) : second.get(i - first.size()); }

    @Override public void set(int i, ItemStack s) {
        if (inFirst(i)) first.set(i, s);
        else second.set(i - first.size(), s);
    }

    @Override public ItemStack remove(int i, int n) { return inFirst(i) ? first.remove(i, n) : second.remove(i - first.size(), n); }

    @Override public ItemStack removeNoUpdate(int i) { return inFirst(i) ? first.removeNoUpdate(i) : second.removeNoUpdate(i - first.size()); }

    @Override public void replaceInPlace(int i, ItemStack s) {
        if (inFirst(i)) first.replaceInPlace(i, s);
        else second.replaceInPlace(i - first.size(), s);
    }

    @Override public void setChanged() {
        first.setChanged();
        second.setChanged();
    }

    @Override public boolean stillValid(MenuPlayer player) { return first.stillValid(player) && second.stillValid(player); }

    @Override public void startOpen(MenuPlayer player) {
        first.startOpen(player);
        second.startOpen(player);
    }

    @Override public void stopOpen(MenuPlayer player) {
        first.stopOpen(player);
        second.stopOpen(player);
    }

    @Override public int maxStackSize() { return Math.min(first.maxStackSize(), second.maxStackSize()); }
}
