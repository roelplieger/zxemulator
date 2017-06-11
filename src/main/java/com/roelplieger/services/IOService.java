package com.roelplieger.services;

import com.roelplieger.exceptions.PortException;

public interface IOService {

	byte in(int port) throws PortException;

	void out(int port, byte value) throws PortException;

}
