package ch.naviqore.raptor.model;

import java.util.Map;

record Lookup(Map<String, Integer> stops, Map<String, Integer> routes) {
}
