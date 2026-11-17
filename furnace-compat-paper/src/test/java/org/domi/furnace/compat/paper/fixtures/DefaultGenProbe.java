package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/** T-219 기본 월드 생성기·바이옴 공급자를 주는 플러그인(시험용). 돌려줄 객체는 시험이 정적 필드에 넣는다. */
public final class DefaultGenProbe extends JavaPlugin {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    public static volatile ChunkGenerator GENERATOR;
    public static volatile BiomeProvider PROVIDER;

    public static void reset() {
        LOG.clear();
        GENERATOR = null;
        PROVIDER = null;
    }

    @Override
    public void onEnable() { LOG.add(getName() + ":enable"); }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        LOG.add(getName() + ":generator " + worldName + " " + id);
        return GENERATOR;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String worldName, String id) {
        LOG.add(getName() + ":provider " + worldName + " " + id);
        return PROVIDER;
    }
}
