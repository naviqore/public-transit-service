package ch.naviqore.raptor.model;

public record StopContext(Transfer[] transfers, Stop[] stops, int[] stopRoutes) {
}
