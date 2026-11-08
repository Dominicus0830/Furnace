package net.minecraft.core;

/** 실수 좌표(Mojang 이름 shim, H7b). {@link net.minecraft.world.phys.Vec3} 가 구현한다. */
public interface Position {
    double x();

    double y();

    double z();
}
