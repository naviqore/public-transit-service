package org.naviqore.benchmark;

import org.naviqore.benchmark.version.Version;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.*;

/**
 * The arguments of a benchmark command: the command, its positional arguments, and its options, each given as
 * {@code --name value}.
 *
 * @param command    the command
 * @param positional the positional arguments, in order
 * @param options    the options by name
 */
public record Arguments(String command, List<String> positional, Map<String, String> options) {

    private static final String DEFAULT_FEED = "SWITZERLAND";
    private static final Path DEFAULT_OUTPUT = Path.of("benchmark/output");

    public static Arguments parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No command given");
        }
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (i + 1 == args.length) {
                    throw new IllegalArgumentException("Option " + args[i] + " has no value");
                }
                options.put(args[i].substring(2), args[++i]);
            } else {
                positional.add(args[i]);
            }
        }

        return new Arguments(args[0], List.copyOf(positional), Collections.unmodifiableMap(options));
    }

    /**
     * The positional argument at an index.
     *
     * @param index the index of the argument
     * @param name  what the argument is, for the message if it is missing
     */
    public String positional(int index, String name) {
        if (index >= positional.size()) {
            throw new IllegalArgumentException("Command " + command + " needs " + name);
        }

        return positional.get(index);
    }

    /**
     * The version whose jar is the positional argument at an index, see {@link Version#load(Path)}.
     */
    public Version version(int index) throws ReflectiveOperationException, MalformedURLException {
        return Version.load(Path.of(positional(index, "the jar of a version")));
    }

    /**
     * The feed of option {@code --feed}: a dataset of {@link org.naviqore.gtfs.schedule.GtfsScheduleDataset} by name,
     * or the path of a ZIP file; the Swiss national feed if none is given.
     */
    public Feed feed() throws IOException {
        return Feed.of(options.getOrDefault("feed", DEFAULT_FEED));
    }

    /**
     * The directory of the outputs, option {@code --output}; {@code benchmark/output} if none is given.
     */
    public Path output() {
        return options.containsKey("output") ? Path.of(options.get("output")) : DEFAULT_OUTPUT;
    }

    /**
     * The workloads of option {@code --workloads}, separated by commas.
     *
     * @param defaults the workloads if none are given
     */
    public List<String> workloads(List<String> defaults) {
        return options.containsKey("workloads") ? List.of(options.get("workloads").split(",")) : defaults;
    }

    /**
     * A number option.
     *
     * @param name         the name of the option
     * @param defaultValue the value if the option is not given
     */
    public int number(String name, int defaultValue) {
        return options.containsKey(name) ? Integer.parseInt(options.get(name)) : defaultValue;
    }

    /**
     * A text option, empty if it is not given.
     */
    public Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name));
    }

}
