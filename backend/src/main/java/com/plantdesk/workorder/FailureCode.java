package com.plantdesk.workorder;

/**
 * Breakdown cause codes, grouped by trade the way a maintenance log is filled in on shift.
 *
 * <p>{@code countsAsFailure} matters for MTBF: a motor that tripped because the grid
 * dipped did not fail. Counting external supply interruptions against the asset would make
 * healthy equipment look unreliable and send the planner after the wrong machine.
 */
public enum FailureCode {
    E01("Insulation failure / earth fault", true),
    E02("Overload or protection trip", true),
    E03("Control circuit / contactor fault", true),
    E04("Winding or cable burnt", true),
    M01("Bearing failure", true),
    M02("Lubrication failure", true),
    M03("Misalignment / high vibration", true),
    M04("Seal or gland leakage", true),
    M05("Wear, breakage or fatigue", true),
    I01("Sensor / transmitter fault", true),
    I02("Calibration drift", true),
    O01("Operational error", true),
    X01("External / supply interruption", false);

    private final String description;
    private final boolean countsAsFailure;

    FailureCode(String description, boolean countsAsFailure) {
        this.description = description;
        this.countsAsFailure = countsAsFailure;
    }

    public String description() {
        return description;
    }

    public boolean countsAsFailure() {
        return countsAsFailure;
    }
}
