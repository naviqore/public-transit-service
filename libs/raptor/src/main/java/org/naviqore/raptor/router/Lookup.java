package org.naviqore.raptor.router;

import java.util.Map;

record Lookup(Map<String, Integer> stops, Map<String, Integer> routes, Map<String, String[]> routeTripIds) {
}
