package org.naviqore.raptor.router;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Extension for JUnit 5 tests for injecting RaptorTestBuilder instances. This extension allows test methods to receive
 * a RaptorTestBuilder instance as a parameter.
 */
public class RaptorRouterTestExtension implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, @NonNull ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(RaptorRouterTestBuilder.class);
    }

    @Override
    public Object resolveParameter(@NonNull ParameterContext parameterContext,
                                   @NonNull ExtensionContext extensionContext) {
        return new RaptorRouterTestBuilder();
    }
}