package org.bukkit.craftbukkit;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.domi.furnace.compat.nms.NmsChunks;
import org.domi.furnace.compat.nms.NmsLootDamage;

/** Paper CraftLootTable — 서버 데이터 팩의 표를 키로 가리킨다. 굴리기는 NMS {@code LootTable} 다리를 지난다. */
public class CraftLootTable implements LootTable {

    private final NamespacedKey key;
    private volatile net.minecraft.world.level.storage.loot.LootTable handle;

    public CraftLootTable(NamespacedKey key, net.minecraft.world.level.storage.loot.LootTable handle) {
        this.handle = handle;
        this.key = key;
    }

    /** 호환 모듈 전용: 표의 형(매개변수 집합)은 처음 쓸 때 서버에서 묻는다. */
    public static CraftLootTable ofKey(NamespacedKey key) { return new CraftLootTable(key, null); }

    public net.minecraft.world.level.storage.loot.LootTable getHandle() {
        var h = handle;
        if (h == null) {
            String set = NmsChunks.bridge().lootParamSet(key.toString());
            h = net.minecraft.world.level.storage.loot.LootTable.furnaceOf(key.toString(), LootContextParamSets.furnaceByName(set));
            handle = h;
        }
        return h;
    }

    @Override
    public Collection<ItemStack> populateLoot(Random random, LootContext context) {
        Preconditions.checkArgument(context != null, "LootContext cannot be null");
        LootParams nmsContext = convertContext(context, random);
        List<net.minecraft.world.item.ItemStack> nmsItems = getHandle().getRandomItems(nmsContext);
        Collection<ItemStack> bukkit = new ArrayList<>(nmsItems.size());
        for (net.minecraft.world.item.ItemStack item : nmsItems) {
            if (item.isEmpty()) continue;
            bukkit.add(CraftItemStack.asBukkitCopy(item));
        }
        return bukkit;
    }

    @Override
    public void fillInventory(Inventory inventory, Random random, LootContext context) {
        Preconditions.checkArgument(inventory != null, "Inventory cannot be null");
        Preconditions.checkArgument(context != null, "LootContext cannot be null");
        LootParams nmsContext = convertContext(context, random);
        // 호환 계층의 Bukkit 인벤토리는 NMS 컨테이너가 아니다 — 칸을 복사해 채우고 바뀐 칸만 되쓴다
        org.bukkit.inventory.ItemStack[] before = inventory.getContents();
        org.domi.furnace.compat.nms.NmsLocalContainer handle = new org.domi.furnace.compat.nms.NmsLocalContainer(before.length, inventory.getHolder());
        for (int i = 0; i < before.length; i++) handle.setItem(i, CraftItemStack.asNMSCopy(before[i]));
        getHandle().fillInventory(handle, nmsContext, random.nextLong(), true);
        for (int i = 0; i < before.length; i++) {
            net.minecraft.world.item.ItemStack now = handle.getItem(i);
            if (!net.minecraft.world.item.ItemStack.matches(now, CraftItemStack.asNMSCopy(before[i]))) inventory.setItem(i, CraftItemStack.asBukkitCopy(now));
        }
    }

    @Override
    public NamespacedKey getKey() { return key; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LootParams convertContext(LootContext context, Random random) {
        Preconditions.checkArgument(context != null, "LootContext cannot be null");
        Location loc = context.getLocation();
        Preconditions.checkArgument(loc.getWorld() != null, "LootContext.getLocation#getWorld cannot be null");
        ServerLevel handle = ((CraftWorld) loc.getWorld()).getHandle();
        LootParams.Builder builder = new LootParams.Builder(handle);
        setMaybe(builder, LootContextParams.ORIGIN, new Vec3(loc.getX(), loc.getY(), loc.getZ()));
        if (getHandle() != net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
            if (context.getLootedEntity() != null) {
                net.minecraft.world.entity.Entity nmsLootedEntity = ((CraftEntity) context.getLootedEntity()).getHandle();
                setMaybe(builder, LootContextParams.THIS_ENTITY, nmsLootedEntity);
                setMaybe(builder, LootContextParams.DAMAGE_SOURCE, NmsLootDamage.generic());
                setMaybe(builder, LootContextParams.ORIGIN, nmsLootedEntity.position());
            }
            if (context.getKiller() != null) {
                net.minecraft.world.entity.player.Player nmsKiller = ((CraftHumanEntity) context.getKiller()).getHandle();
                setMaybe(builder, LootContextParams.ATTACKING_ENTITY, nmsKiller);
                setMaybe(builder, LootContextParams.DAMAGE_SOURCE, NmsLootDamage.playerAttack(nmsKiller));
                setMaybe(builder, LootContextParams.LAST_DAMAGE_PLAYER, nmsKiller);
                // 바닐라는 player.getUseItem()(쓰는 중인 아이템) — 호환 계층 Player 에 그 읽기가 아직 없어 빈 스택(쓰는 중이 아니면 같다)
                setMaybe(builder, LootContextParams.TOOL, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        return builder.create(getHandle().getParamSet());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setMaybe(LootParams.Builder builder, LootContextParam param, Object value) {
        if (getHandle().getParamSet().getRequired().contains(param) || getHandle().getParamSet().getAllowed().contains(param)) {
            builder.withParameter(param, value);
        }
    }

    @Override
    public String toString() { return getKey().toString(); }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LootTable table)) return false;
        return table.getKey().equals(this.getKey());
    }

    @Override
    public int hashCode() { return Objects.hash(key); }
}
