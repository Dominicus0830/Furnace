package org.bukkit.craftbukkit;

import org.bukkit.RegionAccessor;

/**
 * Paper CraftRegionAccessor — 월드({@link CraftWorld})와 생성 중 영역({@link org.bukkit.craftbukkit.generator.CraftLimitedRegion})의 공통 상위
 * (T-264 P2c). Paper 는 여기서 NMS 레벨 위에 RegionAccessor 를 한 번에 구현하지만, Furnace 는 두 하위 클래스가 각자 자기 손잡이(서버 월드·생성 영역)로
 * 구현한다 — 상위 사슬만 Paper 와 같다. Paper 의 추상 멤버(getHandle 등)는 T-261 껍데기 멤버가 합친다.
 */
public abstract class CraftRegionAccessor implements RegionAccessor {
}
