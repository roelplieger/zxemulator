package com.roelplieger.services.impl;

import org.springframework.stereotype.Component;

import com.roelplieger.exceptions.MemoryException;
import com.roelplieger.services.RegisterService;

@Component
public class RegisterServiceImpl implements RegisterService {

	private static final byte BIT0 = (byte)0b00000001;
	private static final byte BIT1 = (byte)0b00000010;
	private static final byte BIT2 = (byte)0b00000100;
	private static final byte BIT4 = (byte)0b00010000;
	private static final byte BIT6 = (byte)0b01000000;
	private static final byte BIT7 = (byte)0b10000000;

	private boolean[] parityLookupTable = new boolean[256];
	private short AF;
	private short BC;
	private short DE;
	private short HL;
	private short WZ;
	private short AF2;
	private short BC2;
	private short DE2;
	private short HL2;
	private short WZ2;
	private short IX;
	private short IY;
	private short SP;
	private short PC;
	private byte I;
	private byte R;

	public RegisterServiceImpl() {
		buildParityLookupTable();
	}

	private void buildParityLookupTable() {
		for(int x = 0; x < 256; x++) {
			int t = x;
			t ^= t >> 4;
			t &= 0x0f;
			parityLookupTable[x] = (((0x6996 >> t) & 1) == 0);
		}
	}

	private byte getHighRegister(short register) {
		return (byte)(register >>> 8);
	}

	private short setHighRegister(short register, byte value) {
		// replace high byte of register with value
		return (short)(((register & 0xff) + (value << 8)) & 0xffff);
	}

	private byte getLowRegister(short register) {
		return (byte)(register & 0xff);
	}

	private short setLowRegister(short register, byte value) {
		// replace lower byte of register with unsigned value
		return (short)(((register & 0xff00) + (value & 0xff)) & 0xffff);
	}

	@Override
	public byte getA() {
		return getHighRegister(AF);
	}

	@Override
	public void setA(byte value) {
		AF = setHighRegister(AF, value);
	}

	@Override
	public byte getB() {
		return getHighRegister(BC);
	}

	@Override
	public void setB(byte value) {
		BC = setHighRegister(BC, value);
	}

	@Override
	public byte getC() {
		return getLowRegister(BC);
	}

	@Override
	public void setC(byte value) {
		BC = setLowRegister(BC, value);
	}

	@Override
	public byte getD() {
		return getHighRegister(DE);
	}

	@Override
	public void setD(byte value) {
		DE = setHighRegister(DE, value);
	}

	@Override
	public byte getE() {
		return getLowRegister(DE);
	}

	@Override
	public void setE(byte value) {
		DE = setLowRegister(DE, value);
	}

	@Override
	public byte getF() {
		return getLowRegister(AF);
	}

	@Override
	public void setF(byte value) {
		AF = setLowRegister(AF, value);
	}

	@Override
	public byte getH() {
		return getHighRegister(HL);
	}

	@Override
	public void setH(byte value) {
		HL = setHighRegister(HL, value);
	}

	@Override
	public byte getL() {
		return getLowRegister(HL);
	}

	@Override
	public void setL(byte value) {
		HL = setLowRegister(HL, value);
	}

	@Override
	public byte getW() {
		return getHighRegister(WZ);
	}

	@Override
	public void setW(byte value) {
		WZ = setHighRegister(WZ, value);
	}

	@Override
	public byte getZ() {
		return getLowRegister(WZ);
	}

	@Override
	public void setZ(byte value) {
		WZ = setLowRegister(WZ, value);
	}

	@Override
	public byte getA2() {
		return getHighRegister(AF2);
	}

	@Override
	public void setA2(byte value) {
		AF2 = setHighRegister(AF2, value);
	}

	@Override
	public byte getB2() {
		return getHighRegister(BC2);
	}

	@Override
	public void setB2(byte value) {
		BC2 = setHighRegister(BC2, value);
	}

	@Override
	public byte getC2() {
		return getLowRegister(BC2);
	}

	@Override
	public void setC2(byte value) {
		BC2 = setLowRegister(BC2, value);
	}

	@Override
	public byte getD2() {
		return getHighRegister(DE2);
	}

	@Override
	public void setD2(byte value) {
		DE2 = setHighRegister(DE2, value);
	}

	@Override
	public byte getE2() {
		return getLowRegister(DE2);
	}

	@Override
	public void setE2(byte value) {
		DE2 = setLowRegister(DE2, value);
	}

	@Override
	public byte getF2() {
		return getLowRegister(AF2);
	}

	@Override
	public void setF2(byte value) {
		AF2 = setLowRegister(AF2, value);
	}

	@Override
	public byte getH2() {
		return getHighRegister(HL2);
	}

	@Override
	public void setH2(byte value) {
		HL2 = setHighRegister(HL2, value);
	}

