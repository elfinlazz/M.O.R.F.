/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package hexlab.morf.config;

/**
 * <p>
 * A set of options related to rendering a {@link ConfigValue}. Passed to
 * {@link ConfigValue#render(ConfigRenderOptions)}.
 *
 * <p>
 * Here is an example of creating a {@code ConfigRenderOptions}:
 *
 * <pre>
 *     ConfigRenderOptions options =
 *         ConfigRenderOptions.defaults().setComments(false)
 * </pre>
 */
public final class ConfigRenderOptions {
    private final boolean originComments;
    private final boolean comments;
    private final boolean formatted;
    private final boolean json;

    private ConfigRenderOptions(boolean originComments, boolean comments, boolean formatted,
            boolean json) {
        this.originComments = originComments;
        this.comments = comments;
        this.formatted = formatted;
        this.json = json;
    }

    /**
     * Returns the default render options which are verbose (commented and
     * formatted). See {@link ConfigRenderOptions#concise} for stripped-down
     * options. This rendering will not be valid JSON since it has comments.
     *
     * @return the default render options
     */
    public static ConfigRenderOptions defaults() {
        return new ConfigRenderOptions(true, true, true, true);
    }

    /**
     * Returns concise render options (no whitespace or comments). For a
     * resolved {@link Config}, the concise rendering will be valid JSON.
     *
     * @return the concise render options
     */
    public static ConfigRenderOptions concise() {
        return new ConfigRenderOptions(false, false, false, true);
    }

    /**
     * Returns options with comments toggled. This controls human-written
     * comments but not the autogenerated "origin of this setting" comments,
     * which are controlled by {@link ConfigRenderOptions#setOriginComments}.
     *
     * @param value
     *            true to include comments in the render
     * @return options with requested setting for comments
     */
    public ConfigRenderOptions setComments(boolean value) {
        if (value == comments)
            return this;
        else
            return new ConfigRenderOptions(originComments, value, formatted, json);
    }

    /**
     * Returns whether the options enable comments. This method is mostly used
     * by the config lib internally, not by applications.
     *
     * @return true if comments should be rendered
     */
    public boolean getComments() {
        return comments;
    }

    /**
     * Returns options with origin comments toggled. If this is enabled, the
     * library generates comments for each setting based on the
     * {@link ConfigValue#origin} of that setting's value. For example these
     * comments might tell you which file a setting comes from.
     *
     * <p>
     * {@code setOriginComments()} controls only these autogenerated
     * "origin of this setting" comments, to toggle regular comments use
     * {@link ConfigRenderOptions#setComments}.
     *
     * @param value
     *            true to include autogenerated setting-origin comments in the
     *            render
     * @return options with origin comments toggled
     */
    public ConfigRenderOptions setOriginComments(boolean value) {
        if (value == originComments)
            return this;
        else
            return new ConfigRenderOptions(value, comments, formatted, json);
    }

    /**
     * Returns whether the options enable automated origin comments. This method
     * is mostly used by the config lib internally, not by applications.
     *
     * @return true if origin comments should be rendered
     */
    public boolean getOriginComments() {
        return originComments;
    }

    /**
     * Returns options with formatting toggled. Formatting means indentation and
     * whitespace, enabling formatting makes things prettier but larger.
     *
     * @param value
     *            true to include comments in the render
     * @return options with requested setting for formatting
     */
    public ConfigRenderOptions setFormatted(boolean value) {
        if (value == formatted)
            return this;
        else
            return new ConfigRenderOptions(originComments, comments, value, json);
    }

    /**
     * Returns whether the options enable formatting. This method is mostly used
     * by the config lib internally, not by applications.
     *
     * @return true if comments should be rendered
     */
    public boolean getFormatted() {
        return formatted;
    }

    /**
     * Returns options with JSON toggled. JSON means that HOCON extensions
     * (omitting commas, quotes for example) won't be used. However, whether to
     * use comments is controlled by the separate {@link #setComments(boolean)}
     * and {@link #setOriginComments(boolean)} options. So if you enable
     * comments you will get invalid JSON despite setting this to true.
     *
     * @param value
     *            true to include non-JSON extensions in the render
     * @return options with requested setting for JSON
     */
    public ConfigRenderOptions setJson(boolean value) {
        if (value == json)
            return this;
        else
            return new ConfigRenderOptions(originComments, comments, formatted, value);
    }

    /**
     * Returns whether the options enable JSON. This method is mostly used by
     * the config lib internally, not by applications.
     *
     * @return true if only JSON should be rendered
     */
    public boolean getJson() {
        return json;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ConfigRenderOptions(");
        if (originComments)
            sb.append("originComments,");
        if (comments)
            sb.append("comments,");
        if (formatted)
            sb.append("formatted,");
        if (json)
            sb.append("json,");
        if (sb.charAt(sb.length() - 1) == ',')
            sb.setLength(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }
}