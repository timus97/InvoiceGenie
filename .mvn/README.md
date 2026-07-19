# Maven project marker

This directory marks the multi-module root for Maven base-dir detection
(important on Windows when the drive root would otherwise be selected).

- `maven.config` — optional default Maven CLI flags (one flag per line)
- Do **not** put shell comments in `jvm.config`; every non-empty line is a JVM arg
