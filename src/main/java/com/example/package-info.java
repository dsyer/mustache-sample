@JStachePath(prefix = "templates/", suffix = ".mustache")
@JStacheFlags(flags = Flag.DEBUG)
@JStacheFormatterTypes(types = LocalDate.class)
package com.example;

import java.time.LocalDate;

import io.jstach.jstache.JStacheFlags;
import io.jstach.jstache.JStacheFlags.Flag;
import io.jstach.jstache.JStacheFormatterTypes;
import io.jstach.jstache.JStachePath;
