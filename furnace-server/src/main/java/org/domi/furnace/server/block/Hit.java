// 블록 적중(바닐라 BlockHitResult): 블록 좌표, 면, 적중 지점(월드 좌표), 블록 안쪽인가.
package org.domi.furnace.server.block;

public record Hit(Pos pos, int face, double x, double y, double z, boolean inside) {

    /** 같은 지점·면, 다른 블록(바닐라 withPosition). */
    public Hit withPos(Pos p) { return new Hit(p, face, x, y, z, inside); }
}
