package org.domi.furnace.compat.paper;

import java.net.InetSocketAddress;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Furnace 플레이어 위의 Bukkit Player. 접속이 끊긴 뒤에도 객체는 남지만 isOnline() 이 false 다. */
final class CompatPlayer extends CompatLivingEntity implements Player {

    private final org.domi.furnace.api.entity.Player handle;

    CompatPlayer(org.domi.furnace.api.entity.Player handle) {
        super(handle);
        this.handle = handle;
    }

    org.domi.furnace.api.entity.Player handle() { return handle; }

    private CompatPlayerInventory inventory;

    @Override
    public org.bukkit.inventory.PlayerInventory getInventory() {
        CompatPlayerInventory i = inventory;
        if (i == null) inventory = i = new CompatPlayerInventory(this);
        return i;
    }

    @Override
    public org.bukkit.inventory.ItemStack getItemInHand() { return getInventory().getItemInHand(); }

    @Override
    public void setItemInHand(org.bukkit.inventory.ItemStack item) { getInventory().setItemInHand(item); }

    @Override
    public void updateInventory() {}

    @Override
    public String getName() { return handle.name(); }

    @Override
    public String getDisplayName() { return Compat.legacy(handle.displayName()); }

    @Override
    public boolean isOnline() { return handle.isOnline(); }

    @Override
    public Player getPlayer() { return handle.isOnline() ? this : null; }

    @Override
    public InetSocketAddress getAddress() { return handle.address(); }

    @Override
    public void kickPlayer(String message) { handle.kick(message == null ? org.domi.furnace.api.text.Text.empty() : Compat.text(message)); }

    @Override
    public int getLevel() { return handle.level(); }

    @Override
    public void setLevel(int level) { handle.setLevel(level); }

    @Override
    public void giveExp(int amount) { handle.giveExperience(amount); }

    @Override
    public int getFoodLevel() { return handle.foodLevel(); }

    @Override
    public void setFoodLevel(int value) { handle.setFoodLevel(value); }

    @Override
    public boolean performCommand(String command) { return handle.performCommand(command); }

    @Override
    public int getPing() { return handle.ping(); }

    @Override
    public String getLocale() { return handle.locale() == null ? "en_us" : handle.locale().toString().toLowerCase(Locale.ROOT); }

    @Override
    public GameMode getGameMode() { return GameMode.valueOf(handle.gameMode().name()); }

    @Override
    public void setGameMode(GameMode mode) {
        handle.setGameMode(org.domi.furnace.api.entity.GameMode.valueOf(mode.name()));
    }

    // ---- CommandSender / Permissible

    @Override
    public void sendMessage(String message) { handle.sendMessage(Compat.text(message)); }

    @Override
    public void sendMessage(String... messages) {
        for (String m : messages) sendMessage(m);
    }

    @Override
    public boolean isOp() { return handle.isOp(); }

    @Override
    public void setOp(boolean value) { Compat.furnace().offlinePlayer(handle.uniqueId()).setOpLevel(value ? 4 : 0); }

    @Override
    public boolean isPermissionSet(String name) { return handle.isPermissionSet(name); }

    @Override
    public boolean hasPermission(String name) { return handle.hasPermission(name); }

    @Override
    public String toString() { return "CraftPlayer{name=" + handle.name() + "}"; }
}
