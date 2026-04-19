Rocket optimisation uses a genetic algorithm (Jenetics.io) to optimise rocket design parameters for maximum apogee. 
OpenRocket core physics engine is used to simulate rocket flight and extract flight data (apogee, max acceleration, max velocity).
Uses OpenRocket-23.09 with java 17. 
GA_base_rocket.ork file is specified in /rockets, algorithm iterates on this base rocket to improve design. 
Base rocket should include all internal components and NoseCone, BodyTube and TrapezoidalFinSet. GA optimises these parameters.
Allowable ranges for genotype parameters need to be specified RocketOptimiser.java. 
Output rocket design written to /rockets/GA_rocket_vX.ork.
Run with Windows PowerShell: cd: ./run.ps1 optimise x y
where x, y are pop_size and generations e.g. 50 100
