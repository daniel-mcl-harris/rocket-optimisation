package com.danielh;

import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.util.DoubleRange;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

public class RocketOptimizer {
    
    private static final java.io.PrintStream ORIGINAL_OUT = System.out;
    private static final java.io.PrintStream ORIGINAL_ERR = System.err;
    
    private static final DoubleRange NOSE_CONE_RANGE = DoubleRange.of(0.1, 1.0);
    private static final DoubleRange BODY_TUBE_RANGE = DoubleRange.of(0.4, 1.0);
    private static final DoubleRange FIN_ROOT_CHORD_RANGE = DoubleRange.of(0.1, 0.2);
    private static final DoubleRange FIN_TIP_CHORD_RANGE = DoubleRange.of(0.0, 0.2);
    private static final DoubleRange FIN_HEIGHT_RANGE = DoubleRange.of(0.03, 0.2);
    private static final DoubleRange FIN_SWEEP_LENGTH_RANGE = DoubleRange.of(0.0, 0.05);
    
    private static String rocketFilePath;
    private static MotorCatalogue.Selection motor;
    private static final AtomicInteger rejectedEvaluations = new AtomicInteger();
    private static volatile boolean countRejections;
    
    public static OptimisationReport optimise(int populationSize, int generations, String rocketPath,
                                               double baselineNose, double baselineBody,
                                               double baselineRootChord, double baselineTipChord,
                                               double baselineHeight, double baselineSweepLength,
                                               MotorCatalogue.Selection selectedMotor, boolean plot) {
        long startTime = System.currentTimeMillis();
        rocketFilePath = rocketPath;
        motor = selectedMotor;
        
        try {
            OpenRocketDocument document = loadRocket(new File(rocketPath));
            if (document == null || document.getRocket() == null) {
                System.err.println("Failed to load rocket document");
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error loading rocket: " + e.getMessage());
            return null;
        }
        
        final Genotype<DoubleGene> genotype = Genotype.of(
            DoubleChromosome.of(NOSE_CONE_RANGE),
            DoubleChromosome.of(BODY_TUBE_RANGE),
            DoubleChromosome.of(FIN_ROOT_CHORD_RANGE),
            DoubleChromosome.of(FIN_TIP_CHORD_RANGE),
            DoubleChromosome.of(FIN_HEIGHT_RANGE),
            DoubleChromosome.of(FIN_SWEEP_LENGTH_RANGE)
        );
        
        Engine<DoubleGene, Double> engine = Engine
            .builder(f -> fitnessFunction(f), genotype)
            .populationSize(populationSize)
            .selector(new TournamentSelector<>(3))
            .alterers(
                new SinglePointCrossover<>(0.5),
                new Mutator<>(0.8)
            )
            .survivorsSelector(new EliteSelector<>(Math.max(populationSize / 10, 5)))
            .offspringSelector(new TournamentSelector<>(3))
            .minimizing()
            .build();
        
        System.out.println("\n=== ROCKET DESIGN OPTIMISATION ===");
        System.out.println("Population: " + populationSize + ", Generations: " + generations);
        System.out.println("Optimising: Nose Cone, Body Tube, Fin Parameters");

        final ApogeeChart chart;
        if (plot) {
            chart = new ApogeeChart(generations);
            chart.openWindow();
        } else {
            chart = null;
        }
        
        final Genotype<DoubleGene> baselineGenotype = Genotype.of(
            DoubleChromosome.of(DoubleGene.of(baselineNose, NOSE_CONE_RANGE.min(), NOSE_CONE_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineBody, BODY_TUBE_RANGE.min(), BODY_TUBE_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineRootChord, FIN_ROOT_CHORD_RANGE.min(), FIN_ROOT_CHORD_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineTipChord, FIN_TIP_CHORD_RANGE.min(), FIN_TIP_CHORD_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineHeight, FIN_HEIGHT_RANGE.min(), FIN_HEIGHT_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineSweepLength, FIN_SWEEP_LENGTH_RANGE.min(), FIN_SWEEP_LENGTH_RANGE.max()))
        );
        
        rejectedEvaluations.set(0);
        countRejections = false;
        double baselineFitness = fitnessFunction(baselineGenotype);
        final Phenotype<DoubleGene, Double>[] globalBest = new Phenotype[1];
        globalBest[0] = Phenotype.of(baselineGenotype, 1, baselineFitness);

        PrintStream quiet = new PrintStream(OutputStream.nullOutputStream());
        System.setOut(quiet);
        System.setErr(quiet);
        AsciiProgress progress = new AsciiProgress(generations, ORIGINAL_OUT, AsciiProgress.standardOutputIsTerminal());
        int[] completed = {0};
        countRejections = true;
        try {
            engine.stream()
                .limit(generations)
                .forEach(result -> {
                    Phenotype<DoubleGene, Double> generationBest = result.population().stream()
                        .min((p1, p2) -> Double.compare(p1.fitness(), p2.fitness()))
                        .orElse(null);
                    
                    if (generationBest != null && generationBest.fitness() < globalBest[0].fitness()) {
                        globalBest[0] = generationBest;
                    }
                    completed[0]++;
                    double best = bestApogee(globalBest[0].fitness());
                    progress.update(completed[0], best);
                    if (chart != null) {
                        chart.record(completed[0], best);
                    }
                });
        } finally {
            countRejections = false;
            progress.finish(completed[0], bestApogee(globalBest[0].fitness()));
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
        }
        
        OptimisationReport report = new OptimisationReport(populationSize, generations);
        report.rejectedCandidateEvaluations = rejectedEvaluations.get();
        report.apogeeChart = chart;
        
        if (globalBest[0].fitness() > baselineFitness) {
            report.optimisedNoseCone = baselineNose;
            report.optimisedBodyTube = baselineBody;
            report.optimisedFinRootChord = baselineRootChord;
            report.optimisedFinTipChord = baselineTipChord;
            report.optimisedFinHeight = baselineHeight;
            report.optimisedFinSweepLength = baselineSweepLength;
        } else {
            report.optimisedNoseCone = globalBest[0].genotype().get(0).get(0).allele();
            report.optimisedBodyTube = globalBest[0].genotype().get(1).get(0).allele();
            report.optimisedFinRootChord = globalBest[0].genotype().get(2).get(0).allele();
            report.optimisedFinTipChord = globalBest[0].genotype().get(3).get(0).allele();
            report.optimisedFinHeight = globalBest[0].genotype().get(4).get(0).allele();
            report.optimisedFinSweepLength = globalBest[0].genotype().get(5).get(0).allele();
        }
        
        report.bestPhenotype = globalBest[0];
        report.executionTimeMs = System.currentTimeMillis() - startTime;
        
        return report;
    }
    
    
    private static Double fitnessFunction(Genotype<DoubleGene> genotype) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        System.setOut(new FilteringPrintStream(originalOut));
        System.setErr(new FilteringPrintStream(originalErr));
        
        try {
            Rocket rocket = reloadRocket();
            if (rocket == null) return 1000.0;
            MotorCatalogue.mount(rocket, motor);
            
            setNoseConeLength(rocket, genotype.get(0).get(0).allele());
            setBodyTubeLength(rocket, genotype.get(1).get(0).allele());
            setFinParameters(rocket, 
                genotype.get(2).get(0).allele(), genotype.get(3).get(0).allele(),
                genotype.get(4).get(0).allele(), genotype.get(5).get(0).allele());
            
            RocketAnalysis.Ensemble ensemble = RocketAnalysis.flyAllWinds(rocket);
            if (ensemble.rejected) {
                if (countRejections) {
                    rejectedEvaluations.incrementAndGet();
                }
                return AscentStability.REJECTED_FITNESS;
            }
            double meanApogee = ensemble.average.apogee;
            if (Double.isNaN(meanApogee)) {
                return 1000.0;
            }
            return -meanApogee;
        } catch (Exception e) {
            return 1000.0;
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static double bestApogee(double fitness) {
        return fitness < 0 ? -fitness : Double.NaN;
    }
    
    private static Rocket reloadRocket() {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        System.setOut(new FilteringPrintStream(originalOut));
        System.setErr(new FilteringPrintStream(originalErr));
        
        try {
            OpenRocketDocument document = new GeneralRocketLoader(new File(rocketFilePath)).load();
            return document != null ? document.getRocket() : null;
        } catch (Exception e) {
            return null;
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static OpenRocketDocument loadRocket(File file) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        System.setOut(new FilteringPrintStream(originalOut));
        System.setErr(new FilteringPrintStream(originalErr));
        
        try {
            return new GeneralRocketLoader(file).load();
        } catch (Exception e) {
            System.err.println("Failed to load: " + e.getMessage());
            return null;
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
    
    private static void setNoseConeLength(Rocket rocket, double length) {
        for (RocketComponent child : rocket.getChildren()) {
            for (RocketComponent subchild : child.getChildren()) {
                if (subchild instanceof NoseCone) {
                    ((NoseCone) subchild).setLength(length);
                }
            }
        }
    }
    
    private static void setBodyTubeLength(Rocket rocket, double length) {
        for (RocketComponent child : rocket.getChildren()) {
            for (RocketComponent subchild : child.getChildren()) {
                if (subchild instanceof BodyTube) {
                    ((BodyTube) subchild).setLength(length);
                }
            }
        }
    }
    
    private static void setFinParameters(Rocket rocket, double rootChord, double tipChord, 
                                        double height, double sweepLength) {
        setFinParametersRecursive(rocket, rootChord, tipChord, height, sweepLength);
    }
    
    private static void setFinParametersRecursive(RocketComponent component, double rootChord,
                                                  double tipChord, double height, double sweepLength) {
        for (RocketComponent child : component.getChildren()) {
            if (child.getClass().getSimpleName().contains("Fin")) {
                tryInvoke(child, "setRootChord", rootChord);
                tryInvoke(child, "setTipChord", tipChord);
                tryInvoke(child, "setHeight", height);
                tryInvoke(child, "setSweep", sweepLength);
            }
            setFinParametersRecursive(child, rootChord, tipChord, height, sweepLength);
        }
    }
    
    private static void tryInvoke(Object obj, String methodName, double value) {
        try {
            obj.getClass().getMethod(methodName, double.class).invoke(obj, value);
        } catch (Exception e) {
        }
    }
    
}

