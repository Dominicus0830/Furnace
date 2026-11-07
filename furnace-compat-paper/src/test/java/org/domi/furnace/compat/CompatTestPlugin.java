// 호환 코어 시험용 플러그인 — 코어 객체를 만들어 두고 선언된 명령을 노출한다.
package org.domi.furnace.compat;

import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.compat.command.LegacyCommands;
import org.domi.furnace.compat.sched.LegacyScheduler;
import org.domi.furnace.compat.sched.PaperSchedulers;

public final class CompatTestPlugin extends FurnacePlugin {

    public LegacyScheduler legacy;
    public PaperSchedulers paper;
    public LegacyCommands commands;

    @Override
    public void onEnable() {
        legacy = new LegacyScheduler(server());
        paper = new PaperSchedulers(server());
        commands = new LegacyCommands(server());
        commands.declareFromDescription(this);
    }

    @Override
    public void onDisable() {
        if (paper != null) paper.close();
    }
}
