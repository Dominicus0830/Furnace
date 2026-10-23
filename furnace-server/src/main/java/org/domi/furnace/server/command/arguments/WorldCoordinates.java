// 바닐라 WorldCoordinates(Coordinates 구현) — x/y/z 세 칸. 로컬 좌표('^')는 3D 좌표 인자가 생길 때 함께 들어온다.
package org.domi.furnace.server.command.arguments;

import org.domi.furnace.server.command.CommandSource;

public record WorldCoordinates(WorldCoordinate x, WorldCoordinate y, WorldCoordinate z) {

    /** 실행 위치를 기준으로 푼 절대 좌표 {x, y, z}. */
    public double[] getPosition(CommandSource source) {
        return new double[] {x.get(source.x()), y.get(source.y()), z.get(source.z())};
    }
}
