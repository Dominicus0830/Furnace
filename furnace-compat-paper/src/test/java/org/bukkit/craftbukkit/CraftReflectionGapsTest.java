package org.bukkit.craftbukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.junit.jupiter.api.Test;

/**
 * T-264 덫 계측(11-23, 서버 러너 · Modrinth 1.21.1 인기 플러그인 100개)에서 실행 중 걸린 CraftBukkit 리플렉션 자리. 모두 Paper 1.21.1 과 같은
 * 서술자·값이어야 한다(값은 Paper 서버 바이트코드 대조).
 */
class CraftReflectionGapsTest {

    /** 9개 플러그인이 getMethod("getCommandMap") 으로 부른다 — 가장 좁은 반환형이 Paper 처럼 SimpleCommandMap 이고 진짜 몸통이어야 한다. */
    @Test
    void narrowReturnTypesMatchPaper() throws Exception {
        assertEquals(org.bukkit.command.SimpleCommandMap.class, CraftServer.class.getMethod("getCommandMap").getReturnType());
        assertEquals(org.bukkit.craftbukkit.inventory.CraftItemFactory.class, CraftServer.class.getMethod("getItemFactory").getReturnType());
        assertEquals(org.bukkit.craftbukkit.scheduler.CraftScheduler.class, CraftServer.class.getMethod("getScheduler").getReturnType());
        assertEquals(org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer.class,
                org.bukkit.craftbukkit.entity.CraftEntity.class.getMethod("getPersistentDataContainer").getReturnType());
    }

    @Test
    void minecraftRegistryAccess() {
        assertSame(RegistryAccess.SERVER, CraftRegistry.getMinecraftRegistry());
        ResourceKey<net.minecraft.core.Registry<Object>> bogus = ResourceKey.createRegistryKey(ResourceLocation.withDefaultNamespace("nope"));
        var e = assertThrows(IllegalStateException.class, () -> CraftRegistry.getMinecraftRegistry(bogus));
        assertEquals("Missing registry: " + bogus, e.getMessage());
    }

    @Test
    void mappingsVersionIsPapers() {
        assertEquals("7092ff1ff9352ad7e2260dc150e6a3ec", CraftMagicNumbers.INSTANCE.getMappingsVersion());
    }
}
