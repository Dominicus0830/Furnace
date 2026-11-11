package org.domi.furnace.compat.nms;

import java.util.List;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.world.FluidData;

/**
 * 유체 shim 도우미(N0). 레지스트리는 {@link Fluids} 다섯 상수로 만들고 서버의 내장 목록({@code builtIn("minecraft:fluid")})과 순서가 같은지 확인한다.
 * 블록 상태의 유체는 서버 블록 동작({@code BlockTypes.fluid})에서 읽는다 — 잠긴 블록·켈프·거품 기둥 규칙을 서버 한곳에만 둔다.
 */
public final class NmsFluids {

    private NmsFluids() {}

    private static volatile NmsDefaultedRegistry<Fluid> registry;

    public static DefaultedRegistry<Fluid> registry() {
        NmsDefaultedRegistry<Fluid> r = registry;
        if (r == null) {
            synchronized (NmsFluids.class) {
                r = registry;
                if (r == null) {
                    r = new NmsDefaultedRegistry<>(Registries.FLUID, ResourceLocation.withDefaultNamespace("empty"));
                    List<String> ids = Furnace.server().gameRegistries().builtIn("minecraft:fluid");
                    List<String> want = List.of("minecraft:empty", "minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava");
                    if (!ids.equals(want)) throw new IllegalStateException("유체 레지스트리가 바닐라 1.21.1 과 다르다: " + ids);
                    for (String id : ids) r.add(ResourceLocation.parse(id), byKey(id));
                    registry = r;
                }
            }
        }
        return r;
    }

    private static Fluid byKey(String id) {
        return switch (id) {
            case "minecraft:empty" -> Fluids.EMPTY;
            case "minecraft:flowing_water" -> Fluids.FLOWING_WATER;
            case "minecraft:water" -> Fluids.WATER;
            case "minecraft:flowing_lava" -> Fluids.FLOWING_LAVA;
            case "minecraft:lava" -> Fluids.LAVA;
            default -> throw new IllegalArgumentException(id);
        };
    }

    /** 서버 유체 값의 상태 shim(같은 표의 같은 객체). */
    public static FluidState state(FluidData data) {
        if (data.isEmpty()) return Fluids.EMPTY.defaultFluidState();
        boolean water = data.type().endsWith("water");
        if (data.isSource()) return (water ? Fluids.WATER : Fluids.LAVA).getSource(data.falling());
        return (water ? Fluids.WATER : Fluids.LAVA).getFlowing(data.amount(), data.falling());
    }

    /** 블록 상태 번호의 유체 상태(바닐라 BlockState.getFluidState). */
    public static FluidState ofBlockState(int stateId) { return state(Furnace.server().blockTypes().fluid(stateId)); }
}
