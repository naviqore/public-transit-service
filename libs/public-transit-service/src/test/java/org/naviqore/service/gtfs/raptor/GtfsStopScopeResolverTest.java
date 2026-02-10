package org.naviqore.service.gtfs.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.Stop;
import org.naviqore.service.StopScope;
import org.naviqore.utils.spatial.index.KDTree;
import org.naviqore.utils.spatial.index.KDTreeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GtfsStopScopeResolverTest {

    private static final int RADIUS = 500;
    private GtfsSchedule schedule;
    private GtfsStopScopeResolver resolver;
    private KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialIndex;

    @BeforeEach
    void setUp() {
        schedule = new GtfsRaptorTestSchedule().build();
        spatialIndex = new KDTreeBuilder<org.naviqore.gtfs.schedule.model.Stop>().addLocations(
                schedule.getStops().values()).build();
        resolver = new GtfsStopScopeResolver(schedule, spatialIndex, RADIUS);
    }

    @Test
    void resolve_strict_shouldReturnOnlyProvidedId() {
        Stop input = TypeMapper.map(schedule.getStops().get("B1"));
        Set<String> result = resolver.resolve(input, StopScope.STRICT);

        assertEquals(1, result.size());
        assertTrue(result.contains("B1"));
        assertFalse(result.contains("B2"));
    }

    @Test
    void resolve_children_shouldCollectDescendants() {
        // B is parent of B1 and B2
        Stop input = TypeMapper.map(schedule.getStops().get("B"));
        Set<String> result = resolver.resolve(input, StopScope.CHILDREN);

        assertEquals(3, result.size());
        assertTrue(result.containsAll(Set.of("B", "B1", "B2")));
    }

    @Test
    void resolve_related_shouldFindStationSiblings() {
        // start from B1 (child), RELATED should climb to B and find B, B1, B2
        Stop input = TypeMapper.map(schedule.getStops().get("B1"));
        Set<String> result = resolver.resolve(input, StopScope.RELATED);

        assertEquals(3, result.size());
        assertTrue(result.containsAll(Set.of("B", "B1", "B2")));
    }

    @Test
    void resolve_nearby_shouldUseSpatialIndex() {
        // Stop A is far from B/C/D
        Stop input = TypeMapper.map(schedule.getStops().get("A"));
        Set<String> result = resolver.resolve(input, StopScope.NEARBY);

        // only A should be in its own 500m radius
        assertTrue(result.contains("A"));
        assertFalse(result.contains("B"));
    }

    @Test
    void findRoot_cycleDetection_shouldNotLoopInfinitely() {
        // create mocks for the cycle test to bypass builder validations
        org.naviqore.gtfs.schedule.model.Stop stopA = mock(org.naviqore.gtfs.schedule.model.Stop.class);
        org.naviqore.gtfs.schedule.model.Stop stopB = mock(org.naviqore.gtfs.schedule.model.Stop.class);

        when(stopA.getId()).thenReturn("A");
        when(stopB.getId()).thenReturn("B");

        // setup cycle: A -> B -> A
        when(stopA.getParent()).thenReturn(Optional.of(stopB));
        when(stopB.getParent()).thenReturn(Optional.of(stopA));

        Map<String, org.naviqore.gtfs.schedule.model.Stop> brokenMap = new HashMap<>();
        brokenMap.put("A", stopA);
        brokenMap.put("B", stopB);

        GtfsSchedule brokenSchedule = mock(GtfsSchedule.class);
        when(brokenSchedule.getStops()).thenReturn(brokenMap);

        GtfsStopScopeResolver cycleResolver = new GtfsStopScopeResolver(brokenSchedule, spatialIndex, RADIUS);
        Stop input = TypeMapper.map(stopA);

        assertDoesNotThrow(() -> cycleResolver.resolve(input, StopScope.RELATED));
    }
}