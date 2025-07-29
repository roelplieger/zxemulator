package com.roelplieger.services.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.roelplieger.exceptions.PortException;
import com.roelplieger.services.AddressBusService;
import com.roelplieger.services.IOService;

@Component
public class AddressBusServiceImpl implements AddressBusService {

	private static Map<Integer, IOService> ioServicesIn = new HashMap<>();
	private static Map<Integer, IOService> ioServicesOut = new HashMap<>();

	@Override
	public byte in(int port) throws PortException {
		IOService ioService = ioServicesIn.get(port & 0xffff);
		if(ioService != null) {
			return ioService.in(port);
		}
		return 0;
		// throw new PortException(String.format("Port %x not registered", port));
	}

	@Override
	public void out(int port, byte value) throws PortException {
		IOService ioService = ioServicesOut.get(port & 0xffff);
		if(ioService != null) {
			ioService.out(port, value);
		} else {
			// throw new PortException(String.format("Port %x not registered", port));
		}
	}

	@Override
	public void registerPort(int port, IOService ioService, boolean inPort) throws PortException {
		if(inPort) {
			if (ioServicesIn.containsKey(port & 0xffff)) {
				throw new PortException(String.format("In Port %x already registered", port & 0xffff));
			}

			ioServicesIn.put(port & 0xffff, ioService);
		} else {
			if (ioServicesOut.containsKey(port & 0xffff)) {
				throw new PortException(String.format("Out Port %x already registered", port & 0xffff));
			}

			ioServicesOut.put(port & 0xffff, ioService);
		}
	}

}
