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
