package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 플레이어가 표지판 글을 바꿨다. Furnace 는 아직 알리지 않는다. Paper 의 Adventure 줄(lines()) 은 호환 모듈이 Adventure 를 볼 수 없어 없다. */
public class SignChangeEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final Player player;
    private final String[] lines;
    private final Side side;

    public SignChangeEvent(Block theBlock, Player thePlayer, String[] theLines, Side side) {
        super(theBlock);
        this.player = thePlayer;
        this.lines = theLines;
        this.side = side;
    }

    @Deprecated
    public SignChangeEvent(Block theBlock, Player thePlayer, String[] theLines) { this(theBlock, thePlayer, theLines, Side.FRONT); }

    public Player getPlayer() { return player; }

    public String[] getLines() { return lines; }

    public String getLine(int index) { return lines[index]; }

    public void setLine(int index, String line) { lines[index] = line; }

    public Side getSide() { return side; }
}
