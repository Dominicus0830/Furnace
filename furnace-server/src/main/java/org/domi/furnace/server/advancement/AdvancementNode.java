// 바닐라 AdvancementNode — 트리의 한 칸. 자식 순서는 넣은 순서(바닐라는 정체성 해시 순이라 고정되지 않는다).
package org.domi.furnace.server.advancement;

import java.util.ArrayList;
import java.util.List;

public final class AdvancementNode {

    private final Advancement advancement;
    private final AdvancementNode parent;
    private final List<AdvancementNode> children = new ArrayList<>();

    AdvancementNode(Advancement advancement, AdvancementNode parent) {
        this.advancement = advancement;
        this.parent = parent;
    }

    public Advancement advancement() { return advancement; }

    public AdvancementNode parent() { return parent; }

    public List<AdvancementNode> children() { return children; }

    public AdvancementNode root() {
        AdvancementNode n = this;
        while (n.parent != null) n = n.parent;
        return n;
    }

    // 바닐라 AdvancementNode.equals/hashCode = 보관자(id) 기준 — HashSet 순서가 id 해시를 따른다.
    @Override public int hashCode() { return advancement.id().hashCode(); }

    @Override public boolean equals(Object o) { return o instanceof AdvancementNode n && n.advancement.id().equals(advancement.id()); }
}
