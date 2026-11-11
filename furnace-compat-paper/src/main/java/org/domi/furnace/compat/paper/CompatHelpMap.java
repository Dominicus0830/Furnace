package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.Command;
import org.bukkit.help.HelpMap;
import org.bukkit.help.HelpTopic;
import org.bukkit.help.HelpTopicFactory;

/**
 * Bukkit HelpMap. 플러그인이 넣은 항목·공장을 보관하고 돌려준다(CraftBukkit SimpleHelpMap 처럼 이름은 대소문자 무시).
 * Furnace 의 /help 는 바닐라 명령 트리 도움말이라 여기 넣은 항목을 아직 보이지 않는다 — 문서 docs/api/compat-paper.md.
 */
public final class CompatHelpMap implements HelpMap {

    private final Map<String, HelpTopic> topics = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<Class<?>, HelpTopicFactory<?>> factories = new ConcurrentHashMap<>();

    @Override
    public synchronized HelpTopic getHelpTopic(String topicName) {
        if (topicName == null) return null;
        if (topicName.isEmpty()) return null;
        return topics.get(topicName);
    }

    @Override
    public synchronized Collection<HelpTopic> getHelpTopics() { return List.copyOf(topics.values()); }

    @Override
    public synchronized void addTopic(HelpTopic topic) {
        if (topic == null) throw new IllegalArgumentException("topic 이 null");
        topics.putIfAbsent(topic.getName(), topic);   // CraftBukkit: 이미 있는 이름은 덮지 않는다
    }

    @Override
    public synchronized void clear() { topics.clear(); }

    @Override
    public void registerHelpTopicFactory(Class<?> commandClass, HelpTopicFactory<?> factory) {
        // CraftBukkit: Command 하위 클래스이거나 CommandExecutor 구현이어야 한다
        if (!Command.class.isAssignableFrom(commandClass) && !org.bukkit.command.CommandExecutor.class.isAssignableFrom(commandClass)) {
            throw new IllegalArgumentException("commandClass must implement either Command or CommandExecutor!");
        }
        factories.put(commandClass, factory);
    }

    /** 시험·진단용: 등록된 공장. */
    public HelpTopicFactory<?> factoryFor(Class<?> commandClass) { return factories.get(commandClass); }

    @Override
    public List<String> getIgnoredPlugins() { return new ArrayList<>(); }
}
