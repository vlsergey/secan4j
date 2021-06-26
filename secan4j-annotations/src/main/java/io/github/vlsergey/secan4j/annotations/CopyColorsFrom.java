package io.github.vlsergey.secan4j.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Mark target to copy color from
 */
@Inherited
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER, METHOD })
public @interface CopyColorsFrom {

    OnIntersection value() default OnIntersection.REPORT;

}
