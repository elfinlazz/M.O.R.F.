/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package hexlab.morf.config.impl;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import hexlab.morf.config.*;

/**
 * One thing to keep in mind in the future: as Collection-like APIs are added
 * here, including iterators or size() or anything, they should be consistent
 * with a one-level java.util.Map from paths to non-null values. Null values are
 * not "in" the map.
 */
final class SimpleConfig implements Config, MergeableValue, Serializable {

    private static final long serialVersionUID = 1L;

    final private AbstractConfigObject object;

    SimpleConfig(AbstractConfigObject object) {
        this.object = object;
    }

    @Override
    public AbstractConfigObject root() {
        return object;
    }

    @Override
    public ConfigOrigin origin() {
        return object.origin();
    }

    @Override
    public SimpleConfig resolve() {
        return resolve(ConfigResolveOptions.defaults());
    }

    @Override
    public SimpleConfig resolve(ConfigResolveOptions options) {
        return resolveWith(this, options);
    }

    @Override
    public SimpleConfig resolveWith(Config source) {
        return resolveWith(source, ConfigResolveOptions.defaults());
    }

    @Override
    public SimpleConfig resolveWith(Config source, ConfigResolveOptions options) {
        AbstractConfigValue resolved = ResolveContext.resolve(object, ((SimpleConfig) source).object, options);

        if (resolved == object)
            return this;
        else
            return new SimpleConfig((AbstractConfigObject) resolved);
    }

    @Override
    public boolean hasPath(String pathExpression) {
        Path path = Path.newPath(pathExpression);
        ConfigValue peeked;
        try {
            peeked = object.peekPath(path);
        } catch (ConfigException.NotResolved e) {
            throw ConfigImpl.improveNotResolved(path, e);
        }
        return peeked != null && peeked.valueType() != ConfigValueType.NULL;
    }

    @Override
    public boolean isEmpty() {
        return object.isEmpty();
    }

    private static void findPaths(Set<Map.Entry<String, ConfigValue>> entries, Path parent,
            AbstractConfigObject obj) {
        for (Map.Entry<String, ConfigValue> entry : obj.entrySet()) {
            String elem = entry.getKey();
            ConfigValue v = entry.getValue();
            Path path = Path.newKey(elem);
            if (parent != null)
                path = path.prepend(parent);
            if (v instanceof AbstractConfigObject) {
                findPaths(entries, path, (AbstractConfigObject) v);
            } else if (v instanceof ConfigNull) {
                // nothing; nulls are conceptually not in a Config
            } else {
                entries.add(new AbstractMap.SimpleImmutableEntry<String, ConfigValue>(path.render(), v));
            }
        }
    }

    @Override
    public Set<Map.Entry<String, ConfigValue>> entrySet() {
        Set<Map.Entry<String, ConfigValue>> entries = new HashSet<Map.Entry<String, ConfigValue>>();
        findPaths(entries, null, object);
        return entries;
    }

    static private AbstractConfigValue findKey(AbstractConfigObject self, String key,
            ConfigValueType expected, Path originalPath) {
        AbstractConfigValue v = self.peekAssumingResolved(key, originalPath);
        if (v == null)
            throw new ConfigException.Missing(originalPath.render());

        if (expected != null)
            v = DefaultTransformer.transform(v, expected);

        if (v.valueType() == ConfigValueType.NULL)
            throw new ConfigException.Null(v.origin(), originalPath.render(),
                    expected != null ? expected.name() : null);
        else if (expected != null && v.valueType() != expected)
            throw new ConfigException.WrongType(v.origin(), originalPath.render(), expected.name(),
                    v.valueType().name());
        else
            return v;
    }

    static private AbstractConfigValue find(AbstractConfigObject self, Path path,
            ConfigValueType expected, Path originalPath) {
        try {
            String key = path.first();
            Path next = path.remainder();
            if (next == null) {
                return findKey(self, key, expected, originalPath);
            } else {
                AbstractConfigObject o = (AbstractConfigObject) findKey(self, key,
                        ConfigValueType.OBJECT,
                        originalPath.subPath(0, originalPath.length() - next.length()));
                assert (o != null); // missing was supposed to throw
                return find(o, next, expected, originalPath);
            }
        } catch (ConfigException.NotResolved e) {
            throw ConfigImpl.improveNotResolved(path, e);
        }
    }

