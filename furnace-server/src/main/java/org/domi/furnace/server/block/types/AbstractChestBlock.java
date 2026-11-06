// 바닐라 AbstractChestBlock: 상자·엔더 상자 바탕(블록 엔티티 종류를 가진다 — 결합은 하위 종류가 정한다).
package org.domi.furnace.server.block.types;

public abstract class AbstractChestBlock extends BaseEntityBlock {

    protected AbstractChestBlock(int block) { super(block); }
}
