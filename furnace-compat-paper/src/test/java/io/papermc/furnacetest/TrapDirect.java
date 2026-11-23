package io.papermc.furnacetest;

import org.domi.furnace.compat.nms.NmsShell;
import org.domi.furnace.compat.nms.NmsShellMember;

/** 시험용(T-260): 플러그인 코드가 직접(getstatic) 쓰는 덫 껍데기 — ShellInitTrapTest 만 쓴다(첫 초기화를 보려고 따로 둔다). */
@NmsShellMember(initTrap = true)
public class TrapDirect {
    @NmsShellMember
    public static Object VALUE;

    static {
        if (true) throw NmsShell.unsupportedClass("io.papermc.furnacetest.TrapDirect");
    }
}
