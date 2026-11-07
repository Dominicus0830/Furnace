package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.plugin.java.JavaPlugin;

/** 생명주기 순서·의존 순서를 로그로 남기는 가장 단순한 플러그인(시험용). 이름은 plugin.yml 이 정한다. */
public final class ProbePlugin extends JavaPlugin {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();

    @Override
    public void onLoad() { LOG.add(getName() + ":load"); }

    @Override
    public void onEnable() { LOG.add(getName() + ":enable"); }

    @Override
    public void onDisable() { LOG.add(getName() + ":disable"); }
}
