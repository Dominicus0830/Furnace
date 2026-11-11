package org.bukkit.help;

import org.bukkit.command.Command;

/** 명령 종류마다 HelpTopic 을 만드는 공장(Bukkit). null 을 돌려주면 그 명령은 도움말에 넣지 않는다. */
public interface HelpTopicFactory<TCommand extends Command> {

    HelpTopic createTopic(TCommand command);
}
