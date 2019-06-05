package com.fh;

import java.io.Serializable;
import java.sql.Timestamp;

public final class SensorRecord implements Serializable {

    private java.sql.Timestamp Timestamp;

    private byte[] PatientId;
    private String Unit;
    private double Value;

    public SensorRecord() {
        this.PatientId = "".getBytes();
        this.Timestamp = new Timestamp(0);
        this.Unit = "";
        this.Value = 0.0;
    }

    public SensorRecord(byte[] patientId, Timestamp timestamp, String unit, double value) {
        PatientId = patientId;
        Timestamp = timestamp;
        Unit = unit;
        Value = value;
    }

    public byte[] getPatientId() {
        return PatientId;
    }

    public void setPatientId(byte[] patientId) {
        PatientId = patientId;
    }

    public Timestamp getTimestamp() {
        return Timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        Timestamp = timestamp;
    }

    public String getUnit() {
        return Unit;
    }

    public void setUnit(String unit) {
        Unit = unit;
    }

    public double getValue() {
        return Value;
    }

    public void setValue(double value) {
        Value = value;
    }

}