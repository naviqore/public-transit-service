package ch.naviqore.service.gtfsraptor;

/**
 * Represents a walk between two points. Is only intended to be used in the {@link WalkCalculator}.
 *
 * @param duration Duration in seconds.
 * @param distance Distance in meters.
 */
public record Walk(int duration, int distance) {
}
