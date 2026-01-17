package com.aiantfarm.service.ant.runner;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * Loads prompt templates from classpath resources.
 *
 * Resource: /prompts.properties
 * Keys: prompt.*
 * Placeholders: {name}
 */
public final class PromptTemplates {
  private PromptTemplates() {}

  private static final String RESOURCE = "/prompts.properties";
  private static final Properties PROPS = load();

  private static Properties load() {
    try (InputStream is = PromptTemplates.class.getResourceAsStream(RESOURCE)) {
      if (is == null) {
        throw new IllegalStateException("Missing resource " + RESOURCE);
      }
      Properties p = new Properties();
      p.load(new InputStreamReader(is, StandardCharsets.ISO_8859_1));
      return p;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load prompt templates from " + RESOURCE, e);
    }
  }

  public static String render(String key, Map<String, String> values) {
    String template = PROPS.getProperty(key);
    if (template == null) {
      throw new IllegalArgumentException("Unknown prompt template key: " + key);
    }

    String out = template;
    if (values != null) {
      for (var entry : values.entrySet()) {
        String k = entry.getKey();
        String v = entry.getValue();
        out = out.replace("{" + k + "}", v == null ? "" : v);
      }
    }

    // remove unresolved placeholders (optional sections)
    out = out.replaceAll("\\{[a-zA-Z0-9_]+}", "");

    return out;
  }
}
