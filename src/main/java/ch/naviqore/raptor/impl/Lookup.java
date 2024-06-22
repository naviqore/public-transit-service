package ch.naviqore.raptor.impl;

import java.util.Map;

record Lookup(Map<String, Integer> stops, Map<String, Integer> routes) {
}
