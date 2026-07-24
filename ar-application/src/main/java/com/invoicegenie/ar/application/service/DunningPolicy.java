package com.invoicegenie.ar.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simple dunning policy: days-past-due thresholds map to levels (1-based).
 *
 * <p>Defaults: 30 → level 1, 60 → level 2, 90 → level 3.
 * Configured via {@code invoicegenie.dunning.levels} (comma-separated days).
 */
public final class DunningPolicy {

    private final List<Integer> levelDays;
    private final boolean enabled;

    public DunningPolicy(boolean enabled, List<Integer> levelDays) {
        this.enabled = enabled;
        if (levelDays == null || levelDays.isEmpty()) {
            this.levelDays = List.of(30, 60, 90);
        } else {
            List<Integer> sorted = new ArrayList<>(levelDays);
            Collections.sort(sorted);
            this.levelDays = List.copyOf(sorted);
        }
    }

    public static DunningPolicy defaults() {
        return new DunningPolicy(true, List.of(30, 60, 90));
    }

    public static DunningPolicy parse(boolean enabled, String levelsCsv) {
        if (levelsCsv == null || levelsCsv.isBlank()) {
            return new DunningPolicy(enabled, List.of(30, 60, 90));
        }
        List<Integer> days = Arrays.stream(levelsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
        return new DunningPolicy(enabled, days);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Integer> levelDays() {
        return levelDays;
    }

    /**
     * @return dunning level 1..N when daysPastDue meets a threshold; 0 if none
     */
    public int levelFor(int daysPastDue) {
        if (daysPastDue <= 0) {
            return 0;
        }
        int level = 0;
        for (int i = 0; i < levelDays.size(); i++) {
            if (daysPastDue >= levelDays.get(i)) {
                level = i + 1;
            }
        }
        return level;
    }
}