package org.openbmap.unifiedNlp.services;


public class Cell {
	public int cellId;
	public int lac;
	public int mcc;
	public int mnc;

	public String toString() {
		return mcc + "|" + mnc + "|" + lac + "|" + cellId;
	}
}
