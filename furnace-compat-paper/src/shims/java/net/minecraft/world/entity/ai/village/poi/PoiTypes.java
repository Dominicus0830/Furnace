package net.minecraft.world.entity.ai.village.poi;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.compat.nms.NmsPoiTypes;

/** 바닐라 관심 지점 종류 열쇠와 상태별 종류 찾기(NMS shim 5d). 상태 대응은 서버의 바닐라 표다. */
class PoiTypes {

    public static final ResourceKey<PoiType> ARMORER = createKey("armorer");
    public static final ResourceKey<PoiType> BUTCHER = createKey("butcher");
    public static final ResourceKey<PoiType> CARTOGRAPHER = createKey("cartographer");
    public static final ResourceKey<PoiType> CLERIC = createKey("cleric");
    public static final ResourceKey<PoiType> FARMER = createKey("farmer");
    public static final ResourceKey<PoiType> FISHERMAN = createKey("fisherman");
    public static final ResourceKey<PoiType> FLETCHER = createKey("fletcher");
    public static final ResourceKey<PoiType> LEATHERWORKER = createKey("leatherworker");
    public static final ResourceKey<PoiType> LIBRARIAN = createKey("librarian");
    public static final ResourceKey<PoiType> MASON = createKey("mason");
    public static final ResourceKey<PoiType> SHEPHERD = createKey("shepherd");
    public static final ResourceKey<PoiType> TOOLSMITH = createKey("toolsmith");
    public static final ResourceKey<PoiType> WEAPONSMITH = createKey("weaponsmith");
    public static final ResourceKey<PoiType> HOME = createKey("home");
    public static final ResourceKey<PoiType> MEETING = createKey("meeting");
    public static final ResourceKey<PoiType> BEEHIVE = createKey("beehive");
    public static final ResourceKey<PoiType> BEE_NEST = createKey("bee_nest");
    public static final ResourceKey<PoiType> NETHER_PORTAL = createKey("nether_portal");
    public static final ResourceKey<PoiType> LODESTONE = createKey("lodestone");
    public static final ResourceKey<PoiType> LIGHTNING_ROD = createKey("lightning_rod");

    private static ResourceKey<PoiType> createKey(String id) {
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.withDefaultNamespace(id));
    }

    /** 바닐라: 그 상태의 관심 지점 종류(레지스트리 손잡이 — 같은 종류면 같은 객체), 없으면 빈 값. */
    public static Optional<Holder<PoiType>> forState(BlockState state) {
        return Optional.ofNullable(NmsPoiTypes.forState(state));
    }

    /** 바닐라: 그 상태가 어느 관심 지점 종류에 드는가. */
    public static boolean hasPoi(BlockState state) {
        return NmsPoiTypes.forState(state) != null;
    }
}
