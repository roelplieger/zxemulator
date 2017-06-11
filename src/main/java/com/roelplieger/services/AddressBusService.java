package com.roelplieger.services;

import com.roelplieger.exceptions.PortException;

public interface AddressBusService extends IOService {

	void registerPort(int port, IOService ioService) throws PortException;

}
