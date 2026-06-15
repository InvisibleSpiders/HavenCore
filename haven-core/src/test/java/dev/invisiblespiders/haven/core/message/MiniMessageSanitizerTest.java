package dev.invisiblespiders.haven.core.message;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniMessageSanitizerTest {

    private final MiniMessageSanitizer sanitizer = new MiniMessageSanitizer(
            new FilterSettings(50, List.of("(?i)badword", "(?i)https?://")));

    @Test
    void allowsPlainText() {
        assertThat(sanitizer.sanitize("Hello world")).isEqualTo("Hello world");
    }

    @Test
    void allowsColorTags() {
        assertThat(sanitizer.sanitize("<red>Hello</red>")).isEqualTo("<red>Hello</red>");
    }

    @Test
    void allowsGradientTag() {
        assertThat(sanitizer.sanitize("<gradient:red:blue>text</gradient>"))
                .isEqualTo("<gradient:red:blue>text</gradient>");
    }

    @Test
    void stripsClickTag() {
        String result = sanitizer.sanitize("<click:run_command:/op badguy>text</click>");
        assertThat(result).doesNotContain("<click");
        assertThat(result).contains("text");
    }

    @Test
    void stripsHoverTag() {
        String result = sanitizer.sanitize("<hover:show_text:'evil'>text</hover>");
        assertThat(result).doesNotContain("<hover");
    }

    @Test
    void stripsInsertionTag() {
        String result = sanitizer.sanitize("<insertion:secret>text</insertion>");
        assertThat(result).doesNotContain("<insertion");
    }

    @Test
    void throwsOnBlockedPattern() {
        assertThatThrownBy(() -> sanitizer.sanitize("buy at https://spamsite.com"))
                .isInstanceOf(MiniMessageSanitizer.BlockedContentException.class);
    }

    @Test
    void throwsOnBlockedWord() {
        assertThatThrownBy(() -> sanitizer.sanitize("you are a badword"))
                .isInstanceOf(MiniMessageSanitizer.BlockedContentException.class);
    }

    @Test
    void throwsWhenExceedsMaxLength() {
        String longInput = "a".repeat(51);
        assertThatThrownBy(() -> sanitizer.sanitize(longInput))
                .isInstanceOf(MiniMessageSanitizer.BlockedContentException.class)
                .hasMessageContaining("length");
    }

    @Test
    void allowsMaxLengthExactly() {
        String input = "a".repeat(50);
        assertThat(sanitizer.sanitize(input)).isEqualTo(input);
    }
}
