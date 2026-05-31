package com.snowedunderproductions.graphprobe.annotations;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link org.junit.jupiter.params.provider.ArgumentsProvider} registered by the
 * {@link DynamicSource} annotation that dispatches test-argument loading to either a classpath
 * CSV file or a delegate {@link org.junit.jupiter.params.provider.ArgumentsProvider}.
 *
 * <p>This class is not intended to be used directly.  It is registered automatically when a
 * test method is annotated with {@code @DynamicSource}.
 *
 * <p>The dispatch logic is as follows:
 * <ul>
 *   <li>When the {@code USE_CSV} environment variable is {@code "true"} <em>and</em> the
 *       {@link DynamicSource#csvResource()} attribute is non-empty, arguments are parsed from
 *       the specified classpath CSV resource.</li>
 *   <li>Otherwise, the class specified by {@link DynamicSource#argumentsProvider()} is
 *       instantiated reflectively and its
 *       {@link org.junit.jupiter.params.provider.ArgumentsProvider#provideArguments(ExtensionContext)}
 *       method is called.  Typical delegates extend
 *       {@link com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider} and load
 *       data from the shared HikariCP connection pool.</li>
 * </ul>
 *
 * @see DynamicSource
 * @see com.snowedunderproductions.graphprobe.database.BaseArgumentsProvider
 */
public class DynamicSourceProvider
    implements ArgumentsProvider, AnnotationConsumer<DynamicSource> {

    private static final Logger log = LoggerFactory.getLogger(
        DynamicSourceProvider.class
    );

    private String csvResource;
    private char delimiter;
    private int linesToSkip;
    private Class<? extends ArgumentsProvider> delegateProvider;

    /**
     * Captures the {@link DynamicSource} annotation attributes so that they are available
     * when {@link #provideArguments(ExtensionContext)} is later called by the JUnit engine.
     *
     * @param annotation the {@link DynamicSource} annotation present on the test method
     */
    @Override
    public void accept(DynamicSource annotation) {
        this.csvResource = annotation.csvResource();
        this.delimiter = annotation.delimiter();
        this.linesToSkip = annotation.linesToSkip();
        this.delegateProvider = annotation.argumentsProvider();
    }

    /**
     * Provides the stream of {@link org.junit.jupiter.params.provider.Arguments} for the
     * parameterised test, selecting the data source at runtime.
     *
     * <p>When {@code USE_CSV=true} and {@link DynamicSource#csvResource()} is non-empty, the
     * arguments are loaded from the CSV resource via {@link #readCsvFile(String)}.  Otherwise
     * the delegate provider configured by {@link DynamicSource#argumentsProvider()} is
     * instantiated and called.
     *
     * @param context the JUnit {@link ExtensionContext} for the current test
     * @return a {@link java.util.stream.Stream} of {@link org.junit.jupiter.params.provider.Arguments}
     *         for the parameterised test invocations
     * @throws Exception if the CSV resource cannot be read or the delegate provider cannot be
     *         instantiated or invoked
     */
    @Override
    @SuppressWarnings("deprecation")
    public Stream<? extends Arguments> provideArguments(
        ExtensionContext context
    ) throws Exception {
        String useCsvEnv = System.getenv("USE_CSV");

        if ("true".equalsIgnoreCase(useCsvEnv) && !csvResource.isEmpty()) {
            log.info("Using CSV file: {}", csvResource);
            List<String> csvLines = readCsvFile(csvResource);
            return loadCsvArguments(csvLines, delimiter, linesToSkip);
        } else {
            log.info(
                "Using Arguments Provider: {}",
                delegateProvider.getName()
            );
            return loadArgumentsFromProvider(delegateProvider, context);
        }
    }

    /**
     * Converts a list of raw CSV lines into a stream of
     * {@link org.junit.jupiter.params.provider.Arguments}, skipping the configured number of
     * header lines and splitting each remaining line on the given delimiter.
     *
     * @param lines       the full list of lines read from the CSV resource
     * @param delimiter   the field separator character
     * @param linesToSkip the number of leading lines to discard (typically 1 for a header row)
     * @return a stream of {@link org.junit.jupiter.params.provider.Arguments}, one per data row
     */
    private Stream<? extends Arguments> loadCsvArguments(
        @NotNull List<String> lines,
        char delimiter,
        int linesToSkip
    ) {
        return lines
            .stream()
            .skip(linesToSkip) // skip specified number of lines
            .map(line -> line.split(String.valueOf(delimiter)))
            .map(Arguments::of);
    }

    /**
     * Reflectively instantiates the given {@link org.junit.jupiter.params.provider.ArgumentsProvider}
     * class, optionally feeds it the current {@link DynamicSource} annotation state if the
     * instance also implements
     * {@link org.junit.jupiter.params.support.AnnotationConsumer}{@code <DynamicSource>}, and
     * then returns its argument stream.
     *
     * @param providerClass the {@link org.junit.jupiter.params.provider.ArgumentsProvider}
     *                      implementation to instantiate; must have a public no-arg constructor
     * @param context       the JUnit {@link ExtensionContext} for the current test
     * @return a stream of {@link org.junit.jupiter.params.provider.Arguments} from the delegate
     * @throws Exception                if the delegate cannot be instantiated or its
     *                                  {@code provideArguments} method throws
     * @throws IllegalArgumentException if the class cannot be instantiated (e.g. no public
     *                                  no-arg constructor) or the provider is an
     *                                  {@code AnnotationConsumer} for an incompatible annotation
     *                                  type
     */
    @SuppressWarnings("deprecation")
    private Stream<? extends Arguments> loadArgumentsFromProvider(
        Class<? extends ArgumentsProvider> providerClass,
        ExtensionContext context
    ) throws Exception {
        Objects.requireNonNull(
            providerClass,
            "Provider class must not be null"
        );
        Objects.requireNonNull(context, "Extension context must not be null");

        try {
            ArgumentsProvider provider = providerClass
                .getDeclaredConstructor()
                .newInstance();

            // Use a type-safe pattern for AnnotationConsumer
            if (provider instanceof AnnotationConsumer<?>) {
                try {
                    @SuppressWarnings("unchecked")
                    AnnotationConsumer<DynamicSource> consumer =
                        (AnnotationConsumer<DynamicSource>) provider;
                    consumer.accept(createDynamicSourceAnnotation());
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(
                        "Provider is AnnotationConsumer but not of DynamicSource: " +
                        providerClass.getName(),
                        e
                    );
                }
            }

            return provider.provideArguments(context);
        } catch (
            NoSuchMethodException
            | InstantiationException
            | IllegalAccessException
            | InvocationTargetException e
        ) {
            throw new IllegalArgumentException(
                "Failed to instantiate provider: " + providerClass.getName(),
                e
            );
        }
    }

    /**
     * Creates a synthetic {@link DynamicSource} annotation instance populated with the attribute
     * values captured by {@link #accept(DynamicSource)}.
     *
     * <p>This proxy is passed to the delegate provider when it implements
     * {@link org.junit.jupiter.params.support.AnnotationConsumer}{@code <DynamicSource>}, so
     * that it can access the same annotation state as the outer provider.
     *
     * @return a runtime {@link DynamicSource} proxy backed by the current field values
     */
    @NotNull
    @Contract(value = " -> new", pure = true)
    private DynamicSource createDynamicSourceAnnotation() {
        return new DynamicSource() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return DynamicSource.class;
            }

            @Override
            public String csvResource() {
                return csvResource;
            }

            @Override
            public char delimiter() {
                return delimiter;
            }

            @Override
            public int linesToSkip() {
                return linesToSkip;
            }

            @Override
            public Class<? extends ArgumentsProvider> argumentsProvider() {
                return delegateProvider;
            }
        };
    }

    /**
     * Read the CSV file from the classpath.
     *
     * @param resource The resource to read.
     * @return List of lines from the CSV file.
     * @throws Exception if the file is not found.
     */
    List<String> readCsvFile(@NotNull String resource) throws Exception {
        InputStream is = getClass().getResourceAsStream(resource);
        if (is == null) {
            throw new IllegalArgumentException(
                "Resource not found: " + resource
            );
        }
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)
            )
        ) {
            return reader.lines().collect(Collectors.toList());
        }
    }
}
