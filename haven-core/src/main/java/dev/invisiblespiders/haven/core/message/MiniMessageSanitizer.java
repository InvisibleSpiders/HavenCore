package dev.invisiblespiders.haven.core.message;

import java.util.List;
import java.util.regex.Pattern;

public class MiniMessageSanitizer {

    private static final List<String> ALLOWED_OPEN_TAGS = List.of(
            "color", "colour", "gradient", "rainbow", "bold", "b", "italic", "i",
            "underlined", "u", "strikethrough", "st", "obfuscated", "obf", "reset",
            "newline", "br",
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white"
    );

    private static final List<String> BLOCKED_TAG_PREFIXES = List.of(
            "click", "hover", "insertion", "font", "transition", "selector", "nbt",
            "score", "translate", "lang", "key"
    );

    private static final Pattern TAG_PATTERN = Pattern.compile("<([^/>][^>]*)>");

    private final FilterSettings filter;
    private final List<Pattern> compiledPatterns;

    public MiniMessageSanitizer(FilterSettings filter) {
        this.filter = filter;
        this.compiledPatterns = filter.blockedPatterns().stream()
                .map(Pattern::compile)
                .toList();
    }

    public String sanitize(String input) {
        if (input.length() > filter.maxLength()) {
            throw new BlockedContentException(
                    "Message exceeds maximum length of " + filter.maxLength() + " characters.");
        }
        String stripped = stripBlockedTags(input);
        String plainText = stripped.replaceAll("<[^>]*>", "");
        for (Pattern pattern : compiledPatterns) {
            if (pattern.matcher(plainText).find()) {
                throw new BlockedContentException("Message contains blocked content.");
            }
        }
        return stripped;
    }

    private String stripBlockedTags(String input) {
        var sb = new StringBuilder();
        var matcher = TAG_PATTERN.matcher(input);
        int lastEnd = 0;
        while (matcher.find()) {
            String tagContent = matcher.group(1).toLowerCase().trim();
            String tagName = tagContent.startsWith("/") ? tagContent.substring(1) : tagContent;
            int colon = tagName.indexOf(':');
            String baseTagName = colon >= 0 ? tagName.substring(0, colon) : tagName;
            int space = baseTagName.indexOf(' ');
            if (space >= 0) baseTagName = baseTagName.substring(0, space);

            final String finalBase = baseTagName;
            boolean blocked = BLOCKED_TAG_PREFIXES.stream().anyMatch(b -> finalBase.startsWith(b));
            boolean allowed = !blocked && (
                    ALLOWED_OPEN_TAGS.contains(finalBase)
                    || finalBase.startsWith("#")
                    || finalBase.isEmpty()
            );

            if (allowed) {
                sb.append(input, lastEnd, matcher.end());
            } else {
                sb.append(input, lastEnd, matcher.start());
            }
            lastEnd = matcher.end();
        }
        sb.append(input, lastEnd, input.length());
        return sb.toString();
    }

    public static class BlockedContentException extends RuntimeException {
        public BlockedContentException(String message) { super(message); }
    }
}
