package org.bukkit.plugin;

public class InvalidDescriptionException extends Exception {

    public InvalidDescriptionException(Throwable cause, String message) { super(message, cause); }

    public InvalidDescriptionException(Throwable cause) { super("Invalid plugin.yml", cause); }

    public InvalidDescriptionException(String message) { super(message); }

    public InvalidDescriptionException() { super("Invalid plugin.yml"); }
}
