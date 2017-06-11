package com.roelplieger.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.roelplieger.exceptions.MemoryException;

public interface MemoryService {
	byte readByte(int pointer) throws MemoryException;

	void writeByte(int pointer, byte value) throws MemoryException;

	short readShort(int pointer) throws MemoryException;

	void writeShort(int pointer, short value) throws MemoryException;

	void loadFile(int pointer, File file) throws MemoryException, IOException;

	void loadInputStream(int pointer, InputStream inputStream) throws MemoryException, IOException;
}
