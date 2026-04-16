@echo off
REM Wrapper script to run the app with JVM module opening and filtered output
setlocal enabledelayedexpansion

REM Build if needed
echo Building project...
call mvn clean compile dependency:copy-dependencies -q 2>nul

REM Run Java with warnings filtered out
echo Running application...
java --add-opens java.base/java.lang=ALL-UNNAMED ^
     -cp "target/classes;target/dependency/*" ^
     com.danielh.App 2>&1 | findstr /v "WARNING: package sun.misc" | findstr /v "WARNING: A terminally deprecated" | findstr /v "WARNING: Please consider reporting" | findstr /v "WARNING: sun.misc.Unsafe" | findstr /v "WARNING: Final field" | findstr /v "WARNING: Use --enable-final-field-mutation" | findstr /v "WARNING: Mutating final fields"

endlocal
