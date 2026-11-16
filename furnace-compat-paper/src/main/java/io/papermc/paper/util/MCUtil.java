package io.papermc.paper.util;

import java.util.concurrent.Executor;

/**
 * Paper 내부 도우미(N2b-6 — 플러그인이 쓰는 것만). {@link #MAIN_EXECUTOR} 는 Paper 에서 주 스레드에 맡기는 실행기 — Furnace 는 전역 스레드에서 돌린다
 * (Bukkit 주 스레드 작업과 같은 곳).
 */
public final class MCUtil {

    private MCUtil() {}

    public static final Executor MAIN_EXECUTOR = task -> org.domi.furnace.api.Furnace.server().scheduler().global().execute(task);
}
