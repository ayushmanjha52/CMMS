package com.plantdesk.asset;

/**
 * ABC criticality as maintenance planners use it: A = failure stops generation/production
 * or is a safety risk; B = degraded output, standby exists; C = run-to-failure acceptable.
 */
public enum Criticality {
    A,
    B,
    C
}
