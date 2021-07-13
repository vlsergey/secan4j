# Security Annotations Initiative for Java
This is master repository for secan4j project.

## Goal of the project
- Make Java applications more secure.
- Detect and reduce front of attack for:
  - SQL Injections
  - XSS attack
  - Sensitive Information leaks

## How does it work
- secan4j uses taint static analysys to assign security annotation for every argument and variable used in Java program
- if two incopatible annotations were found on single variable (like `userprovided` and `sqlcommand`, or `sensitive` and `logoutput`) error will be written to analysis log

## Direct run from command line
```
java -cp ... io.github.vlsergey.secan4j.core.Secan4j [--basePackage=<basePackage>] <classPath>

      <classPath>   Application classpath to scan (URLs)
      --basePackage=<basePackage>
                    Base package to scan for entry points
```

### Output result example
```
Found source-sink link with following trace:
* io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample:sqlInjection:21 MethodParameterTraceItem [Annotation @RequestParam configured as @UserProvided on argument #1 of method 'sqlInjection' of class io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample]
* (no source code position) CopierBrush.CopyTraceItem(src=MethodParameterTraceItem [Annotation @RequestParam configured as @UserProvided on argument #1 of method 'sqlInjection' of class io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample])
* (no source code position) CopierBrush.CopyTraceItem(src=CopierBrush.CopyTraceItem(src=MethodParameterTraceItem [Annotation @RequestParam configured as @UserProvided on argument #1 of method 'sqlInjection' of class io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample]))
* java.sql.Connection:prepareStatement:-1 MethodParameterTraceItem [Configuration info for argument #0 of method 'prepareStatement' of class java.sql.Connection]
```
