package com.roelplieger.services.impl;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.roelplieger.exceptions.MemoryException;
import com.roelplieger.services.KeyboardService;
import com.roelplieger.services.MemoryService;
import com.roelplieger.services.MonitorService;

@Component
public class MonitorServiceImpl extends JPanel implements MonitorService, KeyListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8872829725467666959L;
	private static final int FLASH_DURATION = 50; // 1 second
	private static final int[] COLOR_MAP = {
		0x000000, 0x0000D7, 0xD70000, 0xD700D7, 0x00D700, 0x00D7D7, 0xD7D700, 0xD7D7D7, 0x000000, 0x0000FF, 0xFF0000, 0xFF00FF, 0x00FF00, 0x00FFFF, 0xFFFF00, 0xFFFFFF
	};
	private BufferedImage bi = new BufferedImage(256, 192, BufferedImage.TYPE_INT_RGB);
	private int flashCounter = FLASH_DURATION;
	private boolean colorsInverted = false;
	private int borderColor = 7;
	private boolean listenForBorderChange = false;

	@Autowired
	MemoryService memoryService;
	@Autowired
	KeyboardService keyboardService;

	public MonitorServiceImpl() {
		setBounds(0, 0, 256, 192);
		addKeyListener(this);
		setFocusable(true);
	}

	@Override
	protected void paintComponent(Graphics g) {
		try {
			byte x = (memoryService.readByte(0x5C48));
			/**
			 * Wait for x to become 56 (bordercolor 7), after that accept border color changes
			 */
			if(!listenForBorderChange) {
				if(x == 56) {
					listenForBorderChange = true;
				}
			} else {
				borderColor = (x - 7 * (0x01 - (x & 0x20) / 0x20)) / 8;
			}
		} catch(MemoryException e1) {
			e1.printStackTrace();
		}
		getParent().setBackground(new Color(COLOR_MAP[borderColor]));
		try {
			drawCanvas();
		} catch(MemoryException e) {
			e.printStackTrace();
		}
		g.drawImage(bi, 0, 0, new Color(0, 0, 0), null);
	}

	private void drawCanvas() throws MemoryException {
		for(int y = 0; y < 24; y++) {
			for(int x = 0; x < 32; x++) {
				for(int dy = 0; dy < 8; dy++) {
					short bitmapPointer = (short)(0x4000 + 32 * (8 * y + dy) + x);
					byte bitmap = memoryService.readByte(bitmapPointer);
					// http://whatnotandgobbleaduke.blogspot.nl/2011/07/zx-spectrum-screen-memory-layout.html
					int convertedY = 8 * y + dy;
					convertedY = (convertedY & 0xc0) + ((convertedY & 0x38) >> 3) + ((convertedY & 0x07) << 3);
					short attribPointer = (short)(0x5800 + 32 * (convertedY / 8) + x);
					byte attribute = memoryService.readByte(attribPointer);
					int[] colors = getColors(attribute);
					for(int bit = 0; bit < 8; bit++) {
						int rgb = ((bitmap & (byte)(Math.pow(2, 7 - bit))) != 0) ? colors[0] : colors[1];
						bi.setRGB(8 * x + bit, convertedY, rgb);
					}
				}
			}
		}
	}

	private int[] getColors(byte attribute) {
		int[] colors = new int[2];
		boolean bright = (attribute & 0x40) != 0;
		boolean flash = (attribute & 0x80) != 0;
		int fgIdx = (attribute & 0x07) + ((bright) ? 0x08 : 0x00);
		int bgIdx = ((attribute & 0x38) >> 3) + ((bright) ? 0x08 : 0x00);
		if(flash && colorsInverted) {
			colors[0] = COLOR_MAP[bgIdx];
			colors[1] = COLOR_MAP[fgIdx];
		} else {
			colors[0] = COLOR_MAP[fgIdx];
			colors[1] = COLOR_MAP[bgIdx];
		}
		return colors;
	}

	@Override
	public JComponent getInstance() {
		return this;
	}

	@Override
	public void vsync() {
		// handle flashing colors
		flashCounter--;
		if(flashCounter == 0) {
			flashCounter = FLASH_DURATION;
			colorsInverted = !colorsInverted;
		}

		repaint();
	}

	@Override
	public void keyTyped(KeyEvent e) {

	}

	@Override
	public void keyPressed(KeyEvent e) {
		keyboardService.keyDown(e.getKeyCode());
	}

	@Override
	public void keyReleased(KeyEvent e) {
		keyboardService.keyUp(e.getKeyCode());
	}
}
