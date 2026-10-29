# Rocket optimisation

A genetic algorithm (Jenetics) changes the nose cone, body tube, and trapezoidal fins of `rockets/GA_base_rocket.ork` and keeps the design with the highest mean apogee. Every candidate is flown with OpenRocket core **24.12** (`info.openrocket:core:24.12` from Maven Central). The project compiles as Java 26 (`maven.compiler.source` and `maven.compiler.target` in `pom.xml`). The base file was saved by OpenRocket 23.09 and loads in 24.12. It needs a nose cone, a body tube, a trapezoidal fin set, and a motor mount.

Run commands from the project directory. The rocket path is relative to that directory.

## Run an optimisation

There is one command, `optimise`. The spelling is fixed; `optimize` is not recognised. There are no flags. Arguments are positional:

```
optimise [motor] [population generations]
```

| Position | Name | Type | Default | What it does |
|---|---|---|---|---|
| 1 | command | word | none | Must be `optimise`, compared without regard to case. |
| 2 | motor | text | `I350R-0` | Looked up in OpenRocket's motor database and mounted for the whole run, replacing the motor saved in the `.ork`. |
| 3 | population | integer | `50` | Jenetics population size. Used only when argument 4 is also present. |
| 4 | generations | integer | `100` | Number of generations. Used only when argument 3 is also present. |

`run.ps1` forwards every argument to `com.danielh.App`. It does not add options of its own.

### Windows

`run.ps1` compiles, copies dependencies, and starts Java with the Windows classpath separator. It drops a few JDK warning lines (`sun.misc.Unsafe`, final-field mutation, and similar). Maven's own error output is discarded.

```powershell
./run.ps1 optimise I350R-0 50 100
```

Quick check:

```powershell
./run.ps1 optimise I350R-0 3 1
```

Motor only, leaving population and generations at 50 and 100:

```powershell
./run.ps1 optimise H128W-6
```

Defaults for everything (`I350R-0`, population 50, generations 100):

```powershell
./run.ps1 optimise
```

### Maven or Java

The same entry point, without the PowerShell wrapper. This is the form that works in bash. On PowerShell, prefer `run.ps1`, because `-Dexec.args=...` is easy to split on spaces.

```bash
mvn -q compile exec:java -Dexec.args="optimise I350R-0 50 100"
```

Or compile once and launch Java directly. The classpath separator is `:` on Linux and macOS, and `;` on Windows (which is what `run.ps1` uses).

```bash
mvn -q compile dependency:copy-dependencies
java --add-opens java.base/java.lang=ALL-UNNAMED \
  -cp "target/classes:target/dependency/*" \
  com.danielh.App optimise I350R-0 3 1
```

`mvn package` also builds `target/rocket-app.jar`. `target/` is build output and is not part of the repository. OpenRocket does not need to be built from source.

## What you can change from the command line

### Motor

The name is matched to a designation or common name in the bundled thrust-curve database. Forms that work:

| Example | Meaning |
|---|---|
| `I350R-0` | Designation `I350R`, ejection delay 0 seconds (at burnout). |
| `I350R-6` | Same motor, 6 second delay, when that delay is within 0.05 s of a listed delay. |
| `I350R-P` or `I350R-plugged` | Plugged: no ejection charge. |
| `I350R` | No delay in the name. The shortest listed delay is used. If the curve only lists plugged, that is what flies. For the bundled I350R curve that is plugged. |
| `AeroTech/I350R-0` | Manufacturer, a slash, then the designation and delay. Matching ignores case and punctuation in the manufacturer name. |
| `AeroTech I350R-0` | Same idea, with a space before the designation. |

A delay written as a number is still mounted when that exact delay is not on the curve. `I350R-0` is the important case: the bundled AeroTech curve lists only plugged, and 0 is still installed so the file's burnout ejection is kept. Several curves that share one manufacturer, designation, and diameter resolve to the one with the greatest launch mass.

There is no command that lists the database. To find a name, try a designation. An unknown name stops the run with exit code 1, prints an example of the accepted form, and lists up to eight close matches (edit distance up to 2, or a designation that contains the text you typed), each with manufacturer and delays. If nothing is close, the list says `none`.

If the same designation belongs to more than one manufacturer or diameter, the run stops with exit code 1 and lists each one. Add the manufacturer, for example `AeroTech/G80-7`.

