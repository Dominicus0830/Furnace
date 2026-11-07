// Bukkit CommandException — 명령 실행기·완성기가 던진 예외를 감싼다.
package org.domi.furnace.compat.command;

public final class LegacyCommandException extends RuntimeException {

    public LegacyCommandException(String message, Throwable cause) { super(message, cause); }
}
