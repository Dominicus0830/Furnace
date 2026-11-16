// S→C commands(0x11) — 소스가 쓸 수 있는 노드만 남긴 명령 트리(바닐라 Commands.sendCommands + ClientboundCommandsPacket).
// 노드 번호는 바닐라 enumerateNodes 처럼 루트부터 너비 우선(자식 → 리다이렉트 대상 순).
package org.domi.furnace.server.command;

import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.command.brigadier.ArgumentCommandNode;
import org.domi.furnace.server.command.brigadier.CommandNode;
import org.domi.furnace.server.command.brigadier.LiteralCommandNode;
import org.domi.furnace.server.command.brigadier.RootCommandNode;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public final class CommandTreePacket implements Packet {

    public static final int ID = 0x11;
    private static final int TYPE_ROOT = 0, TYPE_LITERAL = 1, TYPE_ARGUMENT = 2;
    private static final int FLAG_EXECUTABLE = 0x04, FLAG_REDIRECT = 0x08, FLAG_SUGGESTIONS = 0x10;

    private final byte[] body;

    private CommandTreePacket(byte[] body) { this.body = body; }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { buf.writeBytes(body); }

    /** source 가 쓸 수 있는 노드로만 이루어진 트리. */
    public static CommandTreePacket forSource(RootCommandNode<CommandSource> root, CommandSource source) {
        return forSource(root, source, true);
    }

    /**
     * sendNamespaced=false 면 이름에 ':' 가 든 노드(루트의 minecraft:give·플러그인:명령, 하위 리터럴 포함 — Spigot fillUsableCommands 처럼 모든 단계)를 보내지 않는다
     * (Paper spigot.yml commands.send-namespaced=false — 서버에서는 계속 실행된다).
     */
    public static CommandTreePacket forSource(RootCommandNode<CommandSource> root, CommandSource source, boolean sendNamespaced) {
        return forSource(root, source, sendNamespaced, null);
    }

    /**
     * rootNames 가 있으면 사용 가능한 최상위 명령 이름(트리 순서)을 변경 가능한 집합으로 넘기고, 돌아온 뒤 집합에 없는 최상위 노드를 루트의 자식에서 뺀다
     * (CraftBukkit Commands.sendCommands: PlayerCommandSendEvent 뒤 rootcommandnode.removeCommand). 뺀 노드가 다른 노드의 리다이렉트 대상이면 트리에는 그대로 실린다
     * (바닐라는 루트에서만 지우고 번호 매기기가 리다이렉트를 따라가므로) — 번호는 빼고 난 뒤 너비 우선으로 매긴다.
     */
    public static CommandTreePacket forSource(RootCommandNode<CommandSource> root, CommandSource source, boolean sendNamespaced,
                                              java.util.function.Consumer<java.util.Set<String>> rootNames) {
        // 1) 사용 가능한 자식만 남긴 그림자 트리(원본 노드 → 남은 자식 목록)
        Map<CommandNode<CommandSource>, List<CommandNode<CommandSource>>> usable = new IdentityHashMap<>();
        collect(root, source, usable, sendNamespaced);
        if (rootNames != null) {
            List<CommandNode<CommandSource>> top = usable.get(root);
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (CommandNode<CommandSource> c : top) names.add(c.getName());
            rootNames.accept(names);
            usable.put(root, top.stream().filter(c -> names.contains(c.getName())).toList());
        }
        // 2) 너비 우선 번호
        Map<CommandNode<CommandSource>, Integer> index = new IdentityHashMap<>();
        List<CommandNode<CommandSource>> order = new ArrayList<>();
        ArrayDeque<CommandNode<CommandSource>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            CommandNode<CommandSource> node = queue.poll();
            if (index.containsKey(node)) continue;
            index.put(node, order.size());
            order.add(node);
            queue.addAll(usable.getOrDefault(node, List.of()));
            if (node.getRedirect() != null) queue.add(node.getRedirect());
        }
        ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        try {
            Types.writeVarInt(buf, order.size());
            for (CommandNode<CommandSource> node : order) writeNode(buf, node, usable.getOrDefault(node, List.of()), index);
            Types.writeVarInt(buf, index.get(root));
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return new CommandTreePacket(out);
        } finally {
            buf.release();
        }
    }

    private static void collect(CommandNode<CommandSource> node, CommandSource source,
                                Map<CommandNode<CommandSource>, List<CommandNode<CommandSource>>> usable, boolean sendNamespaced) {
        List<CommandNode<CommandSource>> kept = new ArrayList<>();
        for (CommandNode<CommandSource> child : node.getChildren()) {
            if (!sendNamespaced && child.getName().indexOf(':') >= 0) continue;   // Paper/Spigot: 모든 단계에서 ':' 든 자식을 뺀다
            if (!child.canUse(source)) continue;
            kept.add(child);
            if (!usable.containsKey(child)) collect(child, source, usable, sendNamespaced);
        }
        usable.put(node, kept);
    }

    private static void writeNode(ByteBuf buf, CommandNode<CommandSource> node, List<CommandNode<CommandSource>> children,
                                  Map<CommandNode<CommandSource>, Integer> index) {
        int flags;
        if (node instanceof RootCommandNode) flags = TYPE_ROOT;
        else if (node instanceof LiteralCommandNode) flags = TYPE_LITERAL;
        else flags = TYPE_ARGUMENT;
        if (node.getCommand() != null) flags |= FLAG_EXECUTABLE;
        boolean redirect = node.getRedirect() != null && index.containsKey(node.getRedirect());
        if (redirect) flags |= FLAG_REDIRECT;
        ArgumentCommandNode<CommandSource, ?> arg = node instanceof ArgumentCommandNode<CommandSource, ?> a ? a : null;
        if (arg != null && arg.getCustomSuggestions() != null) flags |= FLAG_SUGGESTIONS;
        buf.writeByte(flags);
        Types.writeVarInt(buf, children.size());
        for (CommandNode<CommandSource> c : children) Types.writeVarInt(buf, index.get(c));
        if (redirect) Types.writeVarInt(buf, index.get(node.getRedirect()));
        if (node instanceof LiteralCommandNode<CommandSource> lit) {
            Types.writeString(buf, lit.getLiteral());
        } else if (arg != null) {
            Types.writeString(buf, arg.getName());
            Types.writeVarInt(buf, ArgumentTypeIds.idOf(arg.getType().networkId()));
            arg.getType().writeProperties(buf);
            if (arg.getCustomSuggestions() != null) {
                Types.writeString(buf, arg.getCustomSuggestions() instanceof NamedSuggestionProvider n ? n.id() : NamedSuggestionProvider.ASK_SERVER);
            }
        }
    }
}
