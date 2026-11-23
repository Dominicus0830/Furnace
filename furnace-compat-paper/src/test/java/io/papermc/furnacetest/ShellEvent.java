package io.papermc.furnacetest;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.domi.furnace.compat.nms.NmsShell;
import org.domi.furnace.compat.nms.NmsShellMember;

/** 시험용(T-260): Furnace 에 모양만 있는 Paper 이벤트 껍데기를 흉내 낸다(HANDLER_LIST 에 값이 없어 초기화가 던진다). */
@NmsShellMember(initTrap = true)
public class ShellEvent extends Event {
    @NmsShellMember
    private static final HandlerList HANDLER_LIST;

    static {
        if (true) throw NmsShell.unsupportedClass("io.papermc.furnacetest.ShellEvent");
        HANDLER_LIST = null;
    }

    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
