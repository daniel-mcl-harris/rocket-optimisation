Rocket optimisation uses a genetic algorithm (Jenetics) to optimise nose-cone length, body-tube length and trapezoidal fin geometry for maximum apogee.

The flight model is OpenRocket core **24.12** (`info.openrocket:core:24.12` on Maven Central). `pom.xml` compiles as Java 26. The base design `rockets/GA_base_rocket.ork` was saved by OpenRocket 23.09 and loads in 24.12. It needs a nose cone, a body tube and a trapezoidal fin set, plus the motor that should actually be flown. The command-line motor name checks the request against that mounted motor; mass, centre of gravity and thrust come from the motor in the file (AeroTech I350R for the base rocket).

During a run, centre of pressure, centre of gravity and the stability margin come from OpenRocket rather than from a length estimate:

- CP: `BarrowmanCalculator.getWorstCP` (the design-figure value) and `getCP` at zero wind angle, with `FlightConditions` at the default Mach (0.3) and zero angle of attack.
- CG and mass: `MassCalculator.calculateLaunch`, `calculateBurnout` and `calculateStructure` on the selected `FlightConfiguration`.
- Stability margin in calibres: `(CP.x - launch CG.x) / FlightConfiguration.getReferenceLength()`.
- Flight data: `Simulation.simulate()` then `FlightData` (`getMaxAltitude`, `getMaxVelocity`, `getMaxAcceleration`, `getTimeToApogee`, `getLaunchRodVelocity`) and `FlightDataType.TYPE_STABILITY` on the sustainer branch.

Designs with a launch stability margin below 2 calibres are penalised. Genotype ranges are in `RocketOptimizer.java`. Each accepted design is written to `rockets/GA_rocket_vX.ork`.

On Windows, from the project directory:

```
./run.ps1 optimise I350R-0 50 100
```

The arguments are the motor name, population size and number of generations. Maven resolves the OpenRocket jar; OpenRocket itself does not need to be built from source. `target/` is build output and is not part of the repository.
