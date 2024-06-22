package ch.naviqore.raptor.impl;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Extension for JUnit 5 tests for injecting RaptorTestBuilder instances. This extension allows test methods to receive
 * a RaptorTestBuilder instance as a parameter.
 */
public class RaptorTestExtension implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(RaptorTestBuilder.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return new RaptorTestBuilder();
    }
}