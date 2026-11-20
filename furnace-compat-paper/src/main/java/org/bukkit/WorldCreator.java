package org.bukkit;

import java.util.Locale;
import java.util.Random;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

/**
 * Paper 1.21.1 WorldCreator(새 월드 설명서 — {@link Bukkit#createWorld}). 값과 기본값은 Paper 와 같다(시드는 무작위, 구조물 켬, NORMAL).
 * Furnace 에서 다른 점: Adventure TriState 를 쓰는 keepSpawnLoaded 는 없고 옛 keepSpawnInMemory 만 있다 — docs/api/compat-paper.md.
 */
public class WorldCreator {

    private final NamespacedKey key;
    private final String name;
    private long seed;
    private World.Environment environment = World.Environment.NORMAL;
    private ChunkGenerator generator = null;
    private BiomeProvider biomeProvider = null;
    private WorldType type = WorldType.NORMAL;
    private boolean generateStructures = true;
    private String generatorSettings = "";
    private boolean hardcore = false;
    private Boolean keepSpawnLoaded = null;   // Paper TriState: null = NOT_SET

    public WorldCreator(String name) { this(name, worldKey(name)); }

    /** Paper: 주 월드 이름·_nether·_the_end 는 바닐라 키, 나머지는 minecraft:<소문자 이름, 빈칸은 _>. */
    private static NamespacedKey worldKey(String name) {
        String main = Bukkit.getUnsafe().getMainLevelName();
        if (name.equals(main)) return NamespacedKey.minecraft("overworld");
        if (name.equals(main + "_nether")) return NamespacedKey.minecraft("the_nether");
        if (name.equals(main + "_the_end")) return NamespacedKey.minecraft("the_end");
        return NamespacedKey.minecraft(name.toLowerCase(Locale.ENGLISH).replace(" ", "_"));
    }

    public WorldCreator(String levelName, NamespacedKey worldKey) {
        if (levelName == null || worldKey == null) throw new IllegalArgumentException("World name and key cannot be null");
        this.name = levelName;
        this.seed = new Random().nextLong();
        this.key = worldKey;
    }

    public WorldCreator(NamespacedKey worldKey) { this(worldKey.getKey(), worldKey); }

    public NamespacedKey key() { return key; }

    public static WorldCreator ofNameAndKey(String levelName, NamespacedKey worldKey) { return new WorldCreator(levelName, worldKey); }

    public static WorldCreator ofKey(NamespacedKey worldKey) { return new WorldCreator(worldKey); }

    /** 그 월드의 설정을 베낀다(이름·키는 그대로). */
    public WorldCreator copy(World world) {
        if (world == null) throw new IllegalArgumentException("World cannot be null");
        seed = world.getSeed();
        environment = world.getEnvironment();
        generator = world.getGenerator();
        biomeProvider = world.getBiomeProvider();
        type = world.getWorldType();
        generateStructures = world.canGenerateStructures();
        hardcore = world.isHardcore();
        keepSpawnLoaded = world.getKeepSpawnInMemory();
        return this;
    }

    public WorldCreator copy(WorldCreator creator) {
        if (creator == null) throw new IllegalArgumentException("Creator cannot be null");
        seed = creator.seed;
        environment = creator.environment;
        generator = creator.generator;
        biomeProvider = creator.biomeProvider;
        type = creator.type;
        generateStructures = creator.generateStructures;
        generatorSettings = creator.generatorSettings;
        hardcore = creator.hardcore;
        keepSpawnLoaded = creator.keepSpawnLoaded;
        return this;
    }

    public String name() { return name; }

    public long seed() { return seed; }

    public WorldCreator seed(long seed) {
        this.seed = seed;
        return this;
    }

    public World.Environment environment() { return environment; }

    public WorldCreator environment(World.Environment env) {
        this.environment = env;
        return this;
    }

    public WorldType type() { return type; }

    public WorldCreator type(WorldType type) {
        this.type = type;
        return this;
    }

    public ChunkGenerator generator() { return generator; }

