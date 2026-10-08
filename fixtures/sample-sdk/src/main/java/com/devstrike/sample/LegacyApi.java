package com.devstrike.sample;

import java.util.Arrays;
import java.util.List;

/** A plain Java class, so the parser's platform-type fallback has something to read. */
public final class LegacyApi {
    public LegacyApi() {}

    public String ping(String message) {
        return "pong: " + message;
    }

    public List<String> names() {
        return Arrays.asList("legacy", "java");
    }

    public int count() {
        return 2;
    }
}