A motor wider than the mount by more than 2 mm prints a warning and is still installed. Mass, centre of gravity, stability, and thrust all follow the motor that was mounted. The written `.ork` contains that motor.

### Population and generations

Both numbers have to be present. `optimise I350R-0 10` does not set the population to 10; arguments 3 and 4 are read together, so this stays at 50 and 100.

Each candidate is three flight simulations (one per wind case), run together. A smoke test is a population of 3 to 10 and 1 to 5 generations. The default 50 and 100 is a long run. The search ranges themselves are not arguments; they are the `DoubleRange` constants in `RocketOptimizer.java`:

| Parameter | Range |
|---|---|
| Nose-cone length | 0.10 to 1.00 m |
| Body-tube length | 0.40 to 1.00 m |
| Fin root chord | 0.10 to 0.20 m |
| Fin tip chord | 0.00 to 0.20 m |
| Fin height | 0.03 to 0.20 m |
| Fin sweep length | 0.00 to 0.05 m |

If either number is not an integer, the program prints `Invalid parameters. Usage: App optimise <motorType> [population] [generations]` and then continues with population 50 and generations 100. It does not stop, and it does not check that the integers are positive.

## What is not a command-line option

The rocket file is always `rockets/GA_base_rocket.ork`, set in `App.java`.

Wind is always the three cases in `WindCases.java`. Seeds are fixed, so the same geometry scores the same way every time. The rod is launched into the wind.

| Case | Average wind | Turbulence intensity | OpenRocket's label |
|---|---|---|---|
| calm | 1 m/s (about 2 knots) | 0.08 | low |
| moderate | 4 m/s (about 8 knots) | 0.12 | medium |
| breezy | 7 m/s (about 14 knots) | 0.18 | high |

There is no switch for output path, log level, seed, or report format. A progress line counts generations. When the run finishes it prints one report to standard output and writes the chosen design to `rockets/GA_rocket_vN.ork`, using the next free `N`. Those files are gitignored.

## How to read the report

The report has a configuration block (population, generations, motor, wind cases), then the base design and the optimised design. For each design it prints the six geometry values, launch and burnout mass and centre of gravity, a component breakdown, centre of pressure, and stability.

**Mean apogee (fitness)** is the average of the three wind cases. That number is the only thing the search maximises. Time to apogee, flight time, maximum velocity, maximum acceleration, and velocity off the rod are averages too, and each wind case is listed under them with its own apogee, rod speed, and minimum stability.

**Stability** is informational. Static margin is the Barrowman centre of pressure minus the launch centre of gravity, divided by the rocket's reference length, in calibres. In-flight stability is the lowest and highest sample across all three flights. Neither one changes the score. If the final design's static margin or worst in-flight margin is below 1 calibre, or the static margin or the highest in-flight margin is above 6 calibres, the report prints `WARNING: stability is outside 1 to 6 calibres. It is not part of the score.`

**Verdict** compares the optimised mean apogee with the base mean apogee:

| Difference | Line in the report |
|---|---|
| More than 1 m higher | `OPTIMISATION SUCCESSFUL` |
| From 1 m lower through 1 m higher | `NO SIGNIFICANT CHANGE` |
| More than 1 m lower | `OPTIMISATION FAILED` |

A successful run ends with exit code 0. An unknown or ambiguous motor ends with exit code 1 before any flights. If the rocket file cannot be loaded, the program prints `Failed to load baseline rocket` and returns without a non-zero exit code.

## Flight model

Centre of pressure, centre of gravity, and the stability margin come from OpenRocket.

- CP: `BarrowmanCalculator.getWorstCP` at the default Mach (0.3) and zero angle of attack, which is the design-figure value, plus `getCP` at zero wind angle.
- CG and mass: `MassCalculator.calculateLaunch`, `calculateBurnout`, and `calculateStructure` on the selected flight configuration.
- Stability in calibres: `(CP.x - launch CG.x) / FlightConfiguration.getReferenceLength()`.
- Flight data: `FlightData` for apogee, time, velocity, acceleration, and velocity off the rod. `FlightDataType.TYPE_STABILITY` on the sustainer branch supplies the in-flight range. Centre-of-pressure samples before the rocket leaves the rod are empty, so the in-flight range starts after launch-rod clearance.
