package ch.naviqore.service;

public interface LegVisitor<T> {

    T visit(PublicTransitLeg publicTransitLeg);

    T visit(Walk walk);

}