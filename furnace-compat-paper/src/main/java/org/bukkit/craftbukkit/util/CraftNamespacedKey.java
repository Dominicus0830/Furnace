package org.bukkit.craftbukkit.util;

import net.minecraft.resources.ResourceLocation;
import org.bukkit.NamespacedKey;

/** CraftBukkit 의 NamespacedKey ↔ NMS ResourceLocation 변환(N2b-6). */
public final class CraftNamespacedKey {

    public CraftNamespacedKey() {}

    public static NamespacedKey fromStringOrNull(String string) {
        if (string == null || string.isEmpty()) return null;
        ResourceLocation r = ResourceLocation.tryParse(string);
        return r == null ? null : fromMinecraft(r);
    }

    public static NamespacedKey fromString(String string) { return fromMinecraft(ResourceLocation.parse(string)); }

    public static NamespacedKey fromMinecraft(ResourceLocation key) { return new NamespacedKey(key.getNamespace(), key.getPath()); }

    public static ResourceLocation toMinecraft(NamespacedKey key) { return ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey()); }
}
