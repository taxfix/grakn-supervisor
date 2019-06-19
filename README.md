# Grakn Supervisor

Supervisor process to manage both storage and server Grakn process. Includes a probe to periodically test Grakn health,
and manage that all process shutdown in a all-for-all strategy.

## Build
```
sbt universal:packageBin
```