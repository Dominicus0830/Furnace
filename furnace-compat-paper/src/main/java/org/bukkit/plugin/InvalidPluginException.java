package org.bukkit.plugin;

public class InvalidPluginException extends Exception {

    public InvalidPluginException(Throwable cause) { super(cause); }

    public InvalidPluginException() {}

    public InvalidPluginException(String message, Throwable cause) { super(message, cause); }

    public InvalidPluginException(String message) { super(message); }
}
