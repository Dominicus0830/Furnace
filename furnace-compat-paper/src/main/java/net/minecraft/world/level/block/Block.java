package net.minecraft.world.level.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.compat.nms.NmsBlockProperties;

/**
 * 블록 종류(Mojang 이름 shim, N0). 블록마다 객체 하나, 상태마다 객체 하나다 — 서버 블록 종류 표({@code Furnace.blockTypes()})에서
 * 만들고 바닐라 상태 번호와 맞는지 만들 때 확인한다. {@code getId}·{@code stateById}(모르는 번호면 공기)·toString {@code Block{minecraft:stone}}·
 * {@code getDescriptionId} 는 바닐라 {@code dfy} 와 같다. 블록 동작(설치·틱·상호작용)은 서버 안에 있어 두지 않았다.
 */
public class Block extends BlockBehaviour implements ItemLike {

    private final ResourceLocation key;
    private final int baseStateId;
    private final List<Property<?>> properties;
    private final int[] strides;
    private final BlockState[] states;
    private final StateDefinition<Block, BlockState> stateDefinition;
    private final BlockState defaultState;
    private final String itemKey;
    private final boolean air;

    /**
     * 생성기 전용 생성자(T-231): 블록 shim 표({@link org.domi.furnace.compat.nms.NmsBlocks})가 바닐라 클래스(종류 표)로 만든다 — 표지에 블록 종류가 실려 온다.
     * 바닐라 하위 클래스(생성 shim)는 이 생성자로 올라온다.
     */
    protected Block(org.domi.furnace.compat.nms.ShimCtor tag) {
        this(org.domi.furnace.compat.nms.NmsBlocks.kindOf(tag));
    }

    private Block(org.domi.furnace.compat.nms.NmsBlocks.Kind kind) { this(kind.type(), kind.types()); }

