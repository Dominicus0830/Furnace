// 바닐라 TheEndPortalBlockEntity: 엔드 차원문 블록에 붙는 데이터 없는 블록 엔티티.
// 엔드 관문(TheEndGatewayBlockEntity)의 부모 클래스이기도 해서, 엔더 드래곤 전투의 hasActiveExitPortal 이 이 종류(관문 포함)의 존재로 판정한다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;

public class EndPortalBlockEntity extends BlockEntity {

    private static final int END_PORTAL = B.id("end_portal");

    public EndPortalBlockEntity(Pos pos, int state) { super("minecraft:end_portal", pos, state); }

    protected EndPortalBlockEntity(String type, Pos pos, int state) { super(type, pos, state); }

    @Override public boolean isValid(int state) { return B.block(state) == END_PORTAL; }
}
