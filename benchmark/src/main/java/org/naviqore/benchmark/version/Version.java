package org.naviqore.benchmark.version;

import lombok.extern.slf4j.Slf4j;
import org.naviqore.benchmark.subject.ServiceSubject;
import org.naviqore.benchmark.subject.Subject;
import org.naviqore.benchmark.workload.Query;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A version of the libraries, loaded from its jar into a class loader of its own, whose parent is the platform class
 * loader: the classes of two versions never meet, nor do they meet those of the benchmark running them.
 * <p>
 * The benchmark calls the version's subject through a proxy of its own {@link Subject} interface, which finds each
 * method on the version's subject by its name and parameter types. These are JDK types, which all class loaders share.
 *
 * @param name    the name of the version, as outputs show it
 * @param subject the subject of the version
 */
@Slf4j
public record Version(String name, Subject subject) {

    /**
     * The name of the version on the class path of the benchmark itself, instead of a jar.
     */
    public static final String CLASS_PATH = "classpath";

    /**
     * Loads a version from its jar, named after the file without its extension, or the version on the class path of the
     * benchmark if the jar is {@value #CLASS_PATH}: a single version needs no class loader of its own, which lets the
     * commands of one version run from an IDE.
     *
     * @param jar the jar of the benchmark module with all it depends on, built from the version
     */
    public static Version load(Path jar) throws ReflectiveOperationException, MalformedURLException {
        if (jar.toString().equals(CLASS_PATH)) {
            return new Version(CLASS_PATH, new ServiceSubject());
        }

        return load(jar.getFileName().toString().replaceFirst("\\.jar$", ""), jar.toUri().toURL());
    }

    /**
     * Loads a version from its class path into a class loader of its own.
     *
     * @param name      the name of the version
     * @param classPath the class path of the version: the benchmark module and all it depends on
     */
    static Version load(String name, URL... classPath) throws ReflectiveOperationException {
        URLClassLoader classLoader = new URLClassLoader(name, classPath, ClassLoader.getPlatformClassLoader());
        Object target = classLoader.loadClass(ServiceSubject.class.getName()).getConstructor().newInstance();

        // the methods of the version's subject by those of the interface, each looked up once
        Map<Method, Method> methods = new ConcurrentHashMap<>();
        Subject subject = (Subject) Proxy.newProxyInstance(Subject.class.getClassLoader(),
                new Class<?>[]{Subject.class}, (_, method, arguments) -> {
                    Method targetMethod = methods.computeIfAbsent(method, _ -> find(target, method));
                    try {
                        return targetMethod.invoke(target, arguments);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

        return new Version(name, subject);
    }

    private static Method find(Object target, Method method) {
        try {
            return target.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException(
                    "The subject of the version has no method " + method.getName() + ", built before it had one?", e);
        }
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e9);
    }

    /**
     * Reads the feed into the version and builds its service, logging how long each took.
     *
     * @param feed the path of the ZIP file of the feed
     */
    public void build(Path feed) {
        long started = System.nanoTime();
        subject.read(feed.toString());
        long read = System.nanoTime();
        subject.build();
        log.info("Version {}: read the feed in {} s, built the service in {} s", name, seconds(read - started),
                seconds(System.nanoTime() - read));
    }

    /**
     * Answers a query, see {@link Subject#answer(List)}.
     */
    public List<String> answer(Query query) {
        return subject.answer(query.encode());
    }

    /**
     * Measures a query, see {@link Subject#time(List)}.
     */
    public long[] time(Query query) {
        return subject.time(query.encode());
    }

}
