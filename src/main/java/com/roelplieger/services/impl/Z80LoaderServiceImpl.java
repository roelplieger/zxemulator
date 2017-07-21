package com.roelplieger.services.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.roelplieger.exceptions.MemoryException;
import com.roelplieger.services.ClockService;
import com.roelplieger.services.MemoryService;
import com.roelplieger.services.MonitorService;
import com.roelplieger.services.RegisterService;
import com.roelplieger.services.Z80LoaderService;
import com.roelplieger.services.Z80Service;

@Service
/**
 * Load and start a Z80 file, see http://www.worldofspectrum.org/faq/reference/z80format.htm
 * 
 * @author roel
 *
 */
public class Z80LoaderServiceImpl implements Z80LoaderService {

	private byte[] memory;
	private int startOfMemory = 30; // for version 1
	private boolean compressed = false;

	@Autowired
	ClockService clockService;
	@Autowired
	RegisterService registerService;
	@Autowired
	MemoryService memoryService;
	@Autowired
	Z80Service z80Service;
	@Autowired
	MonitorService monitorService;

	@Override
	public void loadAndStartZ80() {
		JFileChooser fileChooser = new JFileChooser();

		clockService.setActive(false);
		int result = fileChooser.showOpenDialog(null);
		if(result == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			loadAndStartZ80(file);
		}
		clockService.setActive(true);
	}

	private void loadAndStartZ80(File file) {
		try {
			loadZ80(file);
		} catch(IOException | MemoryException e) {
			e.printStackTrace();
		}
	}

	private void loadZ80(File file) throws IOException, MemoryException {
		memory = Files.readAllBytes(file.toPath());

		processHeader();

		uncompressMemory();
	}

	private void processHeader() {
		boolean newVersion = false;
		registerService.setA(memory[0]);
		registerService.setF(memory[1]);
		registerService.setBC(readShort(2));
		registerService.setHL(readShort(4));
		short PC = readShort(6);
		newVersion = (PC == 0);
		if(!newVersion) {
			registerService.setPC(PC);
		}
		registerService.setSP(readShort(8));
		registerService.setI(memory[10]);
		byte R = (byte)(memory[11] & 0xef);
		byte m12 = memory[12];
		if((m12 & 0xff) == 0xff) {
			m12 = 1;
		}
		if((m12 & 0x01) != 0) {
			R |= 0x80;
		}
		int borderColor = (m12 >> 1) & 0x07;
		monitorService.setBorderColor(borderColor);

		registerService.setR(R);
		compressed = (m12 & 0x20) != 0;
		registerService.setDE(readShort(13));
		registerService.setBC2(readShort(15));
		registerService.setDE2(readShort(17));
		registerService.setHL2(readShort(19));
		registerService.setA2(memory[21]);
		registerService.setF2(memory[22]);
		registerService.setIY(readShort(23));
		registerService.setIX(readShort(25));
		z80Service.setIM(memory[29] & 0x03, memory[27] != 0);

		if(newVersion) {
			startOfMemory += readShort(30);
			System.out.println("New version of .z80");
		}
	}

	private short readShort(int i) {
		return (short)(((memory[i] & 0xff) + ((memory[i + 1] & 0xff) << 8)) & 0xffff);
	}

	private void uncompressMemory() throws MemoryException, IOException {
		List<Byte> tmp = new ArrayList<>();
		int i = startOfMemory;
		if(compressed) {
			while(i < memory.length) {
				byte b = memory[i];
				if((b & 0xff) == 0x00 && (memory[i + 1] & 0xff) == 0xed && (memory[i + 2] & 0xff) == 0xed && (memory[i + 3] & 0xff) == 0x00) {
					// version 1 EOF
					break;
				}
				if((b & 0xff) == 0xed && (memory[i + 1] & 0xff) == 0xed) {
					int count = (memory[i + 2] & 0xff);
					byte value = memory[i + 3];
					while(count-- > 0) {
						tmp.add(value);
					}
					i += 4;
				} else {
					tmp.add(b);
					i++;
				}
			}
		} else {
			for(i = startOfMemory; i < memory.length; i++) {
				tmp.add(memory[i]);
			}
		}
		byte[] mem = new byte[tmp.size()];
		for(int m = 0; m < tmp.size(); m++) {
			mem[m] = tmp.get(m);
		}
		InputStream is = new ByteArrayInputStream(mem);
		memoryService.loadInputStream(0x4000, is);
		is.close();
	}

	@Override
	public void loadAndStartBinary() {
		JFileChooser fileChooser = new JFileChooser();

		clockService.setActive(false);
		int result = fileChooser.showOpenDialog(null);
		if(result == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			loadAndStartBinary(file);
		}
		clockService.setActive(true);
	}

	private void loadAndStartBinary(File file) {
		try {
			memory = Files.readAllBytes(file.toPath());
			InputStream is = new ByteArrayInputStream(memory);
			memoryService.loadInputStream(0x8000, is);
			is.close();
			resetZ80State();
		} catch(IOException | MemoryException e) {
			e.printStackTrace();
		}

	}

	private void resetZ80State() {
		z80Service.setIM(0, false);
		registerService.setAF((short)0);
		registerService.setBC((short)0);
		registerService.setDE((short)0);
		registerService.setHL((short)0);
		registerService.setAF2((short)0);
		registerService.setBC2((short)0);
		registerService.setDE2((short)0);
		registerService.setHL2((short)0);
		registerService.setIX((short)0);
		registerService.setIY((short)0);
		registerService.setPC((short)0x4000);
		registerService.setSP((short)0xffff);
		registerService.setI((byte)0);
		registerService.setR((byte)0);
	}

}
