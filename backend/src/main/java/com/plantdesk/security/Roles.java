package com.plantdesk.security;

/**
 * SpEL expressions for {@code @PreAuthorize}, named by intent so a controller reads as
 * "who may do this" rather than as a list of role strings.
 */
public final class Roles {

    public static final String ADMIN = "hasRole('PLANT_ADMIN')";
    public static final String MANAGE_WORK = "hasAnyRole('PLANT_ADMIN','MAINTENANCE_MANAGER')";
    public static final String DO_WORK = "hasAnyRole('PLANT_ADMIN','MAINTENANCE_MANAGER','TECHNICIAN')";
    public static final String ANY = "hasAnyRole('PLANT_ADMIN','MAINTENANCE_MANAGER','TECHNICIAN','VIEWER')";

    private Roles() {}
}
