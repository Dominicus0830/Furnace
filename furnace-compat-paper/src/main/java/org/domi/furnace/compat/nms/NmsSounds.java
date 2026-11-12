package org.domi.furnace.compat.nms;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.domi.furnace.api.Furnace;

/**
 * 소리 레지스트리 shim(easy-nms E4c-1b). 서버 내장 레지스트리 목록({@code GameRegistries.builtIn("minecraft:sound_event")}, 바닐라 번호 순 1611개)에서
 * 처음 쓸 때 한 번 만든다. 바닐라처럼 모두 범위가 고정되지 않은 소리다(SoundEvents 의 등록 도우미가 createVariableRangeEvent 만 쓴다). 기본값 없는 레지스트리다.
 */
public final class NmsSounds {

    private NmsSounds() {}

    private static volatile NmsMappedRegistry<SoundEvent> registry;

    public static Registry<SoundEvent> registry() { return mapped(); }

    private static NmsMappedRegistry<SoundEvent> mapped() {
        NmsMappedRegistry<SoundEvent> r = registry;
        if (r == null) {
            synchronized (NmsSounds.class) {
                r = registry;
                if (r == null) {
                    r = new NmsMappedRegistry<>(Registries.SOUND_EVENT);
                    var ids = Furnace.server().gameRegistries().builtIn("minecraft:sound_event");
                    if (ids.isEmpty()) throw new IllegalStateException("서버가 소리 레지스트리 목록을 주지 않았다");
                    for (String id : ids) {
                        ResourceLocation loc = ResourceLocation.parse(id);
                        r.add(loc, SoundEvent.createVariableRangeEvent(loc));
                    }
                    registry = r;
                }
            }
        }
        return r;
    }

    /** SoundEvents 상수용: 이름("entity.pig.ambient")의 등록된 소리. 없으면 IllegalStateException(서버 표와 shim 이 어긋남). */
    public static SoundEvent event(String path) { return reference(path).value(); }

    /** SoundEvents 상수용: 이름의 등록된 손잡이. */
    public static Holder.Reference<SoundEvent> reference(String path) {
        return mapped().getHolder(ResourceLocation.withDefaultNamespace(path))
                .orElseThrow(() -> new IllegalStateException("서버 소리 레지스트리에 없는 이름: " + path));
    }
}
