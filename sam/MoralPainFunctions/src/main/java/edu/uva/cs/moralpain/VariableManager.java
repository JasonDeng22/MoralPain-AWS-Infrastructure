package edu.uva.cs.moralpain;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * A variable manager that implements a lookup chain for environment-like
 * variable values. First, explicitly set
 * variables are checked. Then, the environment. Lastly, system properties.
 *
 * This class aids in dependency injection.
 */
public class VariableManager {
    Map<String, String> environment = System.getenv();
    Properties properties = System.getProperties();
    Map<String, String> preferred;

    public VariableManager() {
        this(Collections.emptyMap());
    }

    public VariableManager(Map<String, String> preferred) {
        this.preferred = preferred;
    }

    public String getOrDefault(String key, String def) {
        return preferred.getOrDefault(
                key, environment.getOrDefault(
                        key, properties.getProperty(
                                key, def)));
    }

    public String get(String key) {
        return getOrDefault(key, null);
    }

    public boolean containsKey(String key) {
        return preferred.containsKey(key) || environment.containsKey(key) || properties.containsKey(key);
    }

    public void put(String key, String value) {
        preferred.put(key, value);
    }

    public void remove(String key) {
        preferred.remove(key);
    }
}