Rocket optimisation uses a genetic algorithm (Jenetics) to optimise nose-cone length, body-tube length and trapezoidal fin geometry for maximum apogee.

The flight model is OpenRocket core **24.12** (`info.openrocket:core:24.12` on Maven Central). `pom.xml` compiles as Java 26. The base design `rockets/GA_base_rocket.ork` was saved by OpenRocket 23.09 and loads in 24.12. It needs a nose cone, a body tube, a trapezoidal fin set, and a motor mount.

The motor argument is looked up in OpenRocket's thrust-curve database and mounted for the whole run, replacing the motor saved in the file. Mass, centre of gravity, stability and thrust all follow that motor, and the written `.ork` has it installed. Names look like `I350R-0`, `I350R-P` (plugged), or `AeroTech/I350R-0`. If the delay is omitted, the shortest listed delay is used (plugged, for the bundled I350R curve). A numeric delay that the curve does not list is still mounted, which is how `I350R-0` flies: the database curve is plugged-only, and 0 means ejection at burnout, matching the base file. An unknown or ambiguous name stops the run and lists close matches or the manufacturers that collided. When several thrust curves share one manufacturer, designation and diameter, the heaviest launch mass is used.

During a run, centre of pressure, centre of gravity and the stability margin come from OpenRocket rather than from a length estimate:

- CP: `BarrowmanCalculator.getWorstCP` (the design-figure value) and `getCP` at zero wind angle, with `FlightConditions` at the default Mach (0.3) and zero angle of attack.
- CG and mass: `MassCalculator.calculateLaunch`, `calculateBurnout` and `calculateStructure` on the selected `FlightConfiguration`.
- Stability margin in calibres: `(CP.x - launch CG.x) / FlightConfiguration.getReferenceLength()`.
- Flight data: each candidate is simulated on the three wind cases in `WindCases` and the apogee, times, velocity, acceleration and rod-exit speed are averaged. `FlightDataType.TYPE_STABILITY` is read on the sustainer branch. The rod is launched into the wind. The reported in-flight stability is the lowest and highest sample across all three cases, so weathercocking on the breezy case is visible.
- The pink-noise model for each case is built with a fixed seed (`24011`, `24012`, `24013`). OpenRocket's `setRandomSeed` does not replace that model, so the run installs one directly. The same geometry therefore scores the same every time.

The wind cases are a light breeze, a typical club afternoon, and a breezy day:

| Case | Average wind | Turbulence intensity | OpenRocket's label |
|---|---|---|---|
| calm | 1 m/s (about 2 knots) | 0.08 | low |
| moderate | 4 m/s (about 8 knots) | 0.12 | medium |
| breezy | 7 m/s (about 14 knots) | 0.18 | high |

Designs are penalised when the smaller of the static Barrowman margin and the worst in-flight margin across those winds is below 2 calibres. An apogee change of 1 m or less is reported as no significant change. Genotype ranges are in `RocketOptimizer.java`. The fin sweep length range is 0 to 0.05 m. Each accepted design is written to `rockets/GA_rocket_vX.ork`.

On Windows, from the project directory:

```
./run.ps1 optimise I350R-0 50 100
```

The arguments are the motor name, population size and number of generations. Maven resolves the OpenRocket jar; OpenRocket itself does not need to be built from source. `target/` is build output and is not part of the repository.
