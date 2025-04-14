package org.naviqore.service;

public interface Leg {

    LegType getLegType();

    <T> T accept(LegVisitor<T> visitor);

    int getDistance();

    int getDuration();

}
