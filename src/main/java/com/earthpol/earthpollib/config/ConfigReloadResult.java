package com.earthpol.earthpollib.config;

import java.util.Map;
import java.util.Set;

/** Validation errors prevent application. Restart-only changes remain on disk until the next startup. */
public record ConfigReloadResult(boolean applied, Map<String, String> errors, Set<String> restartRequired) {
    public ConfigReloadResult {
        errors = Map.copyOf(errors);
        restartRequired = Set.copyOf(restartRequired);
    }
}