    public WorldCreator generator(ChunkGenerator generator) {
        this.generator = generator;
        return this;
    }

    /** "플러그인[:id]" 의 기본 생성기(Plugin#getDefaultWorldGenerator). 못 찾으면 콘솔에 알리고 null. */
    public WorldCreator generator(String generator) {
        this.generator = getGeneratorForName(name, generator, Bukkit.getConsoleSender());
        return this;
    }

    public WorldCreator generator(String generator, CommandSender output) {
        this.generator = getGeneratorForName(name, generator, output);
        return this;
    }

    public BiomeProvider biomeProvider() { return biomeProvider; }

    public WorldCreator biomeProvider(BiomeProvider biomeProvider) {
        this.biomeProvider = biomeProvider;
        return this;
    }

    public WorldCreator biomeProvider(String biomeProvider) {
        this.biomeProvider = getBiomeProviderForName(name, biomeProvider, Bukkit.getConsoleSender());
        return this;
    }

    public WorldCreator biomeProvider(String biomeProvider, CommandSender output) {
        this.biomeProvider = getBiomeProviderForName(name, biomeProvider, output);
        return this;
    }

    /** 생성기 설정(JSON). FLAT 이면 바닐라 평지 설정({"layers":[{"block":..,"height":..}],"biome":..}), 그 밖에는 쓰지 않는다. */
    public WorldCreator generatorSettings(String generatorSettings) {
        this.generatorSettings = generatorSettings;
        return this;
    }

    public String generatorSettings() { return generatorSettings; }

    public WorldCreator generateStructures(boolean generate) {
        this.generateStructures = generate;
        return this;
    }

    public boolean generateStructures() { return generateStructures; }

    public WorldCreator hardcore(boolean hardcore) {
        this.hardcore = hardcore;
        return this;
    }

    public boolean hardcore() { return hardcore; }

    /** Paper: keepSpawnLoaded(TriState.byBoolean(v)). */
    @Deprecated(forRemoval = true)
    public WorldCreator keepSpawnInMemory(boolean keepSpawnInMemory) {
        this.keepSpawnLoaded = keepSpawnInMemory;
        return this;
    }

    /** Paper: keepSpawnLoaded() == TRUE(정하지 않았으면 false). */
    @Deprecated(forRemoval = true)
    public boolean keepSpawnInMemory() { return Boolean.TRUE.equals(keepSpawnLoaded); }

    public World createWorld() { return Bukkit.createWorld(this); }

    public static WorldCreator name(String name) { return new WorldCreator(name); }

    public static ChunkGenerator getGeneratorForName(String world, String name, CommandSender output) {
        if (world == null) throw new IllegalArgumentException("World name must be specified");
        if (output == null) output = Bukkit.getConsoleSender();
        if (name == null) return null;
        String[] split = name.split(":", 2);
        String id = split.length > 1 ? split[1] : null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin(split[0]);
        if (plugin == null) {
            output.sendMessage("Could not set generator for world '" + world + "': Plugin '" + split[0] + "' does not exist");
        } else if (!plugin.isEnabled()) {
            output.sendMessage("Could not set generator for world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' is not enabled");
        } else {
            return plugin.getDefaultWorldGenerator(world, id);
        }
        return null;
    }

    public static BiomeProvider getBiomeProviderForName(String world, String name, CommandSender output) {
        if (world == null) throw new IllegalArgumentException("World name must be specified");
        if (output == null) output = Bukkit.getConsoleSender();
        if (name == null) return null;
        String[] split = name.split(":", 2);
        String id = split.length > 1 ? split[1] : null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin(split[0]);
        if (plugin == null) {
            output.sendMessage("Could not set biome provider for world '" + world + "': Plugin '" + split[0] + "' does not exist");
        } else if (!plugin.isEnabled()) {
            output.sendMessage("Could not set set biome provider for world '" + world + "': Plugin '" + plugin.getDescription().getFullName() + "' is not enabled");
        } else {
            return plugin.getDefaultBiomeProvider(world, id);
        }
        return null;
    }
}
