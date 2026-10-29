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

`run.ps1` and `run.sh` forward every argument to `com.danielh.App`. They do not add application options of their own. They do pass three JVM flags, `--enable-native-access=ALL-UNNAMED`, `--sun-misc-unsafe-memory-access=allow`, and `--enable-final-field-mutation=ALL-UNNAMED`, so the JDK does not print the `sun.misc.Unsafe` and final-field warnings. Java is not piped or redirected, so it keeps the console and the progress line can update in place.

### Windows

`run.ps1` compiles, copies dependencies, and starts Java with the Windows classpath separator, attached to the PowerShell console. Maven's own output, including errors, is left visible (`mvn -q` still prints failures). If Maven or Java fails, the script exits with that program's code. A successful search that opened the chart window does not return to the prompt until that window is closed.

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

`run.sh` is the same sequence as `run.ps1`, with `:` in the classpath. Java is attached to the terminal. Maven and Java errors stay visible, and a non-zero exit from either command is the script's exit code.

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

`optimise I350R 10` sets the population to 10 and leaves generations at 100. `optimise I350R --gens 5` leaves the population at 50 and runs 5 generations. `optimise I350R 10 5` sets both, and so does `optimise I350R --pop 10 --gens 5`. `--no-plot` turns the apogee chart off.

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

There is no switch for output path, log level, seed, or report format. `--no-plot` is the switch that skips the chart. While the search runs, a plain ASCII progress line shows the generation, the best mean apogee so far, and the elapsed time and ETA, for example `[##########----------]  45% gen 45/100 best 2553.9 m  elapsed 1:12 eta 1:28`. On a terminal it stays on one line. If output is piped, the same text is printed occasionally as normal lines. When the run finishes it prints one report to standard output and writes the chosen design to `rockets/GA_rocket_vN.ork`, using the next free `N`. Those files are gitignored, and so are the chart files written beside them.

### Apogee chart

During the search a separate window titled **Best apogee by generation** plots the best accepted apogee against generation. It is a plain Swing frame, opened on Swing's event thread when the run starts, and each generation repaints it without blocking the search. The ASCII progress line keeps printing in the terminal.

From PowerShell, `run.ps1` starts Java on that console, with no pipe. `System.console()` is set, so the ASCII progress stays on one line and rewrites itself with a carriage return while the search runs. The chart is not drawn in that console. It is its own desktop window, opened when the search starts, and it updates on each generation. The console attachment does not create, hide, or freeze that window.

When the search ends, the report is printed in PowerShell and `rockets/GA_rocket_vN.csv` and `rockets/GA_rocket_vN.png` are written next to the saved rocket. The window stays open. `run.ps1` is still waiting on Java, so the prompt returns when the window is closed. Closing the window during the search just dismisses it; the search keeps going and the script exits when the run finishes. `--no-plot` skips the window and does not write the CSV or PNG.

Over SSH, or anywhere `GraphicsEnvironment.isHeadless()` is true, the window is skipped and no exception is thrown. The CSV and PNG are still written, and the process exits as soon as the report is printed. The ASCII progress line is unchanged.

## How to read the report

After a one-line header (population, generations, time, motor, and the three wind cases), the report is two plain-ASCII tables. Columns are Parameter, Base, Optimised, and Change. Units are in the parameter name.

The design table is nose and fin geometry, Barrowman centre of pressure, launch centre of gravity, launch mass, and static margin in calibres. Those mass properties are OpenRocket's, from the rocket that was flown and saved, and they include the recovery parachute. The flight table is the mean of the three wind cases: mean apogee (this is the fitness), time to apogee, flight time, maximum velocity, maximum acceleration, maximum Mach, velocity off the rod, vertical descent rate at touchdown, speed at parachute deployment, and the lowest and highest in-flight stability.

**Static margin** is informational. It is centre of pressure minus launch centre of gravity, divided by the reference length. If the final design's static margin or worst in-flight margin is below 1 calibre, or the static margin or the highest in-flight margin is above 6 calibres, the report prints `WARNING: stability is outside 1 to 6 calibres. It is not part of the score.` The raw in-flight minimum includes samples near apogee, where a calibre stops meaning anything, so that warning can name a large negative number on a flight that was still accepted. Samples from the moment the parachute opens onward are left out of that range: the opening instant is not a flying margin.

**In-flight instability** is part of the score. Each wind case is checked from launch-rod clearance to apogee. A case is rejected for a tumble event (OpenRocket has left stable flight and will descend with the tumble model), an aborted simulation, stability below 1.0 cal for more than 0.05 s while faster than 30 m/s, or an angle of attack above 20 degrees for more than 0.05 s in that same window. Samples slower than 30 m/s are ignored. On an ascent longer than 2.0 s, the last 2.0 s before apogee are ignored as well; a shorter flight is judged on the fast samples it has, so a rocket that falls over during boost is still rejected. One failing wind case rejects the candidate. Its fitness is a floor of 1, which is worse than every positive apogee, so a higher apogee cannot buy a tumble or an unstable climb. The report prints the thresholds, how many candidate evaluations were rejected, and whether the base and optimised designs passed. The baseline flight is not included in that count.

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
- Ascent check: `AscentStability` reads the `TUMBLE`, `SIM_ABORT`, and `EXCEPTION` events, plus `TYPE_STABILITY`, `TYPE_VELOCITY_TOTAL`, and `TYPE_AOA` between rod clearance and apogee. The supersonic-body warning stays informational. The base file has no recovery device; `RecoveryChute` attaches one before every flight, so that warning is not raised for a search flight.
- Recovery: every candidate carries the same parachute, added in code by `RecoveryChute` (it is not baked into `GA_base_rocket.ork`). It is a round canopy, Cd 0.8, diameter 0.55 m, packed mass 0.025 kg, 4 cm behind the front of the body tube. It deploys at apogee plus 1 s. The diameter is sized for about 6 m/s vertical speed at the burnout mass; a base-rocket flight touches down near 5.8 m/s. Descent rate in the report is that vertical touchdown speed, so a crosswind does not make it look faster. Deployment speed is OpenRocket's speed at canopy opening, about 9 to 12 m/s on the base rocket.
