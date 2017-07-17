package com.roelplieger.services;

import javax.swing.JComponent;

public interface MonitorService {

	JComponent getInstance();

	void vsync();

	void setBorderColor(int color);
}
