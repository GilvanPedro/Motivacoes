package com.motivacional.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "  dQw4w9WgXcQ  ",
    })
    void extractsId(String input) {
        assertEquals(Optional.of("dQw4w9WgXcQ"), YouTube.extractId(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://vimeo.com/123456", "https://www.youtube.com/", "abc"})
    void rejectsInvalid(String input) {
        assertTrue(YouTube.extractId(input).isEmpty());
    }
}
