# Rocket optimisation

A genetic algorithm (Jenetics) changes the nose cone, body tube, and trapezoidal fins of `rockets/GA_base_rocket.ork` and keeps the design with the highest mean apogee. Every candidate is flown with OpenRocket core **24.12** (`info.openrocket:core:24.12` from Maven Central). The project compiles as Java 26 (`maven.compiler.source` and `maven.compiler.target` in `pom.xml`). The base file was saved by OpenRocket 23.09 and loads in 24.12. It needs a nose cone, a body tube, a trapezoidal fin set, and a motor mount.

Run commands from the project directory. The rocket path is relative to that directory.

## Run an optimisation

The only command is `optimise` (any capitalisation). Anything else, including `optimize` or no command at all, prints a short usage hint that names `optimise` and exits with code 1.

```
optimise [motor] [population] [generations]
optimise [motor] [--pop <population>] [--gens <generations>]
```

| Argument | Type | Default | What it does |
|---|---|---|---|
| `motor` | text | `I350R` | First word that is not an option. Looked up in OpenRocket's motor database and mounted for the whole run, replacing the motor saved in the `.ork`. |
| `population` or `--pop` | positive integer | `50` | Jenetics population size. |
| `generations` or `--gens` | positive integer | `100` | Number of generations. |

Population and generations are independent. Give both, either, or neither. A single number after the motor is the population, and generations stay at 100. To set only the generation count, use `--gens`. `--pop` and `--gens` also accept `--pop=10` and `--gens=5`. Giving the same count twice (for example a positional population and `--pop`) prints the usage hint and exits 1.

`run.ps1` and `run.sh` forward every argument to `com.danielh.App`. They do not add options of their own.

### Windows

`run.ps1` compiles, copies dependencies, and starts Java with the Windows classpath separator. It drops a few JDK warning lines (`sun.misc.Unsafe`, final-field mutation, and similar). Maven's own output, including errors, is left visible. If Maven or Java fails, the script exits with that program's code.

```powershell
./run.ps1 optimise I350R 50 100
```

Quick check:

```powershell
./run.ps1 optimise I350R 3 1
```

Motor only, leaving population and generations at 50 and 100:

```powershell
./run.ps1 optimise H128W-6
```

Defaults for everything (`I350R`, population 50, generations 100):

```powershell
./run.ps1 optimise
```

Population 50, but only 5 generations:

```powershell
./run.ps1 optimise I350R --gens 5
```

### Unix

`run.sh` is the same sequence as `run.ps1`, with `:` in the classpath. Maven and Java errors stay on the terminal, and a non-zero exit from either command is the script's exit code.

```bash
./run.sh optimise I350R 50 100
./run.sh optimise H128W-6 --pop 10 --gens 5
```

### Maven or Java

The same entry point, without the PowerShell wrapper. This is the form that works in bash. On PowerShell, prefer `run.ps1`, because `-Dexec.args=...` is easy to split on spaces.

```bash
mvn -q compile exec:java -Dexec.args="optimise I350R 50 100"
```

Or compile once and launch Java directly. The classpath separator is `:` on Linux and macOS, and `;` on Windows (which is what `run.ps1` uses).

```bash
mvn -q compile dependency:copy-dependencies
java --add-opens java.base/java.lang=ALL-UNNAMED \
  -cp "target/classes:target/dependency/*" \
  com.danielh.App optimise I350R 3 1
```

`mvn package` also builds `target/rocket-app.jar`. `target/` is build output and is not part of the repository. OpenRocket does not need to be built from source.

## What you can change from the command line

### Motor

The name is matched to a designation or common name in the bundled thrust-curve database. Forms that work:

| Example | Meaning |
|---|---|
| `I350R-P` or `I350R-plugged` | Plugged: no ejection charge. This is the only delay the bundled AeroTech I350R curve lists. |
| `I350R` | No delay in the name. The shortest listed delay is used. For the bundled I350R curve that is plugged, the same flight as `I350R-P`. |
| `H128W-6` | A numeric delay that is on that curve (within 0.05 s of a listed delay). |
| `AeroTech/I350R` | Manufacturer, a slash, then the designation. Matching ignores case and punctuation in the manufacturer name. |
| `AeroTech I350R-P` | Same idea, with a space before the designation. |

A typed delay that is not in the curve's standard delays is rejected. The run exits 1 and lists those delays and the names that work. `I350R-0` does this: the bundled curve lists only `P`, and the message says to use `I350R-P` or bare `I350R`. Several curves that share one manufacturer, designation, and diameter resolve to the one with the greatest launch mass.

There is no command that lists the database. To find a name, try a designation. An unknown name stops the run with exit code 1, prints an example of the accepted form, and lists up to eight close matches (edit distance up to 2, or a designation that contains the text you typed), each with manufacturer and delays. If nothing is close, the list says `none`.

