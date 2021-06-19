package io.github.vlsergey.secan4j.annotations;

/**
 * How should secan4j deal when source data (marked with {@link UserProvided}
 * for example) comes to node marked with sink annotation ({@link Command} for
 * example). {@link #REPORT} means this case must be reported.
 * {@link #OVERWRITE} means secan4j must assume that data are always
 * {@link Command} and ignore incoming change.
 * <p>
 * This is usefull when user code contains data escaping procedure and developer
 * 100% sure that this method is correct:
 * 
 * <pre>
 * public static @Command(OVERWRITE) String escapeSqlData(@UserProvided String userData) {
 *     // incoming is userData, but return is okay to include in SQL
 *     return doEscape(userData);
 * }
 * </pre>
 */
public enum OnIntersection {

    OVERWRITE,

    REPORT,

    ;

}
