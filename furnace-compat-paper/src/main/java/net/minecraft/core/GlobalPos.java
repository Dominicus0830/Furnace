package net.minecraft.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 차원 + 블록 좌표(Mojang 이름 shim, 엔티티 데이터 칸 optional_global_pos). 바닐라처럼 레코드이고 toString 은 "차원키 좌표".
 * 코덱 필드는 껍데기 합치기가 더한다.
 */
public record GlobalPos(ResourceKey<Level> dimension, BlockPos pos) {

    public static GlobalPos of(ResourceKey<Level> dimension, BlockPos pos) { return new GlobalPos(dimension, pos); }

    @Override
    public String toString() { return dimension + " " + pos; }

    /** 호환 모듈 전용: Furnace 값으로. */
    public org.domi.furnace.api.world.GlobalPos furnace() { return new org.domi.furnace.api.world.GlobalPos(dimension.location().toString(), pos.furnace()); }

    /** 호환 모듈 전용: Furnace 값에서. */
    public static GlobalPos fromFurnace(org.domi.furnace.api.world.GlobalPos p) {
        return new GlobalPos(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(p.dimension())), BlockPos.fromFurnace(p.pos()));
    }
}
