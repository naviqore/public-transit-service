package ch.naviqore.service;

public interface ConnectionQueryConfiguration {
    int getMaximumWalkingDuration();

    int getMinimumTransferDuration();

    int getMaximumTransferNumber();

    int getMaximumTravelTime();
}
