package org.bukkit.boss;

import org.bukkit.Keyed;

/** 키로 찾는(저장되는, /bossbar 와 같은) 보스바. Furnace 호환 계층은 아직 만들지 않는다. */
public interface KeyedBossBar extends BossBar, Keyed {
}
