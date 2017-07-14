package com.roelplieger.services.impl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.springframework.stereotype.Service;

import com.roelplieger.exceptions.PortException;
import com.roelplieger.services.SoundService;

@Service
public class SoundServiceImpl implements SoundService {
	private final static int BUF_SIZE = 64;
	private final static long FREQUENCY = 8192;
	private volatile boolean status;
	private SourceDataLine line = null;
	private ScheduledExecutorService soundClockService = Executors.newSingleThreadScheduledExecutor();
	private final byte[][] buf = new byte[2][BUF_SIZE];

	public SoundServiceImpl() {
		initSound();
		if(line != null) {
			startSound();
		}
	}

	private void initSound() {
		AudioFormat format = new AudioFormat(FREQUENCY, 8, 1, false, false);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format); // format is an AudioFormat object
		if(AudioSystem.isLineSupported(info)) {
			try {
				line = AudioSystem.getSourceDataLine(format);
				line.open(format);
			} catch(LineUnavailableException ex) {
				System.out.println(ex.getMessage());
			}
		}
	}

	private void startSound() {
		Runnable soundClock = new Runnable() {
			int bufIdx = 0;
			int bufCnt = 0;

			@Override
			public void run() {
				buf[bufIdx][bufCnt++] = (byte)((status) ? 0x80 : 0x00);
				if(bufCnt == BUF_SIZE) {
					line.write(buf[bufIdx], 0, BUF_SIZE);
					bufIdx = (bufIdx + 1) % 2;
					bufCnt = 0;
				}
			}
		};

		soundClockService.scheduleAtFixedRate(soundClock, 0, 1000000000L / FREQUENCY, TimeUnit.NANOSECONDS); // 8 kHz
		line.start();
	}

	@Override
	public byte in(int port) throws PortException {
		return 0;
	}

	@Override
	public void out(int port, byte value) throws PortException {
		status = (value & 0x10) != 0;
	}

}
