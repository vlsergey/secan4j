package io.github.vlsergey.secan4j.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Mark field that is the most important field of the class and thus defines
 * class attributes. For example {@link String}'s or
 * {@link java.lang.AbstractStringBuilder} <code>value</code> fields.
 *
 * Works in both way, but field-to-parent is most important (because
 * parent-to-field is usual empiric anyway).
 */
@Inherited
@Retention(RUNTIME)
@Target({ FIELD })
public @interface ParentAttributesDefiner {

}
