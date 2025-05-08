package org.naviqore.service.repo;

public class NoRepositoryConfiguredException extends IllegalStateException {
    public NoRepositoryConfiguredException(String message) {
        super(message);
    }
}