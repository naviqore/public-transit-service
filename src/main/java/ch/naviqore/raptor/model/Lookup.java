package ch.naviqore.raptor.model;

import java.util.Map;

public record Lookup(Map<String, Integer> stops, Map<String, Integer> routes) {
}
