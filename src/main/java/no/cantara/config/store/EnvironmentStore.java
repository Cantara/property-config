package no.cantara.config.store;

import no.cantara.config.EnvironmentVariableEscaping;
import no.cantara.config.SourceConfigurationLocationException;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

class EnvironmentStore extends AbstractStore {

    private static final Logger log = getLogger(EnvironmentStore.class);

    private final Set<String> casingSet;

    private final Set<String> basePropertyKeys;
    private final String prefix;
    private final boolean useEscaping;

    EnvironmentStore(Set<String> basePropertyKeys, SourceConfigurationLocationException location, String prefix, boolean useEscaping, Set<String> casingSet) {
        super(location);
        this.basePropertyKeys = basePropertyKeys;
        this.prefix = prefix;
        this.useEscaping = useEscaping;
        this.casingSet = casingSet;
    }

    public String envVarToJavaProperty(String envVarKey) {
        if (!useEscaping) {
            return envVarKey;
        }
        return EnvironmentVariableEscaping.unescape(envVarKey);
    }

    public String javaPropertyToEnvVar(String propKey) {
        if (!useEscaping) {
            return propKey;
        }
        return EnvironmentVariableEscaping.escape(propKey);
    }

    @Override
    public String get(String key) {
        if (!basePropertyKeys.contains(key)) {
            return null; // key does not override an existing property from a base store
        }
        if (!key.toLowerCase().equals(key)) {
            // key has at least one uppercase letter
            if (!casingSet.contains(key)) {
                // no casing specified for key
                return null;
            }
        }
        String envKey = prefix + javaPropertyToEnvVar(key);
        String value = System.getenv(envKey);
        return value;
    }

    @Override
    public void putAllToMap(Map<String, String> map) {
        Map<String, String> casingByLowercase = casingSet.stream().collect(Collectors.toMap(String::toLowerCase, k -> k));
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String strippedEnvVarKey = entry.getKey().substring(prefix.length());
                if (useEscaping) {
                    if (map.containsKey(strippedEnvVarKey)) {
                        log.warn("Environment-variable '{}' will NOT override property with same name. To override this property, use environment-variable '{}'", strippedEnvVarKey, EnvironmentVariableEscaping.escape(strippedEnvVarKey));
                    }
                }
                String propKey = envVarToJavaProperty(strippedEnvVarKey);
                String possiblyAliasedPropKey = casingByLowercase.getOrDefault(propKey, propKey);
                if (basePropertyKeys.contains(possiblyAliasedPropKey)) {
                    map.put(possiblyAliasedPropKey, entry.getValue());
                } else {
                    // not an override, filter out
                }
            }
        }
    }

    @Override
    public String toString() {
        if (prefix == null || prefix.isEmpty()) {
            return "Environment-variables";
        }
        return "Environment-variables '" + prefix + "*'";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnvironmentStore that = (EnvironmentStore) o;
        return Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix);
    }
}
