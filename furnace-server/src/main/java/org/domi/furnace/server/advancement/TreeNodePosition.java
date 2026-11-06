// 바닐라 TreeNodePosition — 발전 과제 화면의 배치(Reingold-Tilford). 모든 계산은 바닐라처럼 32비트 float.
package org.domi.furnace.server.advancement;

import java.util.ArrayList;
import java.util.List;

final class TreeNodePosition {

    private final AdvancementNode node;
    private final TreeNodePosition parent;
    private final TreeNodePosition previousSibling;
    private final int childIndex;
    private final List<TreeNodePosition> children = new ArrayList<>();
    private TreeNodePosition ancestor;
    private TreeNodePosition thread;
    private int x;
    private float y;
    private float mod;
    private float change;
    private float shift;

    private TreeNodePosition(AdvancementNode node, TreeNodePosition parent, TreeNodePosition previousSibling, int childIndex, int x) {
        if (node.advancement().display() == null) throw new IllegalArgumentException("Can't position an invisible advancement!");
        this.node = node;
        this.parent = parent;
        this.previousSibling = previousSibling;
        this.childIndex = childIndex;
        this.ancestor = this;
        this.x = x;
        this.y = -1.0f;
        TreeNodePosition last = null;
        for (AdvancementNode child : node.children()) last = addChild(child, last);
    }

    private TreeNodePosition addChild(AdvancementNode n, TreeNodePosition previous) {
        if (n.advancement().display() != null) {
            previous = new TreeNodePosition(n, this, previous, children.size() + 1, x + 1);
            children.add(previous);
        } else {
            for (AdvancementNode c : n.children()) previous = addChild(c, previous);
        }
        return previous;
    }

    private void firstWalk() {
        if (children.isEmpty()) {
            y = previousSibling != null ? previousSibling.y + 1.0f : 0.0f;
        } else {
            TreeNodePosition defaultAncestor = null;
            for (TreeNodePosition child : children) {
                child.firstWalk();
                defaultAncestor = child.apportion(defaultAncestor == null ? child : defaultAncestor);
            }
            executeShifts();
            float midpoint = (children.get(0).y + children.get(children.size() - 1).y) / 2.0f;
            if (previousSibling != null) {
                y = previousSibling.y + 1.0f;
                mod = y - midpoint;
            } else {
                y = midpoint;
            }
        }
    }

    private float secondWalk(float modSum, int depth, float minY) {
        y += modSum;
        x = depth;
        if (y < minY) minY = y;
        for (TreeNodePosition child : children) minY = child.secondWalk(modSum + mod, depth + 1, minY);
        return minY;
    }

    private void thirdWalk(float shiftY) {
        y += shiftY;
        for (TreeNodePosition child : children) child.thirdWalk(shiftY);
    }

    private void executeShifts() {
        float shiftAcc = 0.0f, changeAcc = 0.0f;
        for (int i = children.size() - 1; i >= 0; i--) {
            TreeNodePosition c = children.get(i);
            c.y += shiftAcc;
            c.mod += shiftAcc;
            changeAcc += c.change;
            shiftAcc += c.shift + changeAcc;
        }
    }

    private TreeNodePosition previousOrThread() {
        if (thread != null) return thread;
        return !children.isEmpty() ? children.get(0) : null;
    }

    private TreeNodePosition nextOrThread() {
        if (thread != null) return thread;
        return !children.isEmpty() ? children.get(children.size() - 1) : null;
    }

    private TreeNodePosition apportion(TreeNodePosition defaultAncestor) {
        if (previousSibling == null) return defaultAncestor;
        TreeNodePosition vInsideRight = this, vOutsideRight = this;
        TreeNodePosition vInsideLeft = previousSibling;
        TreeNodePosition vOutsideLeft = parent.children.get(0);
        float sInsideRight = mod, sOutsideRight = mod, sInsideLeft = vInsideLeft.mod, sOutsideLeft = vOutsideLeft.mod;
        while (vInsideLeft.nextOrThread() != null && vInsideRight.previousOrThread() != null) {
            vInsideLeft = vInsideLeft.nextOrThread();
            vInsideRight = vInsideRight.previousOrThread();
            vOutsideLeft = vOutsideLeft.previousOrThread();
            vOutsideRight = vOutsideRight.nextOrThread();
            vOutsideRight.ancestor = this;
            float gap = vInsideLeft.y + sInsideLeft - (vInsideRight.y + sInsideRight) + 1.0f;
            if (gap > 0.0f) {
                vInsideLeft.getAncestor(this, defaultAncestor).moveSubtree(this, gap);
                sInsideRight += gap;
                sOutsideRight += gap;
            }
            sInsideLeft += vInsideLeft.mod;
            sInsideRight += vInsideRight.mod;
            sOutsideLeft += vOutsideLeft.mod;
            sOutsideRight += vOutsideRight.mod;
        }
        if (vInsideLeft.nextOrThread() != null && vOutsideRight.nextOrThread() == null) {
            vOutsideRight.thread = vInsideLeft.nextOrThread();
            vOutsideRight.mod += sInsideLeft - sOutsideRight;
        } else {
            if (vInsideRight.previousOrThread() != null && vOutsideLeft.previousOrThread() == null) {
                vOutsideLeft.thread = vInsideRight.previousOrThread();
                vOutsideLeft.mod += sInsideRight - sOutsideLeft;
            }
            defaultAncestor = this;
        }
        return defaultAncestor;
    }

    private void moveSubtree(TreeNodePosition right, float amount) {
        float subtrees = (float) (right.childIndex - childIndex);
        if (subtrees != 0.0f) {
            right.change -= amount / subtrees;
            change += amount / subtrees;
        }
        right.shift += amount;
        right.y += amount;
        right.mod += amount;
    }

    private TreeNodePosition getAncestor(TreeNodePosition other, TreeNodePosition defaultAncestor) {
        if (ancestor != null) for (TreeNodePosition c : other.parent.children) if (c == ancestor) return ancestor;
        return defaultAncestor;
    }

    private void finalizePosition() {
        DisplayInfo d = node.advancement().display();
        if (d != null) {
            d.x = (float) x;
            d.y = y;
        }
        for (TreeNodePosition child : children) child.finalizePosition();
    }

    static void run(AdvancementNode root) {
        if (root.advancement().display() == null) throw new IllegalArgumentException("Can't position children of an invisible root!");
        TreeNodePosition pos = new TreeNodePosition(root, null, null, 1, 0);
        pos.firstWalk();
        float minY = pos.secondWalk(0.0f, 0, pos.y);
        if (minY < 0.0f) pos.thirdWalk(-minY);
        pos.finalizePosition();
    }
}