	@Override
	public byte getL2() {
		return getLowRegister(HL2);
	}

	@Override
	public void setL2(byte value) {
		HL2 = setLowRegister(HL2, value);
	}

	@Override
	public byte getW2() {
		return getHighRegister(WZ2);
	}

	@Override
	public void setW2(byte value) {
		WZ2 = setHighRegister(WZ2, value);
	}

	@Override
	public byte getZ2() {
		return getLowRegister(WZ2);
	}

	@Override
	public void setZ2(byte value) {
		WZ2 = setLowRegister(WZ2, value);
	}

	@Override
	public byte getI() {
		return I;
	}

	@Override
	public void setI(byte value) {
		I = value;
	}

	@Override
	public byte getR() {
		return R;
	}

	@Override
	public void setR(byte value) {
		R = value;
	}

	@Override
	public short getAF() {
		return AF;
	}

	@Override
	public void setAF(short value) {
		AF = value;
	}

	@Override
	public short getBC() {
		return BC;
	}

	@Override
	public void setBC(short value) {
		BC = value;
	}

	@Override
	public short getDE() {
		return DE;
	}

	@Override
	public void setDE(short value) {
		DE = value;
	}

	@Override
	public short getHL() {
		return HL;
	}

	@Override
	public void setHL(short value) {
		HL = value;
	}

	@Override
	public short getWZ() {
		return WZ;
	}

	@Override
	public void setWZ(short value) {
		WZ = value;
	}

	@Override
	public short getIX() {
		return IX;
	}

	@Override
	public void setIX(short value) {
		IX = value;
	}

	@Override
	public short getIY() {
		return IY;
	}

	@Override
	public void setIY(short value) {
		IY = value;
	}

	@Override
	public short getPC() {
		return PC;
	}

	@Override
	public void setPC(short value) {
		PC = value;
	}

	@Override
	public short getSP() {
		return SP;
	}

	@Override
	public void setSP(short value) {
		SP = value;
	}

	@Override
	public short getAF2() {
		return AF2;
	}

	@Override
	public void setAF2(short value) {
		AF2 = value;
	}

	@Override
	public short getBC2() {
		return BC2;
	}

	@Override
	public void setBC2(short value) {
		BC2 = value;
	}

	@Override
	public short getDE2() {
		return DE2;
	}

	@Override
	public void setDE2(short value) {
		DE2 = value;
	}

	@Override
	public short getHL2() {
		return HL2;
	}

	@Override
	public void setHL2(short value) {
		HL2 = value;
	}

	@Override
	public short getWZ2() {
		return WZ2;
	}

	@Override
	public void setWZ2(short value) {
		WZ2 = value;
	}

	@Override
	public boolean getSignFlag() {
		return (getF() & BIT7) != 0;
	}

	@Override
	public void setSignFlag(boolean value) {
		if(value) {
			setFlagBit(BIT7);
		} else {
			resetFlagBit(BIT7);
		}
	}

	@Override
	public boolean getZeroFlag() {
		return (getF() & BIT6) != 0;
	}

	@Override
	public void setZeroFlag(boolean value) {
		if(value) {
			setFlagBit(BIT6);
		} else {
			resetFlagBit(BIT6);
		}
	}

	private void resetFlagBit(byte bit) {
		byte F = getF();
		F &= ~bit;
		setF(F);
	}

	private void setFlagBit(byte bit) {
		byte F = getF();
		F |= bit;
		setF(F);
	}

	@Override
	public boolean getHalfCarryFlag() {
		return (getF() & BIT4) != 0;
	}

	@Override
	public void setHalfCarryFlag(boolean value) {
		if(value) {
			setFlagBit(BIT4);
		} else {
			resetFlagBit(BIT4);
		}
	}

	@Override
	public boolean getParityOverflowFlag() {
		return (getF() & BIT2) != 0;
	}

	@Override
	public void setParityOverflowFlag(boolean value) {
		if(value) {
			setFlagBit(BIT2);
		} else {
			resetFlagBit(BIT2);
		}
	}

	@Override
	public boolean getAddSubtractFlag() {
		return (getF() & BIT1) != 0;
	}

	@Override
	public void setAddSubtractFlag(boolean value) {
		if(value) {
			setFlagBit(BIT1);
		} else {
			resetFlagBit(BIT1);
		}
	}

	@Override
	public boolean getCarryFlag() {
		return (getF() & BIT0) != 0;
	}

	@Override
	public void setCarryFlag(boolean value) {
		if(value) {
			setFlagBit(BIT0);
		} else {
			resetFlagBit(BIT0);
		}
	}

	@Override
	public boolean getSignFlag2() {
		return (getF2() & BIT7) != 0;
	}

