package com.roelplieger.services.impl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.roelplieger.services.ClockService;
import com.roelplieger.services.MonitorService;
import com.roelplieger.services.Z80Service;

@Component
public class ClockServiceImpl implements ClockService {

	@Autowired
	Z80Service z80Service;
	@Autowired
	MonitorService monitorService;

	private boolean active;

	private ScheduledExecutorService systemClockService = Executors.newSingleThreadScheduledExecutor();
	private ScheduledExecutorService vsyncClockService = Executors.newSingleThreadScheduledExecutor();

	@Override
	public void stop() {
		if(active) {
			systemClockService.shutdown();
			vsyncClockService.shutdown();
		}
		active = false;
	}

	@Override
	public void start() {
		active = true;
		Runnable systemClock = new Runnable() {
			@Override
			public void run() {
				try {
					if(active) {
						z80Service.step();
					}
				} catch(Exception e) {
					e.printStackTrace();
					active = false;
				}
			}
		};
		Runnable vsyncClock = new Runnable() {
			@Override
			public void run() {
				if(active) {
					monitorService.vsync();
					z80Service.vsync();
				}
			}
		};

		z80Service.initialize();

		systemClockService.scheduleAtFixedRate(systemClock, 0, 238, TimeUnit.NANOSECONDS); // ~ 4MHz
		vsyncClockService.scheduleAtFixedRate(vsyncClock, 0, 20, TimeUnit.MILLISECONDS); // 50Hz
	}

}
