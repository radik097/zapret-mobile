package dev.zapret.mobile;

/** A downloadable custom strategy: a raw split-position/delay pair applied via the CUSTOM native profile. */
final class StrategyPack {
    final String id;
    final String name;
    final int splitPosition;
    final long delayMs;

    StrategyPack(String id, String name, int splitPosition, long delayMs) {
        this.id = id;
        this.name = name;
        this.splitPosition = splitPosition;
        this.delayMs = delayMs;
    }
}
