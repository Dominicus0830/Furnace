// JavaPlugin 생성자가 읽어 가는 환경. 호환 모듈이 메인 클래스를 만들기 직전에 이 스레드에 걸어 둔다
// (Bukkit 은 PluginClassLoader 가 initialize 로 넘기지만, Furnace 의 클래스로더는 호환 모듈을 모른다).
package org.domi.furnace.compat.paper;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginDescriptionFile;

public final class PluginInit {

    private static final ThreadLocal<PluginInit> PENDING = new ThreadLocal<>();

    private final BukkitPluginAdapter owner;
    private final PluginDescriptionFile description;
    private final Logger logger;
    private final File dataFolder;
    private final File jar;
    private final ClassLoader loader;

    PluginInit(BukkitPluginAdapter owner, PluginDescriptionFile description, Logger logger, File dataFolder, File jar, ClassLoader loader) {
        this.owner = owner;
        this.description = description;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.jar = jar;
        this.loader = loader;
    }

    /** 이 스레드가 지금 만드는 플러그인의 환경(JavaPlugin 생성자 전용). 없으면 null. */
    public static PluginInit take() {
        PluginInit i = PENDING.get();
        PENDING.remove();
        return i;
    }

    static void expect(PluginInit init) { PENDING.set(init); }

    static void clear() { PENDING.remove(); }

    public BukkitPluginAdapter owner() { return owner; }

    public PluginDescriptionFile description() { return description; }

    public Logger logger() { return logger; }

    public File dataFolder() { return dataFolder; }

    public File jar() { return jar; }

    public ClassLoader loader() { return loader; }
}
