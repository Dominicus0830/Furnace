package io.papermc.paper.block.fluid;

import org.bukkit.Fluid;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Paper 1.21.1 FluidData(한 칸의 유체 상태). 이 호환 계층에는 아직 만드는 곳이 없다 — {@link org.bukkit.RegionAccessor#getFluidData} 는 T-218. */
public interface FluidData extends Cloneable {

    Fluid getFluidType();

    FluidData clone();

    Vector computeFlowDirection(Location location);

    int getLevel();

    float computeHeight(Location location);

    boolean isSource();
}
