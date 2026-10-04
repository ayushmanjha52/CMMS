/**
 * Tenant isolation. Package-level filter definitions so every entity can reference them.
 */
@FilterDef(name = TenantFilters.TENANT,
        parameters = @ParamDef(name = TenantFilters.TENANT_PARAM, type = UUID.class))
@FilterDef(name = TenantFilters.ASSIGNEE,
        parameters = @ParamDef(name = TenantFilters.ASSIGNEE_PARAM, type = UUID.class))
package com.plantdesk.tenancy;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;
