package com.roelplieger.services.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.roelplieger.exceptions.MemoryException;
import com.roelplieger.exceptions.PortException;
import com.roelplieger.services.AddressBusService;
import com.roelplieger.services.MemoryService;
import com.roelplieger.services.RegisterService;
import com.roelplieger.services.Z80Service;

@Component
public class Z80ServiceImpl implements Z80Service {

	@Autowired
	RegisterService registerService;
	@Autowired
	MemoryService memoryService;
	@Autowired
	AddressBusService addresBusService;
	@Autowired
	KeyboardService keyboardService;

	private boolean useClockCycles = true;
	private int clockCycles;
	private boolean halted = false;
	// interrupt flip flops
	private boolean iff1 = false;
	private boolean iff2 = false;
	// interrupt mode
	private int im = 0;
	private boolean execInterrupt = false;

	private boolean nextClockCycle() {
		if(useClockCycles && clockCycles > 1) {
			clockCycles--;
			if(clockCycles > 0) {
				return false;
			}
		}
		return true;
	}

	private boolean getParity(byte x) {
		int count = 0;

		for(int i = 0; i < 8; i++) {
			// check most right bit
			if((x & 1) != 0) {
				count++;
			}
			x = (byte)((x & 0xff) >>> 1);
		}

		return (count % 2) != 0;
	}

	private int getAbsolutePointer(int pointer) {
		if(pointer < 0) {
			pointer += 0x10000;
		}
		return pointer & 0xffff;
	}

	private void push(short x) throws MemoryException {
		int SP = getAbsolutePointer(registerService.getSP());
		memoryService.writeByte(--SP, (byte)(x >>> 8));
		memoryService.writeByte(--SP, (byte)(x & 0xFF));
		registerService.setSP((short)SP);
		// System.out.println(String.format("push %x - %x", x, SP));
	}

	private short pop() throws MemoryException {
		int SP = getAbsolutePointer(registerService.getSP());
		// 16 bits value on stack is low order byte first, then high order byte
		// short low = (memoryService.readByte(SP++));
		// short high = (short)(memoryService.readByte(SP++) << 8);
		// registerService.setSP(SP);
		// System.out.println("pop " + String.format("%x", (high & 0xff00) + (low & 0xff)));
		// return (short)((high & 0xff00) + (low & 0xff));

		short value = memoryService.readShort(SP);
		SP += 2;
		registerService.setSP((short)SP);
		// swap bytes
		// value = (short)(((value & 0xff) << 8) + ((value & 0xff00) >> 8));
		// System.out.println(String.format("pop %x - %x", value, SP));
		return value;
	}

	private boolean checkByteOverflow(byte x, byte y, int result) {
		// see http://teaching.idallen.com/dat2343/10f/notes/040_overflow.txt
		if((byte)((x & 0x80) ^ (y & 0x80)) != 0) {
			if((byte)((x & 0x80) ^ (result & 0x80)) != 0 || (byte)((y & 0x80) ^ (result & 0x80)) != 0) {
				return true;
			}
		}

		return false;
	}

	private boolean checkShortOverflow(short x, short y, int result) {
		// see http://teaching.idallen.com/dat2343/10f/notes/040_overflow.txt
		if((short)((x & 0x8000) ^ (y & 0x8000)) != 0) {
			if((short)((x & 0x8000) ^ (result & 0x8000)) != 0 || (short)((y & 0x8000) ^ (result & 0x8000)) != 0) {
				return true;
			}
		}

		return false;
	}

