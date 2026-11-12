package org.domi.furnace.compat.nms;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 기본값 있는 얼어 있는 내장 레지스트리 shim(바닐라 DefaultedMappedRegistry 의 읽기 규칙). 모르는 이름·번호·값이면
 * {@code get}·{@code byId}·{@code getKey}·{@code getId} 가 기본값 쪽을 돌려주고, {@code getOptional} 류는 비어 있다.
 */
public final class NmsDefaultedRegistry<T> extends NmsMappedRegistry<T> implements DefaultedRegistry<T> {

    public NmsDefaultedRegistry(ResourceKey<? extends Registry<T>> key, ResourceLocation defaultKey) {
        super(key, java.util.Objects.requireNonNull(defaultKey, "defaultKey"));
    }

    @Override
    public ResourceLocation getDefaultKey() { return defaultKey(); }
}
