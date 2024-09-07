package ch.naviqore.service.gtfs.nativecxx.raptor;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

@Slf4j
public class NativeGtfsRaptor {

    public static void main(String[] args) throws Throwable {

        String relativePath = "src/main/java/ch/naviqore/service/gtfs/nativecxx/raptor";
        String absolutePath = System.getProperty("user.dir") + File.separator + relativePath;
        System.setProperty("java.library.path", absolutePath);

        //IMPORTANT: Load the libraries in the correct order!
        System.load(absolutePath + File.separator + "otherLibrary.dll");
        System.load(absolutePath + File.separator + "library.dll");

        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.loaderLookup();

        /* TEST FUNCTION TO ADD TWO NUMBERS */
        {
            MemorySegment addNumbersSymbol = lookup.find("addNumbers").orElseThrow();

            MethodHandle addNumbers = linker.downcallHandle(addNumbersSymbol,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            int result = (int) addNumbers.invoke(10, 15);

            log.info("Result of add_numbers(10, 15): {}", result);
        }
        /* TEST FUNCTION TO ADD ONE (using shared libraries) */
        {
            MemorySegment addOneSymbol = lookup.find("addOne").orElseThrow();

            MethodHandle addNumbers = linker.downcallHandle(addOneSymbol,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            int result = (int) addNumbers.invoke(10);

            log.info("Result of addOne(10): {}", result);
        }

        /* FUNCTION GET SIZE OF MESSAGE IN C++ */
        int stringLength;
        {
            MemorySegment addStringLengthSymbol = lookup.find("getMessageLength").orElseThrow();

            MethodHandle addStringLength = linker.downcallHandle(addStringLengthSymbol,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));

            stringLength = (int) addStringLength.invoke();
            log.info("Result of get_message_length(): {}", stringLength);
        }

        /* FUNCTION TO FILL STRING BUFFER WITH CHARACTERS IN C++ */
        {
            MemorySegment fillStringSymbol = lookup.find("fillString").orElseThrow();

            MethodHandle fillString = linker.downcallHandle(fillStringSymbol,
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

            byte[] buffer = new byte[stringLength + 1];
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment bufferSegment = arena.allocate(ValueLayout.JAVA_BYTE, buffer.length);

                fillString.invoke(bufferSegment, (long) buffer.length);

                String javaString = cStringToJavaString(bufferSegment, buffer.length);

                log.info("Result of fillString(): {}", javaString);
            }
        }
    }

    private static String cStringToJavaString(MemorySegment cStringSegment, long length) {
        if (cStringSegment == null || length == 0) {
            log.error("cStringSegment is null or length is 0");
            throw new IllegalArgumentException("cStringSegment is null or length is 0");
        }

        byte[] bytes = new byte[(int) length];
        for (int i = 0; i < length; i++) {
            bytes[i] = cStringSegment.get(ValueLayout.JAVA_BYTE, i);
        }

        return new String(bytes, StandardCharsets.UTF_8).trim();
    }
}