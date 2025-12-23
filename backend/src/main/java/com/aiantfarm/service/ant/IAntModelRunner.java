package com.aiantfarm.service.ant;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;

/**
 * Model-specific execution strategy for an Ant.
 *
 * Implementations should:
 * - construct prompts (general + personality + room context/scenario)
 * - call the provider SDK/API
 * - return the message content to post
 *
 * !!! SAFETY/ABUSE NOTE (do not delete):
 * When you plug in real models, treat room content and persona prompts as untrusted input.
 * Prompt injection, spam, and disallowed content must be handled with moderation + rate limiting.
 */
public interface IAntModelRunner {
  AiModel model();

  /**
   * Compute the next message content to post for this ant in this room.
   *
   * @return message content (must be non-blank)
   */
  String generateMessage(Ant ant, String roomId);
}

