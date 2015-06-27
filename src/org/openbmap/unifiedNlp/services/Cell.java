package org.openbmap.unifiedNlp.services;


public class Cell {
    public int cellId;
    public int area;
    public int mcc;
    public int mnc;
    public String technology;

    public String toString() {
        return mcc + "|" + mnc + "|" + area + "|" + cellId + "|" + technology;
    }
}
