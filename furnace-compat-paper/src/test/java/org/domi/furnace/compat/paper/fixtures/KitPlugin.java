package org.domi.furnace.compat.paper.fixtures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** 접속하면 이름·마법·영속 데이터를 단 칼을 주는 흔한 "키트" 플러그인 모양(시험용). 결과·예외·스레드 이름을 남긴다. */
public final class KitPlugin extends JavaPlugin implements Listener {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    public static final AtomicReference<Throwable> ERROR = new AtomicReference<>();
    public static volatile String thread;
    public static volatile int leftovers = -1;

    public static void reset() {
        LOG.clear();
        ERROR.set(null);
        thread = null;
        leftovers = -1;
    }

    @Override
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        thread = Thread.currentThread().getName();
        try {
            var inv = e.getPlayer().getInventory();
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = sword.getItemMeta();
            meta.setDisplayName("§6Kit Sword");
            meta.setLore(new ArrayList<>(List.of("§7first", "§7second")));
            meta.addEnchant(Enchantment.SHARPNESS, 3, true);
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this, "kit"), PersistentDataType.STRING, "starter");
            sword.setItemMeta(meta);
            leftovers = inv.addItem(sword, new ItemStack(Material.BREAD, 5)).size();
            LOG.add("main=" + inv.getItemInMainHand().getType());
            LOG.add("slot0=" + inv.getItem(0));
        } catch (Throwable t) {
            ERROR.set(t);
        }
    }
}
