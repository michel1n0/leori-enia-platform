package com.leori.enia.registry.domain;

import com.leori.enia.registry.domain.event.DatasetRegistered;
import com.leori.enia.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A dataset registered in the AI governance registry. */
public final class Dataset {

  private final DatasetId id;
  private final String name;
  private final String description;
  private final Instant createdAt;
  private final List<DomainEvent> domainEvents = new ArrayList<>();

  private Dataset(Builder builder) {
    this(
      builder.id,
      builder.name,
      builder.description,
      builder.createdAt
    );

    domainEvents.add(new DatasetRegistered(id, createdAt));
  }

  private Dataset(
    DatasetId id,
    String name,
    String description,
    Instant createdAt
  ) {
    this.id = Objects.requireNonNull(id, "Dataset id is required");
    this.name = requireText(name, "Name is required");
    this.description = requireText(description, "Description is required");
    this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt is required");
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Restores persisted business state without registering a new dataset or emitting events. */
  public static Dataset rehydrate(
    DatasetId id,
    String name,
    String description,
    Instant createdAt
  ) {
    return new Dataset(id, name, description, createdAt);
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

  public DatasetId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
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

    private DatasetId id;
    private String name;
    private String description;
    private Instant createdAt;

    private Builder() {
    }

    public Builder id(DatasetId id) {
      this.id = id;
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

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Dataset build() {
      return new Dataset(this);
    }
  }
}
