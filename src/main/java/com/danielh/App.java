package com.danielh;

import java.io.File;
import java.util.List;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.simulation.*;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;
import info.openrocket.core.motor.*;
import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.startup.Application;

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

        // Configure motor mount and add H268 motor
        try {
            motorMount.setMotorMount(true);
            
            // Get the motor database through Guice
            MotorDatabase motorDatabase = Application.getInjector().getInstance(MotorDatabase.class);
            
            // Find the H268 motor
            // findMotors(digest, type, manufacturer, designation, diameter, length)
            List<? extends Motor> motors = motorDatabase.findMotors(
                null,           // digest - null to ignore
                null,           // Motor.Type - null to ignore
                null,           // manufacturer - null to ignore  
                "H268",         // designation - the motor model
                Double.NaN,     // diameter - NaN to ignore
                Double.NaN      // length - NaN to ignore
            );
            
            if (!motors.isEmpty()) {
                Motor h268Motor = motors.get(0);
                
                // Get the motor configuration and set the motor
                MotorConfiguration motorConfig = motorMount.getDefaultMotorConfig();
                motorConfig.setMotor(h268Motor);
                
                // Verify motor was set
                Motor setMotor = motorConfig.getMotor();
                System.out.println("Motor mount configured with H268 motor: " + (setMotor != null ? setMotor.getDesignation() : "null"));
            } else {
                System.out.println("H268 motor not found in database");
            }
        } catch (Exception e) {
            System.out.println("Error configuring motor: " + e.getMessage());
            e.printStackTrace();
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