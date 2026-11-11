package org.bukkit.help;

import org.bukkit.command.CommandSender;

/** /help 의 한 항목(Bukkit). 플러그인이 만들어 HelpMap 에 넣는다. */
public abstract class HelpTopic {

    protected String name = "";
    protected String shortText = "";
    protected String fullText = "";
    protected String amendedPermission = null;

    /** 이 사람이 항목을 볼 수 있는가. */
    public abstract boolean canSee(CommandSender player);

    public void amendCanSee(String amendedPermission) { this.amendedPermission = amendedPermission; }

    public String getName() { return name; }

    public String getShortText() { return shortText; }

    public String getFullText(CommandSender forWho) { return fullText; }

    public void amendTopic(String amendedShortText, String amendedFullText) {
        shortText = applyAmendment(shortText, amendedShortText);
        fullText = applyAmendment(fullText, amendedFullText);
    }

    /** amendment 의 "&lt;text&gt;" 자리에 원래 글을 넣는다. amendment 가 null 이면 원래 글. */
    protected String applyAmendment(String baseText, String amendment) {
        return amendment == null ? baseText : amendment.replace("<text>", baseText);
    }
}
