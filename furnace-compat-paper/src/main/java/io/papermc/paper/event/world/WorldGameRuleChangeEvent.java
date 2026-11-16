// Paper WorldGameRuleChangeEvent: 게임 규칙 값이 바뀌기 직전. /gamerule(보낸 쪽 있음)과 World#setGameRule·setGameRuleValue(보낸 쪽 null)에서 불린다.
// 값은 문자열로 오고 바꿀 수 있다. 취소하면 값이 그대로다(/gamerule 은 그래도 성공 알림을 지금 값으로 보낸다).
package io.papermc.paper.event.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;

public class WorldGameRuleChangeEvent extends WorldEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final CommandSender commandSender;
    private final GameRule<?> gameRule;
    private String value;
    private boolean cancelled;

    public WorldGameRuleChangeEvent(World world, CommandSender commandSender, GameRule<?> gameRule, String value) {
        super(world);
        this.commandSender = commandSender;
        this.gameRule = gameRule;
        this.value = value;
    }

    /** 명령을 낸 쪽, API 로 바꾸면 null. */
    public CommandSender getCommandSender() { return commandSender; }

    public GameRule<?> getGameRule() { return gameRule; }

    public String getValue() { return value; }

    public void setValue(String value) { this.value = value; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
