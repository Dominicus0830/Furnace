package net.minecraft.network.chat.contents;

import java.util.Locale;

/** 번역 형식 오류(Mojang 이름 shim). 메시지는 바닐라와 같다. */
public class TranslatableFormatException extends IllegalArgumentException {

    public TranslatableFormatException(TranslatableContents contents, String message) {
        super(String.format(Locale.ROOT, "Error parsing: %s: %s", contents, message));
    }

    public TranslatableFormatException(TranslatableContents contents, int index) {
        super(String.format(Locale.ROOT, "Invalid index %d requested for %s", index, contents));
    }

    public TranslatableFormatException(TranslatableContents contents, Throwable cause) {
        super(String.format(Locale.ROOT, "Error while parsing: %s", contents), cause);
    }
}
