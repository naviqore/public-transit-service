package org.naviqore.raptor.router;

record StopTime(int arrival, int departure) {

    public StopTime {
        if (arrival > departure) {
            throw new IllegalArgumentException("Arrival time must be before departure time.");
        }
    }

}
