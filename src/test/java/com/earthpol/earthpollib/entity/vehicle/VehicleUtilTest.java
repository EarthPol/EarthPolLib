package com.earthpol.earthpollib.entity.vehicle;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleUtilTest {

    @Test
    void entityOverloadsWorkForNonVehicleMounts() {
        FakeEntity mount = fakeEntity("mount");
        Entity driver = fakeEntity("driver").proxy;
        Entity passenger = fakeEntity("passenger").proxy;
        Entity outsider = fakeEntity("outsider").proxy;

        mount.passengers.add(driver);
        mount.passengers.add(passenger);

        VehicleUtil util = new VehicleUtil();

        assertEquals(VehicleUtil.VehicleRiderStatus.DRIVER, util.getVehicleRiderStatus(driver, mount.proxy));
        assertEquals(VehicleUtil.VehicleRiderStatus.PASSENGER, util.getVehicleRiderStatus(passenger, mount.proxy));
        assertEquals(VehicleUtil.VehicleRiderStatus.NOT_RIDING, util.getVehicleRiderStatus(outsider, mount.proxy));
        assertEquals(driver, util.getDriver(mount.proxy));
        assertTrue(VehicleUtil.isDriverOfVehicle(driver, mount.proxy));
        assertTrue(VehicleUtil.isPassengerOfVehicle(passenger, mount.proxy));
        assertFalse(VehicleUtil.isOnboardVehicle(outsider, mount.proxy));
    }

    private static FakeEntity fakeEntity(String name) {
        return new FakeEntity(name);
    }

    private static final class FakeEntity {
        private final String name;
        private final List<Entity> passengers = new ArrayList<>();
        private final Entity proxy;

        private FakeEntity(String name) {
            this.name = name;
            this.proxy = (Entity) Proxy.newProxyInstance(
                    Entity.class.getClassLoader(),
                    new Class<?>[]{Entity.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getPassengers" -> passengers;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> name;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
