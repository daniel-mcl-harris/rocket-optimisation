package com.danielh;

import java.io.File;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.simulation.*;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.startup.Application;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;

public class App {
    public static void main(String[] args) {
        // Initialize OpenRocket core
        OpenRocketCore.initialize();


        // Create a simple rocket
        Rocket rocket = new Rocket();
        AxialStage stage = new AxialStage();
        rocket.addChild(stage);

        // Add nose cone
        NoseCone noseCone = new NoseCone();
        noseCone.setLength(0.1); // 10cm
        stage.addChild(noseCone);

        // Add body tube
        BodyTube bodyTube = new BodyTube();
        bodyTube.setLength(0.3); // 30cm
        bodyTube.setOuterRadius(0.025); // 2.5cm radius
        stage.addChild(bodyTube);

        // Add motor mount
        InnerTube motorMount = new InnerTube();
        motorMount.setLength(0.07); // 7cm
        motorMount.setOuterRadius(0.012); // 1.2cm radius
        bodyTube.addChild(motorMount);

        // Configure motor mount for H268 motor
        // Note: Setting a motor requires accessing the OpenRocket motor database singleton,
        // which is loaded asynchronously on initialization. In a production application,
        // you would either:
        // 1. Load a pre-configured rocket file (with motor already set) using loadRocketFromFile()
        // 2. Access the motor database after ensuring it's fully loaded
        //
        // For now, we configure the motor mount but set no actual motor
        try {
            motorMount.setMotorMount(true);
            System.out.println("Motor mount configured (designate for H268 motors)");
        } catch (Exception e) {
            System.out.println("Error configuring motor mount: " + e.getMessage());
        }

        // Create simulation
        Simulation simulation = new Simulation(rocket);

        // Configure simulation options
        SimulationOptions options = simulation.getOptions();
        // Set motor, recovery, etc...

        // Run simulation
        try {
            simulation.simulate();
            System.out.println("Rocket created successfully!");
            
            // Get the simulated data from the simulation
            try {
                java.lang.reflect.Method getSimulatedData = simulation.getClass().getMethod("getSimulatedData");
                Object simulatedData = getSimulatedData.invoke(simulation);
                
                if (simulatedData != null) {
                    // Try to get maxAltitude from the simulated data
                    java.lang.reflect.Method getMaxAltitude = simulatedData.getClass().getMethod("getMaxAltitude");
                    double apogee = (double) getMaxAltitude.invoke(simulatedData);
                    System.out.println("Apogee: " + apogee + " meters");
                } else {
                    System.out.println("Simulated data is null - no simulation data available");
                }
            } catch (Exception e) {
                System.err.println("Error extracting apogee: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

   // Example: Load a rocket from a file
   private Rocket loadRocketFromFile(File file) {
      try {
         GeneralRocketLoader loader = new GeneralRocketLoader(file);
         OpenRocketDocument document = loader.load();
         return document.getRocket();
      } catch (Exception e) {
         System.err.println("Failed to load rocket from file: " + e.getMessage());
         return null;
      }
   }

   // Example: Save a rocket to a file
   private void saveRocketToFile(OpenRocketDocument document, File file) {
      try {
            GeneralRocketSaver saver = new GeneralRocketSaver();
            saver.save(file, document);
      } catch (Exception e) {
            System.err.println("Failed to save rocket to file: " + e.getMessage());
      }
   }

   private OpenRocketDocument createNewRocket() {
      // --- ROCKET ---
      OpenRocketDocument document = OpenRocketDocumentFactory.createNewRocket();
      Rocket rocket = document.getRocket();
      AxialStage stage = rocket.getStage(0);

      // --- NOSE CONE ---
      NoseCone noseCone = new NoseCone();
      noseCone.setShapeType(Transition.Shape.OGIVE);
      noseCone.setShapeParameter(1.0);
      noseCone.setLength(0.15);
      noseCone.setBaseRadius(0.025);
      noseCone.setThickness(0.002);

      stage.addChild(noseCone);

      // --- BODY TUBE ---
      BodyTube bodyTube = new BodyTube();
      bodyTube.setLength(0.2);
      bodyTube.setOuterRadius(0.025);
      bodyTube.setThickness(0.002);

      stage.addChild(bodyTube);

      // --- TRANSITION ---
      Transition transition = new Transition();
      transition.setLength(0.1);
      transition.setForeRadius(0.025);
      transition.setAftRadius(0.01);
      transition.setShapeType(Transition.Shape.OGIVE);
      transition.setShapeParameter(1.0);

      stage.addChild(transition);

      // --- FIN SET ---
      TrapezoidFinSet finSet = new TrapezoidFinSet();
      finSet.setHeight(0.05);
      finSet.setRootChord(0.08);
      finSet.setSweep(0.05);
      finSet.setFinCount(3);
      finSet.setThickness(0.005);
      finSet.setAxialOffset(0);

      bodyTube.addChild(finSet); // Attach fins to the parent

      return document;
   }
}