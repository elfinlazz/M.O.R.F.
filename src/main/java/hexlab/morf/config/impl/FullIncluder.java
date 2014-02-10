/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package hexlab.morf.config.impl;

import hexlab.morf.config.ConfigIncluder;
import hexlab.morf.config.ConfigIncluderClasspath;
import hexlab.morf.config.ConfigIncluderFile;
import hexlab.morf.config.ConfigIncluderURL;

interface FullIncluder extends ConfigIncluder, ConfigIncluderFile, ConfigIncluderURL,
        ConfigIncluderClasspath {

}
