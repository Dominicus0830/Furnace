package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 메뉴·보스바·죽음 시험 플러그인이 남기는 기록(플러그인 클래스로더와 시험 코드가 함께 본다). */
public final class MenuProbe {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();
    public static volatile Object inventory;
    public static volatile Object bossBar;
    public static volatile Throwable failure;

    private MenuProbe() {}

    public static void reset() {
        LOG.clear();
        inventory = null;
        bossBar = null;
        failure = null;
    }
}
