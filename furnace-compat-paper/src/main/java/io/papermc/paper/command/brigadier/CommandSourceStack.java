package io.papermc.paper.command.brigadier;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

/** Paper API: Brigadier 명령이 받는 출처(paper-api 1.21.1 와 같은 멤버). */
public interface CommandSourceStack {

    /** 명령이 실행되는 위치(월드·좌표·시선). */
    Location getLocation();

    /** 명령을 보낸 쪽(콘솔·플레이어·명령 블록…). */
    CommandSender getSender();

    /** 실행하는 엔티티. 없으면 null. */
    Entity getExecutor();
}
