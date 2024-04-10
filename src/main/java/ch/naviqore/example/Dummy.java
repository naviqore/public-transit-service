package ch.naviqore.example;

import lombok.RequiredArgsConstructor;

/**
 * Dummy class
 *
 * @author munterfi
 */
@RequiredArgsConstructor
public class Dummy {

    private final Container container;

    public int add(int a) {
        int newValue = container.getValue() + a;
        container.setValue(newValue);
        return newValue;
    }
}
