package com.aiantfarm.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Captures a single execution of an Ant in a specific room.
 *
 * Long-term intent: show users what their Ant is "thinking" and why it fails.
 * For now we store minimal notes + error for visibility.
 */
public record AntRun(
    String id,
    String antId,
    String ownerUserId,
    String roomId,
    Instant startedAt,
    Instant finishedAt,
    AntRunStatus status,
    String antNotes,
    String error
) {

  public static AntRun started(String antId, String ownerUserId, String roomId) {
    Objects.requireNonNull(antId, "antId");
    Objects.requireNonNull(ownerUserId, "ownerUserId");
    Objects.requireNonNull(roomId, "roomId");

    return new AntRun(
        UUID.randomUUID().toString(),
        antId,
        ownerUserId,
        roomId,
        Instant.now(),
        null,
        AntRunStatus.RUNNING,
        null,
        null
    );
  }

  public AntRun succeeded(String antNotes) {
    return new AntRun(
        this.id,
        this.antId,
        this.ownerUserId,
        this.roomId,
        this.startedAt,
        Instant.now(),
        AntRunStatus.SUCCEEDED,
        antNotes,
        null
    );
  }

  public AntRun failed(String antNotes, String error) {
    return new AntRun(
        this.id,
        this.antId,
        this.ownerUserId,
        this.roomId,
        this.startedAt,
        Instant.now(),
        AntRunStatus.FAILED,
        antNotes,
        error
    );
  }
}

