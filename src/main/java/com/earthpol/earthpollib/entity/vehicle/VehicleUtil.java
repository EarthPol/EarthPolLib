package com.earthpol.earthpollib.entity.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public final class VehicleUtil {
    public enum VehicleRiderStatus {
        DRIVER,
        PASSENGER,
        NOT_RIDING;
    }

    public VehicleRiderStatus getVehicleRiderStatus(Entity entity, @NotNull Vehicle vehicle) {
        return getVehicleRiderStatus(entity, (Entity) vehicle);
    }

    public VehicleRiderStatus getVehicleRiderStatus(Entity entity, @NotNull Entity vehicle) {
        if(isDriverOfVehicle(entity, vehicle)) {return VehicleRiderStatus.DRIVER;}
        if(isPassengerOfVehicle(entity, vehicle)) {return VehicleRiderStatus.PASSENGER;}
        return VehicleRiderStatus.NOT_RIDING;
    }

    public @Nullable Entity getDriver(@NotNull Vehicle vehicle) {
        return getDriver((Entity) vehicle);
    }

    public @Nullable Entity getDriver(@NotNull Entity vehicle) {
        if(vehicle.getPassengers().isEmpty()) {return null;}
        return vehicle.getPassengers().getFirst();
    }

    public static boolean isOnboardVehicle(@NotNull Entity entity, Vehicle vehicle) {
        return isOnboardVehicle(entity, (Entity) vehicle);
    }

    public static boolean isOnboardVehicle(@NotNull Entity entity, Entity vehicle) {
        if (vehicle == null) {return false;}
        return vehicle.getPassengers().contains(entity);
    }

    public static boolean isOnboardVehicle(@NotNull Entity entity) {
        return entity.getVehicle() != null;
    }

    public static boolean isDriverOfVehicle(Entity entity, @NotNull Vehicle vehicle) {
        return isDriverOfVehicle(entity, (Entity) vehicle);
    }

    public static boolean isDriverOfVehicle(Entity entity, @NotNull Entity vehicle) {
        if (!isOnboardVehicle(entity, vehicle)) {return false;}
        return vehicle.getPassengers().getFirst() == entity;
    }

    public static boolean isPassengerOfVehicle(Entity entity, @NotNull Vehicle vehicle) {
        return isPassengerOfVehicle(entity, (Entity) vehicle);
    }

    public static boolean isPassengerOfVehicle(Entity entity, @NotNull Entity vehicle) {
        if(!isOnboardVehicle(entity, vehicle)) {return false;}
        return (!isDriverOfVehicle(entity,vehicle) && isOnboardVehicle(entity,vehicle));
    }


}
