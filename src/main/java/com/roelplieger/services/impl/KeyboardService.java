package com.roelplieger.services.impl;

import java.util.Date;

import org.springframework.stereotype.Component;

import com.roelplieger.exceptions.PortException;
import com.roelplieger.services.IOService;

@Component
public class KeyboardService implements IOService {

	private long start = new Date().getTime();

	@Override
	public byte in(int port) throws PortException {
		System.out.println(String.format("%x", port));

		long now = new Date().getTime();
		if(port == 0xfbfe) {
			if((now - start > 4000) && (now - start < 4200)) {
				System.out.println(start + " 0x1e");
				return (byte)0x1e;
			}
		}

		return (byte)0xFF;
	}

	@Override
	public void out(int port, byte value) throws PortException {

	}

}
