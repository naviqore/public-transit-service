package ch.naviqore.service;

import ch.naviqore.service.impl.PublicTransitServiceImpl;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PublicTransitServiceFactory {

    private final String gtfsFilePath;

    public PublicTransitService create() {
        return new PublicTransitServiceImpl(gtfsFilePath);
    }

    public static void main(String[] args) {
        PublicTransitService service = new PublicTransitServiceFactory("benchmark/input/switzerland.zip").create();
    }

}
