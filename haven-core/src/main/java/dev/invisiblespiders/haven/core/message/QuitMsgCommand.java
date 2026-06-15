package dev.invisiblespiders.haven.core.message;

import dev.invisiblespiders.haven.api.service.HavenMessageService;
import dev.invisiblespiders.haven.api.service.HavenPlayerService;

public class QuitMsgCommand extends AbstractMsgCommand {
    public QuitMsgCommand(MessageSettings settings, HavenPlayerService playerService,
                           HavenMessageService messageService, MiniMessageSanitizer sanitizer) {
        super("quit", settings.quitPresets(), playerService, messageService, sanitizer);
    }
}
