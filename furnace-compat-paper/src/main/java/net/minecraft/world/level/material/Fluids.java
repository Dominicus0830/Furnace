package net.minecraft.world.level.material;

/** 유체 다섯 종류(Mojang 이름 shim, N0). 레지스트리 순서는 바닐라와 같다: empty, flowing_water, water, flowing_lava, lava. */
public class Fluids {

    public static final Fluid EMPTY = new EmptyFluid();
    public static final FlowingFluid FLOWING_WATER = new WaterFluid.Flowing();
    public static final FlowingFluid WATER = new WaterFluid.Source();
    public static final FlowingFluid FLOWING_LAVA = new LavaFluid.Flowing();
    public static final FlowingFluid LAVA = new LavaFluid.Source();
}