    AbstractConfigValue find(Path pathExpression, ConfigValueType expected, Path originalPath) {
        return find(object, pathExpression, expected, originalPath);
    }

    AbstractConfigValue find(String pathExpression, ConfigValueType expected) {
        Path path = Path.newPath(pathExpression);
        return find(path, expected, path);
    }

    @Override
    public AbstractConfigValue getValue(String path) {
        return find(path, null);
    }

    @Override
    public boolean getBoolean(String path) {
        ConfigValue v = find(path, ConfigValueType.BOOLEAN);
        return (Boolean) v.unwrapped();
    }

    private ConfigNumber getConfigNumber(String path) {
        ConfigValue v = find(path, ConfigValueType.NUMBER);
        return (ConfigNumber) v;
    }

    @Override
    public Number getNumber(String path) {
        return getConfigNumber(path).unwrapped();
    }

    @Override
    public int getInt(String path) {
        ConfigNumber n = getConfigNumber(path);
        return n.intValueRangeChecked(path);
    }

    @Override
    public long getLong(String path) {
        return getNumber(path).longValue();
    }

    @Override
    public double getDouble(String path) {
        return getNumber(path).doubleValue();
    }

    @Override
    public String getString(String path) {
        ConfigValue v = find(path, ConfigValueType.STRING);
        return (String) v.unwrapped();
    }

    @Override
    public ConfigList getList(String path) {
        AbstractConfigValue v = find(path, ConfigValueType.LIST);
        return (ConfigList) v;
    }

    @Override
    public AbstractConfigObject getObject(String path) {
        AbstractConfigObject obj = (AbstractConfigObject) find(path, ConfigValueType.OBJECT);
        return obj;
    }

    @Override
    public SimpleConfig getConfig(String path) {
        return getObject(path).toConfig();
    }

    @Override
    public Object getAnyRef(String path) {
        ConfigValue v = find(path, null);
        return v.unwrapped();
    }

    @Override
    public Long getBytes(String path) {
        Long size = null;
        try {
            size = getLong(path);
        } catch (ConfigException.WrongType e) {
            ConfigValue v = find(path, ConfigValueType.STRING);
            size = parseBytes((String) v.unwrapped(),
                    v.origin(), path);
        }
        return size;
    }

    @Deprecated
    @Override
    public Long getMilliseconds(String path) {
        return getDuration(path, TimeUnit.MILLISECONDS);
    }

    @Deprecated
    @Override
    public Long getNanoseconds(String path) {
        return getDuration(path, TimeUnit.NANOSECONDS);
    }

    @Override
    public long getDuration(String path, TimeUnit unit) {
        ConfigValue v = find(path, ConfigValueType.STRING);
        long result = unit.convert(
                       parseDuration((String) v.unwrapped(), v.origin(), path),
                       TimeUnit.NANOSECONDS);
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getHomogeneousUnwrappedList(String path,
            ConfigValueType expected) {
        List<T> l = new ArrayList<T>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue cv : list) {
            // variance would be nice, but stupid cast will do
            AbstractConfigValue v = (AbstractConfigValue) cv;
            if (expected != null) {
                v = DefaultTransformer.transform(v, expected);
            }
            if (v.valueType() != expected)
                throw new ConfigException.WrongType(v.origin(), path,
                        "list of " + expected.name(), "list of "
                                + v.valueType().name());
            l.add((T) v.unwrapped());
        }
        return l;
    }

    @Override
    public List<Boolean> getBooleanList(String path) {
        return getHomogeneousUnwrappedList(path, ConfigValueType.BOOLEAN);
    }

    @Override
    public List<Number> getNumberList(String path) {
        return getHomogeneousUnwrappedList(path, ConfigValueType.NUMBER);
    }

