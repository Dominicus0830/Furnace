// 바닐라 FunctionInstantiationException(ew) — 매크로 인자 오류(missing_arguments·missing_argument·parse). 메시지는 번역 가능한 Text.
package org.domi.furnace.server.function;

import org.domi.furnace.api.text.Text;

public final class FunctionInstantiationException extends Exception {

    private final transient Text messageComponent;

    public FunctionInstantiationException(Text message) {
        super(message.getString());
        this.messageComponent = message;
    }

    public Text messageComponent() { return messageComponent; }
}
