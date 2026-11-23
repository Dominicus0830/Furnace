// T-219·T-222 기본 세 월드와 이름별(createWorld) Bukkit 생성기·바이옴 공급자(bukkit.yml worlds.<이름>.generator·biome-provider 자리 — Furnace 는
// furnace.properties world-generator.<차원>·world-biome-provider.<차원>). CraftServer#getGenerator(String)·getBiomeProvider(String) 와 같은
// 규칙·같은 문구로 플러그인을 찾고, MinecraftServer#loadWorld0 처럼 공급자가 없으면 생성기의 기본 공급자를 쓴다.
package org.domi.furnace.compat.paper;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.api.worldgen.DefaultWorldGeneration;

public final class BukkitDefaultWorlds {

    private static final Logger LOG = Logger.getLogger("Minecraft");

    private BukkitDefaultWorlds() {}

    /** 요청의 생성기·공급자 플러그인이 Bukkit 플러그인이면 그 몫을 맡는다(아니면 null — 서버나 다른 형식이 알린다). */
    public static DefaultWorldGeneration resolve(DefaultWorldGeneration.Request r) {
        boolean genMine = !r.generator().isEmpty() && plugin(r.generator()) != null;
        boolean providerMine = !r.biomeProvider().isEmpty() && plugin(r.biomeProvider()) != null;
        if (!genMine && !providerMine) return null;
        World.Environment env = switch (r.environment()) {
            case NORMAL -> World.Environment.NORMAL;
            case NETHER -> World.Environment.NETHER;
            case THE_END -> World.Environment.THE_END;
        };
        // 생성기·공급자의 WorldInfo(CraftWorldInfo 자리): 서버가 붙이기 직전 prepare 로 채우고, 붙인 뒤에는 그 CraftWorld 를 따른다
        var info = BukkitBiomeProviders.worldInfo(r.worldName(), env, r.seed(), r.minY(), r.maxY());
        ChunkGenerator generator = genMine ? generator(r.worldName(), r.generator()) : null;
        BiomeProvider provider = providerMine ? biomeProvider(r.worldName(), r.biomeProvider()) : null;
        if (provider == null && generator != null) provider = generator.getDefaultBiomeProvider(info);   // loadWorld0: 생성기의 기본 공급자
        var hooks = generator == null ? null : BukkitChunkGenerators.adapt(generator, info);
        var biomes = provider == null ? null : BukkitBiomeProviders.adapt(provider, info);
        return new DefaultWorldGeneration(hooks, biomes, genMine, providerMine);
    }

    private static Plugin plugin(String spec) {
        return Bukkit.getPluginManager().getPlugin(DefaultWorldGeneration.Request.pluginOf(spec));
    }

    /** CraftServer#getGenerator(String) 의 뒷부분(설정에서 spec 을 읽은 뒤) — 플러그인이 없으면 Paper 문구를 남기고 null. */
    public static ChunkGenerator generator(String world, String spec) {
        Plugin plugin = plugin(spec);
        String id = DefaultWorldGeneration.Request.idOf(spec);
        if (plugin == null) {
            LOG.severe("Could not set generator for default world '" + world + "': Plugin '" + DefaultWorldGeneration.Request.pluginOf(spec) + "' does not exist");
            return null;
        }
        if (!plugin.isEnabled()) {
            LOG.severe("Could not set generator for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' is not enabled yet (is it load:STARTUP?)");
            return null;
        }
        try {
            ChunkGenerator result = plugin.getDefaultWorldGenerator(world, id);
            if (result == null) {
                LOG.severe("Could not set generator for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' lacks a default world generator");
            }
            return result;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Could not set generator for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName(), t);
            return null;
        }
    }

    /** CraftServer#getBiomeProvider(String) 의 뒷부분. */
    public static BiomeProvider biomeProvider(String world, String spec) {
        Plugin plugin = plugin(spec);
        String id = DefaultWorldGeneration.Request.idOf(spec);
        if (plugin == null) {
            LOG.severe("Could not set biome provider for default world '" + world + "': Plugin '" + DefaultWorldGeneration.Request.pluginOf(spec) + "' does not exist");
            return null;
        }
        if (!plugin.isEnabled()) {
            LOG.severe("Could not set biome provider for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' is not enabled yet (is it load:STARTUP?)");
            return null;
        }
        try {
            BiomeProvider result = plugin.getDefaultBiomeProvider(world, id);
            if (result == null) {
                LOG.severe("Could not set biome provider for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' lacks a default world biome provider");
            }
            return result;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Could not set biome provider for default world '" + world + "': Plugin '" + plugin.getDescription().getFullName(), t);
            return null;
        }
    }
}
