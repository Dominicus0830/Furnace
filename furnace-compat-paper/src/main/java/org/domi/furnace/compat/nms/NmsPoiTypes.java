package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.api.internal.BlockStateInternals;

/**
 * 관심 지점 종류 레지스트리 shim(NMS shim 5d). 번호·이름·최대 티켓·유효 거리·상태 대응은 서버의 바닐라 표({@link BlockStateInternals#poiTypes()},
 * {@link BlockStateInternals#poiType(int)})다. 기본값 없는 레지스트리다.
 */
public final class NmsPoiTypes {

    private NmsPoiTypes() {}

    private static final class Built {
        final NmsMappedRegistry<PoiType> registry = new NmsMappedRegistry<>(Registries.POINT_OF_INTEREST_TYPE);
        final List<Holder.Reference<PoiType>> byIndex = new ArrayList<>();

        Built() {
            BlockStateInternals br = NmsBlockStates.bridge();
            List<BlockStateInternals.PoiTypeRow> rows = br.poiTypes();
            List<Set<BlockState>> states = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) states.add(new HashSet<>());
            int n = NmsBlocks.stateCount();
            for (int id = 0; id < n; id++) {
                int t = br.poiType(id);
                if (t >= 0) states.get(t).add(NmsBlocks.state(id));
            }
            for (int i = 0; i < rows.size(); i++) {
                var r = rows.get(i);
                byIndex.add(registry.add(ResourceLocation.parse(r.name()), new PoiType(states.get(i), r.maxTickets(), r.validRange())));
            }
        }
    }

    private static volatile Built built;

    private static Built built() {
        Built b = built;
        if (b == null) {
            synchronized (NmsPoiTypes.class) {
                b = built;
                if (b == null) built = b = new Built();
            }
        }
        return b;
    }

    public static Registry<PoiType> registry() { return built().registry; }

    /** 그 상태의 종류 손잡이(레지스트리의 것), 없으면 null. */
    public static Holder<PoiType> forState(BlockState state) {
        int t = NmsBlockStates.bridge().poiType(state.furnaceStateId());
        return t < 0 ? null : built().byIndex.get(t);
    }

    /** 레지스트리의 종류 손잡이(또는 그 값을 든 손잡이)의 번호. 레지스트리에 없는 종류면 UnsupportedNmsCall. */
    public static int indexOf(Holder<PoiType> type, String what) {
        var list = built().byIndex;
        for (int i = 0; i < list.size(); i++) if (list.get(i) == type || list.get(i).value() == type.value()) return i;
        throw new UnsupportedNmsCall(what, "only the built-in point of interest types are supported (got " + type + ")");
    }
}
