package ch.naviqore.service.exception;

public class RouteNotFoundException extends NotFoundException {

    public RouteNotFoundException(String id) {
        super("Route", id);
    }

}
