package io.github.vlsergey.secan4j.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks parameters for database processing (SQL), shell command line text, etc.
 */
@Inherited
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER, METHOD })
public @interface Command {

}
