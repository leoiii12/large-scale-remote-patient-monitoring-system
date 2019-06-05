package com.fh;

import java.io.Serializable;

public class AdwinNode implements Serializable{
    private double Sum[];
    private double Variance[];
    private long Time[];
    private double Max[];
    private double Min[];
    private int Size;
    private int MaxBuckets;
    
    public AdwinNode(){
        Sum = new double[6];
        Variance = new double[6];
        Time = new long[6];
        Max = new double[6];
        Min = new double[6];
        Size = 0;
        MaxBuckets = 5;
    }

    public AdwinNode(int maxBuckets){
        Sum = new double[maxBuckets + 1];
        Variance = new double[maxBuckets + 1];
        Time = new long[maxBuckets + 1];
        Max = new double[maxBuckets + 1];
        Min = new double[maxBuckets + 1];
        Size = 0;
        MaxBuckets = maxBuckets;
    }

    public void addBack(double value, double var, long t, double max, double min){
        Sum[Size] = value;
        Variance[Size] = var;
        Time[Size] = t;
        Max[Size] = max;
        Min[Size] = min;
        Size += 1;
    }

    public void dropFront(int n){
        for(int i = n; i < MaxBuckets + 1; i ++){
            Sum[i - n] = Sum[i];
            Variance[i - n] = Variance[i];
            Time[i - n] = Time[i];
            Max[i - n] = Max[i];
            Min[i - n] = Min[i];
        }
        for(int i = 1; i < n + 1; i ++){
            Sum[MaxBuckets - i + 1] = 0.0;
            Variance[MaxBuckets - i + 1] = 0.0;
            Time[MaxBuckets - i + 1] = 0L;
            Max[MaxBuckets - i + 1] = 0.0;
            Min[MaxBuckets - i + 1] = 0.0;
        }
        Size -= n;
    }

    public double[] getSum(){
        return Sum;
    }
    public void setSum(double[] sum){
        Sum = sum;
    }

    public double[] getVariance(){
        return Variance;
    }
    public void setVariance(double[] variance){
        Variance = variance;
    }

    public long[] getTime(){
        return Time;
    }

    public void setTime(long[] time){
        Time = time;
    }

    public double[] getMax(){
        return Max;
    }
    public void setMax(double[] max){
        Max = max;
    }

    public double[] getMin(){
        return Min;
    }
    public void setMin(double[] min){
        Min = min;
    }

    public int getSize(){
        return Size;
    }
    public void setSize(int s){
        Size = s;
    }

    public int getMaxBuckets(){
        return MaxBuckets;
    }
    public void setMaxBuckets(int maxBuckets){
        MaxBuckets = maxBuckets;
    }

}