package com.roelplieger.services.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.roelplieger.exceptions.PortException;
import com.roelplieger.services.KeyboardService;

@Component
public class KeyboardServiceImpl implements KeyboardService {

	private final Set<Integer> keysPressed = new HashSet<>();
	private final Map<Integer, Map<Integer, Byte>> portKeyMap = new ConcurrentHashMap<Integer, Map<Integer, Byte>>();

	public KeyboardServiceImpl() {
		// init key maps
		Map<Integer, Byte> keyMap = new HashMap<>();
		keyMap.put(86, (byte)0x10); // V
		keyMap.put(67, (byte)0x08); // C
		keyMap.put(88, (byte)0x04); // X
		keyMap.put(90, (byte)0x02); // Z
		keyMap.put(16, (byte)0x01); // SHIFT
		portKeyMap.put(0xfefe, keyMap);

		keyMap = new HashMap<>();
		keyMap.put(71, (byte)0x10); // G
		keyMap.put(70, (byte)0x08); // F
		keyMap.put(68, (byte)0x04); // D
		keyMap.put(83, (byte)0x02); // S
		keyMap.put(65, (byte)0x01); // A
		portKeyMap.put(0xfdfe, keyMap);

		keyMap = new HashMap<>();
		keyMap.put(84, (byte)0x10); // T
		keyMap.put(82, (byte)0x08); // R
		keyMap.put(69, (byte)0x04); // E
		keyMap.put(87, (byte)0x02); // W
		keyMap.put(81, (byte)0x01); // Q
		portKeyMap.put(0xfbfe, keyMap);

		keyMap = new HashMap<>();
		keyMap.put(53, (byte)0x10); // 5
		keyMap.put(52, (byte)0x08); // 4
		keyMap.put(51, (byte)0x04); // 3
		keyMap.put(50, (byte)0x02); // 2
		keyMap.put(49, (byte)0x01); // 1
		portKeyMap.put(0xf7fe, keyMap);

		keyMap = new HashMap<>();
		keyMap.put(54, (byte)0x10); // 6
		keyMap.put(55, (byte)0x08); // 7
		keyMap.put(56, (byte)0x04); // 8
		keyMap.put(57, (byte)0x02); // 9
		keyMap.put(48, (byte)0x01); // 0
		portKeyMap.put(0xeffe, keyMap);

		keyMap = new HashMap<>();
		keyMap.put(89, (byte)0x10); // Y
		keyMap.put(85, (byte)0x08); // U
		keyMap.put(73, (byte)0x04); // I
		keyMap.put(79, (byte)0x02); // O
		keyMap.put(80, (byte)0x01); // P
		portKeyMap.put(0xdffe, keyMap);

		keyMap = new HashMap<>();
		keyMap.put(72, (byte)0x10); // H
		keyMap.put(74, (byte)0x08); // J
		keyMap.put(75, (byte)0x04); // K
		keyMap.put(76, (byte)0x02); // L
		keyMap.put(10, (byte)0x01); // ENTER
		portKeyMap.put(0xbffe, keyMap);

		keyMap = new HashMap<>();
		keyMap.put(66, (byte)0x10); // B
		keyMap.put(78, (byte)0x08); // N
		keyMap.put(77, (byte)0x04); // M
		keyMap.put(17, (byte)0x02); // SYM (Ctrl)
		keyMap.put(32, (byte)0x01); // SPACE
		portKeyMap.put(0x7ffe, keyMap);
	}

	@Override
	public byte in(int port) throws PortException {
		Map<Integer, Byte> keyMap = portKeyMap.get(port);
		byte keys = 0x00;

		for(int keyCode: keysPressed) {
			if(keyMap.containsKey(keyCode)) {
				byte scanCode = keyMap.get(keyCode);
				keys |= scanCode;
			}
		}

		if(keys != 0x00) {
			// System.out.println(String.format("%x - %x", port, (byte)(0x1f - keys)));
			return (byte)(0x1f - keys);
		}
		return (byte)0xFF;
	}

	@Override
	public void out(int port, byte value) throws PortException {

	}

	@Override
	public void keyDown(int keyCode) {
		keysPressed.add(keyCode);
	}

	@Override
	public void keyUp(int keyCode) {
		keysPressed.remove(keyCode);
	}
}
