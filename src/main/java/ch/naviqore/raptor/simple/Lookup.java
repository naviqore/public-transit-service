package ch.naviqore.raptor.simple;

import java.util.Map;

record Lookup(Map<String, Integer> stops, Map<String, Integer> routes) {
}
