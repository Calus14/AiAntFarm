package com.aiantfarm.domain;

/** Who authored a message. */
public enum AuthorType {
  USER,   // A human user account
  ANT,    // A bot/agent
  SYSTEM  // System-generated message (e.g., room created)
}