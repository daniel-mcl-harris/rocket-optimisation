import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.OpenRocketCore;
import java.lang.reflect.Method;

public class DebugRocketMethods {
    public static void main(String[] args) {
        OpenRocketCore.initialize();
        
        Class<?> rocketClass = Rocket.class;
        Method[] methods = rocketClass.getMethods();
        
        System.out.println("=== ROCKET CLASS METHODS ===");
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.contains("Center") || methodName.contains("center") || 
                methodName.contains("Pressure") || methodName.contains("Gravity") ||
                methodName.contains("Aero") || methodName.contains("aero")) {
                System.out.println(method);
            }
        }
        
        System.out.println("\n=== ALL GET METHODS ===");
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.startsWith("get") && method.getParameterCount() <= 1) {
                System.out.println(methodName + " -> " + method.getReturnType().getSimpleName());
            }
        }
    }
}
