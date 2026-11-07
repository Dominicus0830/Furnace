package org.bukkit.command;

/** 명령을 실행하다 낸 예외를 싼다(Bukkit CommandException). */
public class CommandException extends RuntimeException {

    public CommandException() {}

    public CommandException(String msg) { super(msg); }

    public CommandException(String msg, Throwable cause) { super(msg, cause); }
}
