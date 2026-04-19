import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.OpenRocketCore;
import java.lang.reflect.Method;

public class DebugCGMethods {
    public static void main(String[] args) {
        OpenRocketCore.initialize();
        
        // Check Rocket class methods related to CG, mass, etc.
        System.out.println("=== ROCKET CLASS METHODS FOR CG/MASS ===");
        for (Method m : Rocket.class.getMethods()) {
            String name = m.getName();
            if ((name.contains("CG") || name.contains("Mass") || name.contains("Position") ||
                 name.contains("Inertia") || name.contains("Location")) && 
                m.getParameterCount() <= 1) {
                System.out.println(m);
            }
        }
        
        // Check RocketComponent class methods
        System.out.println("\n=== ROCKETCOMPONENT CLASS METHODS ===");
        for (Method m : RocketComponent.class.getMethods()) {
            String name = m.getName();
            if ((name.contains("CG") || name.contains("Mass") || name.contains("Position") ||
                 name.contains("Inertia") || name.contains("Location") || name.contains("Offset")) && 
                m.getParameterCount() <= 1 &&
                m.getDeclaringClass() == RocketComponent.class) {
                System.out.println(m);
            }
        }
    }
}
