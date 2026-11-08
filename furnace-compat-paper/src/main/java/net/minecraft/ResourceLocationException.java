package net.minecraft;

/** 잘못된 리소스 위치(Mojang 이름 shim, H7b). 바닐라와 같은 RuntimeException 하위. */
public class ResourceLocationException extends RuntimeException {

    public ResourceLocationException(String message) { super(message); }

    public ResourceLocationException(String message, Throwable cause) { super(message, cause); }
}
