package ch.naviqore.raptor;

import java.util.Map;

record Lookup(Map<String, Integer> stops, Map<String, Integer> routes) {
}
