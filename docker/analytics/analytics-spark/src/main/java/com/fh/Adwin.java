package com.fh;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Adwin implements Serializable{
    private int MinClock;
    private int MinLenWindow;
    private double Delta;
    private int MaxBuckets;
    private int MinTime;
    private int LastBucketRow;
    private double Sum;
    private int Width;
    private double Variance;
    private int BucketNumber;

    private List<AdwinNode> NodeList;
    private int Count;

    private double Ratio;
    private double Relative;
    private double Max;
    private double Min;

    private String Unit;

    public Adwin(){
        MinClock = 1;
        MinLenWindow = 16;
        Delta = 0.01;
        MaxBuckets = 5;
        MinTime = 0;
        LastBucketRow = 0;
        Sum = 0.0;
        Width = 0;
        Variance = 0.0;
        BucketNumber = 0;

        NodeList = new ArrayList<AdwinNode>();
        Count = 0;
        NodeList.add(0, new AdwinNode(MaxBuckets));
        Count += 1;

        Ratio = 1.0;
        Max = Double.MIN_VALUE;
        Min = Double.MAX_VALUE;

        Unit = "";
    }

    public Adwin(double d, String unit){
        MinClock = 1;
        MinLenWindow = 16;
        Delta = d;
        MaxBuckets = 5;
        MinTime = 0;
        LastBucketRow = 0;
        Sum = 0.0;
        Width = 0;
        Variance = 0.0;
        BucketNumber = 0;

        NodeList = new ArrayList<AdwinNode>();
        Count = 0;
        NodeList.add(0, new AdwinNode(MaxBuckets));
        Count += 1;

        Ratio = 1.0;
        Max = Double.MIN_VALUE;
        Min = Double.MAX_VALUE;

        Unit = unit;
    }

    public void copyFrom(Adwin adwin){
        MinClock = adwin.getMinClock();
        MinLenWindow = adwin.getMinLenWindow();
        Delta = adwin.getDelta();
        MaxBuckets = adwin.getMaxBuckets();
        MinTime = adwin.getMinTime();
        LastBucketRow = adwin.getLastBucketRow();
        Sum = adwin.getSum();
        Width = adwin.getWidth();
        Variance = adwin.getVariance();
        BucketNumber = adwin.getBucketNumber();
        NodeList = adwin.getNodeList();
        Count = adwin.getCount();
    }

    public long startTime(){
        return NodeList.get(NodeList.size() - 1).getTime()[0];
    }

    public long endTime(){
        return NodeList.get(0).getTime()[NodeList.get(0).getSize() - 1];
    }

    public boolean update(double value, long time){
        insertElement(value, time);
        compressBuckets();
        return checkDrift();
    }

    // Dynamically change Delta(sensitivity) and ratio of threshold.
    public void updateParameter(){
        if(NodeList.get(0).getSize() > 1 && Max - Min > 0){
            double diff = Math.abs(NodeList.get(0).getSum()[NodeList.get(0).getSize() - 1] - NodeList.get(0).getSum()[NodeList.get(0).getSize() - 2]);
            double relative = diff / (Max - Min);
            Relative = 0.3 * relative + 0.7 * Relative;
            // System.out.println(Relative);
            if(Relative > 0.15 && Relative < 0.4){
                if(diff < 0.4){
                    Delta = Math.min(0.8, 0.3 + (Relative - 0.08) * 3); // Cutoff at 0.8
                    Ratio = Math.max(0.35, 1.0 - Relative * 3.5);        // Cutoff at 0.35
                }
                else{
                    Delta = Math.min(0.6, 0.3 + (Relative - 0.08) * 3); // Cutoff at 0.6
                    Ratio = Math.max(0.5, 1.0 - Relative * 2.5);        // Cutoff at 0.5
                    Relative -= 0.015;
                }
            }
            else if(Relative >= 0.4)
                Relative -= 0.1;
        }
    }

    public void insertElement(double value, long time){
        Width += 1;
        NodeList.get(0).addBack(value, 0.0, time, value, value);
        BucketNumber += 1;

        if(value > Max)
            Max = value;
        if(value < Min)
            Min = value;
        if(!Unit.equals("StepCount/Count") && !Unit.equals("HeartRate/Bpm") && !Unit.equals("Respiratory/Bpm"))
            updateParameter();

        if(Width > 1)
            Variance += ((double)(Width) - 1.0) / ((double)Width) * (value - (double)Sum / ((double)(Width) - 1.0)) * (value - (double)Sum / ((double)(Width) - 1.0));
        Sum += value;
    }

    public void compressBuckets(){
        int i = 0;
        int cont = 0;

        AdwinNode cursor = NodeList.get(0);
        AdwinNode nextNode = null;

        int index1 = 0;
        int index2 = 0;

        while(true){
            int k = cursor.getSize();
            if(k == MaxBuckets + 1){
                index2 = index1 + 1;
                if(index2 == NodeList.size()){
                    NodeList.add(new AdwinNode(MaxBuckets));
                    Count += 1;
                    LastBucketRow += 1;
                }
                nextNode = NodeList.get(index2);

                double n1 = (double)(bucketSize(i));
                double n2 = (double)(bucketSize(i));
                double u1 = cursor.getSum()[0] / n1;
                double u2 = cursor.getSum()[1] / n2;
                double incVariance = n1 * n2 * (u1 - u2) * (u1 - u2) / (n1 + n2);
                // System.out.println(incVariance);
                nextNode.addBack(cursor.getSum()[0] + cursor.getSum()[1],
                        cursor.getVariance()[0] + cursor.getVariance()[1] + incVariance,
                        Math.min(cursor.getTime()[0], cursor.getTime()[1]),
                        Math.max(cursor.getMax()[0], cursor.getMax()[1]),
                        Math.min(cursor.getMin()[0], cursor.getMin()[1]));

                BucketNumber -= 1;
                cursor.dropFront(2);
                if(nextNode.getSize() <= MaxBuckets)
                    break;
            }
            else{
                break;
            }

            index1 += 1;
            cursor = NodeList.get(index1);

            i += 1;
            if(index1 == NodeList.size())
                break;
        }
    }

    public boolean checkDrift(){
        boolean change = false;
        boolean quit = false;
        AdwinNode it = null;

        MinTime += 1;

        if(MinTime % MinClock == 0 && Width > MinLenWindow){
            boolean blnTalla = true;

            while(blnTalla){
                blnTalla = false;
                quit = false;
                double n0 = 0.0;
                double n1 = (double)(Width);
                double u0 = 0.0;
                double u1 = Sum;
                it = NodeList.get(NodeList.size() - 1);
                int i = LastBucketRow;

                int index = NodeList.size() - 1;

                while(true){
                    for(int k = 0; k < it.getSize(); k ++){
                        if(i == 0 || k == it.getSize() - 1){
                            quit = true;
                            break;
                        }
                        n0 += (double)(bucketSize(i));
                        n1 -= (double)(bucketSize(i));
                        u0 += it.getSum()[k];
                        u1 -= it.getSum()[k];
                        if(n0 >= 5 && n1 >= 5 && cutExpression(n0, n1, u0, u1)){
                            blnTalla = true;
                            change = true;
                            if(Width > 0){
                                for(int j = 0; j < k + 1; j ++)
                                    deleteElement();
                                quit = true;
                                break;
                            }
                        }
                    }

                    if(quit || it == null)
                        break;

                    index -= 1;
                    it = NodeList.get(index);
                    i -= 1;
                }
            }
        }
        return change;
    }   

    public void updateMin(){
        for(int i = 0; i < NodeList.size(); i ++){
            for(int j = 0; j < NodeList.get(i).getSize(); j ++){
                if(Min > NodeList.get(i).getMin()[j])
                    Min = NodeList.get(i).getMin()[j];
            }
        }
    }

    public void updateMax(){
        for(int i = 0; i < NodeList.size(); i ++){
            for(int j = 0; j < NodeList.get(i).getSize(); j ++){
                if(Max < NodeList.get(i).getMax()[j])
                    Max = NodeList.get(i).getMax()[j];
            }
        }
    }

    public void deleteElement(){
        AdwinNode node = NodeList.get(NodeList.size() - 1);
        int n1 = bucketSize(LastBucketRow);
        Width -= n1;
        Sum -= node.getSum()[0];
        double u1 = node.getSum()[0] / (double)(n1);
        double incVariance = (node.getVariance()[0] + (double)(n1) * (double)(Width) * ((double)(u1) - Sum / (double)(Width)) * (u1 - Sum / (double)(Width))) / ((double)(n1) + (double)(Width));
        Variance -= incVariance;

        if(node.getMax()[0] == Max){
            Max = Double.MIN_VALUE;
            updateMax();
        }
        if(node.getMin()[0] == Min){
            Min = Double.MAX_VALUE;
            updateMin();
        }

        node.dropFront(1);
        if(node.getSize() == 0){
            NodeList.remove(NodeList.size() - 1);
            Count -= 1;
            LastBucketRow -= 1;
        }
    }

    boolean cutExpression(double n0, double n1, double u0, double u1){
        double n = (double)(Width);
        double diff = u0 / n0 - u1 / n1;
        double v = Variance / (double)(Width);
        double dd = Math.log(2.0 * Math.log(n) / Delta);

        double m = 1.0 / (n0 - 5.0 + 1.0) + 1.0 / (n1 - 5.0 + 1.0);
        double eps = Math.sqrt(2.0 * m * v * dd) + 2.0 / 3.0 * dd * m;
        eps *= Ratio;
        if(Math.abs(diff) > eps)
            return true;
        else
            return false;
    }


    public int getMinClock(){
        return MinClock;
    }
    public void setMinClock(int minClock){
        MinClock = minClock;
    }


    public int getMinLenWindow(){
        return MinLenWindow;
    }
    public void setMinLenWindow(int minLenWindow){
        MinLenWindow = minLenWindow;
    }


    public double getDelta(){
        return Delta;
    }
    public void setDelta(double delta){
        Delta = delta;
    }


    public int getMaxBuckets(){
        return MaxBuckets;
    }
    public void setMaxBuckets(int maxBuckets){
        MaxBuckets = maxBuckets;
    }


    public int getMinTime(){
        return MinTime;
    }
    public void setMinTime(int minTime){
        MinTime = minTime;
    }


    public int getLastBucketRow(){
        return LastBucketRow;
    }
    public void setLastBucketRow(int lastBucketRow){
        LastBucketRow = lastBucketRow;
    }


    public double getSum(){
        return Sum;
    }
    public void setSum(double sum){
        Sum = sum;
    }


    public int getWidth(){
        return Width;
    }
    public void setWidth(int width){
        Width = width;
    }

    
    public double getVariance(){
        return Variance;
    }
    public void setVariance(double variance){
        Variance = variance;
    }


    public int getBucketNumber(){
        return BucketNumber;
    }
    public void setBucketNumber(int bucketNumber){
        BucketNumber = bucketNumber;
    }

    private int bucketSize(int Row){ return (int) Math.pow(2,Row);}

    

    public List<AdwinNode> getNodeList(){
        return NodeList;
    }
    public void setNodeList(List<AdwinNode> l){
        NodeList = l;
    }

    public int getCount(){
        return Count;
    }

    public void setCount(int count){
        Count = count;
    }

    public double getRatio(){
        return Ratio;
    }
    public void setRatio(double ratio){
        Ratio = ratio;
    }

    public double getRelative(){
        return Relative;
    }
    public void setRelative(double relative){
        Relative = relative;
    }

    public double getMax(){
        return Max;
    }
    public void setMax(double max){
        Max = max;
    }

    public double getMin(){
        return Min;
    }
    public void setMin(double min){
        Min = min;
    }

    public String getUnit(){
        return Unit;
    }
    public void setUnit(String unit){
        Unit = unit;
    }
}