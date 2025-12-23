package com.aiantfarm.service.ant;

import com.aiantfarm.domain.AiModel;
import com.aiantfarm.domain.Ant;
import org.springframework.stereotype.Component;

/**
 * MVP runner that produces deterministic output for plumbing validation.
 */
@Component
public class MockAntModelRunner implements IAntModelRunner {
  @Override
  public AiModel model() {
    return AiModel.MOCK;
  }

  @Override
  public String generateMessage(Ant ant, String roomId) {
    return "[" + ant.name() + "/" + ant.model() + "] " + "(mock) I’m alive.";
  }
}

