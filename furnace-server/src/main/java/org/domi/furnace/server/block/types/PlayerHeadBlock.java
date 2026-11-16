// 바닐라 PlayerHeadBlock: 플레이어 머리(프로필은 블록 엔티티).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.level.block.PlayerHeadBlock")
public class PlayerHeadBlock extends SkullBlock {

    public PlayerHeadBlock(int block) { super(block); }
}
