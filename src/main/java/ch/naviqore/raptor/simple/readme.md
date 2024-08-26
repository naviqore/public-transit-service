# A Simple Raptor Algorithm Implementation

This package presents a straightforward implementation of the Raptor algorithm. This implementation is a copy of the
implementation released in version 0.3.0 (commit #6ed7904) of the algorithm. Notably, the routeLatestDeparture method
has been deliberately removed, which allowed for the elimination of all "time-type" checks within the routing algorithm.

This implementation directly borrows from an earlier version of the code. Consequently, there is a significant amount of
code duplication, when compared to the "router" package implementation. However, this is considered acceptable within
the context of this project, as the code serves as conceptual proof rather than a foundation for future enhancements.