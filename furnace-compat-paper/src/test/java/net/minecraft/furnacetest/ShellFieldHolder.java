package net.minecraft.furnacetest;

import org.domi.furnace.compat.nms.NmsShellMember;

/** 시험용(T-260): 전체 껍데기의 필드 표지를 리플렉션 다리가 알아보는지 본다. 생성기 대신 손으로 표지를 단다. */
public class ShellFieldHolder {
    @NmsShellMember
    public int shellValue;
    public int liveValue = 5;
    /** private 껍데기 필드도 리플렉션(getDeclaredField + setAccessible)으로는 읽힌다 — 막혀야 한다. */
    @NmsShellMember
    private static Object hidden;
}
