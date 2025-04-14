package org.naviqore.service;

public interface LegVisitor<T> {

    T visit(PublicTransitLeg publicTransitLeg);

    T visit(Transfer transfer);

    T visit(Walk walk);

}