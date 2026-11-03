package org.naviqore.timetable.model;

import org.naviqore.timetable.type.TransferType;

import java.util.Optional;

/**
 * A transfer from one stop to another, or within a stop.
 */
public interface Transfer {

    Stop getFromStop();

    Stop getToStop();

    TransferType getTransferType();

    /**
     * Retrieves the minimum time in seconds needed for the transfer, if the transfer defines one.
     *
     * @return the minimum transfer time
     */
    Optional<Integer> getMinTransferTime();

}
