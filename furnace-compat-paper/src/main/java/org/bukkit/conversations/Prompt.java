// 대화의 한 단계: 보여 줄 문구, 입력을 기다리는가, 입력을 받아 다음 단계를 고른다.
package org.bukkit.conversations;

public interface Prompt extends Cloneable {

    /** 다음 단계가 없다 — 이것을 돌려주면 대화가 끝난다. */
    Prompt END_OF_CONVERSATION = null;

    String getPromptText(ConversationContext context);

    /** 참이면 대답을 기다린다. 거짓이면 문구를 보여 준 뒤 곧바로 {@link #acceptInput}(null)으로 넘어간다. */
    boolean blocksForInput(ConversationContext context);

    /** 입력을 받아 다음 단계를 돌려준다(끝이면 {@link #END_OF_CONVERSATION}). */
    Prompt acceptInput(ConversationContext context, String input);
}
