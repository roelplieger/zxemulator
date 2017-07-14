package com.roelplieger.services;

public interface KeyboardService extends IOService {

	void keyDown(int keyCode);

	void keyUp(int keyCode);
}
