package no.cantara.config.store;

import no.cantara.config.ApplicationProperties;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

public class StoreBasedApplicationProperties implements ApplicationProperties {

    private static final Logger log = getLogger(StoreBasedApplicationProperties.class);

    /*
     * The original list of property-sources. Useful for debugging what source a configuration came from.
     */
    private final Deque<Store> storeList;

    /*
     * A very efficient immutable map with pre-resolved entries.
     */
    private final Map<String, String> effectiveProperties;

    private StoreBasedApplicationProperties(Deque<Store> storeList) {
        this.storeList = storeList;
        this.effectiveProperties = Collections.unmodifiableMap(buildMapFromStore());
    }

    @Override
    public Map<String, String> map() {
        return effectiveProperties;
    }

    @Override
    public String get(String name) {
        return effectiveProperties.get(name);
    }

    @Override
    public List<Source> sourcesOf(String name) {
        List<Source> result = new ArrayList<>(storeList.size());
        for (Store store : storeList) {
            if (store.get(name) != null) {
                result.add(new DebuggableSource(store, name));
            }
        }
        return result;
    }

    Map<String, String> buildMapFromStore() {
        Map<String, String> map = new LinkedHashMap<>();
        Iterator<Store> it = storeList.descendingIterator();
        while (it.hasNext()) {
            Store store = it.next();
            store.putAllToMap(map);
        }
        return map;
    }

    public static class Builder implements ApplicationProperties.Builder {
        final Deque<Store> storeList = new LinkedList<>();
        final Set<String> expectedApplicationProperties = new LinkedHashSet<>();
        final Set<String> lowercaseAliases = new LinkedHashSet<>();

        private void validate(Map<String, String> properties) {
            if (expectedApplicationProperties.size() > 0) {
                log.info("*********************");
                log.info("The application has resolved the following properties");
                log.info(ApplicationProperties.logObfuscatedProperties(properties));
                log.info("*********************");
                final Set<String> expectedKeys = expectedApplicationProperties;
                final List<String> undefinedProperties = expectedKeys.stream().filter(expectedPropertyName -> !properties.containsKey(expectedPropertyName)).collect(toList());
                if (!undefinedProperties.isEmpty()) {
                    final String message = "Expected properties is not loaded " + undefinedProperties;
                    log.error(message);
                    throw new RuntimeException(message);
                }
                final List<String> undefinedValues = expectedKeys.stream()
                        .filter(expectedPropertyName ->
                                properties.get(expectedPropertyName) == null || properties.get(expectedPropertyName).isEmpty()
                        ).collect(toList());
                if (!undefinedValues.isEmpty()) {
                    final String message = "Expected properties is defined without value " + undefinedValues;
                    log.error(message);
                    throw new RuntimeException(message);
                }
                final List<String> additionalProperties = properties.keySet().stream().filter(s -> !expectedKeys.contains(s)).collect(toList());
                if (!additionalProperties.isEmpty()) {
                    log.warn("The following properties are loaded but not defined as expected for the application {}", additionalProperties);
                }

            }
        }

        @Override
        public ApplicationProperties.Builder aliasAsLowercase(String name) {
            lowercaseAliases.add(name);
            return this;
        }

        @Override
        public ApplicationProperties.Builder expectedProperties(Class... expectedApplicationProperties) {
            final List<String> propertyNames = Arrays.stream(expectedApplicationProperties)
                    .map(aClass -> {
                        final Set<String> fields = Arrays.stream(aClass.getDeclaredFields())
                                .filter(field -> field.getType() == String.class)
                                .map(field -> {
                                    try {
                                        return (String) field.get(null);
                                    } catch (IllegalAccessException e) {
                                        log.warn("Field with name {} is non-accessible", field.getName());
                                        return "";
                                    }
                                }).filter(s -> !s.isEmpty())
                                .collect(Collectors.toSet());
                        return fields;
                    }).flatMap(Collection::stream)
                    .collect(Collectors.toList());
            this.expectedApplicationProperties.addAll(propertyNames);
            return this;
        }

        @Override
        public ApplicationProperties.Builder map(Map<String, String> map) {
            storeList.addFirst(new MapStore(map, 0));
            return this;
        }

        @Override
        public ApplicationProperties.Builder classpathPropertiesFile(String resourcePath) {
            storeList.addFirst(new ClasspathPropertiesStore(resourcePath));
            return this;
        }

        @Override
        public ApplicationProperties.Builder filesystemPropertiesFile(String resourcePath) {
            storeList.addFirst(new FilesystemPropertiesStore(resourcePath));
            return this;
        }

        @Override
        public ApplicationProperties.Builder enableEnvironmentVariables() {
            storeList.addFirst(new EnvironmentStore("", true));
            return this;
        }

        @Override
        public ApplicationProperties.Builder enableEnvironmentVariables(String prefix) {
            storeList.addFirst(new EnvironmentStore(prefix, true));
            return this;
        }

        @Override
        public ApplicationProperties.Builder enableEnvironmentVariablesWithoutEscaping() {
            storeList.addFirst(new EnvironmentStore("", false));
            return this;
        }

        @Override
        public ApplicationProperties.Builder enableSystemProperties() {
            storeList.addFirst(new SystemPropertiesStore(""));
            return this;
        }

        @Override
        public ApplicationProperties.Builder enableSystemProperties(String prefix) {
            storeList.addFirst(new SystemPropertiesStore(prefix));
            return this;
        }

        @Override
        public ValueBuilder values() {
            return new ValueBuilder() {
                final Map<String, String> map = new LinkedHashMap<>();

                @Override
                public ValueBuilder put(String name, String value) {
                    map.put(name, value);
                    return this;
                }

                @Override
                public ApplicationProperties.Builder end() {
                    storeList.addFirst(new MapStore(map, 1));
                    return Builder.this;
                }
            };
        }

        @Override
        public StoreBasedApplicationProperties build() {
            StoreBasedApplicationProperties applicationProperties = new StoreBasedApplicationProperties(storeList);
            validate(applicationProperties.effectiveProperties);
            return applicationProperties;
        }


    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoreBasedApplicationProperties that = (StoreBasedApplicationProperties) o;
        return Objects.equals(storeList, that.storeList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeList);
    }
}