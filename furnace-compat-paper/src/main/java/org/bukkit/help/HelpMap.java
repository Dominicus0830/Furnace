package org.bukkit.help;

import java.util.Collection;
import java.util.List;

/** 서버 도움말 항목 모음(Bukkit). */
public interface HelpMap {

    HelpTopic getHelpTopic(String topicName);

    Collection<HelpTopic> getHelpTopics();

    void addTopic(HelpTopic topic);

    void clear();

    void registerHelpTopicFactory(Class<?> commandClass, HelpTopicFactory<?> factory);

    List<String> getIgnoredPlugins();
}
