package com.fh;

import java.io.Serializable;

public class AggregatedRecord implements Serializable {

    public byte[] PatientId;
    public long Timestamp;
    public java.lang.String Unit;
    public double AvgValue;
    public double MinValue;
    public double MaxValue;

    public byte[] getPatientId() {
        return PatientId;
    }

    public void setPatientId(byte[] patientId) {
        PatientId = patientId;
    }

    public long getTimestamp() {
        return Timestamp;
    }

    public void setTimestamp(long timestamp) {
        Timestamp = timestamp;
    }

    public String getUnit() {
        return Unit;
    }

    public void setUnit(String unit) {
        Unit = unit;
    }

    public double getAvgValue() {
        return AvgValue;
    }

    public void setAvgValue(double avgValue) {
        AvgValue = avgValue;
    }

    public double getMinValue() {
        return MinValue;
    }

    public void setMinValue(double minValue) {
        MinValue = minValue;
    }

    public double getMaxValue() {
        return MaxValue;
    }

    public void setMaxValue(double maxValue) {
        MaxValue = maxValue;
    }
}
