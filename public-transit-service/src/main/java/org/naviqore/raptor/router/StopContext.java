package org.naviqore.raptor.router;

record StopContext(Transfer[] transfers, Stop[] stops, int[] stopRoutes) {
}
