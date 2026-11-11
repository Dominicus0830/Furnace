package org.domi.furnace.compat.nms;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.registry.GameRegistries;

/**
 * 태그 shim 도우미(N0). 태그 내용은 Furnace 에 두고(데이터 팩 리로드로 바뀐다) 물을 때마다 {@link GameRegistries} 로 읽는다 —
 * 리로드 뒤에도 옛 내용을 들고 있지 않는다. 다른 레지스트리의 태그로 물으면 바닐라처럼 거짓·빈 결과.
 */
public final class NmsTags {

    private NmsTags() {}

    private static GameRegistries registries() { return Furnace.server().gameRegistries(); }

    /** entry(레지스트리 키가 붙은 항목)가 태그에 들어 있는가. */
    public static boolean isTagged(ResourceKey<?> entry, TagKey<?> tag) {
        if (!tag.registry().location().equals(entry.registry())) return false;
        return registries().isTagged(entry.registry().toString(), tag.location().toString(), entry.location().toString());
    }

    /** entry 가 들어 있는 태그들(태그 이름 순서는 서버 태그 표 순). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Stream<TagKey<T>> tagsOf(ResourceKey<T> entry) {
        String reg = entry.registry().toString();
        String id = entry.location().toString();
        ResourceKey<? extends Registry<T>> regKey = (ResourceKey) entry.registryKey();
        GameRegistries g = registries();
        return g.tags(reg).stream().filter(t -> g.isTagged(reg, t, id))
                .map(t -> TagKey.create(regKey, net.minecraft.resources.ResourceLocation.parse(t)));
    }

    public static boolean exists(ResourceKey<? extends Registry<?>> registry, TagKey<?> tag) {
        return registries().tag(registry.location().toString(), tag.location().toString()).isPresent();
    }

    public static List<String> entries(ResourceKey<? extends Registry<?>> registry, TagKey<?> tag) {
        return registries().tag(registry.location().toString(), tag.location().toString()).orElse(List.of());
    }

    public static <T> Stream<TagKey<T>> names(ResourceKey<? extends Registry<T>> registry) {
        return registries().tags(registry.location().toString()).stream().map(t -> TagKey.create(registry, net.minecraft.resources.ResourceLocation.parse(t)));
    }
}
