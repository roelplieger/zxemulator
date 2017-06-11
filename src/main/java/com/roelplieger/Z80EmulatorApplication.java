package com.roelplieger;

import java.awt.Container;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;

import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.roelplieger.exceptions.MemoryException;
import com.roelplieger.services.ClockService;
import com.roelplieger.services.MemoryService;
import com.roelplieger.services.MonitorService;

@SpringBootApplication
public class Z80EmulatorApplication extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2718386211378027496L;

	@Autowired
	MonitorService monitorService;
	@Autowired
	MemoryService memoryService;
	@Autowired
	ClockService clockService;

	private void initUI() {

		createLayout(monitorService.getInstance());

		setTitle("ZX Spectrum emulator");
		setSize(286, 252);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	private void createLayout(JComponent... arg) {

		Container pane = getContentPane();
		GroupLayout gl = new GroupLayout(pane);
		pane.setLayout(gl);

		gl.setAutoCreateContainerGaps(true);

		gl.setHorizontalGroup(gl.createSequentialGroup().addComponent(arg[0]));

		gl.setVerticalGroup(gl.createSequentialGroup().addComponent(arg[0]));
	}

	public static void main(String[] args) {
		ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Z80EmulatorApplication.class).headless(false).web(false).run(args);

		EventQueue.invokeLater(() -> {
			Z80EmulatorApplication ex = ctx.getBean(Z80EmulatorApplication.class);
			ex.initUI();
			ex.setVisible(true);
			initializeSpectrum(ex);
		});
	}

	private static void initializeSpectrum(Z80EmulatorApplication ex) {
		// load ROM
		ClassLoader classLoader = ex.getClass().getClassLoader();
		try {
			File file = new File(classLoader.getResource("48.rom").getFile());
			ex.memoryService.loadFile(0, file);
			// run!
			ex.clockService.start();
		} catch(MemoryException | IOException e) {
			e.printStackTrace();
		}
	}
}
