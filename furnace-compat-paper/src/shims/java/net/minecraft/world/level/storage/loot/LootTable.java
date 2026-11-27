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
 * {@code getRandomItemsRaw}·{@code fill(Container, …)} 도 같은 다리를 지난다. {@code RandomSource}·{@code LootContext} 를 받는 굴리기는 아직 던진다.
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
        for (var r : bridgeCall(params, seed, "LootTable#getRandomItems", 0, null)) output.accept(ItemStack.fromFurnace(r, r.amount()));
    }

    /** 바닐라 getRandomItemsRaw(LootParams, Consumer): 표 함수까지 적용하고 스택을 나누지 않는다. */
    public void getRandomItemsRaw(LootParams params, Consumer<ItemStack> output) {
        for (var r : bridgeCall(params, RANDOMIZE_SEED, "LootTable#getRandomItemsRaw", 1, null)) output.accept(r.isEmpty() ? ItemStack.EMPTY : ItemStack.fromFurnace(r, r.amount()));
    }

    /** 바닐라 fill(Container, LootParams, long): 빈 칸에 표 결과를 바닐라 난수 순서대로 채운다(가득 차면 경고하고 멈춤). */
    public void fill(net.minecraft.world.Container container, LootParams params, long seed) {
        int n = container.getContainerSize();
        List<org.domi.furnace.api.item.ItemStack> contents = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ItemStack s = container.getItem(i);
            contents.add(s.isEmpty() ? org.domi.furnace.api.item.ItemStack.EMPTY : s.furnaceValue());
        }
        List<org.domi.furnace.api.item.ItemStack> after = bridgeCall(params, seed, "LootTable#fill", 2, contents);
        for (int i = 0; i < n; i++) {
            if (after.get(i) == contents.get(i)) continue;   // 건드리지 않은 칸
            container.setItem(i, after.get(i).isEmpty() ? ItemStack.EMPTY : ItemStack.fromFurnace(after.get(i), after.get(i).amount()));
        }
    }

    /** kind 0 = getRandomItems(스택 나눔), 1 = getRandomItemsRaw, 2 = fill(contents 가 칸 내용). */
    private List<org.domi.furnace.api.item.ItemStack> bridgeCall(LootParams params, long seed, String what, int kind, List<org.domi.furnace.api.item.ItemStack> contents) {
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
        java.util.function.Supplier<List<org.domi.furnace.api.item.ItemStack>> roll = () -> kind == 0 ? org.domi.furnace.compat.nms.NmsChunks.bridge().lootRoll(world, furnaceId, api, seed)
                : kind == 1 ? org.domi.furnace.compat.nms.NmsChunks.bridge().lootRollRaw(world, furnaceId, api, seed)
                : org.domi.furnace.compat.nms.NmsChunks.bridge().lootFill(world, furnaceId, api, seed, contents);
        // 개체가 있으면 그 주인 스레드, 없고 위치가 있으면 그 칸의 청크를 가진 스레드(위치·바이옴 조건이 거짓으로 답하지 않게), 둘 다 없으면 그대로
        List<org.domi.furnace.api.item.ItemStack> rolled = owner != null
                ? org.domi.furnace.compat.nms.NmsEntities.call(world, owner, what, roll)
                : origin != null
                        ? org.domi.furnace.compat.nms.NmsOwner.call(world, (int) Math.floor(origin.x), (int) Math.floor(origin.y), (int) Math.floor(origin.z), what, roll)
                        : roll.get();
        return rolled;
    }

    private static org.domi.furnace.api.entity.Entity apiEntity(net.minecraft.world.entity.Entity e) { return e == null ? null : e.getBukkitEntity().furnaceHandle(); }

    private static org.domi.furnace.api.entity.Entity firstNonNull(org.domi.furnace.api.entity.Entity... es) {
        for (var e : es) if (e != null) return e;
        return null;
    }
}
