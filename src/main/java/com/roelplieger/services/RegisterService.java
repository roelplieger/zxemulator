package com.roelplieger.services;

import com.roelplieger.exceptions.MemoryException;

public interface RegisterService {
	public static final int A = 7;
	public static final int B = 0;
	public static final int C = 1;
	public static final int D = 2;
	public static final int E = 3;
	public static final int H = 4;
	public static final int L = 5;

	// 8 bit registers
	byte get(int register) throws MemoryException;

	void set(int register, byte value) throws MemoryException;

	byte getA();

	void setA(byte value);

	byte getB();

	void setB(byte value);

	byte getC();

	void setC(byte value);

	byte getD();

	void setD(byte value);

	byte getE();

	void setE(byte value);

	byte getF();

	void setF(byte value);

	byte getH();

	void setH(byte value);

	byte getL();

	void setL(byte value);

	// 'forgotten' registers (http://www.z80.info/z80arki.htm)
	byte getW();

	void setW(byte value);

	byte getZ();

	void setZ(byte value);

	// 8 bit shadow registers
	byte getA2();

	void setA2(byte value);

	byte getB2();

	void setB2(byte value);

	byte getC2();

	void setC2(byte value);

	byte getD2();

	void setD2(byte value);

	byte getE2();

	void setE2(byte value);

	byte getF2();

	void setF2(byte value);

	byte getH2();

	void setH2(byte value);

	byte getL2();

	void setL2(byte value);

	// 'forgotten' shadow registers
	byte getW2();

	void setW2(byte value);

	byte getZ2();

	void setZ2(byte value);

	// Interrupt page address register
	byte getI();

	void setI(byte value);

	// Memory Refresh register
	byte getR();

	void setR(byte value);

	// 16 bit registers
	short getAF();

	void setAF(short value);

	short getBC();

	void setBC(short value);

	short getDE();

	void setDE(short value);

	short getHL();

	void setHL(short value);

	short getWZ();

	void setWZ(short value);

	short getIX();

	void setIX(short value);

	short getIY();

	void setIY(short value);

	short getPC();

	void setPC(short value);

	short getSP();

	void setSP(short value);

	// 16 bit shadow registers
	short getAF2();

	void setAF2(short value);

	short getBC2();

	void setBC2(short value);

	short getDE2();

	void setDE2(short value);

	short getHL2();

	void setHL2(short value);

	short getWZ2();

	void setWZ2(short value);

	// flags
	boolean getSignFlag();

	void setSignFlag(boolean value);

	boolean getZeroFlag();

	void setZeroFlag(boolean value);

	boolean getHalfCarryFlag();

	void setHalfCarryFlag(boolean value);

	boolean getParityOverflowFlag();

	void setParityOverflowFlag(boolean value);

	boolean getAddSubtractFlag();

	void setAddSubtractFlag(boolean value);

	boolean getCarryFlag();

	void setCarryFlag(boolean value);

	// shadow flags
	boolean getSignFlag2();

	void setSignFlag2(boolean value);

	boolean getZeroFlag2();

	void setZeroFlag2(boolean value);

	boolean getHalfCarryFlag2();

	void setHalfCarryFlag2(boolean value);

	boolean getParityOverflowFlag2();

	void setParityOverflowFlag2(boolean value);

	boolean getAddSubtractFlag2();

	void setAddSubtractFlag2(boolean value);

	boolean getCarryFlag2();

	void setCarryFlag2(boolean value);

	boolean getParity(byte value);
}
