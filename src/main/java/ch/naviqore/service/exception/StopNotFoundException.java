package ch.naviqore.service.exception;

public class StopNotFoundException extends NotFoundException {

    public StopNotFoundException(String id) {
        super("Stop", id);
    }

}