    @Override
    public List<Integer> getIntList(String path) {
        List<Integer> l = new ArrayList<Integer>();
        List<AbstractConfigValue> numbers = getHomogeneousWrappedList(path, ConfigValueType.NUMBER);
        for (AbstractConfigValue v : numbers) {
            l.add(((ConfigNumber) v).intValueRangeChecked(path));
        }
        return l;
    }

    @Override
    public List<Long> getLongList(String path) {
        List<Long> l = new ArrayList<Long>();
        List<Number> numbers = getNumberList(path);
        for (Number n : numbers) {
            l.add(n.longValue());
        }
        return l;
    }

    @Override
    public List<Double> getDoubleList(String path) {
        List<Double> l = new ArrayList<Double>();
        List<Number> numbers = getNumberList(path);
        for (Number n : numbers) {
            l.add(n.doubleValue());
        }
        return l;
    }

    @Override
    public List<String> getStringList(String path) {
        return getHomogeneousUnwrappedList(path, ConfigValueType.STRING);
    }

    @SuppressWarnings("unchecked")
    private <T extends ConfigValue> List<T> getHomogeneousWrappedList(
            String path, ConfigValueType expected) {
        List<T> l = new ArrayList<T>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue cv : list) {
            // variance would be nice, but stupid cast will do
            AbstractConfigValue v = (AbstractConfigValue) cv;
            if (expected != null) {
                v = DefaultTransformer.transform(v, expected);
            }
            if (v.valueType() != expected)
                throw new ConfigException.WrongType(v.origin(), path,
                        "list of " + expected.name(), "list of "
                                + v.valueType().name());
            l.add((T) v);
        }
        return l;
    }

    @Override
    public List<ConfigObject> getObjectList(String path) {
        return getHomogeneousWrappedList(path, ConfigValueType.OBJECT);
    }

    @Override
    public List<? extends Config> getConfigList(String path) {
        List<ConfigObject> objects = getObjectList(path);
        List<Config> l = new ArrayList<Config>();
        for (ConfigObject o : objects) {
            l.add(o.toConfig());
        }
        return l;
    }

    @Override
    public List<? extends Object> getAnyRefList(String path) {
        List<Object> l = new ArrayList<Object>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            l.add(v.unwrapped());
        }
        return l;
    }

    @Override
    public List<Long> getBytesList(String path) {
        List<Long> l = new ArrayList<Long>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() == ConfigValueType.NUMBER) {
                l.add(((Number) v.unwrapped()).longValue());
            } else if (v.valueType() == ConfigValueType.STRING) {
                String s = (String) v.unwrapped();
                Long n = parseBytes(s, v.origin(), path);
                l.add(n);
            } else {
                throw new ConfigException.WrongType(v.origin(), path,
                        "memory size string or number of bytes", v.valueType()
                                .name());
            }
        }
        return l;
    }

    @Override
    public List<Long> getDurationList(String path, TimeUnit unit) {
        List<Long> l = new ArrayList<Long>();
        List<? extends ConfigValue> list = getList(path);
        for (ConfigValue v : list) {
            if (v.valueType() == ConfigValueType.NUMBER) {
                Long n = unit.convert(
                           ((Number) v.unwrapped()).longValue(),
                           TimeUnit.MILLISECONDS);
                l.add(n);
            } else if (v.valueType() == ConfigValueType.STRING) {
                String s = (String) v.unwrapped();
                Long n = unit.convert(
                           parseDuration(s, v.origin(), path),
                           TimeUnit.NANOSECONDS);
                l.add(n);
            } else {
                throw new ConfigException.WrongType(v.origin(), path,
                        "duration string or number of milliseconds",
                        v.valueType().name());
            }
        }
        return l;
    }

    @Deprecated
    @Override
    public List<Long> getMillisecondsList(String path) {
        return getDurationList(path, TimeUnit.MILLISECONDS);
    }

    @Deprecated
    @Override
    public List<Long> getNanosecondsList(String path) {
        return getDurationList(path, TimeUnit.NANOSECONDS);
    }

    @Override
    public AbstractConfigObject toFallbackValue() {
        return object;
    }

    @Override
    public SimpleConfig withFallback(ConfigMergeable other) {
        // this can return "this" if the withFallback doesn't need a new
        // ConfigObject
        return object.withFallback(other).toConfig();
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof SimpleConfig) {
            return object.equals(((SimpleConfig) other).object);
        } else {
            return false;
        }
    }

    @Override
    public final int hashCode() {
        // we do the "41*" just so our hash code won't match that of the
        // underlying object. there's no real reason it can't match, but
        // making it not match might catch some kinds of bug.
        return 41 * object.hashCode();
    }

    @Override
    public String toString() {
        return "Config(" + object.toString() + ")";
    }

    private static String getUnits(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (!Character.isLetter(c))
                break;
            i -= 1;
        }
        return s.substring(i + 1);
    }

    /**
     * Parses a duration string. If no units are specified in the string, it is
     * assumed to be in milliseconds. The returned duration is in nanoseconds.
     * The purpose of this function is to implement the duration-related methods
     * in the ConfigObject interface.
     *
     * @param input
     *            the string to parse
     * @param originForException
     *            origin of the value being parsed
     * @param pathForException
     *            path to include in exceptions
     * @return duration in nanoseconds
     * @throws hexlab.morf.config.ConfigException
     *             if string is invalid
     */
    public static long parseDuration(String input,
            ConfigOrigin originForException, String pathForException) {
        String s = ConfigImplUtil.unicodeTrim(input);
        String originalUnitString = getUnits(s);
        String unitString = originalUnitString;
        String numberString = ConfigImplUtil.unicodeTrim(s.substring(0, s.length()
                - unitString.length()));
        TimeUnit units = null;

        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length() == 0)
            throw new ConfigException.BadValue(originForException,
                    pathForException, "No number in duration value '" + input
                            + "'");

        if (unitString.length() > 2 && !unitString.endsWith("s"))
            unitString = unitString + "s";

        // note that this is deliberately case-sensitive
        if (unitString.equals("") || unitString.equals("ms")
                || unitString.equals("milliseconds")) {
            units = TimeUnit.MILLISECONDS;
        } else if (unitString.equals("us") || unitString.equals("microseconds")) {
            units = TimeUnit.MICROSECONDS;
        } else if (unitString.equals("ns") || unitString.equals("nanoseconds")) {
            units = TimeUnit.NANOSECONDS;
        } else if (unitString.equals("d") || unitString.equals("days")) {
            units = TimeUnit.DAYS;
        } else if (unitString.equals("h") || unitString.equals("hours")) {
            units = TimeUnit.HOURS;
        } else if (unitString.equals("s") || unitString.equals("seconds")) {
            units = TimeUnit.SECONDS;
        } else if (unitString.equals("m") || unitString.equals("minutes")) {
            units = TimeUnit.MINUTES;
        } else {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse time unit '"
                            + originalUnitString
                            + "' (try ns, us, ms, s, m, d)");
        }

        try {
            // if the string is purely digits, parse as an integer to avoid
            // possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[0-9]+")) {
                return units.toNanos(Long.parseLong(numberString));
            } else {
                long nanosInUnit = units.toNanos(1);
                return (long) (Double.parseDouble(numberString) * nanosInUnit);
            }
        } catch (NumberFormatException e) {
            throw new ConfigException.BadValue(originForException,
                    pathForException, "Could not parse duration number '"
                            + numberString + "'");
        }
    }

    private static enum MemoryUnit {
        BYTES("", 1024, 0),

        KILOBYTES("kilo", 1000, 1),
        MEGABYTES("mega", 1000, 2),
        GIGABYTES("giga", 1000, 3),
        TERABYTES("tera", 1000, 4),
        PETABYTES("peta", 1000, 5),
        EXABYTES("exa", 1000, 6),
        ZETTABYTES("zetta", 1000, 7),
        YOTTABYTES("yotta", 1000, 8),

        KIBIBYTES("kibi", 1024, 1),
        MEBIBYTES("mebi", 1024, 2),
        GIBIBYTES("gibi", 1024, 3),
        TEBIBYTES("tebi", 1024, 4),
        PEBIBYTES("pebi", 1024, 5),
        EXBIBYTES("exbi", 1024, 6),
        ZEBIBYTES("zebi", 1024, 7),
        YOBIBYTES("yobi", 1024, 8);

        final String prefix;
        final int powerOf;
        final int power;
        final long bytes;

        MemoryUnit(String prefix, int powerOf, int power) {
            this.prefix = prefix;
            this.powerOf = powerOf;
            this.power = power;
            int i = power;
            long bytes = 1;
            while (i > 0) {
                bytes *= powerOf;
                --i;
            }
            this.bytes = bytes;
        }

        private static Map<String, MemoryUnit> makeUnitsMap() {
            Map<String, MemoryUnit> map = new HashMap<String, MemoryUnit>();
            for (MemoryUnit unit : MemoryUnit.values()) {
                map.put(unit.prefix + "byte", unit);
                map.put(unit.prefix + "bytes", unit);
                if (unit.prefix.length() == 0) {
                    map.put("b", unit);
                    map.put("B", unit);
                    map.put("", unit); // no unit specified means bytes
                } else {
                    String first = unit.prefix.substring(0, 1);
                    String firstUpper = first.toUpperCase();
                    if (unit.powerOf == 1024) {
                        map.put(first, unit);             // 512m
                        map.put(firstUpper, unit);        // 512M
                        map.put(firstUpper + "i", unit);  // 512Mi
                        map.put(firstUpper + "iB", unit); // 512MiB
                    } else if (unit.powerOf == 1000) {
                        if (unit.power == 1) {
                            map.put(first + "B", unit);      // 512kB
                        } else {
                            map.put(firstUpper + "B", unit); // 512MB
                        }
                    } else {
                        throw new RuntimeException("broken MemoryUnit enum");
                    }
                }
            }
            return map;
        }

        private static Map<String, MemoryUnit> unitsMap = makeUnitsMap();

        static MemoryUnit parseUnit(String unit) {
            return unitsMap.get(unit);
        }
    }

    /**
     * Parses a size-in-bytes string. If no units are specified in the string,
     * it is assumed to be in bytes. The returned value is in bytes. The purpose
     * of this function is to implement the size-in-bytes-related methods in the
     * Config interface.
     *
     * @param input
     *            the string to parse
     * @param originForException
     *            origin of the value being parsed
     * @param pathForException
     *            path to include in exceptions
     * @return size in bytes
     * @throws hexlab.morf.config.ConfigException
     *             if string is invalid
     */
    public static long parseBytes(String input, ConfigOrigin originForException,
            String pathForException) {
        String s = ConfigImplUtil.unicodeTrim(input);
        String unitString = getUnits(s);
        String numberString = ConfigImplUtil.unicodeTrim(s.substring(0,
                s.length() - unitString.length()));

        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length() == 0)
            throw new ConfigException.BadValue(originForException,
                    pathForException, "No number in size-in-bytes value '"
                            + input + "'");

        MemoryUnit units = MemoryUnit.parseUnit(unitString);

        if (units == null) {
            throw new ConfigException.BadValue(originForException, pathForException,
                    "Could not parse size-in-bytes unit '" + unitString
                            + "' (try k, K, kB, KiB, kilobytes, kibibytes)");
        }

        try {
            // if the string is purely digits, parse as an integer to avoid
            // possible precision loss; otherwise as a double.
            if (numberString.matches("[0-9]+")) {
                return Long.parseLong(numberString) * units.bytes;
            } else {
                return (long) (Double.parseDouble(numberString) * units.bytes);
            }
        } catch (NumberFormatException e) {
            throw new ConfigException.BadValue(originForException, pathForException,
                    "Could not parse size-in-bytes number '" + numberString + "'");
        }
    }

    private AbstractConfigValue peekPath(Path path) {
        return root().peekPath(path);
    }

    private static void addProblem(List<ConfigException.ValidationProblem> accumulator, Path path,
            ConfigOrigin origin, String problem) {
        accumulator.add(new ConfigException.ValidationProblem(path.render(), origin, problem));
    }

    private static String getDesc(ConfigValue refValue) {
        if (refValue instanceof AbstractConfigObject) {
            AbstractConfigObject obj = (AbstractConfigObject) refValue;
            if (obj.isEmpty())
                return "object";
            else
                return "object with keys " + obj.keySet();
        } else if (refValue instanceof SimpleConfigList) {
            return "list";
        } else {
            return refValue.valueType().name().toLowerCase();
        }
    }

    private static void addMissing(List<ConfigException.ValidationProblem> accumulator,
            ConfigValue refValue, Path path, ConfigOrigin origin) {
        addProblem(accumulator, path, origin, "No setting at '" + path.render() + "', expecting: "
                + getDesc(refValue));
    }

    private static void addWrongType(List<ConfigException.ValidationProblem> accumulator,
            ConfigValue refValue, AbstractConfigValue actual, Path path) {
        addProblem(accumulator, path, actual.origin(), "Wrong value type at '" + path.render()
                + "', expecting: " + getDesc(refValue) + " but got: "
                        + getDesc(actual));
    }

    private static boolean couldBeNull(AbstractConfigValue v) {
        return DefaultTransformer.transform(v, ConfigValueType.NULL)
                .valueType() == ConfigValueType.NULL;
    }

    private static boolean haveCompatibleTypes(ConfigValue reference, AbstractConfigValue value) {
        if (couldBeNull((AbstractConfigValue) reference) || couldBeNull(value)) {
            // we allow any setting to be null
            return true;
        } else if (reference instanceof AbstractConfigObject) {
            if (value instanceof AbstractConfigObject) {
                return true;
            } else {
                return false;
            }
        } else if (reference instanceof SimpleConfigList) {
            // objects may be convertible to lists if they have numeric keys
            if (value instanceof SimpleConfigList || value instanceof SimpleConfigObject) {
                return true;
            } else {
                return false;
            }
        } else if (reference instanceof ConfigString) {
            // assume a string could be gotten as any non-collection type;
            // allows things like getMilliseconds including domain-specific
            // interpretations of strings
            return true;
        } else if (value instanceof ConfigString) {
            // assume a string could be gotten as any non-collection type
            return true;
        } else {
            if (reference.valueType() == value.valueType()) {
                return true;
            } else {
                return false;
            }
        }
    }

    // path is null if we're at the root
    private static void checkValidObject(Path path, AbstractConfigObject reference,
            AbstractConfigObject value,
            List<ConfigException.ValidationProblem> accumulator) {
        for (Map.Entry<String, ConfigValue> entry : reference.entrySet()) {
            String key = entry.getKey();

            Path childPath;
            if (path != null)
                childPath = Path.newKey(key).prepend(path);
            else
                childPath = Path.newKey(key);

            AbstractConfigValue v = value.get(key);
            if (v == null) {
                addMissing(accumulator, entry.getValue(), childPath, value.origin());
            } else {
                checkValid(childPath, entry.getValue(), v, accumulator);
            }
        }
    }

    private static void checkListCompatibility(Path path, SimpleConfigList listRef,
            SimpleConfigList listValue, List<ConfigException.ValidationProblem> accumulator) {
        if (listRef.isEmpty() || listValue.isEmpty()) {
            // can't verify type, leave alone
        } else {
            AbstractConfigValue refElement = listRef.get(0);
            for (ConfigValue elem : listValue) {
                AbstractConfigValue e = (AbstractConfigValue) elem;
                if (!haveCompatibleTypes(refElement, e)) {
                    addProblem(accumulator, path, e.origin(), "List at '" + path.render()
                            + "' contains wrong value type, expecting list of "
                            + getDesc(refElement) + " but got element of type " + getDesc(e));
                    // don't add a problem for every last array element
                    break;
                }
            }
        }
    }

    private static void checkValid(Path path, ConfigValue reference, AbstractConfigValue value,
            List<ConfigException.ValidationProblem> accumulator) {
        // Unmergeable is supposed to be impossible to encounter in here
        // because we check for resolve status up front.

        if (haveCompatibleTypes(reference, value)) {
            if (reference instanceof AbstractConfigObject && value instanceof AbstractConfigObject) {
                checkValidObject(path, (AbstractConfigObject) reference,
                        (AbstractConfigObject) value, accumulator);
            } else if (reference instanceof SimpleConfigList && value instanceof SimpleConfigList) {
                SimpleConfigList listRef = (SimpleConfigList) reference;
                SimpleConfigList listValue = (SimpleConfigList) value;
                checkListCompatibility(path, listRef, listValue, accumulator);
            } else if (reference instanceof SimpleConfigList && value instanceof SimpleConfigObject) {
                // attempt conversion of indexed object to list
                SimpleConfigList listRef = (SimpleConfigList) reference;
                AbstractConfigValue listValue = DefaultTransformer.transform(value,
                        ConfigValueType.LIST);
                if (listValue instanceof SimpleConfigList)
                    checkListCompatibility(path, listRef, (SimpleConfigList) listValue, accumulator);
                else
                    addWrongType(accumulator, reference, value, path);
            }
        } else {
            addWrongType(accumulator, reference, value, path);
        }
    }

    @Override
    public boolean isResolved() {
        return root().resolveStatus() == ResolveStatus.RESOLVED;
    }

    @Override
    public void checkValid(Config reference, String... restrictToPaths) {
        SimpleConfig ref = (SimpleConfig) reference;

        // unresolved reference config is a bug in the caller of checkValid
        if (ref.root().resolveStatus() != ResolveStatus.RESOLVED)
            throw new ConfigException.BugOrBroken(
                    "do not call checkValid() with an unresolved reference config, call Config#resolve(), see Config#resolve() API docs");

        // unresolved config under validation is a bug in something,
        // NotResolved is a more specific subclass of BugOrBroken
        if (root().resolveStatus() != ResolveStatus.RESOLVED)
            throw new ConfigException.NotResolved(
                    "need to Config#resolve() each config before using it, see the API docs for Config#resolve()");

        // Now we know that both reference and this config are resolved

        List<ConfigException.ValidationProblem> problems = new ArrayList<ConfigException.ValidationProblem>();

        if (restrictToPaths.length == 0) {
            checkValidObject(null, ref.root(), root(), problems);
        } else {
            for (String p : restrictToPaths) {
                Path path = Path.newPath(p);
                AbstractConfigValue refValue = ref.peekPath(path);
                if (refValue != null) {
                    AbstractConfigValue child = peekPath(path);
                    if (child != null) {
                        checkValid(path, refValue, child, problems);
                    } else {
                        addMissing(problems, refValue, path, origin());
                    }
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new ConfigException.ValidationFailed(problems);
        }
    }

    @Override
    public SimpleConfig withOnlyPath(String pathExpression) {
        Path path = Path.newPath(pathExpression);
        return new SimpleConfig(root().withOnlyPath(path));
    }

    @Override
    public SimpleConfig withoutPath(String pathExpression) {
        Path path = Path.newPath(pathExpression);
        return new SimpleConfig(root().withoutPath(path));
    }

    @Override
    public SimpleConfig withValue(String pathExpression, ConfigValue v) {
        Path path = Path.newPath(pathExpression);
        return new SimpleConfig(root().withValue(path, v));
    }

    SimpleConfig atKey(ConfigOrigin origin, String key) {
        return root().atKey(origin, key);
    }

    @Override
    public SimpleConfig atKey(String key) {
        return root().atKey(key);
    }

    @Override
    public Config atPath(String path) {
        return root().atPath(path);
    }

    // serialization all goes through SerializedConfigValue
    private Object writeReplace() throws ObjectStreamException {
        return new SerializedConfigValue(this);
    }
}