	private byte add(byte x, byte y) {
		int result = x + y;
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0);
		registerService.setHalfCarryFlag(((x & 0xf) + (y & 0xf) & 0x10) != 0);
		registerService.setParityOverflowFlag(checkByteOverflow(x, y, result));
		registerService.setAddSubtractFlag(false);
		registerService.setCarryFlag((result & 0x100) != 0);
		return (byte)(result & 0xff);
	}

	private byte adc(byte x, byte y) {
		boolean CFlag = registerService.getCarryFlag();
		int result = x + y + (CFlag ? 1 : 0);
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0);
		registerService.setHalfCarryFlag((((x & 0xf) + (y & 0xf) + (CFlag ? 1 : 0)) & 0x10) != 0);
		registerService.setParityOverflowFlag(checkByteOverflow(x, y, result));
		registerService.setAddSubtractFlag(false);
		registerService.setCarryFlag((result & 0x100) != 0);
		return (byte)(result & 0xff);
	}

	private byte sub(byte x, byte y) {
		int result = x - y;
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0);
		registerService.setHalfCarryFlag(((x & 0xf) - (y & 0xf)) < 0);
		registerService.setParityOverflowFlag(checkByteOverflow(x, y, result));
		registerService.setAddSubtractFlag(true);
		registerService.setCarryFlag(result < 0);
		return (byte)(result & 0xff);
	}

	private byte sbc(byte x, byte y) {
		boolean CFlag = registerService.getCarryFlag();
		int result = x - y - (CFlag ? 1 : 0);
		registerService.setZeroFlag(result == 0);
		registerService.setSignFlag(result < 0);
		registerService.setHalfCarryFlag((x & 0xf) - (y & 0xf) - (CFlag ? 1 : 0) < 0);
		registerService.setParityOverflowFlag(checkByteOverflow(x, y, result));
		registerService.setAddSubtractFlag(true);
		registerService.setCarryFlag(result < 0);
		return (byte)(result & 0xff);
	}

	private short add(short x, short y) {
		int result = (x & 0xffff) + (y & 0xffff);
		registerService.setHalfCarryFlag(((x & 0xf00) + (y & 0xf00) & 0x1000) != 0);
		registerService.setAddSubtractFlag(false);
		registerService.setCarryFlag((result & 0x10000) != 0);
		return (short)(result & 0xffff);
	}

	private short adc(short x, short y) {
		boolean CFlag = registerService.getCarryFlag();
		int result = (x & 0xffff) + (y & 0xffff) + (CFlag ? 1 : 0);
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0);
		registerService.setHalfCarryFlag((((x & 0xfff) + (y & 0xfff) + (CFlag ? 1 : 0)) & 0x1000) != 0);
		registerService.setParityOverflowFlag(checkShortOverflow(x, y, result));
		registerService.setAddSubtractFlag(false);
		registerService.setCarryFlag((result & 0x10000) != 0);
		return (short)(result & 0xffff);
	}

	private short sbc(short x, short y) {
		boolean CFlag = registerService.getCarryFlag();
		int result = (x & 0xffff) - (y & 0xffff) - (CFlag ? 1 : 0);
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0);
		registerService.setHalfCarryFlag(((x & 0xfff) - (y & 0xfff) - (CFlag ? 1 : 0)) < 0);
		registerService.setParityOverflowFlag(checkShortOverflow(x, y, result));
		registerService.setAddSubtractFlag(true);
		registerService.setCarryFlag(result < 0);
		return (short)(result & 0xffff);
	}

	private byte and(byte x, byte y) {
		int result = (x & 0xff) & (y & 0xff);
		registerService.setZeroFlag(result == 0);
		registerService.setAddSubtractFlag(false);
		registerService.setSignFlag(result < 0);
		registerService.setParityOverflowFlag(getParity((byte)result));
		registerService.setHalfCarryFlag(true);
		registerService.setCarryFlag(false);
		return (byte)result;
	}

	private byte xor(byte x, byte y) {
		int result = (x & 0xff) ^ (y & 0xff);
		registerService.setZeroFlag(result == 0);
		registerService.setAddSubtractFlag(false);
		registerService.setSignFlag(result < 0);
		registerService.setParityOverflowFlag(getParity((byte)result));
		registerService.setHalfCarryFlag(false);
		registerService.setCarryFlag(false);
		return (byte)result;
	}

	private byte or(byte x, byte y) {
		int result = (x & 0xff) | (y & 0xff);
		registerService.setZeroFlag(result == 0);
		registerService.setAddSubtractFlag(false);
		registerService.setSignFlag(result < 0);
		registerService.setParityOverflowFlag(getParity((byte)result));
		registerService.setHalfCarryFlag(false);
		registerService.setCarryFlag(false);
		return (byte)result;
	}

	private void cp(byte x, byte y) {
		int result = (x & 0xff) - (y & 0xff);
		registerService.setCarryFlag(result < 0);
		registerService.setZeroFlag(result == 0);
		registerService.setAddSubtractFlag(true);
		registerService.setSignFlag(result < 0);
		registerService.setParityOverflowFlag(checkByteOverflow(x, y, result));
		registerService.setHalfCarryFlag(false);
	}

	@Override
	public void step() throws MemoryException, PortException {
		if(execInterrupt) {
			execInterrupt = false;

			if(im == 1) {
				startInterrupt();
			} else {
				System.out.println("im" + im + " not supported");
			}
		}

		if(nextClockCycle() && !halted) {
			doStep();
		}
	}

	private void startInterrupt() throws MemoryException {
		iff1 = false;
		push(registerService.getPC());
		switch (im) {
			case 1:
				registerService.setPC((short)0x0038);
				break;
			default:
				break;
		}
	}

	private synchronized void doStep() throws MemoryException, PortException {
		short PC = registerService.getPC();
		// System.out.println(String.format("PC - %x", PC));

		if((PC & 0xffff) > 0x8000) {
			System.out.println(String.format("PC - %x", PC));
		}
		// if(PC == 0x0038) {
		// System.out.println("Interrupt!");
		// }
		// if(PC == 0x09f4) {
		// System.out.println("PRINT-OUT");
		// }
		// if(PC == 0x0b24) {
		// System.out.println("PO-ANY");
		// }
		// if(PC == 0x0b7f) {
		// System.out.println("PR-ALL");
		// }
		// if(PC == 0x0bb7) {
		// System.out.println("PR-ALL-4");
		// }
		// if(PC == 0x11EF) {
		// System.out.println("RAM-DONE");
		// }
		// if(PC == 0x1219) {
		// System.out.println("RAM-SET");
		// }
		// if(PC == 0x0EDF) {
		// System.out.println("CLEAR-PRB");
		// }
		// if(PC == 0x0ADC) {
		// System.out.println("PO-STORE");
		// }
		// if(PC == 0x0D6B) {
		// System.out.println("CLS");
		// }
		// if(PC == 0x0D6E) {
		// System.out.println("CLS-LOWER");
		// }
		// if(PC == 0x0D87) {
		// System.out.println("CLS-1");
		// }
		// if(PC == 0x0D94) {
		// System.out.println("CL-CHAN");
		// }
		// if(PC == 0x0DA0) {
		// System.out.println("CL-CHAN-A");
		// }
		// if(PC == 0x0DAF) {
		// System.out.println("CL-ALL");
		// }
		// if(PC == 0x0D4D) {
		// System.out.println("TEMPS");
		// }
		// if(PC == 0x0DD9) {
		// System.out.println("CL-SET");
		// }
		// if(PC == 0x0DEE) {
		// System.out.println("CL-SET-1");
		// }
		// if(PC == 0x0DF4) {
		// System.out.println("CL-SET-2");
		// }
		// if(PC == 0x0E44) {
		// System.out.println("CL-LINE");
		// }
		// if(PC == 0x0E4A) {
		// System.out.println("CL-LINE-1");
		// }
		// if(PC == 0x0E4D) {
		// System.out.println("CL-LINE-2");
		// }
		// if(PC == 0x0E80) {
		// System.out.println("CL-LINE-3");
		// }
		// if(PC == 0x0E88) {
		// System.out.println("CL-ATTR");
		// }
		// if(PC == 0x0E9B) {
		// System.out.println("CL-ADDR");
		// }
		// if(PC == 0x0C0A) {
		// System.out.println("PO-MSG");
		// }
		// if(PC == 0x0C22) {
		// System.out.println("PO-EACH");
		// }
		// if(PC == 0x0C3B) {
		// System.out.println("PO-SAVE");
		// }
		// if(PC == 0x15f2) {
		// System.out.println("PRINT-A-2");
		// }
		// if(PC == 0x12A9) {
		// halted = true;
		// System.out.println("MAIN-1");
		// }
		if(PC == 0x028E) {
			System.out.println("KEY-SCAN");
		}
		if(PC == 0x02F1) {
			System.out.println("K-NEW");
		}
		if(PC == 0x02C2) {
			System.out.println("return from KEY-SCAN");
		}
		if(PC == 0x031E) {
			System.out.println("K-TEST");
		}
		if(PC == 0x0308) {
			System.out.println("K-END");
		}

		// and with 0xFF and cast to short to deal with signed byte values
		short op = (short)(memoryService.readByte(PC) & 0xFF);

		switch (op) {

			case 0x00:
				// nop
				clockCycles = 4;
				PC++;
				break;

			case 0x01:
				// ld bc,**
				clockCycles = 10;
				short shortValue = memoryService.readShort(PC + 1);
				registerService.setBC(shortValue);
				PC += 3;
				break;

			case 0x02:
				// ld (bc),a
				clockCycles = 7;
				short pointer = registerService.getBC();
				byte A = registerService.getA();
				memoryService.writeByte(pointer, A);
				PC++;
				break;

			case 0x03:
				// inc bc
				clockCycles = 6;
				short BC = registerService.getBC();
				registerService.setBC(++BC);
				PC++;
				break;

			case 0x04:
				// inc b
				clockCycles = 4;
				byte B = registerService.getB();
				registerService.setB(add(B, (byte)1));
				PC++;
				break;

			case 0x05:
				// dec b
				clockCycles = 4;
				B = registerService.getB();
				registerService.setB(sub(B, (byte)1));
				PC++;
				break;

			case 0x06:
				// ld b,*
				clockCycles = 7;
				byte byteValue = memoryService.readByte(PC + 1);
				registerService.setB(byteValue);
				PC += 2;
				break;

			case 0x07:
				// rlca
				clockCycles = 4;
				A = registerService.getA();
				registerService.setCarryFlag((A & 0x80) != 0);
				registerService.setAddSubtractFlag(false);
				registerService.setHalfCarryFlag(false);
				A = (byte)((A << 1) + (registerService.getCarryFlag() ? 1 : 0));
				registerService.setA(A);
				PC++;
				break;

			case 0x08:
				// ex af,af'
				clockCycles = 4;
				short AF = registerService.getAF();
				short AF2 = registerService.getAF2();
				registerService.setAF(AF2);
				registerService.setAF2(AF);
				PC++;
				break;

			case 0x09:
				// add hl,bc
				clockCycles = 11;
				short HL = registerService.getHL();
				BC = registerService.getBC();
				short result = add(HL, BC);
				registerService.setHL(result);
				PC++;
				break;

			case 0x0A:
				// ld a,(bc)
				clockCycles = 7;
				BC = registerService.getBC();
				byteValue = memoryService.readByte(BC);
				registerService.setA(byteValue);
				PC++;
				break;

			case 0x0B:
				// dec bc
				clockCycles = 6;
				BC = registerService.getBC();
				registerService.setBC(--BC);
				PC++;
				break;

			case 0x0C:
				// inc c
				clockCycles = 4;
				byte C = registerService.getC();
				registerService.setC(add(C, (byte)1));
				PC++;
				break;

			case 0x0D:
				// dec c
				clockCycles = 4;
				C = registerService.getC();
				registerService.setC(sub(C, (byte)1));
				PC++;
				break;

			case 0x0E:
				// ld c,*
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				registerService.setC(byteValue);
				PC += 2;
				break;

			case 0x0F:
				// rrca
				clockCycles = 4;
				A = registerService.getA();
				registerService.setCarryFlag((A & 0x01) != 0);
				registerService.setAddSubtractFlag(false);
				registerService.setHalfCarryFlag(false);
				A = (byte)(((A & 0xff) >>> 1) + (registerService.getCarryFlag() ? 0x80 : 0));
				registerService.setA(A);
				PC++;
				break;

			case 0x10:
				// djnz *
				B = registerService.getB();
				registerService.setB(--B);
				if(B != 0) {
					byteValue = memoryService.readByte(PC + 1);
					clockCycles = 13;
					PC += 2 + byteValue;
				} else {
					clockCycles = 8;
					PC += 2;
				}
				break;

			case 0x11:
				// ld de,**
				clockCycles = 10;
				shortValue = memoryService.readShort(PC + 1);
				registerService.setDE(shortValue);
				PC += 3;
				break;

			case 0x12:
				// ld (de),a
				clockCycles = 7;
				A = registerService.getA();
				shortValue = registerService.getDE();
				memoryService.writeByte(shortValue, A);
				PC++;
				break;

			case 0x13:
				// inc de
				clockCycles = 6;
				short DE = registerService.getDE();
				registerService.setDE(++DE);
				PC++;
				break;

			case 0x14:
				// inc d
				clockCycles = 4;
				byte D = registerService.getD();
				registerService.setD(add(D, (byte)1));
				PC++;
				break;

			case 0x15:
				// dec d
				clockCycles = 4;
				D = registerService.getD();
				registerService.setD(sub(D, (byte)1));
				PC++;
				break;

			case 0x16:
				// ld d,*
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				registerService.setD(byteValue);
				PC += 2;
				break;

			case 0x17:
				// rla
				clockCycles = 4;
				boolean previousCarryFlag = registerService.getCarryFlag();
				A = registerService.getA();
				registerService.setCarryFlag((A & 0x80) != 0);
				registerService.setAddSubtractFlag(false);
				registerService.setHalfCarryFlag(false);
				A = (byte)((A << 1) + (previousCarryFlag ? 1 : 0));
				registerService.setA(A);
				PC++;
				break;

			case 0x18:
				// jr *
				clockCycles = 12;
				byteValue = memoryService.readByte(PC + 1);
				PC += 2 + byteValue;
				break;

			case 0x19:
				// add hl,de
				clockCycles = 11;
				HL = registerService.getHL();
				DE = registerService.getDE();
				result = add(HL, DE);
				registerService.setHL(result);
				PC++;
				break;

			case 0x1A:
				// ld a,(de)
				clockCycles = 7;
				DE = registerService.getDE();
				byteValue = memoryService.readByte(DE);
				registerService.setA(byteValue);
				PC++;
				break;

			case 0x1B:
				// dec de
				clockCycles = 6;
				DE = registerService.getDE();
				registerService.setDE(--DE);
				PC++;
				break;

			case 0x1C:
				// inc e
				clockCycles = 4;
				byte E = registerService.getE();
				registerService.setE(add(E, (byte)1));
				PC++;
				break;

			case 0x1D:
				// dec e
				clockCycles = 4;
				E = registerService.getE();
				registerService.setE(sub(E, (byte)1));
				PC++;
				break;

			case 0x1E:
				// ld e,*
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				registerService.setE(byteValue);
				PC += 2;
				break;

			case 0x1F:
				// rra
				clockCycles = 4;
				previousCarryFlag = registerService.getCarryFlag();
				A = registerService.getA();
				registerService.setCarryFlag((A & 0x01) != 0);
				registerService.setAddSubtractFlag(false);
				registerService.setHalfCarryFlag(false);
				A = (byte)(((A & 0xff) >>> 1) + (previousCarryFlag ? 0x80 : 0));
				registerService.setA(A);
				PC++;
				break;

			case 0x20:
				// jr nz,*
				if(!registerService.getZeroFlag()) {
					clockCycles = 12;
					byteValue = memoryService.readByte(PC + 1);
					PC += 2 + byteValue;
				} else {
					clockCycles = 7;
					PC += 2;
				}
				break;

			case 0x21:
				// ld hl,**
				clockCycles = 10;
				shortValue = memoryService.readShort(PC + 1);
				registerService.setHL(shortValue);
				PC += 3;
				break;

			case 0x22:
				// ld (**),hl
				clockCycles = 16;
				HL = registerService.getHL();
				shortValue = memoryService.readShort(PC + 1);
				memoryService.writeShort(shortValue, HL);
				PC += 3;
				break;

			case 0x23:
				// inc hl
				clockCycles = 6;
				HL = registerService.getHL();
				registerService.setHL(++HL);
				PC++;
				break;

			case 0x24:
				// inc h
				clockCycles = 4;
				byte H = registerService.getH();
				registerService.setH(add(H, (byte)1));
				PC++;
				break;

			case 0x25:
				// dec h
				clockCycles = 4;
				H = registerService.getH();
				registerService.setH(sub(H, (byte)1));
				PC++;
				break;

			case 0x26:
				// ld h,*
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				registerService.setH(byteValue);
				PC += 2;
				break;

			case 0x27:
				// daa - see http://stackoverflow.com/questions/8119577/z80-daa-instruction
				clockCycles = 4;
				boolean NFlag = registerService.getAddSubtractFlag(); // false=ADD, true=SUB
				boolean HCFlag = registerService.getHalfCarryFlag();
				boolean CFlag = registerService.getCarryFlag();
				A = registerService.getA();
				if((A & 0x0f) > 9 || HCFlag) {
					if(NFlag) {
						A -= 0x06;
					} else {
						A += 0x06;
					}
					registerService.setCarryFlag(false);
				}
				if(((A & 0xff) >>> 4) > 9 || CFlag) {
					if(NFlag) {
						A -= 0x60;
					} else {
						A += 0x60;
					}
					registerService.setCarryFlag(true);
				}
				registerService.setHalfCarryFlag(false);
				registerService.setA(A);
				registerService.setParityOverflowFlag(getParity(A));
				PC++;
				break;

			case 0x28:
				// jr z,*
				if(registerService.getZeroFlag()) {
					clockCycles = 12;
					byteValue = memoryService.readByte(PC + 1);
					PC += 2 + byteValue;
				} else {
					clockCycles = 7;
					PC += 2;
				}
				break;

			case 0x29:
				// add hl,hl
				clockCycles = 11;
				HL = registerService.getHL();
				HL = add(HL, HL);
				registerService.setHL(HL);
				PC++;
				break;

			case 0x2A:
				// ld hl,(**)
				clockCycles = 16;
				pointer = memoryService.readShort(PC + 1);
				shortValue = memoryService.readShort(pointer);
				registerService.setHL(shortValue);
				PC += 3;
				break;

			case 0x2B:
				// dec hl
				clockCycles = 6;
				HL = registerService.getHL();
				registerService.setHL(--HL);
				PC++;
				break;

			case 0x2C:
				// inc l
				clockCycles = 4;
				byte L = registerService.getL();
				registerService.setL(add(L, (byte)1));
				PC++;
				break;

			case 0x2D:
				// dec l
				clockCycles = 4;
				L = registerService.getL();
				registerService.setL(sub(L, (byte)1));
				PC++;
				break;

			case 0x2E:
				// ld l,*
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				registerService.setL(byteValue);
				PC += 2;
				break;

			case 0x2F:
				// cpl
				clockCycles = 4;
				A = registerService.getA();
				// one's complement
				A = (byte)(-A - 1);
				registerService.setA(A);
				registerService.setHalfCarryFlag(true);
				registerService.setAddSubtractFlag(true);
				PC++;
				break;

			case 0x30:
				// jr nc,*
				if(!registerService.getCarryFlag()) {
					clockCycles = 12;
					byteValue = memoryService.readByte(PC + 1);
					PC += 2 + byteValue;
				} else {
					clockCycles = 7;
					PC += 2;
				}
				break;

			case 0x31:
				// ld sp,**
				clockCycles = 10;
				shortValue = memoryService.readShort(PC + 1);
				registerService.setSP(shortValue);
				PC += 3;
				break;

			case 0x32:
				// ld (**),a
				clockCycles = 13;
				A = registerService.getA();
				shortValue = memoryService.readShort(PC + 1);
				memoryService.writeByte(shortValue, A);
				PC += 3;
				break;

			case 0x33:
				// inc sp
				clockCycles = 6;
				short SP = registerService.getSP();
				registerService.setSP(++SP);
				PC++;
				break;

			case 0x34:
				// inc (hl)
				clockCycles = 11;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				byteValue = add(byteValue, (byte)1);
				memoryService.writeByte(HL, byteValue);
				PC++;
				break;

			case 0x35:
				// dec (hl)
				clockCycles = 11;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				byteValue = sub(byteValue, (byte)1);
				memoryService.writeByte(HL, byteValue);
				PC++;
				break;

			case 0x36:
				// ld (hl),*
				clockCycles = 10;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(PC + 1);
				memoryService.writeByte(HL, byteValue);
				PC += 2;
				break;

			case 0x37:
				// scf
				clockCycles = 4;
				registerService.setCarryFlag(true);
				registerService.setAddSubtractFlag(false);
				registerService.setHalfCarryFlag(false);
				PC++;
				break;

			case 0x38:
				// jr c,*
				if(registerService.getCarryFlag()) {
					clockCycles = 12;
					byteValue = memoryService.readByte(PC + 1);
					PC += 2 + byteValue;
				} else {
					clockCycles = 7;
					PC += 2;
				}
				break;

			case 0x39:
				// add hl, sp
				clockCycles = 11;
				HL = registerService.getHL();
				SP = registerService.getSP();
				HL = add(HL, SP);
				registerService.setHL(HL);
				PC++;
				break;

			case 0x3A:
				// ld a,(**)
				clockCycles = 13;
				pointer = memoryService.readShort(PC + 1);
				byteValue = memoryService.readByte(pointer);
				registerService.setA(byteValue);
				PC += 3;
				break;

			case 0x3B:
				// dec sp
				clockCycles = 6;
				SP = registerService.getSP();
				registerService.setSP(--SP);
				PC++;
				break;

			case 0x3C:
				// inc a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setA(add(A, (byte)1));
				PC++;
				break;

			case 0x3D:
				// dec a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setA(sub(A, (byte)1));
				PC++;
				break;

			case 0x3E:
				// ld a,*
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				registerService.setA(byteValue);
				PC += 2;
				break;

			case 0x3F:
				// ccf
				clockCycles = 4;
				CFlag = registerService.getCarryFlag();
				registerService.setHalfCarryFlag(CFlag);
				registerService.setCarryFlag(!CFlag);
				registerService.setAddSubtractFlag(false);
				PC++;
				break;

			case 0x40:
				// ld b,b
				clockCycles = 4;
				// nothing changes?
				PC++;
				break;

			case 0x41:
				// ld b,c
				clockCycles = 4;
				C = registerService.getC();
				registerService.setB(C);
				PC++;
				break;

			case 0x42:
				// ld b,d
				clockCycles = 4;
				D = registerService.getD();
				registerService.setB(D);
				PC++;
				break;

			case 0x43:
				// ld b,e
				clockCycles = 4;
				E = registerService.getE();
				registerService.setB(E);
				PC++;
				break;

			case 0x44:
				// ld b,h
				clockCycles = 4;
				H = registerService.getH();
				registerService.setB(H);
				PC++;
				break;

			case 0x45:
				// ld b,l
				clockCycles = 4;
				L = registerService.getL();
				registerService.setB(L);
				PC++;
				break;

			case 0x46:
				// ld b,(hl)
				clockCycles = 7;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				registerService.setB(byteValue);
				PC++;
				break;

			case 0x47:
				// ld b,a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setB(A);
				PC++;
				break;

			case 0x48:
				// ld c,b
				clockCycles = 4;
				B = registerService.getB();
				registerService.setC(B);
				PC++;
				break;

			case 0x49:
				// ld c,c
				clockCycles = 4;
				PC++;
				break;

			case 0x4A:
				// ld c,d
				clockCycles = 4;
				D = registerService.getD();
				registerService.setC(D);
				PC++;
				break;

			case 0x4B:
				// ld c,e
				clockCycles = 4;
				E = registerService.getE();
				registerService.setC(E);
				PC++;
				break;

			case 0x4C:
				// ld c,h
				clockCycles = 4;
				H = registerService.getH();
				registerService.setC(H);
				PC++;
				break;

			case 0x4D:
				// ld c,l
				clockCycles = 4;
				L = registerService.getL();
				registerService.setC(L);
				PC++;
				break;

			case 0x4E:
				// ld c,(hl)
				clockCycles = 7;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				registerService.setC(byteValue);
				PC++;
				break;

			case 0x4F:
				// ld c,a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setC(A);
				PC++;
				break;

			case 0x50:
				// ld d,b
				clockCycles = 4;
				B = registerService.getB();
				registerService.setD(B);
				PC++;
				break;

			case 0x51:
				// ld d,c
				clockCycles = 4;
				C = registerService.getC();
				registerService.setD(C);
				PC++;
				break;

			case 0x52:
				// ld d,d
				clockCycles = 4;
				PC++;
				break;

			case 0x53:
				// ld d,e
				clockCycles = 4;
				E = registerService.getE();
				registerService.setD(E);
				PC++;
				break;

			case 0x54:
				// ld d,h
				clockCycles = 4;
				H = registerService.getH();
				registerService.setD(H);
				PC++;
				break;

			case 0x55:
				// ld d,l
				clockCycles = 4;
				L = registerService.getL();
				registerService.setD(L);
				PC++;
				break;

			case 0x56:
				// ld d,(hl)
				clockCycles = 7;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				registerService.setD(byteValue);
				PC++;
				break;

			case 0x57:
				// ld d,a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setD(A);
				PC++;
				break;

			case 0x58:
				// ld e,b
				clockCycles = 4;
				B = registerService.getB();
				registerService.setE(B);
				PC++;
				break;

			case 0x59:
				// ld e,c
				clockCycles = 4;
				C = registerService.getC();
				registerService.setE(C);
				PC++;
				break;

			case 0x5A:
				// ld e,d
				clockCycles = 4;
				D = registerService.getD();
				registerService.setE(D);
				PC++;
				break;

			case 0x5B:
				// ld e,e
				clockCycles = 4;
				PC++;
				break;

			case 0x5C:
				// ld e,h
				clockCycles = 4;
				H = registerService.getH();
				registerService.setE(H);
				PC++;
				break;

			case 0x5D:
				// ld e,l
				clockCycles = 4;
				L = registerService.getL();
				registerService.setE(L);
				PC++;
				break;

			case 0x5E:
				// ld e,(hl)
				clockCycles = 7;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				registerService.setE(byteValue);
				PC++;
				break;

			case 0x5F:
				// ld e,a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setE(A);
				PC++;
				break;

			case 0x60:
				// ld h,b
				clockCycles = 4;
				B = registerService.getB();
				registerService.setH(B);
				PC++;
				break;

			case 0x61:
				// ld h,c
				clockCycles = 4;
				C = registerService.getC();
				registerService.setH(C);
				PC++;
				break;

			case 0x62:
				// ld h,d
				clockCycles = 4;
				D = registerService.getD();
				registerService.setH(D);
				PC++;
				break;

			case 0x63:
				// ld h,e
				clockCycles = 4;
				E = registerService.getE();
				registerService.setH(E);
				PC++;
				break;

			case 0x64:
				// ld h,h
				clockCycles = 4;
				PC++;
				break;

			case 0x65:
				// ld h,l
				clockCycles = 4;
				L = registerService.getL();
				registerService.setH(L);
				PC++;
				break;

			case 0x66:
				// ld h,(hl)
				clockCycles = 7;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				registerService.setH(byteValue);
				PC++;
				break;

			case 0x67:
				// ld h,a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setH(A);
				PC++;
				break;

			case 0x68:
				// ld l,b
				clockCycles = 4;
				B = registerService.getB();
				registerService.setL(B);
				PC++;
				break;

			case 0x69:
				// ld l,c
				clockCycles = 4;
				C = registerService.getC();
				registerService.setL(C);
				PC++;
				break;

			case 0x6A:
				// ld l,d
				clockCycles = 4;
				D = registerService.getD();
				registerService.setL(D);
				PC++;
				break;

			case 0x6B:
				// ld l,e
				clockCycles = 4;
				E = registerService.getE();
				registerService.setL(E);
				PC++;
				break;

			case 0x6C:
				// ld l,h
				clockCycles = 4;
				H = registerService.getH();
				registerService.setL(H);
				PC++;
				break;

			case 0x6D:
				// ld l,l
				clockCycles = 4;
				PC++;
				break;

			case 0x6E:
				// ld e,(hl)
				clockCycles = 7;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				registerService.setL(byteValue);
				PC++;
				break;

			case 0x6F:
				// ld l,a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setL(A);
				PC++;
				break;

			case 0x70:
				// ld (hl),b
				clockCycles = 7;
				pointer = registerService.getHL();
				byteValue = registerService.getB();
				memoryService.writeByte(pointer, byteValue);
				PC++;
				break;

			case 0x71:
				// ld (hl),c
				clockCycles = 7;
				pointer = registerService.getHL();
				byteValue = registerService.getC();
				memoryService.writeByte(pointer, byteValue);
				PC++;
				break;

			case 0x72:
				// ld (hl),d
				clockCycles = 7;
				pointer = registerService.getHL();
				byteValue = registerService.getD();
				memoryService.writeByte(pointer, byteValue);
				PC++;
				break;

			case 0x73:
				// ld (hl),e
				clockCycles = 7;
				pointer = registerService.getHL();
				byteValue = registerService.getE();
				memoryService.writeByte(pointer, byteValue);
				PC++;
				break;

			case 0x74:
				// ld (hl),h
				clockCycles = 7;
				pointer = registerService.getHL();
				byteValue = registerService.getH();
				memoryService.writeByte(pointer, byteValue);
				PC++;
				break;

			case 0x75:
				// ld (hl),l
				clockCycles = 7;
				pointer = registerService.getHL();
				byteValue = registerService.getL();
				memoryService.writeByte(pointer, byteValue);
				PC++;
				break;

			case 0x76:
				// halt:
				clockCycles = 4;
				halted = true;
				PC++;
				break;

			case 0x77:
				// ld (hl),a
				clockCycles = 7;
				pointer = registerService.getHL();
				byteValue = registerService.getA();
				memoryService.writeByte(pointer, byteValue);
				PC++;
				break;

			case 0x78:
				// ld a,b
				clockCycles = 4;
				B = registerService.getB();
				registerService.setA(B);
				PC++;
				break;

			case 0x79:
				// ld a,c
				clockCycles = 4;
				C = registerService.getC();
				registerService.setA(C);
				PC++;
				break;

			case 0x7A:
				// ld a,d
				clockCycles = 4;
				D = registerService.getD();
				registerService.setA(D);
				PC++;
				break;

			case 0x7B:
				// ld a,e
				clockCycles = 4;
				E = registerService.getE();
				registerService.setA(E);
				PC++;
				break;

			case 0x7C:
				// ld a,h
				clockCycles = 4;
				H = registerService.getH();
				registerService.setA(H);
				PC++;
				break;

			case 0x7D:
				// ld a,l
				clockCycles = 4;
				L = registerService.getL();
				registerService.setA(L);
				PC++;
				break;

			case 0x7E:
				// ld a,(hl)
				clockCycles = 7;
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				registerService.setA(byteValue);
				PC++;
				break;

			case 0x7F:
				// ld a,a
				clockCycles = 4;
				PC++;
				break;

			case 0x80:
				// add a,b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				A = add(A, B);
				registerService.setA(A);
				PC++;
				break;

			case 0x81:
				// add a,c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				A = add(A, C);
				registerService.setA(A);
				PC++;
				break;

			case 0x82:
				// add a,d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				A = add(A, D);
				registerService.setA(A);
				PC++;
				break;

			case 0x83:
				// add a,e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				A = add(A, E);
				registerService.setA(A);
				PC++;
				break;

			case 0x84:
				// add a,h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				A = add(A, H);
				registerService.setA(A);
				PC++;
				break;

			case 0x85:
				// add a,l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				A = add(A, L);
				registerService.setA(A);
				PC++;
				break;

			case 0x86:
				// add a,(hl)
				clockCycles = 7;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				A = add(A, byteValue);
				registerService.setA(A);
				PC++;
				break;

			case 0x87:
				// add a,a
				clockCycles = 4;
				A = registerService.getA();
				A = add(A, A);
				registerService.setA(A);
				PC++;
				break;

			case 0x88:
				// adc a,b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				A = adc(A, B);
				registerService.setA(A);
				PC++;
				break;

			case 0x89:
				// adc a,c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				A = adc(A, C);
				registerService.setA(A);
				PC++;
				break;

			case 0x8A:
				// adc a,d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				A = adc(A, D);
				registerService.setA(A);
				PC++;
				break;

			case 0x8B:
				// adc a,e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				A = adc(A, E);
				registerService.setA(A);
				PC++;
				break;

			case 0x8C:
				// adc a,h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				A = adc(A, H);
				registerService.setA(A);
				PC++;
				break;

			case 0x8D:
				// adc a,l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				A = adc(A, L);
				registerService.setA(A);
				PC++;
				break;

			case 0x8E:
				// adc a,(hl)
				clockCycles = 7;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				A = adc(A, byteValue);
				registerService.setA(A);
				PC++;
				break;

			case 0x8F:
				// adc a,a
				clockCycles = 4;
				A = registerService.getA();
				A = adc(A, A);
				registerService.setA(A);
				PC++;
				break;

			case 0x90:
				// sub b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				A = sub(A, B);
				registerService.setA(A);
				PC++;
				break;

			case 0x91:
				// sub c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				A = sub(A, C);
				registerService.setA(A);
				PC++;
				break;

			case 0x92:
				// sub d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				A = sub(A, D);
				registerService.setA(A);
				PC++;
				break;

			case 0x93:
				// sub e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				A = sub(A, E);
				registerService.setA(A);
				PC++;
				break;

			case 0x94:
				// sub h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				A = sub(A, H);
				registerService.setA(A);
				PC++;
				break;

			case 0x95:
				// sub l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				A = sub(A, L);
				registerService.setA(A);
				PC++;
				break;

			case 0x96:
				// sub (hl)
				clockCycles = 7;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				A = sub(A, byteValue);
				registerService.setA(A);
				PC++;
				break;

			case 0x97:
				// sub a
				clockCycles = 4;
				A = registerService.getA();
				A = sub(A, A);
				registerService.setA(A);
				PC++;
				break;

			case 0x98:
				// sbc a,b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				A = sbc(A, B);
				registerService.setA(A);
				PC++;
				break;

			case 0x99:
				// sbc a,c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				A = sbc(A, C);
				registerService.setA(A);
				PC++;
				break;

			case 0x9A:
				// sbc a,d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				A = sbc(A, D);
				registerService.setA(A);
				PC++;
				break;

			case 0x9B:
				// sbc a,e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				A = sbc(A, E);
				registerService.setA(A);
				PC++;
				break;

			case 0x9C:
				// sbc a,h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				A = sbc(A, H);
				registerService.setA(A);
				PC++;
				break;

			case 0x9D:
				// sbc a,l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				A = sbc(A, L);
				registerService.setA(A);
				PC++;
				break;

			case 0x9E:
				// sbc a,(hl)
				clockCycles = 7;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				A = sbc(A, byteValue);
				registerService.setA(A);
				PC++;
				break;

			case 0x9F:
				// sbc a,a
				clockCycles = 4;
				A = registerService.getA();
				A = sbc(A, A);
				registerService.setA(A);
				PC++;
				break;

			case 0xA0:
				// and b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				registerService.setA(and(A, B));
				PC++;
				break;

			case 0xA1:
				// and c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				registerService.setA(and(A, C));
				PC++;
				break;

			case 0xA2:
				// and d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				registerService.setA(and(A, D));
				PC++;
				break;

			case 0xA3:
				// and e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				registerService.setA(and(A, E));
				PC++;
				break;

			case 0xA4:
				// and h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				registerService.setA(and(A, H));
				PC++;
				break;

			case 0xA5:
				// and l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				registerService.setA(and(A, L));
				PC++;
				break;

			case 0xA6:
				// and (hl)
				clockCycles = 4;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				registerService.setA(and(A, byteValue));
				PC++;
				break;

			case 0xA7:
				// and a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setA(and(A, A));
				PC++;
				break;

			case 0xA8:
				// xor b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				registerService.setA(xor(A, B));
				PC++;
				break;

			case 0xA9:
				// xor c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				registerService.setA(xor(A, C));
				PC++;
				break;

			case 0xAA:
				// xor d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				registerService.setA(xor(A, D));
				PC++;
				break;

			case 0xAB:
				// xor e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				registerService.setA(xor(A, E));
				PC++;
				break;

			case 0xAC:
				// xor h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				registerService.setA(xor(A, H));
				PC++;
				break;

			case 0xAD:
				// xor l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				registerService.setA(xor(A, L));
				PC++;
				break;

			case 0xAE:
				// xor (hl)
				clockCycles = 4;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				registerService.setA(xor(A, byteValue));
				PC++;
				break;

			case 0xAF:
				// xor a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setA(xor(A, A));
				PC++;
				break;

			case 0xB0:
				// or b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				registerService.setA(or(A, B));
				PC++;
				break;

			case 0xB1:
				// or c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				registerService.setA(or(A, C));
				PC++;
				break;

			case 0xB2:
				// or d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				registerService.setA(or(A, D));
				PC++;
				break;

			case 0xB3:
				// or e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				registerService.setA(or(A, E));
				PC++;
				break;

			case 0xB4:
				// or h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				registerService.setA(or(A, H));
				PC++;
				break;

			case 0xB5:
				// or l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				registerService.setA(or(A, L));
				PC++;
				break;

			case 0xB6:
				// or (hl)
				clockCycles = 4;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				registerService.setA(or(A, byteValue));
				PC++;
				break;

			case 0xB7:
				// or a
				clockCycles = 4;
				A = registerService.getA();
				registerService.setA(or(A, A));
				PC++;
				break;

			case 0xB8:
				// cp b
				clockCycles = 4;
				A = registerService.getA();
				B = registerService.getB();
				cp(A, B);
				PC++;
				break;

			case 0xB9:
				// cp c
				clockCycles = 4;
				A = registerService.getA();
				C = registerService.getC();
				cp(A, C);
				PC++;
				break;

			case 0xBA:
				// cp d
				clockCycles = 4;
				A = registerService.getA();
				D = registerService.getD();
				cp(A, D);
				PC++;
				break;

			case 0xBB:
				// cp e
				clockCycles = 4;
				A = registerService.getA();
				E = registerService.getE();
				cp(A, E);
				PC++;
				break;

			case 0xBC:
				// cp h
				clockCycles = 4;
				A = registerService.getA();
				H = registerService.getH();
				cp(A, H);
				PC++;
				break;

			case 0xBD:
				// cp l
				clockCycles = 4;
				A = registerService.getA();
				L = registerService.getL();
				cp(A, L);
				PC++;
				break;

			case 0xBE:
				// cp (hl)
				clockCycles = 4;
				A = registerService.getA();
				pointer = registerService.getHL();
				byteValue = memoryService.readByte(pointer);
				cp(A, byteValue);
				PC++;
				break;

			case 0xBF:
				// cp a
				clockCycles = 4;
				A = registerService.getA();
				cp(A, A);
				PC++;
				break;

			case 0xC0:
				// ret nz
				if(registerService.getZeroFlag()) {
					clockCycles = 5;
					PC++;
				} else {
					clockCycles = 11;
					PC = pop();
				}
				break;

			case 0xC1:
				// pop bc
				clockCycles = 10;
				shortValue = pop();
				registerService.setBC(shortValue);
				PC++;
				break;

			case 0xC2:
				// jp nz,**
				clockCycles = 10;
				if(registerService.getZeroFlag()) {
					PC += 3;
				} else {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				}
				break;

			case 0xC3:
				// jp **
				clockCycles = 10;
				pointer = memoryService.readShort(PC + 1);
				PC = pointer;
				break;

			case 0xC4:
				// call nz,**
				if(registerService.getZeroFlag()) {
					clockCycles = 10;
					PC += 3;
				} else {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				}
				break;

			case 0xC5:
				// push bc
				clockCycles = 11;
				push(registerService.getBC());
				PC++;
				break;

			case 0xC6:
				// add a,*
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				A = registerService.getA();
				A = add(A, byteValue);
				registerService.setA(A);
				PC += 2;
				break;

			case 0xC7:
				// rst 00h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x00;
				break;

			case 0xC8:
				// ret z
				if(registerService.getZeroFlag()) {
					clockCycles = 11;
					PC = pop();
				} else {
					clockCycles = 5;
					PC++;
				}
				break;

			case 0xC9:
				// ret
				clockCycles = 10;
				PC = pop();
				break;

			case 0xCA:
				// jp z,**
				clockCycles = 10;
				if(registerService.getZeroFlag()) {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				} else {
					PC += 3;
				}
				break;

			case 0xCB:
				// BITS...
				doBits(PC, 0);
				PC += 2;
				break;

			case 0xCC:
				// call z,**
				if(registerService.getZeroFlag()) {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				} else {
					clockCycles = 10;
					PC += 3;
				}
				break;

			case 0xCD:
				// call **
				clockCycles = 17;
				push((short)(PC + 3));
				PC = memoryService.readShort(PC + 1);
				break;

			case 0xCE:
				// adc a,*
				clockCycles = 7;
				A = registerService.getA();
				byteValue = memoryService.readByte(PC + 1);
				A = adc(A, byteValue);
				registerService.setA(A);
				PC += 2;
				break;

			case 0xCF:
				// rst 08h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x08;
				break;

			case 0xD0:
				// ret nc
				if(registerService.getCarryFlag()) {
					clockCycles = 5;
					PC++;
				} else {
					clockCycles = 11;
					PC = pop();
				}
				break;

			case 0xD1:
				// pop de
				clockCycles = 10;
				shortValue = pop();
				registerService.setDE(shortValue);
				PC++;
				break;

			case 0xD2:
				// jp nc,**
				clockCycles = 10;
				if(registerService.getCarryFlag()) {
					PC += 3;
				} else {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				}
				break;

			case 0xD3:
				// out (*),a
				clockCycles = 11;
				A = registerService.getA();
				int port = (A << 8) + memoryService.readByte(PC + 1);
				addresBusService.out(port, A);
				PC += 2;
				break;

			case 0xD4:
				// call nc,**
				if(registerService.getCarryFlag()) {
					clockCycles = 10;
					PC += 3;
				} else {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				}
				break;

			case 0xD5:
				// push de
				clockCycles = 11;
				push(registerService.getDE());
				PC++;
				break;

			case 0xD6:
				// sub *
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				A = registerService.getA();
				A = sub(A, byteValue);
				registerService.setA(A);
				PC += 2;
				break;

			case 0xD7:
				// rst 10h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x10;
				break;

			case 0xD8:
				// ret c
				if(registerService.getCarryFlag()) {
					clockCycles = 11;
					PC = pop();
				} else {
					clockCycles = 5;
					PC++;
				}
				break;

			case 0xD9:
				// exx
				clockCycles = 4;
				BC = registerService.getBC();
				DE = registerService.getDE();
				HL = registerService.getHL();
				short BC2 = registerService.getBC2();
				short DE2 = registerService.getDE2();
				short HL2 = registerService.getHL2();
				registerService.setBC(BC2);
				registerService.setDE(DE2);
				registerService.setHL(HL2);
				registerService.setBC2(BC);
				registerService.setDE2(DE);
				registerService.setHL2(HL);
				PC += 1;
				break;

			case 0xDA:
				// jp c,**
				clockCycles = 10;
				if(registerService.getCarryFlag()) {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				} else {
					PC += 3;
				}
				break;

			case 0xDB:
				// in a,(*)
				clockCycles = 11;
				A = registerService.getA();
				port = (A << 8) + memoryService.readByte(PC + 1);
				A = addresBusService.in(port);
				registerService.setA(A);
				PC++;
				break;

			case 0xDC:
				// call c,**
				if(registerService.getCarryFlag()) {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				} else {
					clockCycles = 10;
					PC += 3;
				}
				break;

			case 0xDD:
				// IX...
				PC = doIXIY(PC, 0);
				break;

			case 0xDE:
				// sbc a,*
				clockCycles = 7;
				A = registerService.getA();
				byteValue = memoryService.readByte(PC + 1);
				A = sbc(A, byteValue);
				registerService.setA(A);
				PC += 2;
				break;

			case 0xDF:
				// rst 18h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x18;
				break;

			case 0xE0:
				// ret po
				if(registerService.getParityOverflowFlag()) {
					clockCycles = 5;
					PC++;
				} else {
					clockCycles = 11;
					PC = pop();
				}
				break;

			case 0xE1:
				// pop hl
				clockCycles = 10;
				shortValue = pop();
				registerService.setHL(shortValue);
				PC++;
				break;

			case 0xE2:
				// jp po,**
				clockCycles = 10;
				if(registerService.getParityOverflowFlag()) {
					PC += 3;
				} else {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				}
				break;

			case 0xE3:
				// ex (sp),hl
				clockCycles = 19;
				SP = registerService.getSP();
				byteValue = memoryService.readByte(SP);
				L = registerService.getL();
				memoryService.writeByte(SP, L);
				registerService.setL(byteValue);
				byteValue = memoryService.readByte(SP + 1);
				H = registerService.getH();
				memoryService.writeByte(SP + 1, H);
				registerService.setH(byteValue);
				PC++;
				break;

			case 0xE4:
				// call po,**
				if(registerService.getParityOverflowFlag()) {
					clockCycles = 10;
					PC += 3;
				} else {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				}
				break;

			case 0xE5:
				// push hl
				clockCycles = 11;
				push(registerService.getHL());
				PC++;
				break;

			case 0xE6:
				// and *
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				A = registerService.getA();
				A = and(A, byteValue);
				registerService.setA(A);
				PC += 2;
				break;

			case 0xE7:
				// rst 20h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x20;
				break;

			case 0xE8:
				// ret pe
				if(registerService.getParityOverflowFlag()) {
					clockCycles = 11;
					PC = pop();
				} else {
					clockCycles = 5;
					PC++;
				}
				break;

			case 0xE9:
				// jp (hl)
				clockCycles = 4;
				PC = registerService.getHL();
				break;

			case 0xEA:
				// jp pe,**
				clockCycles = 10;
				if(registerService.getParityOverflowFlag()) {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				} else {
					PC += 3;
				}
				break;

			case 0xEB:
				// ex de,hl
				clockCycles = 4;
				DE = registerService.getDE();
				HL = registerService.getHL();
				registerService.setDE(HL);
				registerService.setHL(DE);
				PC++;
				break;

			case 0xEC:
				// call pe,**
				if(registerService.getParityOverflowFlag()) {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				} else {
					clockCycles = 10;
					PC += 3;
				}
				break;

			case 0xED:
				// EXTD...
				PC = doExtd(PC);
				break;

			case 0xEE:
				// xor *
				clockCycles = 7;
				A = registerService.getA();
				byteValue = memoryService.readByte(PC + 1);
				A = xor(A, byteValue);
				registerService.setA(A);
				PC += 2;
				break;

			case 0xEF:
				// rst 28h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x28;
				break;

			case 0xF0:
				// ret p
				if(registerService.getAddSubtractFlag()) {
					clockCycles = 5;
					PC++;
				} else {
					clockCycles = 11;
					PC = pop();
				}
				break;

			case 0xF1:
				// pop af
				clockCycles = 10;
				shortValue = pop();
				registerService.setAF(shortValue);
				PC++;
				break;

			case 0xF2:
				// jp p,**
				clockCycles = 10;
				if(registerService.getAddSubtractFlag()) {
					PC += 3;
				} else {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				}
				break;

			case 0xF3:
				// di
				clockCycles = 4;
				iff1 = false;
				iff2 = false;
				PC++;
				break;

			case 0xF4:
				// call p,**
				if(registerService.getAddSubtractFlag()) {
					clockCycles = 10;
					PC += 3;
				} else {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				}
				break;

			case 0xF5:
				// push af
				clockCycles = 11;
				push(registerService.getAF());
				PC++;
				break;

			case 0xF6:
				// or *
				clockCycles = 7;
				byteValue = memoryService.readByte(PC + 1);
				A = registerService.getA();
				A = or(A, byteValue);
				registerService.setA(A);
				PC += 2;
				break;

			case 0xF7:
				// rst 30h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x30;
				break;

			case 0xF8:
				// ret m
				if(registerService.getAddSubtractFlag()) {
					clockCycles = 11;
					PC = pop();
				} else {
					clockCycles = 5;
					PC++;
				}
				break;

			case 0xF9:
				// ld sp,hl
				clockCycles = 6;
				HL = registerService.getHL();
				registerService.setSP(HL);
				PC++;
				break;

			case 0xFA:
				// jp m,**
				clockCycles = 10;
				if(registerService.getAddSubtractFlag()) {
					pointer = memoryService.readShort(PC + 1);
					PC = pointer;
				} else {
					PC += 3;
				}
				break;

			case 0xFB:
				// ei
				clockCycles = 4;
				iff1 = true;
				iff2 = true;
				PC++;
				break;

			case 0xFC:
				// call m,**
				if(registerService.getAddSubtractFlag()) {
					clockCycles = 17;
					push((short)(PC + 3));
					PC = memoryService.readShort(PC + 1);
				} else {
					clockCycles = 10;
					PC += 3;
				}
				break;

			case 0xFD:
				// IY...
				PC = doIXIY(PC, 1);
				break;

			case 0xFE:
				// cp *
				clockCycles = 7;
				A = registerService.getA();
				byteValue = memoryService.readByte(PC + 1);
				cp(A, byteValue);
				PC += 2;
				break;

			case 0xFF:
				// rst 38h
				clockCycles = 11;
				push((short)(registerService.getPC() + 1));
				PC = 0x38;
				break;

		}
		registerService.setPC(PC);
	}

	private short doExtd(short PC) throws MemoryException, PortException {
		short op = (short)(memoryService.readByte(PC + 1) & 0xff);
		byte C = registerService.getC();

		switch (op) {
			case 0x40:
				// in b,(c)
				clockCycles = 12;
				byte B = registerService.getB();
				int port = ((B << 8) & 0xffff) + (C & 0xff);
				registerService.setB(in(port));
				PC += 2;
				break;

			case 0x41:
				// out (c), b
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, registerService.getB());
				PC += 2;
				break;

			case 0x42:
				// sbc hl,bc
				clockCycles = 15;
				short HL = registerService.getHL();
				short BC = registerService.getBC();
				HL = sbc(HL, BC);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x43:
				// ld (**),bc
				clockCycles = 20;
				short pointer = memoryService.readShort(PC + 2);
				BC = registerService.getBC();
				memoryService.writeShort(pointer, BC);
				PC += 4;
				break;

			case 0x44:
			case 0x4C:
			case 0x54:
			case 0x5C:
			case 0x64:
			case 0x6C:
			case 0x74:
			case 0x7C:
				// neg
				clockCycles = 8;
				byte A = registerService.getA();
				A = sub((byte)0, A);
				registerService.setA(A);
				PC += 2;
				break;

			case 0x45:
			case 0x55:
			case 0x5D:
			case 0x65:
			case 0x6D:
			case 0x75:
			case 0x7D:
				// retn
				clockCycles = 14;
				iff1 = false;
				iff2 = false;
				PC = pop();
				break;

			case 0x46:
			case 0x66:
				// im 0
				clockCycles = 8;
				im = 0;
				PC += 2;
				break;

			case 0x47:
				// ld i,a
				clockCycles = 9;
				A = registerService.getA();
				registerService.setI(A);
				PC += 2;
				break;

			case 0x48:
				// in c,(c)
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				registerService.setC(in(port));
				PC += 2;
				break;

			case 0x49:
				// out (c), c
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, registerService.getC());
				PC += 2;
				break;

			case 0x4A:
				// adc hl,bc
				clockCycles = 15;
				HL = registerService.getHL();
				BC = registerService.getBC();
				HL = adc(HL, BC);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x4B:
				// ld bc, (**)
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				short shortValue = memoryService.readShort(pointer);
				registerService.setBC(shortValue);
				PC += 4;
				break;

			case 0x4D:
				// reti
				clockCycles = 14;
				PC = pop();
				break;

			case 0x4F:
				// ld r,a
				clockCycles = 9;
				A = registerService.getA();
				registerService.setR(A);
				PC += 2;
				break;

			case 0x50:
				// in d,(c)
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				registerService.setD(in(port));
				PC += 2;
				break;

			case 0x51:
				// out (c), d
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, registerService.getD());
				PC += 2;
				break;

			case 0x52:
				// sbc hl,de
				clockCycles = 15;
				HL = registerService.getHL();
				short DE = registerService.getDE();
				HL = sbc(HL, DE);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x53:
				// ld (**),de
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				DE = registerService.getDE();
				memoryService.writeShort(pointer, DE);
				PC += 4;
				break;

			case 0x56:
			case 0x76:
				// im 1
				clockCycles = 8;
				im = 1;
				PC += 2;
				break;

			case 0x57:
				// ld a, i
				clockCycles = 9;
				byte I = registerService.getI();
				registerService.setA(I);
				PC += 2;
				break;

			case 0x58:
				// in e,(c)
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				registerService.setE(in(port));
				PC += 2;
				break;

			case 0x59:
				// out (c), e
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, registerService.getE());
				PC += 2;
				break;

			case 0x5A:
				// adc hl,de
				clockCycles = 15;
				HL = registerService.getHL();
				DE = registerService.getDE();
				HL = adc(HL, DE);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x5B:
				// ld de, (**)
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				shortValue = memoryService.readShort(pointer);
				registerService.setDE(shortValue);
				PC += 4;
				break;

			case 0x5E:
			case 0x7E:
				// im 2
				clockCycles = 8;
				im = 2;
				PC += 2;
				break;

			case 0x5F:
				// ld a, r
				clockCycles = 9;
				byte R = registerService.getR();
				registerService.setA(R);
				PC += 2;
				break;

			case 0x60:
				// in h,(c)
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				registerService.setH(in(port));
				PC += 2;
				break;

			case 0x61:
				// out (c), h
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, registerService.getH());
				PC += 2;
				break;

			case 0x62:
				// sbc hl,hl
				clockCycles = 15;
				HL = registerService.getHL();
				HL = sbc(HL, HL);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x63:
				// ld (**),hl
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				HL = registerService.getHL();
				memoryService.writeShort(pointer, HL);
				PC += 4;
				break;

			case 0x67:
				// rrd
				clockCycles = 18;
				HL = registerService.getHL();
				byte b1 = memoryService.readByte(HL);
				byte b2 = registerService.getA();
				byte b3 = (byte)(((b2 & 0xff) << 4) & 0x0F + (b1 & 0xff) >> 4);
				byte b4 = (byte)((b2 & 0xF0) + (b3 & 0x0F));
				memoryService.writeByte(HL, b3);
				registerService.setA(b4);
				registerService.setSignFlag(b4 < 0);
				registerService.setZeroFlag(b4 == 0);
				registerService.setHalfCarryFlag(false);
				registerService.setParityOverflowFlag(getParity(b4));
				registerService.setAddSubtractFlag(false);
				break;

			case 0x68:
				// in l,(c)
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				registerService.setL(in(port));
				PC += 2;
				break;

			case 0x69:
				// out (c), l
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, registerService.getL());
				PC += 2;
				break;

			case 0x6A:
				// adc hl,hl
				clockCycles = 15;
				HL = registerService.getHL();
				HL = adc(HL, HL);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x6B:
				// ld hl, (**)
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				shortValue = memoryService.readShort(pointer);
				registerService.setHL(shortValue);
				PC += 4;
				break;

			case 0x6F:
				// rld
				clockCycles = 18;
				HL = registerService.getHL();
				b1 = memoryService.readByte(HL);
				b2 = registerService.getA();
				b3 = (byte)(((b1 & 0xff) << 4) & 0xF0 + (b2 & 0xff) >> 4);
				b4 = (byte)((b2 & 0xF0) + (((b1 & 0xff) >> 4) & 0x0F));
				memoryService.writeByte(HL, b3);
				registerService.setA(b4);
				registerService.setSignFlag(b4 < 0);
				registerService.setZeroFlag(b4 == 0);
				registerService.setHalfCarryFlag(false);
				registerService.setParityOverflowFlag(getParity(b4));
				registerService.setAddSubtractFlag(false);
				break;

			case 0x70:
				// in (c)
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				in(port);
				PC += 2;
				break;

			case 0x71:
				// out (c), 0
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, (byte)0);
				PC += 2;
				break;

			case 0x72:
				// sbc hl,sp
				clockCycles = 15;
				HL = registerService.getHL();
				short SP = registerService.getSP();
				HL = sbc(HL, SP);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x73:
				// ld (**),sp
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				SP = registerService.getSP();
				memoryService.writeShort(pointer, SP);
				PC += 4;
				break;

			case 0x78:
				// in a,(c)
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				registerService.setA(in(port));
				PC += 2;
				break;

			case 0x79:
				// out (c), a
				clockCycles = 12;
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				addresBusService.out(port, registerService.getA());
				PC += 2;
				break;

			case 0x7A:
				// adc hl,sp
				clockCycles = 15;
				HL = registerService.getHL();
				SP = registerService.getSP();
				HL = adc(HL, SP);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0x7B:
				// ld sp, (**)
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				shortValue = memoryService.readShort(pointer);
				registerService.setSP(shortValue);
				PC += 4;
				break;

			case 0xA0:
				// ldi (DE) ← (HL), DE ← DE + 1, HL ← HL + 1, BC ← BC – 1
				clockCycles = 16;
				BC = registerService.getBC();
				DE = registerService.getDE();
				HL = registerService.getHL();
				byte byteValue = memoryService.readByte(HL);
				memoryService.writeByte(DE, byteValue);
				BC--;
				DE++;
				HL++;
				registerService.setBC(BC);
				registerService.setDE(DE);
				registerService.setHL(HL);
				registerService.setSignFlag(false);
				registerService.setHalfCarryFlag(false);
				registerService.setParityOverflowFlag(BC != 0);
				PC += 2;
				break;

			case 0xA1:
				// cpi A – (HL), HL ← HL +1, BC ← BC – 1
				clockCycles = 16;
				BC = registerService.getBC();
				HL = registerService.getHL();
				A = registerService.getA();
				byte M = memoryService.readByte(HL);
				byteValue = (byte)(A - M);
				registerService.setSignFlag(byteValue < 0);
				registerService.setZeroFlag(byteValue == 0);
				registerService.setHalfCarryFlag((A & 0xf) - (M & 0xf) < 0);
				HL++;
				BC--;
				registerService.setBC(BC);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0xA2:
				// ini
				clockCycles = 16;
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = addresBusService.in(port);
				memoryService.writeByte(HL, byteValue);
				registerService.setHL((short)(HL + 1));
				registerService.setB((byte)(B - 1));
				registerService.setZeroFlag(B - 1 == 0);
				registerService.setAddSubtractFlag(true);
				PC += 2;
				break;

			case 0xA3:
				// outi
				clockCycles = 16;
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = memoryService.readByte(HL);
				addresBusService.out(port, byteValue);
				registerService.setHL((short)(HL + 1));
				registerService.setB((byte)(B - 1));
				registerService.setZeroFlag(B - 1 == 0);
				registerService.setAddSubtractFlag(true);
				PC += 2;
				break;

			case 0xA8:
				// ldd
				clockCycles = 16;
				BC = registerService.getBC();
				DE = registerService.getDE();
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				memoryService.writeByte(DE, byteValue);
				BC--;
				DE--;
				HL--;
				registerService.setBC(BC);
				registerService.setDE(DE);
				registerService.setHL(HL);
				registerService.setSignFlag(false);
				registerService.setHalfCarryFlag(false);
				registerService.setParityOverflowFlag(BC != 0);
				PC += 2;
				break;

			case 0xA9:
				// cpd A – (HL), HL ← HL +1, BC ← BC – 1
				clockCycles = 16;
				BC = registerService.getBC();
				HL = registerService.getHL();
				A = registerService.getA();
				M = memoryService.readByte(HL);
				byteValue = (byte)(A - M);
				registerService.setSignFlag(byteValue < 0);
				registerService.setZeroFlag(byteValue == 0);
				registerService.setHalfCarryFlag((A & 0xf) - (M & 0xf) < 0);
				HL--;
				BC--;
				registerService.setBC(BC);
				registerService.setHL(HL);
				PC += 2;
				break;

			case 0xAA:
				// ind
				clockCycles = 16;
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = addresBusService.in(port);
				memoryService.writeByte(HL, byteValue);
				registerService.setHL((short)(HL - 1));
				registerService.setB((byte)(B - 1));
				registerService.setZeroFlag(B - 1 == 0);
				registerService.setAddSubtractFlag(true);
				PC += 2;
				break;

			case 0xAB:
				// outd
				clockCycles = 16;
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = memoryService.readByte(HL);
				addresBusService.out(port, byteValue);
				registerService.setHL((short)(HL - 1));
				registerService.setB((byte)(B - 1));
				registerService.setZeroFlag(B - 1 == 0);
				registerService.setAddSubtractFlag(true);
				PC += 2;
				break;

			case 0xB0:
				// ldir
				BC = registerService.getBC();
				DE = registerService.getDE();
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				memoryService.writeByte(DE, byteValue);
				BC--;
				DE++;
				HL++;
				registerService.setBC(BC);
				registerService.setDE(DE);
				registerService.setHL(HL);
				registerService.setSignFlag(false);
				registerService.setHalfCarryFlag(false);
				registerService.setParityOverflowFlag(BC != 0);
				if(BC == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			case 0xB1:
				// cpir A – (HL), HL ← HL +1, BC ← BC – 1
				BC = registerService.getBC();
				HL = registerService.getHL();
				A = registerService.getA();
				M = memoryService.readByte(HL);
				byteValue = (byte)((A & 0xff) - (M & 0xff));
				registerService.setSignFlag(byteValue < 0);
				registerService.setZeroFlag(byteValue == 0);
				registerService.setHalfCarryFlag((A & 0xf) - (M & 0xf) < 0);
				HL++;
				BC--;
				registerService.setBC(BC);
				registerService.setHL(HL);
				if(BC == 0 || byteValue == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			case 0xB2:
				// inir
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = addresBusService.in(port);
				memoryService.writeByte(HL, byteValue);
				registerService.setHL((short)(HL + 1));
				registerService.setB((byte)((B & 0xff) - 1));
				registerService.setZeroFlag((B & 0xff) - 1 == 0);
				registerService.setAddSubtractFlag(true);
				if(B == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			case 0xB3:
				// otir
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = memoryService.readByte(HL);
				addresBusService.out(port, byteValue);
				registerService.setHL((short)(HL + 1));
				registerService.setB((byte)((B & 0xff) - 1));
				registerService.setZeroFlag((B & 0xff) - 1 == 0);
				registerService.setAddSubtractFlag(true);
				if(B == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			case 0xB8:
				// lddr
				BC = registerService.getBC();
				DE = registerService.getDE();
				HL = registerService.getHL();
				byteValue = memoryService.readByte(HL);
				memoryService.writeByte(DE, byteValue);
				BC--;
				DE--;
				HL--;
				registerService.setBC(BC);
				registerService.setDE(DE);
				registerService.setHL(HL);
				registerService.setSignFlag(false);
				registerService.setHalfCarryFlag(false);
				registerService.setParityOverflowFlag(BC != 0);
				if(BC == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			case 0xB9:
				// cpdr A – (HL), HL ← HL +1, BC ← BC – 1
				BC = registerService.getBC();
				HL = registerService.getHL();
				A = registerService.getA();
				M = memoryService.readByte(HL);
				byteValue = (byte)(A - M);
				registerService.setSignFlag(byteValue < 0);
				registerService.setZeroFlag(byteValue == 0);
				registerService.setHalfCarryFlag((A & 0xf) - (M & 0xf) < 0);
				HL--;
				BC--;
				registerService.setBC(BC);
				registerService.setHL(HL);
				if(BC == 0 || byteValue == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			case 0xBA:
				// indr
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = addresBusService.in(port);
				memoryService.writeByte(HL, byteValue);
				registerService.setHL((short)(HL - 1));
				registerService.setB((byte)(B - 1));
				registerService.setZeroFlag(B - 1 == 0);
				registerService.setAddSubtractFlag(true);
				if(B == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			case 0xBB:
				// otdr
				C = registerService.getC();
				HL = registerService.getHL();
				B = registerService.getB();
				port = ((B << 8) & 0xffff) + (C & 0xff);
				byteValue = memoryService.readByte(HL);
				addresBusService.out(port, byteValue);
				registerService.setHL((short)(HL - 1));
				registerService.setB((byte)(B - 1));
				registerService.setZeroFlag(B - 1 == 0);
				registerService.setAddSubtractFlag(true);
				if(B == 0) {
					clockCycles = 16;
					PC += 2;
				} else {
					clockCycles = 21;
				}
				break;

			default:
				break;
		}
		return PC;
	}

	private byte in(int port) throws PortException {
		byte value = addresBusService.in(port);
		registerService.setAddSubtractFlag(false);
		registerService.setParityOverflowFlag(getParity(value));
		registerService.setHalfCarryFlag(false);
		registerService.setZeroFlag(value == 0);
		registerService.setSignFlag(value < 0);
		return value;
	}

	/**
	 * 
	 * @param PC
	 * @param mode
	 *            0 = normal, 1 = IX BITS, 2 = IY BITS
	 * @throws MemoryException
	 */
	private void doBits(short PC, int mode) throws MemoryException {
		// and with 0xFF and cast to short to deal with signed byte values
		// for mode=1 and mode=2 the op comes after the extra opcode and offset byte, so add 2 extra
		short op = (short)(memoryService.readByte(PC + 1 + ((mode == 0) ? 0 : 2)) & 0xFF);
		short subOp = (short)(op & 0x0f);
		byte byteValue = 0;

		op >>>= 4;

		switch (op) {
			case 0x00:
				if(subOp < 8) {
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = rlc(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = rlc(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = rlc(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = rlc(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				} else {
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = rrc(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = rrc(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = rrc(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = rrc(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				}
				break;

			case 0x01:
				if(subOp < 8) {
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = rl(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = rl(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = rl(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = rl(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				} else {
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = rr(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = rr(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = rr(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = rr(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				}
				break;

			case 0x02:
				if(subOp < 8) {
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = sla(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = sla(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = sla(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = sla(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				} else {
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = sra(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = sra(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = sra(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = sra(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				}
				break;

			case 0x03:
				if(subOp < 8) {
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = sll(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = sll(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = sll(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = sll(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				} else {
					subOp -= 8;
					if(subOp == 0x06) {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 15;
								pointer = registerService.getHL();
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								break;

						}
						byteValue = srl(memoryService.readByte(pointer));
						memoryService.writeByte(pointer, byteValue);
					} else {
						short pointer = 0;
						switch (mode) {
							case 0:
								clockCycles = 8;
								byteValue = srl(registerService.get(subOp));
								break;

							case 1:
								clockCycles = 23;
								pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
								byteValue = srl(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;

							case 2:
								clockCycles = 23;
								pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
								byteValue = srl(memoryService.readByte(pointer));
								memoryService.writeByte(pointer, byteValue);
								break;
						}
						registerService.set(subOp, byteValue);
					}
				}
				break;

			case 0x04:
			case 0x05:
			case 0x06:
			case 0x07:
				int bitToTest = 2 * (op - 0x04) + (subOp >>> 3);
				switch (mode) {
					case 0:
						if((subOp & 0x07) == 0x06) {
							clockCycles = 15;
							short HL = registerService.getHL();
							byteValue = memoryService.readByte(HL);
							bit(bitToTest, byteValue);
						} else {
							clockCycles = 8;
							int register = subOp & 0x07;
							byteValue = registerService.get(register);
							bit(bitToTest, byteValue);
						}
						break;

					case 1:
						clockCycles = 20;
						short pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
						byteValue = memoryService.readByte(pointer);
						bit(bitToTest, byteValue);
						break;

					case 2:
						clockCycles = 20;
						pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
						byteValue = memoryService.readByte(pointer);
						bit(bitToTest, byteValue);
						break;
				}
				break;

			case 0x08:
			case 0x09:
			case 0x0A:
			case 0x0B:
				int bitToReset = 2 * (op - 0x08) + (subOp >>> 3);
				if((subOp & 0x07) == 0x06) {
					short pointer = 0;
					switch (mode) {
						case 0:
							clockCycles = 15;
							pointer = registerService.getHL();
							break;

						case 1:
							clockCycles = 23;
							pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
							break;

						case 2:
							clockCycles = 23;
							pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
							break;
					}
					byteValue = memoryService.readByte(pointer);
					byteValue = res(bitToReset, byteValue);
					memoryService.writeByte(pointer, byteValue);
				} else {
					int register = subOp & 0x07;
					short pointer = 0;
					switch (mode) {
						case 0:
							clockCycles = 8;
							byteValue = registerService.get(register);
							break;

						case 1:
							clockCycles = 23;
							pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
							byteValue = memoryService.readByte(pointer);
							break;

						case 2:
							clockCycles = 23;
							pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
							byteValue = memoryService.readByte(pointer);
							break;
					}
					byteValue = res(bitToReset, byteValue);
					if(pointer != 0) {
						memoryService.writeByte(pointer, byteValue);
					}
					registerService.set(register, byteValue);
				}
				break;

			case 0x0C:
			case 0x0D:
			case 0x0E:
			case 0x0F:
				int bitToSet = 2 * (op - 0x0c) + (subOp >>> 3);
				if((subOp & 0x07) == 0x06) {
					short pointer = 0;
					switch (mode) {
						case 0:
							clockCycles = 15;
							pointer = registerService.getHL();
							break;

						case 1:
							clockCycles = 23;
							pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
							break;

						case 2:
							clockCycles = 23;
							pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
							break;
					}
					byteValue = memoryService.readByte(pointer);
					byteValue = set(bitToSet, byteValue);
					memoryService.writeByte(pointer, byteValue);
				} else {
					int register = subOp & 0x07;
					short pointer = 0;
					switch (mode) {
						case 0:
							clockCycles = 8;
							byteValue = registerService.get(register);
							break;

						case 1:
							clockCycles = 23;
							pointer = (short)(registerService.getIX() + memoryService.readByte(PC + 2));
							byteValue = memoryService.readByte(pointer);
							break;

						case 2:
							clockCycles = 23;
							pointer = (short)(registerService.getIY() + memoryService.readByte(PC + 2));
							byteValue = memoryService.readByte(pointer);
							break;
					}
					byteValue = set(bitToSet, byteValue);
					if(pointer != 0) {
						memoryService.writeByte(pointer, byteValue);
					}
					registerService.set(register, byteValue);
				}
				break;
		}
	}

	private short getIXIY(int mode) {
		if(mode == 0) {
			return registerService.getIX();
		}
		return registerService.getIY();
	}

	private void setIXIY(int mode, short value) {
		if(mode == 0) {
			registerService.setIX(value);
		} else {
			registerService.setIY(value);
		}
	}

	/**
	 * IX or IY instructions, mode = 0: IX, mode = 1: IY
	 */
	private short doIXIY(short PC, int mode) throws MemoryException, PortException {
		short op = (short)(memoryService.readByte(PC + 1) & 0xFF);

		switch (op) {
			case 0x09:
				// add ix, bc / add iy, bc
				clockCycles = 15;
				short BC = registerService.getBC();
				short value = getIXIY(mode);
				setIXIY(mode, add(value, BC));
				PC += 2;
				break;

			case 0x19:
				// add ix, de / add iy, de
				clockCycles = 15;
				short DE = registerService.getDE();
				value = getIXIY(mode);
				setIXIY(mode, add(value, DE));
				PC += 2;
				break;

			case 0x21:
				// ld ix, ** / ld iy, **
				clockCycles = 14;
				value = memoryService.readShort(PC + 2);
				setIXIY(mode, value);
				PC += 4;
				break;

			case 0x22:
				// ld (**), ix / ld (**), iy
				clockCycles = 20;
				value = getIXIY(mode);
				short pointer = memoryService.readShort(PC + 2);
				memoryService.writeShort(pointer, value);
				PC += 4;
				break;

			case 0x23:
				// inc ix / inc iy
				clockCycles = 10;
				value = getIXIY(mode);
				setIXIY(mode, ++value);
				PC += 2;
				break;

			case 0x29:
				// add ix, ix / add iy, iy
				clockCycles = 15;
				value = getIXIY(mode);
				setIXIY(mode, add(value, value));
				PC += 2;
				break;

			case 0x2A:
				// ld ix, (**) / ld iy, (**)
				clockCycles = 20;
				pointer = memoryService.readShort(PC + 2);
				value = memoryService.readShort(pointer);
				setIXIY(mode, value);
				PC += 4;
				break;

			case 0x2B:
				// dec ix / dec iy
				clockCycles = 10;
				value = getIXIY(mode);
				setIXIY(mode, --value);
				PC += 2;
				break;

			case 0x34:
				// inc (ix+*) / inc (iy+*)
				clockCycles = 23;
				byte byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				byteValue = add(byteValue, (byte)1);
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x35:
				// dec (ix+*) / dec (iy+*)
				clockCycles = 23;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				byteValue = sub(byteValue, (byte)1);
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x36:
				// ld (ix+*), * / ld (iy+*), *
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(PC + 3);
				memoryService.writeByte(pointer, byteValue);
				PC += 4;
				break;

			case 0x39:
				// add ix, sp / add iy, sp
				clockCycles = 15;
				short SP = registerService.getSP();
				value = getIXIY(mode);
				setIXIY(mode, add(value, SP));
				PC += 2;
				break;

			case 0x46:
				// ld b,(ix+*) / ld b,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				registerService.setB(byteValue);
				PC += 3;
				break;

			case 0x4E:
				// ld c,(ix+*) / ld c,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				registerService.setC(byteValue);
				PC += 3;
				break;

			case 0x56:
				// ld d,(ix+*) / ld d,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				registerService.setD(byteValue);
				PC += 3;
				break;

			case 0x5E:
				// ld e,(ix+*) / ld e,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				registerService.setE(byteValue);
				PC += 3;
				break;

			case 0x66:
				// ld h,(ix+*) / ld h,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				registerService.setH(byteValue);
				PC += 3;
				break;

			case 0x6E:
				// ld l,(ix+*) / ld l,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				registerService.setL(byteValue);
				PC += 3;
				break;

			case 0x70:
				// ld (ix+*),b / ld (iy+*),b
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = registerService.getB();
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x71:
				// ld (ix+*),c / ld (iy+*),c
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = registerService.getC();
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x72:
				// ld (ix+*),d / ld (iy+*),d
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = registerService.getD();
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x73:
				// ld (ix+*),e / ld (iy+*),e
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = registerService.getE();
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x74:
				// ld (ix+*),h / ld (iy+*),h
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = registerService.getH();
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x75:
				// ld (ix+*),l / ld (iy+*),l
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = registerService.getL();
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x77:
				// ld (ix+*),a / ld (iy+*),a
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = registerService.getA();
				memoryService.writeByte(pointer, byteValue);
				PC += 3;
				break;

			case 0x7E:
				// ld a,(ix+*) / ld a,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				registerService.setA(byteValue);
				PC += 3;
				break;

			case 0x86:
				// add a,(ix+*) / add a,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				byte A = registerService.getA();
				registerService.setA(add(A, byteValue));
				PC += 3;
				break;

			case 0x8E:
				// adc a,(ix+*) / adc a,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				A = registerService.getA();
				registerService.setA(adc(A, byteValue));
				PC += 3;
				break;

			case 0x96:
				// sub a,(ix+*) / sub a,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				A = registerService.getA();
				registerService.setA(sub(A, byteValue));
				PC += 3;
				break;

			case 0x9E:
				// sbc a,(ix+*) / sbc a,(iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				A = registerService.getA();
				registerService.setA(sbc(A, byteValue));
				PC += 3;
				break;

			case 0xA6:
				// and (ix+*) / and (iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				A = registerService.getA();
				registerService.setA(and(A, byteValue));
				PC += 3;
				break;

			case 0xAE:
				// xor (ix+*) / xor (iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				A = registerService.getA();
				registerService.setA(xor(A, byteValue));
				PC += 3;
				break;

			case 0xB6:
				// or (ix+*) / or (iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				A = registerService.getA();
				registerService.setA(or(A, byteValue));
				PC += 3;
				break;

			case 0xBE:
				// cp (ix+*) / cp (iy+*)
				clockCycles = 19;
				byteValue = memoryService.readByte(PC + 2);
				pointer = (short)(getIXIY(mode) + byteValue);
				byteValue = memoryService.readByte(pointer);
				A = registerService.getA();
				sub(A, byteValue);
				PC += 3;
				break;

			case 0xCB:
				// ix bits / iy bits
				doBits(PC, mode + 1);
				PC += 4;
				break;

			case 0xE1:
				// pop ix / pop iy
				clockCycles = 14;
				SP = registerService.getSP();
				byte L = memoryService.readByte(SP++);
				byte H = memoryService.readByte(SP++);
				registerService.setSP(SP);
				setIXIY(mode, (short)(H << 8 + L));
				PC += 2;
				break;

			case 0xE3:
				// ex (sp),ix / ex (sp),iy
				clockCycles = 23;
				SP = registerService.getSP();
				L = memoryService.readByte(SP);
				H = memoryService.readByte(SP + 1);
				value = getIXIY(mode);
				memoryService.writeByte(SP, (byte)(value & 0xff));
				memoryService.writeByte(SP + 1, (byte)(value >> 8));
				setIXIY(mode, (short)(H << 8 + L));
				PC += 2;
				break;

			case 0xE5:
				// push ix / push iy
				clockCycles = 15;
				SP = registerService.getSP();
				value = getIXIY(mode);
				memoryService.writeByte(--SP, (byte)(value >> 8));
				memoryService.writeByte(--SP, (byte)(value & 0xff));
				PC += 2;
				break;

			case 0xE9:
				// jp (ix) / jp (iy)
				clockCycles = 8;
				PC = getIXIY(mode);
				break;

			case 0xF9:
				// ld sp, ix / ld sp, iy
				clockCycles = 10;
				SP = getIXIY(mode);
				registerService.setSP(SP);
				PC += 2;
				break;

			default:
				break;
		}
		return PC;
	}

	private byte set(int bitToSet, byte byteValue) {
		return (byte)(byteValue | (1 << bitToSet));
	}

	private byte res(int bitToReset, byte byteValue) {
		return (byte)(byteValue & ~(1 << bitToReset));
	}

	private void bit(int bitToTest, byte byteValue) {
		registerService.setAddSubtractFlag(false);
		registerService.setHalfCarryFlag(true);
		boolean test = (byteValue & (1 << bitToTest)) == 0;
		registerService.setZeroFlag(test);
	}

	private byte srl(byte b) {
		registerService.setCarryFlag((b & 0x01) != 0);
		byte result = (byte)((b & 0xff) >>> 1);
		registerService.setSignFlag(false);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	private byte sll(byte b) {
		registerService.setCarryFlag((b & 0x80) != 0);
		byte result = (byte)(((b & 0xff) << 1) | 0x01);
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	private byte sra(byte b) {
		registerService.setCarryFlag((b & 0x01) != 0);
		byte result = (byte)(((b & 0xff) >>> 1) | (b & 0x80));
		registerService.setSignFlag(false);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	private byte sla(byte b) {
		registerService.setCarryFlag((b & 0x80) != 0);
		byte result = (byte)(((b & 0xff) << 1) & 0xFE);
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	private byte rr(byte b) {
		boolean CFlag = registerService.getCarryFlag();
		registerService.setCarryFlag((b & 0x01) != 0);
		byte result = (byte)(((b & 0xff) >>> 1) | (CFlag ? 0x80 : 0x00));
		registerService.setSignFlag(false);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	private byte rl(byte b) {
		boolean CFlag = registerService.getCarryFlag();
		registerService.setCarryFlag((b & 0x80) != 0);
		byte result = (byte)(((b & 0xff) << 1) | (CFlag ? 0x01 : 0x00));
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	private byte rrc(byte b) {
		boolean CFlag = (b & 0x01) != 0;
		registerService.setCarryFlag(CFlag);
		byte result = (byte)(((b & 0xff) >>> 1) | (CFlag ? 0x80 : 0x00));
		registerService.setSignFlag(false);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	private byte rlc(byte b) {
		boolean CFlag = (b & 0x80) != 0;
		registerService.setCarryFlag(CFlag);
		byte result = (byte)(((b & 0xff) << 1) | (CFlag ? 0x01 : 0x00));
		registerService.setSignFlag(result < 0);
		registerService.setZeroFlag(result == 0x00);
		registerService.setHalfCarryFlag(false);
		registerService.setParityOverflowFlag(getParity(result));
		registerService.setAddSubtractFlag(false);
		return result;
	}

	@Override
	public void vsync() {
		if(iff1) {
			execInterrupt = true;
			iff1 = false;
		}
	}

	@Override
	public void initialize() {
		try {
			addresBusService.registerPort(0xFEFE, keyboardService);
			addresBusService.registerPort(0xFDFE, keyboardService);
			addresBusService.registerPort(0xFBFE, keyboardService);
			addresBusService.registerPort(0xF7FE, keyboardService);
			addresBusService.registerPort(0xEFFE, keyboardService);
			addresBusService.registerPort(0xDFFE, keyboardService);
			addresBusService.registerPort(0xBFFE, keyboardService);
			addresBusService.registerPort(0x7FFE, keyboardService);
		} catch(PortException e) {
			e.printStackTrace();
		}
		// ClassLoader classLoader = getClass().getClassLoader();
		// File file = new File(classLoader.getResource("zexall").getFile());
		// try {
		// memoryService.loadFile(0x8000, file);
		// } catch(IOException | MemoryException e) {
		// e.printStackTrace();
		// }
		// registerService.setPC((short)0x8000);
	}

}
