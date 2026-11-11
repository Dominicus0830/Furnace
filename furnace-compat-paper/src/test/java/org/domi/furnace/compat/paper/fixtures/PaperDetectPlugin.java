package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.plugin.java.JavaPlugin;

/** PaperLib·FastAsyncWorldEdit 처럼 클래스 유무로 Paper 서버인지 판별하는 플러그인(시험용). */
public final class PaperDetectPlugin extends JavaPlugin {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onLoad() {
        boolean paper = hasClass("com.destroystokyo.paper.PaperConfig") || hasClass("io.papermc.paper.configuration.Configuration");
        LOG.add("paper=" + paper);
    }
}
