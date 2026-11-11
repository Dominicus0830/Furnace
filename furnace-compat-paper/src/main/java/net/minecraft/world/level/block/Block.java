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
     * 호환 모듈 전용 — 블록 shim 표가 만든다. 상태는 바닐라 StateDefinition 순서(속성 이름순, 뒤 속성이 빨리 바뀜)로 만들고
     * {@code baseStateId + 순번} 이 서버의 상태 번호와 같은지 확인한다(어긋나면 IllegalStateException).
     */
    public Block(BlockType type, org.domi.furnace.api.world.BlockTypes types) {
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
                throw new IllegalStateException("상태 번호가 바닐라 순서와 다르다: " + type.id() + names + " 서버=" + id + " 계산=" + (baseStateId + local));
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

    /** 바닐라: null 이면 0. */
    public static int getId(BlockState state) { return state == null ? 0 : state.furnaceStateId(); }

    /** 바닐라: 모르는 번호면 공기의 기본 상태. */
    public static BlockState stateById(int id) {
        BlockState s = org.domi.furnace.compat.nms.NmsBlocks.state(id);
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    public static Block byItem(Item item) { return org.domi.furnace.compat.nms.NmsBlocks.byItem(item); }

    public final BlockState defaultBlockState() { return defaultState; }

    public StateDefinition<Block, BlockState> getStateDefinition() { return stateDefinition; }

    @Override
    public Item asItem() { return Item.byKey(itemKey); }

    /** 바닐라 Util.makeDescriptionId("block", 키). */
    public String getDescriptionId() { return "block." + key.getNamespace() + "." + key.getPath().replace('/', '.'); }

    public Holder.Reference<Block> builtInRegistryHolder() {
        return BuiltInRegistries.BLOCK.getHolder(key).orElseThrow();
    }

    @Override
    public String toString() { return "Block{" + key + "}"; }
}
