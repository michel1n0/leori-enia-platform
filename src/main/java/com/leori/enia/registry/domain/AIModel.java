package com.leori.enia.registry.domain;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.domain.event.AIModelRegistered;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An AI model registered under a specific AI system. */
public final class AIModel {

  private final AIModelId id;
  private final AISystemId systemId;
  private final String name;
  private final String description;
  private final String provider;
  private final Instant createdAt;
  private final List<DomainEvent> domainEvents = new ArrayList<>();

  private AIModel(Builder builder) {
    this(
      builder.id,
      builder.systemId,
      builder.name,
      builder.description,
      builder.provider,
      builder.createdAt
    );

    domainEvents.add(new AIModelRegistered(id, systemId, createdAt));
  }

  private AIModel(
    AIModelId id,
    AISystemId systemId,
    String name,
    String description,
    String provider,
    Instant createdAt
  ) {
    this.id = Objects.requireNonNull(id, "AI model id is required");
    this.systemId = Objects.requireNonNull(systemId, "AI system id is required");
    this.name = requireText(name, "Name is required");
    this.description = requireText(description, "Description is required");
    this.provider = requireText(provider, "Provider is required");
    this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt is required");
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Restores persisted business state without registering a new model or emitting events. */
  public static AIModel rehydrate(
    AIModelId id,
    AISystemId systemId,
    String name,
    String description,
    String provider,
    Instant createdAt
  ) {
    return new AIModel(
      id,
      systemId,
      name,
      description,
      provider,
      createdAt
    );
  }

  private static String requireText(String value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    String normalized = value.trim();
    if (normalized.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  public AIModelId id() {
    return id;
  }

  public AISystemId systemId() {
    return systemId;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public String provider() {
    return provider;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public List<DomainEvent> domainEvents() {
    return List.copyOf(domainEvents);
  }

  public void clearDomainEvents() {
    domainEvents.clear();
  }

  public static final class Builder {

    private AIModelId id;
    private AISystemId systemId;
    private String name;
    private String description;
    private String provider;
    private Instant createdAt;

    private Builder() {
    }

    public Builder id(AIModelId id) {
      this.id = id;
      return this;
    }

    public Builder systemId(AISystemId systemId) {
      this.systemId = systemId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder provider(String provider) {
      this.provider = provider;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public AIModel build() {
      return new AIModel(this);
    }
  }
}
