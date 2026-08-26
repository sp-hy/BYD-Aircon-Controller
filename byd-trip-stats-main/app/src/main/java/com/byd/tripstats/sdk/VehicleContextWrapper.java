package com.byd.tripstats.sdk;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ContextWrapper that overrides all Android permission check methods to
 * unconditionally return PERMISSION_GRANTED.
 *
 * The BYD device classes (BYDAutoChargingDevice, BYDAutoGearboxDevice etc.)
 * call context.enforceCallingOrSelfPermission() before registering listeners.
 * By wrapping the real context with this class and passing it to getInstance(),
 * those checks become no-ops and the devices initialise freely regardless of
 * which permissions our APK has been granted.
 */
public class VehicleContextWrapper extends ContextWrapper {

    public VehicleContextWrapper(Context base) {
        super(base);
    }

    @Override
    public int checkPermission(@NonNull String permission, int pid, int uid) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkSelfPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void enforceCallingOrSelfPermission(
            @NonNull String permission, @Nullable String message) {
        // no-op — suppress SecurityException
    }

    @Override
    public void enforceCallingPermission(
            @NonNull String permission, @Nullable String message) {
        // no-op
    }

    @Override
    public void enforcePermission(
            @NonNull String permission, int pid, int uid, @Nullable String message) {
        // no-op
    }
}
