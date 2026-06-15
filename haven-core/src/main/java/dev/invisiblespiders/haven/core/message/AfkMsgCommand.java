package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;

public class AfkMsgCommand extends AbstractMsgCommand {
    public AfkMsgCommand(MessageSettings settings, HavenPlayerService playerService,
                          HavenMessageService messageService, MiniMessageSanitizer sanitizer) {
        super("afk", settings.afkPresets(), playerService, messageService, sanitizer);
    }
}
