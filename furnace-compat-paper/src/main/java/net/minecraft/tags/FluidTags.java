package net.minecraft.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

/** 바닐라 유체 태그 이름 상수(Mojang 이름 shim, N0). 바닐라 1.21.1 유체 태그 둘. 내용은 지금 걸린 태그다. */
public final class FluidTags {

    private FluidTags() {}

    public static final TagKey<Fluid> WATER = create("water");
    public static final TagKey<Fluid> LAVA = create("lava");

    private static TagKey<Fluid> create(String path) { return TagKey.create(Registries.FLUID, ResourceLocation.withDefaultNamespace(path)); }
}
