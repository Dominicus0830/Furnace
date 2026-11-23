package io.papermc.furnacetest;

import org.domi.furnace.compat.nms.NmsShell;
import org.domi.furnace.compat.nms.NmsShellMember;

/** 시험용(T-260): NMS 밖 껍데기의 클래스 초기화 덫을 손으로 흉내 낸다(생성기 대신 표지·덫을 직접 단다). */
@NmsShellMember(initTrap = true)
public class TrapHolder {
    @NmsShellMember
    public static Object VALUE;

    static {
        if (true) throw NmsShell.unsupportedClass("io.papermc.furnacetest.TrapHolder");
    }
}