	@Override
	public void setSignFlag2(boolean value) {
		if(value) {
			setFlag2Bit(BIT7);
		} else {
			resetFlag2Bit(BIT7);
		}
	}

	private void setFlag2Bit(byte bit) {
		byte F = getF2();
		F |= bit;
		setF2(F);
	}

	private void resetFlag2Bit(byte bit) {
		byte F = getF2();
		F &= ~bit;
		setF2(F);
	}

	@Override
	public boolean getZeroFlag2() {
		return (getF2() & BIT6) != 0;
	}

	@Override
	public void setZeroFlag2(boolean value) {
		if(value) {
			setFlag2Bit(BIT6);
		} else {
			resetFlag2Bit(BIT6);
		}
	}

	@Override
	public boolean getHalfCarryFlag2() {
		return (getF2() & BIT4) != 0;
	}

	@Override
	public void setHalfCarryFlag2(boolean value) {
		if(value) {
			setFlag2Bit(BIT4);
		} else {
			resetFlag2Bit(BIT4);
		}
	}

	@Override
	public boolean getParityOverflowFlag2() {
		return (getF2() & BIT2) != 0;
	}

	@Override
	public void setParityOverflowFlag2(boolean value) {
		if(value) {
			setFlag2Bit(BIT2);
		} else {
			resetFlag2Bit(BIT2);
		}
	}

	@Override
	public boolean getAddSubtractFlag2() {
		return (getF2() & BIT1) != 0;
	}

	@Override
	public void setAddSubtractFlag2(boolean value) {
		if(value) {
			setFlag2Bit(BIT1);
		} else {
			resetFlag2Bit(BIT1);
		}
	}

	@Override
	public boolean getCarryFlag2() {
		return (getF2() & BIT0) != 0;
	}

	@Override
	public void setCarryFlag2(boolean value) {
		if(value) {
			setFlag2Bit(BIT0);
		} else {
			resetFlag2Bit(BIT0);
		}
	}

	@Override
	public String toString() {
		// TODO displays all registers in this format: A CZPSNH BC DE HL IX IY A' CZPSNH' BC' DE' HL' SP
		StringBuilder sb = new StringBuilder();
		sb.append("A  CZPSNH  BC   DE   HL   IX   IY  A' CZPSNH' BC'  DE'  HL'  SP\n");
		sb.append(String.format("%02x ", getA()));
		sb.append(getCarryFlag() ? "1" : "0");
		sb.append(getZeroFlag() ? "1" : "0");
		sb.append(getParityOverflowFlag() ? "1" : "0");
		sb.append(getSignFlag() ? "1" : "0");
		sb.append(getAddSubtractFlag() ? "1" : "0");
		sb.append(getHalfCarryFlag() ? "1" : "0");
		sb.append(String.format(" %04x", getBC()));
		sb.append(String.format(" %04x", getDE()));
		sb.append(String.format(" %04x", getHL()));
		sb.append(String.format(" %04x", getIX()));
		sb.append(String.format(" %04x", getIY()));
		sb.append(String.format(" %02x ", getA2()));
		sb.append(getCarryFlag2() ? "1" : "0");
		sb.append(getZeroFlag2() ? "1" : "0");
		sb.append(getParityOverflowFlag2() ? "1" : "0");
		sb.append(getSignFlag2() ? "1" : "0");
		sb.append(getAddSubtractFlag2() ? "1" : "0");
		sb.append(getHalfCarryFlag2() ? "1" : "0");
		sb.append(String.format(" %04x", getBC2()));
		sb.append(String.format(" %04x", getDE2()));
		sb.append(String.format(" %04x", getHL2()));
		sb.append(String.format(" %04x", getSP()));
		return sb.toString();
	}

	@Override
	public byte get(int register) throws MemoryException {
		byte result = 0;
		switch (register) {
			case A:
				result = getA();
				break;

			case B:
				result = getB();
				break;

			case C:
				result = getC();
				break;

			case D:
				result = getD();
				break;

			case E:
				result = getE();
				break;

			case H:
				result = getH();
				break;

			case L:
				result = getL();
				break;

			default:
				throw new MemoryException("Invalid register index: " + register);
		}
		return result;
	}

	@Override
	public void set(int register, byte value) throws MemoryException {
		switch (register) {
			case A:
				setA(value);
				break;

			case B:
				setB(value);
				break;

			case C:
				setC(value);
				break;

			case D:
				setD(value);
				break;

			case E:
				setE(value);
				break;

			case H:
				setH(value);
				break;

			case L:
				setL(value);
				break;

			default:
				throw new MemoryException("Invalid register index: " + register);
		}
	}

	@Override
	public boolean getParity(byte value) {
		int idx = value;
		if(idx < 0) {
			idx += 256;
		}
		return parityLookupTable[idx];
	}

}
