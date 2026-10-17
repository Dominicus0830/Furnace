// 바닐라 EndPortalBlock: 엔드 차원문. 안에 들어온 엔티티를 엔드(또는 엔드에서 오버월드)로 보낸다 — 모두 엔티티 동작.
package org.domi.furnace.server.block.types;

public class EndPortalBlock extends BaseEntityBlock {

    public EndPortalBlock(int block) { super(block); }

    // TODO(엔티티·차원 이동): entityInside — 모양 안에 들면 엔드로(엔드 쪽은 흑요석 단상 생성, 오버월드 쪽은 리스폰 지점), 엔드의 플레이어는 크레딧.
}