If the same designation belongs to more than one manufacturer or diameter, the run stops with exit code 1 and lists each one. Add the manufacturer, for example `AeroTech/G80-7`.

A motor wider than the mount by more than 2 mm prints a warning and is still installed. Mass, centre of gravity, stability, and thrust all follow the motor that was mounted. The written `.ork` contains that motor.

### Population and generations

`optimise I350R 10` sets the population to 10 and leaves generations at 100. `optimise I350R --gens 5` leaves the population at 50 and runs 5 generations. `optimise I350R 10 5` sets both, and so does `optimise I350R --pop 10 --gens 5`.

A value that is not a positive integer (letters, zero, or a negative number) prints the usage hint and exits 1. The run does not fall back to the defaults.

Each candidate is three flight simulations (one per wind case), run together. A smoke test is a population of 3 to 10 and 1 to 5 generations. The default 50 and 100 is a long run. The search ranges themselves are not arguments; they are the `DoubleRange` constants in `RocketOptimizer.java`:

| Parameter | Range |
|---|---|
| Nose-cone length | 0.10 to 1.00 m |
| Body-tube length | 0.40 to 1.00 m |
| Fin root chord | 0.10 to 0.20 m |
| Fin tip chord | 0.00 to 0.20 m |
| Fin height | 0.03 to 0.20 m |
| Fin sweep length | 0.00 to 0.05 m |

## What is not a command-line option

The rocket file is always `rockets/GA_base_rocket.ork`, set in `App.java`.

Wind is always the three cases in `WindCases.java`. Seeds are fixed, so the same geometry scores the same way every time. The rod is launched into the wind.

| Case | Average wind | Turbulence intensity | OpenRocket's label |
|---|---|---|---|
| calm | 1 m/s (about 2 knots) | 0.08 | low |
| moderate | 4 m/s (about 8 knots) | 0.12 | medium |
| breezy | 7 m/s (about 14 knots) | 0.18 | high |

There is no switch for output path, log level, seed, or report format. While the search runs, a plain ASCII progress line shows the generation, the best mean apogee so far, and the elapsed time and ETA, for example `[##########----------]  45% gen 45/100 best 2553.9 m  elapsed 1:12 eta 1:28`. On a terminal it stays on one line. If output is piped, the same text is printed occasionally as normal lines. When the run finishes it prints one report to standard output and writes the chosen design to `rockets/GA_rocket_vN.ork`, using the next free `N`. Those files are gitignored.

## How to read the report

After a one-line header (population, generations, time, motor, and the three wind cases), the report is two plain-ASCII tables. Columns are Parameter, Base, Optimised, and Change. Units are in the parameter name.

The design table is nose and fin geometry, Barrowman centre of pressure, launch centre of gravity, launch mass, and static margin in calibres. Those mass properties are OpenRocket's, from the rocket that was flown and saved. The flight table is the mean of the three wind cases: mean apogee (this is the fitness), time to apogee, flight time, maximum velocity, maximum acceleration, maximum Mach, velocity off the rod, and the lowest and highest in-flight stability.

**Stability** is informational. Static margin is centre of pressure minus launch centre of gravity, divided by the reference length. If the final design's static margin or worst in-flight margin is below 1 calibre, or the static margin or the highest in-flight margin is above 6 calibres, the report prints `WARNING: stability is outside 1 to 6 calibres. It is not part of the score.`

**Verdict** compares the optimised mean apogee with the base mean apogee:

| Difference | Line in the report |
|---|---|
| More than 1 m higher | `OPTIMISATION SUCCESSFUL` |
| From 1 m lower through 1 m higher | `NO SIGNIFICANT CHANGE` |
| More than 1 m lower | `OPTIMISATION FAILED` |

A successful run ends with exit code 0. An unknown command, a bad population or generation count, and an unknown or ambiguous motor end with exit code 1 before any flights. If `rockets/GA_base_rocket.ork` cannot be loaded, the program prints `Failed to load baseline rocket:` and the file path, then exits 1.

## Flight model

Centre of pressure, centre of gravity, and the stability margin come from OpenRocket.

- CP: `BarrowmanCalculator.getWorstCP` at the default Mach (0.3) and zero angle of attack, which is the design-figure value, plus `getCP` at zero wind angle.
- CG and mass: `MassCalculator.calculateLaunch`, `calculateBurnout`, and `calculateStructure` on the selected flight configuration.
- Stability in calibres: `(CP.x - launch CG.x) / FlightConfiguration.getReferenceLength()`.
- Flight data: `FlightData` for apogee, time, velocity, acceleration, and velocity off the rod. `FlightDataType.TYPE_STABILITY` on the sustainer branch supplies the in-flight range. Centre-of-pressure samples before the rocket leaves the rod are empty, so the in-flight range starts after launch-rod clearance.
