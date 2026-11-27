package net.minecraft.world.level.storage.loot;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 전리품 표(바닐라 {@code LootTable}) — 서버 데이터 팩의 표를 이름으로 가리키는 손잡이다. 굴리기({@code getRandomItems})는 서버 다리 하나
 * ({@code ChunkInternals#lootRoll})로 하므로 바닐라와 같은 난수·조건·함수를 서버가 쓴다. 표 객체를 얻는 길(서버 등록부)은 서버 영역 shim 이다.
 * 아직 못 받는 매개변수(damage_source·block_entity)와 스택을 나누지 않는 {@code getRandomItemsRaw}·{@code RandomSource} 를 받는 굴리기는 던진다.
 */
class LootTable {

    public static final LootContextParamSet DEFAULT_PARAM_SET = LootContextParamSets.ALL_PARAMS;
    public static final long RANDOMIZE_SEED = 0L;
    public static final LootTable EMPTY = new LootTable(LootContextParamSets.EMPTY, "minecraft:empty");

    private final LootContextParamSet paramSet;
    private final String furnaceId;

    LootTable(LootContextParamSet paramSet, String furnaceId) {
        this.paramSet = paramSet;
        this.furnaceId = furnaceId;
    }

    /** 호환 모듈 전용: 데이터 팩 표 이름({@code minecraft:chests/simple_dungeon})의 손잡이. */
    public static LootTable furnaceOf(String id, LootContextParamSet paramSet) { return new LootTable(paramSet, id); }

    public LootContextParamSet getParamSet() { return paramSet; }

    public it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> getRandomItems(LootParams params) { return getRandomItems(params, RANDOMIZE_SEED); }

    public it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> getRandomItems(LootParams params, long seed) {
        it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> out = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        getRandomItems(params, seed, out::add);
        return out;
    }

    public void getRandomItems(LootParams params, Consumer<ItemStack> output) { getRandomItems(params, RANDOMIZE_SEED, output); }

    public void getRandomItems(LootParams params, long seed, Consumer<ItemStack> output) {
        var values = params.furnaceValues();
        var tool = (ItemStack) values.get(LootContextParams.TOOL);
        var origin = (net.minecraft.world.phys.Vec3) values.get(LootContextParams.ORIGIN);
        var thisEntity = (net.minecraft.world.entity.Entity) values.get(LootContextParams.THIS_ENTITY);
        var lastPlayer = (net.minecraft.world.entity.Entity) values.get(LootContextParams.LAST_DAMAGE_PLAYER);
        var attacker = (net.minecraft.world.entity.Entity) values.get(LootContextParams.ATTACKING_ENTITY);
        var direct = (net.minecraft.world.entity.Entity) values.get(LootContextParams.DIRECT_ATTACKING_ENTITY);
        var state = (net.minecraft.world.level.block.state.BlockState) values.get(LootContextParams.BLOCK_STATE);
        var damage = (net.minecraft.world.damagesource.DamageSource) values.get(LootContextParams.DAMAGE_SOURCE);
        String damageType = damage == null ? null : damage.typeHolder().getRegisteredName();
        org.domi.furnace.api.entity.Entity damageEntity = damage == null ? null : apiEntity(damage.getEntity());
        org.domi.furnace.api.entity.Entity damageDirect = damage == null ? null : apiEntity(damage.getDirectEntity());
        var blockEntity = (net.minecraft.world.level.block.entity.BlockEntity) values.get(LootContextParams.BLOCK_ENTITY);
        var api = new ChunkInternals.LootRollParams(
                origin == null ? null : new double[] {origin.x, origin.y, origin.z},
                tool == null ? null : tool.furnaceValue(),
                apiEntity(thisEntity), apiEntity(lastPlayer), apiEntity(attacker), apiEntity(direct),
                state == null ? null : state.furnaceStateId(),
                (Float) values.get(LootContextParams.EXPLOSION_RADIUS), (Integer) values.get(LootContextParams.ENCHANTMENT_LEVEL),
                (Boolean) values.get(LootContextParams.ENCHANTMENT_ACTIVE), params.getLuck(),
                damageType, damageEntity, damageDirect,
                blockEntity == null ? null : blockEntity.getBlockState().furnaceStateId(),
                blockEntity == null ? null : blockEntity.furnaceSaveForLoot(),
                params.furnaceDynamicDrops());
        var world = params.getLevel().getWorld().furnaceHandle();
        // 엔티티는 주인 스레드에서만 읽는다 — 첫 엔티티의 주인 스레드에서 굴린다(다른 엔티티가 다른 구역이면 서버가 ThreadOwnershipException)
        org.domi.furnace.api.entity.Entity owner = firstNonNull(api.thisEntity(), api.lastDamagePlayer(), api.attackingEntity(), api.directAttackingEntity(), api.damageEntity(), api.damageDirectEntity());
        java.util.function.Supplier<List<org.domi.furnace.api.item.ItemStack>> roll = () -> org.domi.furnace.compat.nms.NmsChunks.bridge().lootRoll(world, furnaceId, api, seed);
        // 개체가 있으면 그 주인 스레드, 없고 위치가 있으면 그 칸의 청크를 가진 스레드(위치·바이옴 조건이 거짓으로 답하지 않게), 둘 다 없으면 그대로
        List<org.domi.furnace.api.item.ItemStack> rolled = owner != null
                ? org.domi.furnace.compat.nms.NmsEntities.call(world, owner, "LootTable#getRandomItems", roll)
                : origin != null
                        ? org.domi.furnace.compat.nms.NmsOwner.call(world, (int) Math.floor(origin.x), (int) Math.floor(origin.y), (int) Math.floor(origin.z), "LootTable#getRandomItems", roll)
                        : roll.get();
        for (var r : rolled) output.accept(ItemStack.fromFurnace(r, r.amount()));
    }

    private static org.domi.furnace.api.entity.Entity apiEntity(net.minecraft.world.entity.Entity e) { return e == null ? null : e.getBukkitEntity().furnaceHandle(); }

    private static org.domi.furnace.api.entity.Entity firstNonNull(org.domi.furnace.api.entity.Entity... es) {
        for (var e : es) if (e != null) return e;
        return null;
    }
}
