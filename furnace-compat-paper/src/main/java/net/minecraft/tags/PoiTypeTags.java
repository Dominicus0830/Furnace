package net.minecraft.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/** 관심 지점 종류 태그 이름(Mojang 이름 shim). 내용은 서버에 걸린 태그다. */
public class PoiTypeTags {

    public static final TagKey<PoiType> ACQUIRABLE_JOB_SITE = create("acquirable_job_site");
    public static final TagKey<PoiType> VILLAGE = create("village");
    public static final TagKey<PoiType> BEE_HOME = create("bee_home");

    private PoiTypeTags() {}

    private static TagKey<PoiType> create(String name) {
        return TagKey.create(Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.withDefaultNamespace(name));
    }
}
