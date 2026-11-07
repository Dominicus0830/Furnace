package org.bukkit.command;

import org.bukkit.block.Block;

/**
 * 명령 블록이 보낸 명령의 발신자. Furnace 호환 계층은 아직 명령 블록 발신자를 이 형으로 건네지 않는다 —
 * {@code sender instanceof BlockCommandSender} 는 항상 false 이고 명령 블록이 친 명령은 일반 발신자로 온다.
 */
public interface BlockCommandSender extends CommandSender {

    Block getBlock();
}
