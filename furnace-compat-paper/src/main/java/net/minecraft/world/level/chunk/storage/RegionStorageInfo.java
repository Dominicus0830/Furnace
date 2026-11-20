package net.minecraft.world.level.chunk.storage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 지역 파일이 무엇의 저장인가(Mojang 이름 shim — 바닐라와 같은 기록). 로그·오류 문구에만 쓰인다. */
public record RegionStorageInfo(String level, ResourceKey<Level> dimension, String type) {

    public RegionStorageInfo withTypeSuffix(String suffix) { return new RegionStorageInfo(level, dimension, type + suffix); }
}
