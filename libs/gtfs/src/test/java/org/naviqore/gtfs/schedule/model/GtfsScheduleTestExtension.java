package org.naviqore.gtfs.schedule.model;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Extension for JUnit 5 tests for injecting GtfsScheduleTestBuilder instances. This extension allows test methods to
 * receive a GtfsScheduleTestBuilder instance as a parameter.
 *
 * @author munterfi
 */
public class GtfsScheduleTestExtension implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, @NonNull ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(GtfsScheduleTestBuilder.class);
    }

    @Override
    public Object resolveParameter(@NonNull ParameterContext parameterContext,
                                   @NonNull ExtensionContext extensionContext) {
        return new GtfsScheduleTestBuilder();
    }
}