    /**
     * 상태는 바닐라 StateDefinition 순서(속성 이름순, 뒤 속성이 빨리 바뀜)로 만들고
     * {@code baseStateId + 순번} 이 서버의 상태 번호와 같은지 확인한다(어긋나면 IllegalStateException).
     */
    private Block(BlockType type, org.domi.furnace.api.world.BlockTypes types) {
        this.key = ResourceLocation.parse(type.id());
        this.itemKey = type.item();
        this.air = type.id().equals("minecraft:air") || type.id().equals("minecraft:cave_air") || type.id().equals("minecraft:void_air");
        List<BlockType.PropertyInfo> infos = type.properties();
        this.properties = new ArrayList<>(infos.size());
        Map<String, Property<?>> byName = new LinkedHashMap<>();
        for (BlockType.PropertyInfo info : infos) {
            Property<?> p = NmsBlockProperties.of(info);
            properties.add(p);
            byName.put(p.getName(), p);
        }
        int n = properties.size();
        this.strides = new int[n];
        int count = 1;
        for (int i = n - 1; i >= 0; i--) {
            strides[i] = count;
            count *= properties.get(i).getPossibleValues().size();
        }
        this.states = new BlockState[count];
        Map<String, String> first = new LinkedHashMap<>();
        for (BlockType.PropertyInfo info : infos) first.put(info.name(), info.values().get(0));
        this.baseStateId = types.stateId(new org.domi.furnace.api.world.BlockData(type.id(), first));
        int[] idx = new int[n];
        for (int local = 0; local < count; local++) {
            Map<Property<?>, Comparable<?>> values = new LinkedHashMap<>();
            Map<String, String> names = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                Property<?> p = properties.get(i);
                Comparable<?> v = valueAt(p, idx[i]);
                values.put(p, v);
                names.put(p.getName(), nameOf(p, v));
            }
            int id = types.stateId(new org.domi.furnace.api.world.BlockData(type.id(), names));
            if (id != baseStateId + local) {
                throw new IllegalStateException("state ids differ from the vanilla order: " + type.id() + names + " server=" + id + " computed=" + (baseStateId + local));
            }
            states[local] = new BlockState(this, values, id);
            for (int i = n - 1; i >= 0; i--) {
                if (++idx[i] < properties.get(i).getPossibleValues().size()) break;
                idx[i] = 0;
            }
        }
        this.stateDefinition = new StateDefinition<>(this, byName, List.of(states));
        this.defaultState = states[types.stateId(type.defaultData()) - baseStateId];
    }

    private static Comparable<?> valueAt(Property<?> p, int i) {
        int k = 0;
        for (Comparable<?> v : p.getPossibleValues()) if (k++ == i) return v;
        throw new IndexOutOfBoundsException(i);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String nameOf(Property p, Comparable<?> v) { return p.getName((Comparable) v); }

    private static int indexOf(Property<?> p, Comparable<?> v) {
        int k = 0;
        for (Comparable<?> x : p.getPossibleValues()) {
            if (x.equals(v)) return k;
            k++;
        }
        return -1;
    }

    /** 호환 모듈 전용 — 상태에서 한 속성만 바꾼 상태. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockState furnaceStateWith(BlockState from, Property<?> property, Comparable<?> value) {
        int i = 0;
        while (properties.get(i) != property) i++;   // setValue 가 이미 동일 객체로 있는지 확인했다
        int local = from.furnaceStateId() - baseStateId;
        int cur = indexOf(property, from.getValue((Property) property));
        int next = indexOf(property, value);
        return states[local + (next - cur) * strides[i]];
    }

    /** 호환 모듈 전용 — 레지스트리 키(minecraft:stone). */
    public ResourceLocation furnaceKey() { return key; }

    /** 호환 모듈 전용 — 이 블록의 상태 번호 범위 시작. */
    public int furnaceBaseStateId() { return baseStateId; }

    /** 호환 모듈 전용 — 이 블록의 상태 수. */
    public int furnaceStateCount() { return states.length; }

    /** 호환 모듈 전용 — 이 블록의 상태(번호 = 시작 + 순번). */
    public BlockState furnaceState(int local) { return states[local]; }

    /** 호환 모듈 전용 — 공기 종류(air·cave_air·void_air)인가. */
    public boolean furnaceIsAir() { return air; }

    /** 바닐라: 블록 상태 번호 표(팔레트 컨테이너의 블록 표). 서버 상태 표를 비추는 고정 표다(더할 수 없다). */
    public static final net.minecraft.core.IdMapper<BlockState> BLOCK_STATE_REGISTRY = net.minecraft.core.IdMapper.furnaceFixed(
            BlockState::furnaceStateId, org.domi.furnace.compat.nms.NmsBlocks::state, org.domi.furnace.compat.nms.NmsBlocks::stateCount);

    /** 바닐라: null 이면 0. */
    public static int getId(BlockState state) { return state == null ? 0 : state.furnaceStateId(); }

    /** 바닐라: 모르는 번호면 공기의 기본 상태. */
    public static BlockState stateById(int id) {
        BlockState s = org.domi.furnace.compat.nms.NmsBlocks.state(id);
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    /**
     * 바닐라 Block.updateFromNeighbourShapes(state, level, pos): 서·동·북·남·아래·위 이웃마다 updateShape 를 차례로 건 상태(서버 블록 동작 — updateShape 의
     * 부수 효과, 예: 물에 잠긴 블록의 유체 틱 예약도 그대로). 그 칸의 주인 region 에서 돈다({@code NmsEffects} 값 호출 규칙). 서버 월드가 아닌 레벨은 UnsupportedNmsCall.
     */
    public static BlockState updateFromNeighbourShapes(BlockState state, net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos) {
        return stateById(org.domi.furnace.compat.nms.NmsEffects.call(level, pos, org.domi.furnace.compat.nms.NmsEffects.FROM_NEIGHBOUR_SHAPES,
                state.furnaceStateId(), 0, 0, "Block#updateFromNeighbourShapes"));
    }

    /**
     * 바닐라 getStateForPlacement(ctx): 그 문맥으로 놓을 상태(놓을 수 없으면 null). 서버 블록 동작의 같은 메서드를 문맥·레벨 객체의 가상 메서드로 돌린다
     * (FAWE 처럼 레벨을 상속해 편집 중인 내용을 답하게 했으면 그 답으로 — {@code NmsPlacement}). 부른 스레드에서 읽는다.
     */
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return org.domi.furnace.compat.nms.NmsPlacement.stateForPlacement(this, ctx);
    }

    public static Block byItem(Item item) { return org.domi.furnace.compat.nms.NmsBlocks.byItem(item); }

    public final BlockState defaultBlockState() { return defaultState; }

    /** 바닐라: 폭발 저항(블록 속성). */
    public float getExplosionResistance() { return org.domi.furnace.compat.nms.NmsBlockStates.table().explosionResistance()[defaultState.furnaceStateId()]; }

    /** 바닐라: 마찰(블록 속성, 기본 0.6). */
    public float getFriction() { return org.domi.furnace.compat.nms.NmsBlockStates.table().friction()[defaultState.furnaceStateId()]; }

    /** 바닐라: 기본 상태의 지도 색. */
    public net.minecraft.world.level.material.MapColor defaultMapColor() {
        return net.minecraft.world.level.material.MapColor.furnaceByCol(org.domi.furnace.compat.nms.NmsBlockStates.table().mapColor()[defaultState.furnaceStateId()]);
    }

    /** 바닐라: 모양이 한 칸 블록 전체를 덮는가. */
    public static boolean isShapeFullBlock(net.minecraft.world.phys.shapes.VoxelShape shape) { return net.minecraft.world.phys.shapes.Shapes.furnaceIsFullBlock(shape); }

    private static final java.util.Set<String> PAPER_INDESTRUCTIBLE = java.util.Set.of("minecraft:barrier", "minecraft:bedrock", "minecraft:end_portal_frame",
            "minecraft:end_portal", "minecraft:end_gateway", "minecraft:command_block", "minecraft:repeating_command_block", "minecraft:chain_command_block",
            "minecraft:structure_block", "minecraft:jigsaw");

    /**
     * Paper: 이 블록을 부술 수 있는가 — 기본 설정(unsupported-settings.allow-permanent-block-break-exploits 꺼짐)의 Paper 1.21.1 목록 그대로(N2b-6).
     * Furnace 에는 그 설정이 없어 늘 기본값이다.
     */
    public final boolean isDestroyable() { return !PAPER_INDESTRUCTIBLE.contains(key.toString()); }

    public StateDefinition<Block, BlockState> getStateDefinition() { return stateDefinition; }

    /** 바닐라 getCloneItemStack(level, pos, state): 이 블록의 아이템 하나(가운데 클릭). 블록마다 다르게 정한 종류(배너 무늬 등)는 이 shim 에 없다. */
    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, BlockState state) {
        return new net.minecraft.world.item.ItemStack(this);
    }

    @Override
    public Item asItem() { return Item.byKey(itemKey); }

    /**
     * Paper {@code Block#getExpDrop(state, level, pos, tool, dropExperience)}: 부서질 때 줄 경험치. dropExperience 가 거짓이거나 경험치를 안 주는 블록이면 0,
     * 아니면 바닐라 {@code tryDropExperience} 의 양 — 그 레벨의 난수로 표본을 뽑고 섬세한 손길이면 0(경험치를 떨구지는 않는다). 그 칸의 주인 스레드에서
     * 돈다({@link org.domi.furnace.compat.nms.NmsOwner} — 난수 차례가 바닐라와 같다). Paper 처럼 state 로 계산한다(이 블록이 아니라 state 의 블록 —
     * Paper 에서도 둘이 다르게 부르는 일은 없다). 로드되지 않은 칸은 IllegalStateException(NMS shim 3번 묶음).
     */
    public int getExpDrop(BlockState state, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                          net.minecraft.world.item.ItemStack tool, boolean dropExperience) {
        if (!dropExperience) return 0;
        var w = level.getWorld().furnaceHandle();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (!level.hasChunk(x >> 4, z >> 4)) throw org.domi.furnace.compat.nms.NmsChunks.notLoaded(w, x >> 4, z >> 4, "Block#getExpDrop");
        var b = org.domi.furnace.compat.nms.NmsChunks.bridge();
        int id = state.furnaceStateId();
        var t = (tool == null ? net.minecraft.world.item.ItemStack.EMPTY : tool).furnaceValue();
        return org.domi.furnace.compat.nms.NmsOwner.call(w, x, y, z, "Block#getExpDrop", () -> b.expDropNow(w, id, x, y, z, t));
    }

    /**
     * 바닐라: 블록을 놓은 뒤 블록 종류별 뒷일(침대 머리 놓기, 이름표 붙은 블록 엔티티 등). state 의 블록 종류의 서버 코드(바닐라 이식)를 그 칸 주인 스레드에서
     * 부른다({@link org.domi.furnace.compat.nms.NmsOwner} 규칙, NMS shim 5d — Axiom 블록 놓기). placer 는 null 이거나 플레이어여야 한다(다른 생물이 놓는 일은 Furnace 에
     * 없다 — UnsupportedNmsCall). 로드되지 않은 칸은 IllegalStateException.
     */
    public void setPlacedBy(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack itemStack) {
        org.domi.furnace.api.entity.Player who = null;
        if (placer != null) {
            if (!(placer instanceof net.minecraft.server.level.ServerPlayer sp)) {
                throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Block#setPlacedBy", "only a player (or no one) can be the placer here (got " + placer + ")");
            }
            who = sp.getBukkitEntity().furnaceHandle();
        }
        var item = (itemStack == null ? net.minecraft.world.item.ItemStack.EMPTY : itemStack).furnaceValue();
        org.domi.furnace.compat.nms.NmsEffects.place(level, pos, org.domi.furnace.api.internal.ChunkInternals.PLACE_SET_PLACED_BY, state.furnaceStateId(),
                who, item, false, "Block#setPlacedBy");
    }

    /** 바닐라 Util.makeDescriptionId("block", 키). */
    public String getDescriptionId() { return "block." + key.getNamespace() + "." + key.getPath().replace('/', '.'); }

    public Holder.Reference<Block> builtInRegistryHolder() {
        return BuiltInRegistries.BLOCK.getHolder(key).orElseThrow();
    }

    @Override
    public String toString() { return "Block{" + key + "}"; }
}
