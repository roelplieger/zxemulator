package com.roelplieger.services;

import com.roelplieger.exceptions.MemoryException;
import com.roelplieger.exceptions.PortException;

public interface Z80Service {

	void step() throws MemoryException, PortException;

	void vsync();

	void initialize();

	void setIM(int im, boolean iff2);
}
