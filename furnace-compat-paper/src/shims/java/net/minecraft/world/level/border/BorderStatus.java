package net.minecraft.world.level.border;

/** 바닐라 BorderStatus(경계 색: GROWING 초록 / SHRINKING 빨강 / STATIONARY 파랑). 순서가 서버 WorldBorder.Status 와 같다. */
enum BorderStatus {
    public int getColor() { return ordinal() == 0 ? 4259712 : ordinal() == 1 ? 16724016 : 2138367; }
}
