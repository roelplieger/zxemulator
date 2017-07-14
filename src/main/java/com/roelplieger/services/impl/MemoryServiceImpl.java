package com.roelplieger.services.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.stereotype.Component;

import com.roelplieger.exceptions.MemoryException;
import com.roelplieger.services.MemoryService;

@Component
public class MemoryServiceImpl implements MemoryService {

	private final static int MEM_SIZE = 48 * 1024 * 1024;
	private byte[] memory = new byte[MEM_SIZE];

	/**
	 * Convert negatives to positives
	 * 
	 * @param pointer
	 * @return
	 */
	private int getAbsolutePointer(int pointer) {
		if(pointer < 0) {
			pointer += 0x10000;
		}
		return pointer & 0xffff;
	}

	@Override
	public byte readByte(int pointer) throws MemoryException {
		pointer = getAbsolutePointer(pointer);
		checkPointerOutOfRange(pointer);
		return memory[pointer];
	}

	@Override
	public void writeByte(int pointer, byte value) throws MemoryException {
		pointer = getAbsolutePointer(pointer);
		checkPointerOutOfRange(pointer);
		if(pointer < 0x4000) {
			// System.out.println("cannot write into ROM! " + pointer);
			// in SKIP-NEXT at 0x33F8 bytes are 'written' to ROM locations 0 to 4 as 'imaginary stacking'
		} else {
			memory[pointer] = value;
		}
	}

	private void checkPointerOutOfRange(int pointer) throws MemoryException {
		if(pointer < 0 || pointer >= MEM_SIZE) {
			throw new MemoryException("Pointer out of range");
		}
	}

	@Override
	public short readShort(int pointer) throws MemoryException {
		pointer = getAbsolutePointer(pointer);
		checkPointerOutOfRange(pointer + 1);
		return (short)(((memory[pointer + 1] << 8) + (memory[pointer] & 0xff)) & 0xffff);
	}

	@Override
	public void writeShort(int pointer, short value) throws MemoryException {
		pointer = getAbsolutePointer(pointer);
		checkPointerOutOfRange(pointer + 1);
		if(pointer < 0x4000) {
			// System.out.println("cannot write into ROM! " + pointer);
		} else {
			memory[pointer + 1] = (byte)((value >>> 8) & 0xff);
			memory[pointer] = (byte)(value & 0xff);
		}
	}

	@Override
	public void loadFile(int pointer, File file) throws MemoryException, IOException {
		pointer = getAbsolutePointer(pointer);
		int fileLength = (int)file.length();
		if(pointer + fileLength > MEM_SIZE) {
			throw new MemoryException("Out of memory");
		}
		byte[] fileBytes = Files.readAllBytes(file.toPath());
		for(int i = 0; i < fileBytes.length; i++) {
			memory[pointer + i] = fileBytes[i];
		}
	}

	@Override
	public void loadInputStream(int pointer, InputStream inputStream) throws MemoryException, IOException {
		pointer = getAbsolutePointer(pointer);
		inputStream.read(memory, pointer, MEM_SIZE - pointer);
	}
}
