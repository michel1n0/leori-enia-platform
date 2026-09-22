package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.domain.AIInitiativeId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AIInitiativeETagCodecTest {

    private final AIInitiativeETagCodec codec = new AIInitiativeETagCodec();
    private final AIInitiativeId id = AIInitiativeId.generate();

    @Test
    void strong_tag_round_trips() {
        String tag = codec.encode(id, 7);
        assertEquals("\"ai-initiative:" + id.value() + ":7\"", tag);
        assertEquals(7, codec.decode(List.of(tag)).value());
        assertEquals(id, codec.decode(List.of(tag)).initiativeId());
    }

    @Test
    void rejects_missing_malformed_weak_wildcard_and_lists() {
        assertThrows(AIInitiativeETagCodec.MissingIfMatchException.class,
                () -> codec.decode(null));
        for (String header : List.of("bad", "W/" + codec.encode(id, 0), "*",
                codec.encode(id, 0) + ", " + codec.encode(id, 1),
                "\"ai-initiative:bad:0\"", "\"ai-initiative:" + id.value() + ":-1\"")) {
            assertThrows(AIInitiativeETagCodec.InvalidIfMatchException.class,
                    () -> codec.decode(List.of(header)));
        }
        assertThrows(AIInitiativeETagCodec.InvalidIfMatchException.class,
                () -> codec.decode(List.of(codec.encode(id, 0), codec.encode(id, 1))));
    }

    @Test
    void another_initiatives_tag_remains_valid_syntax_for_application_comparison() {
        AIInitiativeId other = AIInitiativeId.generate();
        assertEquals(other, codec.decode(List.of(codec.encode(other, 0))).initiativeId());
    }
}
