package hexlab.morf.config.impl;

import hexlab.morf.config.ConfigMergeable;
import hexlab.morf.config.ConfigValue;

interface MergeableValue extends ConfigMergeable {
    // converts a Config to its root object and a ConfigValue to itself
    ConfigValue toFallbackValue();
}
