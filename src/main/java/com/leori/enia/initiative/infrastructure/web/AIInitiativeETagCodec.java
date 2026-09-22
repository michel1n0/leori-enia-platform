package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.ExpectedRevision;
import com.leori.enia.initiative.domain.AIInitiativeId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
final class AIInitiativeETagCodec {

    String encode(AIInitiativeId id, long revision) {
        return "\"ai-initiative:" + id.value() + ":" + revision + "\"";
    }

    ExpectedRevision decode(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new MissingIfMatchException();
        }
        if (headers.size() != 1) {
            throw new InvalidIfMatchException();
        }
        String header = headers.getFirst();
        if (header == null || !header.startsWith("\"ai-initiative:") || !header.endsWith("\"")
                || header.indexOf(',', 1) >= 0) {
            throw new InvalidIfMatchException();
        }
        String token = header.substring(15, header.length() - 1);
        int separator = token.lastIndexOf(':');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new InvalidIfMatchException();
        }
        try {
            UUID id = UUID.fromString(token.substring(0, separator));
            if (!id.toString().equals(token.substring(0, separator))) {
                throw new InvalidIfMatchException();
            }
            String revisionText = token.substring(separator + 1);
            if (!revisionText.matches("0|[1-9][0-9]*")) {
                throw new InvalidIfMatchException();
            }
            long revision = Long.parseLong(revisionText);
            return new ExpectedRevision(new AIInitiativeId(id), revision);
        } catch (IllegalArgumentException exception) {
            throw new InvalidIfMatchException();
        }
    }

    static final class MissingIfMatchException extends RuntimeException {
    }

    static final class InvalidIfMatchException extends RuntimeException {
    }
}
