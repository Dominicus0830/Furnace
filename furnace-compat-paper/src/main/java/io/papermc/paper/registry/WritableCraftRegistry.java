package io.papermc.paper.registry;

import org.bukkit.Keyed;
import org.bukkit.Registry;

/**
 * Paper WritableCraftRegistry — 플러그인이 부트스트랩 때 값을 더할 수 있는 레지스트리(1.21.1: 마법부여·게임 이벤트, 관측표 registry.ENCHANTMENT,
 * T-264 P2d). Furnace 에는 레지스트리 수정 단계가 아직 없어 읽기는 CraftRegistry 와 같다 — 클래스만 Paper 와 같다. 생성자는 패키지 전용
 * (CompatRegistries → CraftAccess).
 */
public class WritableCraftRegistry<M, T extends Keyed, B> extends org.bukkit.craftbukkit.CraftRegistry<T, M> {

    WritableCraftRegistry(Registry<T> body) { super(body); }
}
