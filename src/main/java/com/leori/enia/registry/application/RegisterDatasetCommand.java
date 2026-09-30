package com.leori.enia.registry.application;

public record RegisterDatasetCommand(
        String name,
        String description
) {
}
