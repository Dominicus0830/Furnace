package org.bukkit.plugin;

/** 플러그인이 없는 의존을 요구할 때(Bukkit 과 같은 RuntimeException). */
public class UnknownDependencyException extends RuntimeException {

    public UnknownDependencyException(Throwable throwable) { super(throwable); }

    public UnknownDependencyException(String message) { super(message); }

    public UnknownDependencyException(Throwable throwable, String message) { super(message, throwable); }

    public UnknownDependencyException() {}
}
