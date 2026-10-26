// 바닐라 DimensionTransition: 차원 이동의 목적지(레벨·위치·속도·회전). 도착 뒤 콜백(소리·티켓)은 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public record DimensionTransition(ChunkLevel newLevel, double x, double y, double z, double vx, double vy, double vz,
                                  float yRot, float xRot) {}
