package com.fh;

import java.io.Serializable;

public class AdwinAlert implements Serializable {

    private byte[] PatientId;
    private long From;
    private long To;
    private String Unit;
    private double Value;
    private int Width;
    private double Sum;
    private boolean Alert;

    public AdwinAlert() {
        this.PatientId = "".getBytes();
        this.From = 0L;
        this.To = 0L;
        this.Unit = "";
        this.Value = 0.0;
        this.Width = 0;
        this.Sum = 0.0;
        this.Alert = false;
    }

    public AdwinAlert(byte[] PatientId, long From, long To, String Unit, double Value, int Width, double Sum, boolean Alert) {
        this.PatientId = PatientId;
        this.From = From;
        this.To = To;
        this.Unit = Unit;
        this.Value = Value;
        this.Width = Width;
        this.Sum = Sum;
        this.Alert = Alert;
    }

    public byte[] getPatientId() {
        return PatientId;
    }

    public void setPatientId(byte[] value) {
        this.PatientId = value;
    }

    public long getFrom() {
        return From;
    }

    public void setFrom(long value) {
        this.From = value;
    }

    public long getTo() {
        return To;
    }

    public void setTo(long value) {
        this.To = value;
    }

    public String getUnit() {
        return Unit;
    }

    public void setUnit(String value) {
        this.Unit = value;
    }

    public double getValue() {
        return Value;
    }

    public void setValue(double value) {
        this.Value = value;
    }

    public int getWidth() {
        return Width;
    }

    public void setWidth(int width) {
        this.Width = width;
    }

    public double getSum() {
        return Sum;
    }

    public void setSum(double sum) {
        this.Sum = sum;
    }

    public boolean getAlert() {
        return Alert;
    }

    public void setAlert(boolean alert) {
        this.Alert = alert;
    }
}