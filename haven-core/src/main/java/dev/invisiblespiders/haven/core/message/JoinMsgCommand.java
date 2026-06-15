package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;

public class JoinMsgCommand extends AbstractMsgCommand {
    public JoinMsgCommand(MessageSettings settings, HavenPlayerService playerService,
                           HavenMessageService messageService, MiniMessageSanitizer sanitizer) {
        super("join", settings.joinPresets(), playerService, messageService, sanitizer);
    }
}
