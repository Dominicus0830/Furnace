package org.bukkit.craftbukkit.scheduler;

import org.domi.furnace.compat.sched.LegacyTask;

/** 비동기 실행기에서 도는 작업의 손잡이(Paper 와 같은 이름·패키지 전용 — T-264). */
class CraftAsyncTask extends CraftTask {
    CraftAsyncTask(LegacyTask task) { super(task); }
}
