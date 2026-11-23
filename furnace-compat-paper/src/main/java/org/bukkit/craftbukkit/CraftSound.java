package org.bukkit.craftbukkit;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;

/**
 * CraftBukkit 소리 변환(Paper 1.21.1 과 같은 정적 메서드 넷). ProtocolLib {@code BukkitConverters.getSoundConverter} 가 "SoundEvent 를 돌려주고
 * Sound 하나를 받는 정적 메서드"와 그 반대를 찾아 소리 패킷 값(getSoundEffects)을 바꾼다(2026-11-14 ProtocolLib 점검 — 예전에는 클래스가 없어
 * 소리 패킷 만들기·읽기가 모두 실패했다). 소리 레지스트리에 없는 소리(직접 정의)는 Paper 처럼 {@link java.util.NoSuchElementException}, null 은 메시지 없는
 * {@link IllegalArgumentException}(11-23 Paper 1.21.1 바이트코드 대조, T-264 P4).
 */
public class CraftSound {

    private CraftSound() {}

    public static Sound minecraftToBukkit(SoundEvent minecraft) {
        if (minecraft == null) throw new IllegalArgumentException();
        ResourceLocation loc = BuiltInRegistries.SOUND_EVENT.getResourceKey(minecraft).orElseThrow().location();
        Sound bukkit = Sound.byKey(new NamespacedKey(loc.getNamespace(), loc.getPath()));
        if (bukkit == null) throw new IllegalArgumentException();
        return bukkit;
    }

    public static SoundEvent bukkitToMinecraft(Sound bukkit) {
        if (bukkit == null) throw new IllegalArgumentException();
        NamespacedKey k = bukkit.getKey();
        return BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.fromNamespaceAndPath(k.getNamespace(), k.getKey())).orElseThrow();
    }

    public static Holder<SoundEvent> bukkitToMinecraftHolder(Sound bukkit) {
        if (bukkit == null) throw new IllegalArgumentException();
        if (BuiltInRegistries.SOUND_EVENT.wrapAsHolder(bukkitToMinecraft(bukkit)) instanceof Holder.Reference<SoundEvent> holder) return holder;
        throw new IllegalArgumentException("No Reference holder found for " + bukkit
                + ", this can happen if a plugin creates its own sound effect with out properly registering it.");
    }

    public static Sound minecraftHolderToBukkit(Holder<SoundEvent> minecraft) {
        if (minecraft == null) throw new IllegalArgumentException();
        return minecraftToBukkit(minecraft.value());
    }
}
