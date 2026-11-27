// 전리품 표가 컨테이너를 채우기 직전(표를 굴린 뒤, 칸에 나누기 전)에 그 컨테이너를 가진 스레드에서 불린다(Paper LootGenerateEvent 와 같은 자리).
// 취소하면 컨테이너는 채워지지 않는다. loot 를 고치면 고친 목록이 칸에 나뉜다(null 은 빈 스택으로 본다).
package org.domi.furnace.api.event.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

public final class LootGenerateEvent extends WorldEvent implements Cancellable {

    private final String lootTable;
    private final Entity entity;
    private final BlockPos containerBlock;
    private final Entity containerEntity;
    private final Object holderToken;
    private final double[] origin;
    private final float luck;
    private final Entity lootedEntity;
    private final Entity killer;
    private final List<ItemStack> loot;
    private final boolean plugin;
    private boolean cancelled;

    /**
     * @param lootTable 표 이름(minecraft:chests/…)
     * @param entity 전리품 문맥의 this_entity(상자를 연 플레이어 등, 없으면 null)
     * @param containerBlock 채우는 컨테이너가 블록이면 그 칸(아니면 null)
     * @param containerEntity 채우는 컨테이너가 엔티티(상자 달린 탈것)면 그 엔티티(아니면 null)
     * @param holderToken 호환 계층이 건 컨테이너 손잡이(그 계층만 풀어 쓴다, 없으면 null)
     * @param origin 문맥 위치 {x, y, z}(없으면 this_entity 위치)
     * @param lootedEntity 문맥의 this_entity, killer 문맥의 공격 엔티티(플레이어일 때만)
     * @param plugin 플러그인이 직접 채우라고 한 것이면 true(바닐라·서버가 연 것은 false)
     */
    public LootGenerateEvent(String world, String lootTable, Entity entity, BlockPos containerBlock, Entity containerEntity, Object holderToken,
                             double[] origin, float luck, Entity lootedEntity, Entity killer, List<ItemStack> loot, boolean plugin) {
        super(world);
        this.lootTable = lootTable;
        this.entity = entity;
        this.containerBlock = containerBlock;
        this.containerEntity = containerEntity;
        this.holderToken = holderToken;
        this.origin = origin == null ? null : origin.clone();
        this.luck = luck;
        this.lootedEntity = lootedEntity;
        this.killer = killer;
        this.loot = new ArrayList<>(loot);
        this.plugin = plugin;
    }

    public String lootTable() { return lootTable; }

    public Entity entity() { return entity; }

    public BlockPos containerBlock() { return containerBlock; }

    public Entity containerEntity() { return containerEntity; }

    public Object holderToken() { return holderToken; }

    public double[] origin() { return origin == null ? null : origin.clone(); }

    public float luck() { return luck; }

    public Entity lootedEntity() { return lootedEntity; }

    public Entity killer() { return killer; }

    /** 고칠 수 있는 전리품 목록(바닐라는 빈 스택도 들어 있다). */
    public List<ItemStack> loot() { return loot; }

    /** Paper setLoot: 비우고 받은 것을 넣는다(null 컬렉션은 비우기만). */
    public void setLoot(Collection<ItemStack> items) {
        loot.clear();
        if (items != null) loot.addAll(items);
    }

    public boolean plugin() { return plugin; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
