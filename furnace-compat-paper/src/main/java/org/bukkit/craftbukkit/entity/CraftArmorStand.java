package org.bukkit.craftbukkit.entity;

import org.bukkit.entity.ArmorStand;
import org.domi.furnace.api.entity.ArmorStandState;
import org.domi.furnace.compat.paper.EntityStates;

/** 갑옷 거치대. 플래그는 Furnace ArmorStandState 로 지금 값을 읽고 쓴다(소유 스레드 규칙은 CraftEntity 와 같다). */
public class CraftArmorStand extends CraftLivingEntity implements ArmorStand {

    /** 호환 모듈 전용(Compat 캐시가 만든다). */
    public CraftArmorStand(org.domi.furnace.api.entity.LivingEntity handle) { super(handle); }

    @Override
    public boolean hasBasePlate() { return EntityStates.read(furnaceHandle(), ArmorStandState.class, ArmorStandState::hasBasePlate, true, "ArmorStand#hasBasePlate"); }

    @Override
    public void setBasePlate(boolean basePlate) { EntityStates.write(furnaceHandle(), ArmorStandState.class, s -> s.setBasePlate(basePlate), "ArmorStand#setBasePlate"); }

    /** Paper 처럼 중력을 끄면 물리도 끈다(갑옷 거치대만의 규칙 — 켜면 마커여도 물리를 켠다). */
    @Override
    public void setGravity(boolean gravity) {
        EntityStates.write(furnaceHandle(), ArmorStandState.class, s -> {
            s.setNoGravity(!gravity);
            s.setNoPhysics(!gravity);
        }, "ArmorStand#setGravity");
    }

    @Override
    public boolean isVisible() { return EntityStates.read(furnaceHandle(), ArmorStandState.class, s -> !s.isInvisible(), true, "ArmorStand#isVisible"); }

    @Override
    public void setVisible(boolean visible) { EntityStates.write(furnaceHandle(), ArmorStandState.class, s -> s.setInvisible(!visible), "ArmorStand#setVisible"); }

    @Override
    public boolean hasArms() { return EntityStates.read(furnaceHandle(), ArmorStandState.class, ArmorStandState::hasArms, false, "ArmorStand#hasArms"); }

    @Override
    public void setArms(boolean arms) { EntityStates.write(furnaceHandle(), ArmorStandState.class, s -> s.setArms(arms), "ArmorStand#setArms"); }

    @Override
    public boolean isSmall() { return EntityStates.read(furnaceHandle(), ArmorStandState.class, ArmorStandState::isSmall, false, "ArmorStand#isSmall"); }

    @Override
    public void setSmall(boolean small) { EntityStates.write(furnaceHandle(), ArmorStandState.class, s -> s.setSmall(small), "ArmorStand#setSmall"); }

    @Override
    public boolean isMarker() { return EntityStates.read(furnaceHandle(), ArmorStandState.class, ArmorStandState::isMarker, false, "ArmorStand#isMarker"); }

    @Override
    public void setMarker(boolean marker) { EntityStates.write(furnaceHandle(), ArmorStandState.class, s -> s.setMarker(marker), "ArmorStand#setMarker"); }
}
