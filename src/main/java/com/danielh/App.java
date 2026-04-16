package com.danielh;

import java.io.File;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;

public class App {
    public static void main(String[] args) {
         // Initialize OpenRocket core
         OpenRocketCore.initialize();

         // Load rocket from file
         Rocket rocket = loadRocketFromFile(new File("C:\\Users\\Daniel\\rocket-app\\libs\\test_rocket.ork"));
         
         if (rocket == null) {
             System.err.println("Failed to load rocket. Exiting.");
             return;
         }
         
         System.out.println("Rocket loaded successfully!");

         // Create simulation
         Simulation simulation = new Simulation(rocket);

         // Run simulation
         try {
               simulation.simulate();
               System.out.println("Simulation completed!");
               
               // Get the simulated data from the simulation
               try {
                  java.lang.reflect.Method getSimulatedData = simulation.getClass().getMethod("getSimulatedData");
                  Object simulatedData = getSimulatedData.invoke(simulation);
                  
                  System.out.println("Simulated data object: " + simulatedData);
                  
                  if (simulatedData != null) {
                     System.out.println("\n=== FLIGHT DATA RESULTS ===");
                     
                     // Try to get maxAltitude from the simulated data
                     java.lang.reflect.Method getMaxAltitude = simulatedData.getClass().getMethod("getMaxAltitude");
                     double apogee = (double) getMaxAltitude.invoke(simulatedData);
                     System.out.println("Apogee: " + apogee + " meters");
                     
                     // Get time to apogee
                     java.lang.reflect.Method getTimeToApogee = simulatedData.getClass().getMethod("getTimeToApogee");
                     double ttApogee = (double) getTimeToApogee.invoke(simulatedData);
                     System.out.println("Time to apogee: " + ttApogee + " s");
                     
                     // Get max velocity
                     java.lang.reflect.Method getMaxVelocity = simulatedData.getClass().getMethod("getMaxVelocity");
                     double maxVel = (double) getMaxVelocity.invoke(simulatedData);
                     System.out.println("Max velocity: " + maxVel + " m/s");
                     
                     // Get max acceleration
                     java.lang.reflect.Method getMaxAcceleration = simulatedData.getClass().getMethod("getMaxAcceleration");
                     double maxAccel = (double) getMaxAcceleration.invoke(simulatedData);
                     System.out.println("Max acceleration: " + maxAccel + " m/s²");
                     
                     // Get max mach number
                     java.lang.reflect.Method getMaxMachNumber = simulatedData.getClass().getMethod("getMaxMachNumber");
                     double maxMach = (double) getMaxMachNumber.invoke(simulatedData);
                     System.out.println("Max Mach: " + maxMach);
                  } else {
                     System.out.println("Simulated data is null - no simulation data available");
                  }
               } catch (Exception e) {
                  System.err.println("Error extracting flight data: " + e.getMessage());
                  e.printStackTrace();
               }
         } catch (Exception e) {
               System.err.println("Simulation failed: " + e.getMessage());
               e.printStackTrace();
         }
      }

   // Load a rocket from a file
   private static Rocket loadRocketFromFile(File file) {
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
      
      // Add motor mount
      InnerTube motorMount = new InnerTube();
      motorMount.setLength(0.07); // 7cm
      motorMount.setOuterRadius(0.012); // 1.2cm radius
      bodyTube.addChild(motorMount);

      

      return document;
   }
}