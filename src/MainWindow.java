import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JPanel;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

public class MainWindow extends JPanel implements PacketListener {
	
	private JComboBox commPortSelector;
	private JButton btnRefresh, btnConnect; //connection buttons
	private JButton btnEnable, btnSave, btnClearData;
	private JButton btnDrop, btnCamLeft, btnCamRight, btnCamCenter, btnSensorReset, btnPlaneRestart; //servo control buttons
	private PrintStream console; //to display all console messages
	private PrintStream planeMessageConsole, dataLogger;
	private JTextArea planeMessageTextArea, dataLoggerTextArea, consoleTextArea;
	private JTextField servoControlTextBox;
	private JLabel lblRoll, lblPitch, lblSpeed, lblAlt, lblAltAtDrop; //labels to display the values
	private SerialCommunicator serialComm;
	JDialog calibrator;
	long connectTime;
	boolean btnsEnabled = false;
	
	public MainWindow (SerialCommunicator sc) {
		serialComm = sc;
		initializeComponents();
		initializeButtons();
	}
	
	private void setControlButtons (boolean val) {
		btnDrop.setEnabled(val);
		btnCamLeft.setEnabled(val);
		btnCamRight.setEnabled(val);
		btnCamCenter.setEnabled(val);
		btnSensorReset.setEnabled(val);
		btnPlaneRestart.setEnabled(val);
		servoControlTextBox.setEditable(val);
		btnsEnabled = val;
	}
	
	private void updateCommPortSelector() {
		commPortSelector.removeAllItems();
		ArrayList<String> temp = serialComm.getPortList();
		System.out.println("Available serial ports:");
		for (int i = 0; i < temp.size(); i++) {
			commPortSelector.addItem(temp.get(i));
			System.out.print(temp.get(i) + ",");
		}
		System.out.println();
	}
	
	private void initializeButtons() {
		setControlButtons(false);
		//treating the byte sender like a button
		servoControlTextBox.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				char val = e.getKeyChar();
				if (validChar(val)) {
					planeMessageConsole.println("Key sent: " + val);
					serialComm.write(val);
					if (val == 'P') {
						dropPackage();
					}
				}
				servoControlTextBox.setText("");
			}
		});
		
		btnClearData.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dataLoggerTextArea.setText("");
				dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");
				planeMessageTextArea.setText("");
				consoleTextArea.setText("");
			}
		});
		
		btnRefresh.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updateCommPortSelector();
			}
		});
		
		btnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!serialComm.getConnected()) {
					String commPort = commPortSelector.getSelectedItem().toString();
					serialComm.connect(commPort);
					if (serialComm.getConnected()) {
						connectTime = System.currentTimeMillis();
						serialComm.addListener(MainWindow.this);
						btnConnect.setText("Disconnect");
						setControlButtons(true);
					}
				}
				else {
					serialComm.disconnect();
					if (!serialComm.getConnected()) {
						serialComm.removeListener(MainWindow.this);
						btnConnect.setText("Connect");
						setControlButtons(false);
					}
				}
			}
		});
		
		btnEnable.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				btnsEnabled = !btnsEnabled;
				if (serialComm.getConnected()) {
					if (btnsEnabled) {
						btnSensorReset.setEnabled(true);
						btnPlaneRestart.setEnabled(true);
					}
					else {
						btnSensorReset.setEnabled(false);
						btnPlaneRestart.setEnabled(false);
					}
				}
				else {
					System.out.println("Cannot enable sensor/plane resets. Comm. port not connected.");
				}
			}
		});
		
		btnSave.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
                JFileChooser saveFile = new JFileChooser();
                saveFile.addChoosableFileFilter(new FileNameExtensionFilter("Text File (*.txt)", ".txt"));
                saveFile.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int val = saveFile.showSaveDialog(null);
                if (val == JFileChooser.APPROVE_OPTION) {                		
                	String filename = saveFile.getSelectedFile().toString();
                	try {
                	if (saveFile.getFileFilter().getDescription().equals("Text File (*.txt)"))
                		filename += ".txt";
                	} catch (Exception err) {}
                	FileWriter fstream = null;
                	BufferedWriter writer = null;
                	String messages = planeMessageTextArea.getText();
                	String data = dataLoggerTextArea.getText();
                	System.out.println("Attempting to save data as \"" + filename + "\"");
                	try {
                		fstream = new FileWriter(filename);
                		writer = new BufferedWriter(fstream);
                		writer.write("Plane Messages:\n");
                		writer.write(messages);
                		writer.write("Data:\n");
						writer.write(data);
						writer.close();
						System.out.println("File saved succesfully.");
                	} catch (IOException err) {
                		System.out.println("Error saving file.");
                		err.printStackTrace();
                	}
                }
			}
		});
		
		btnDrop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				serialComm.write('P');
				dropPackage();
			}
		});
		

		btnCamLeft.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				serialComm.write('a');
				planeMessageConsole.println("Cam left sent.");
			}
		});
		
		btnCamRight.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				serialComm.write('d');
				planeMessageConsole.println("Cam right sent.");
			}
		});
		btnCamCenter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				serialComm.write('x');
				planeMessageConsole.println("Centre camera sent.");
			}
		});
		
		btnSensorReset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				serialComm.write('r');
				planeMessageConsole.println("Reset sent.");
				dataLoggerTextArea.setText("");
				dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");
			}
		});
		
		btnPlaneRestart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				serialComm.write('q');
				planeMessageConsole.println("Restart sent.");
				dataLoggerTextArea.setText("");
				dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");
			}
		});
	}
	
	private void dropPackage() {
		planeMessageConsole.println("Drop package sent.");
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Package dropped at: "+lblAlt.getText());
		lblAltAtDrop.setText(lblAlt.getText());
	}
	
	private void initializeComponents() {
		this.setLayout(new GridLayout(0, 2, 0, 0));
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(4, 1, 0, 0));
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new GridLayout(2, 1, 0, 0));
		panel.add(topPanel);
		
		JPanel dataControlPanel = new JPanel();
		dataControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Data"));
		dataControlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		JLabel roll, pitch, speed, alt, altAtDrop;
		roll = new JLabel("Roll:");
		pitch = new JLabel("Pitch:");
		speed = new JLabel("Speed:");
		alt = new JLabel("Alt:");
		altAtDrop = new JLabel("Alt at Drop:");

		lblRoll = new JLabel("");
		lblPitch = new JLabel("");
		lblSpeed = new JLabel("");
		lblAlt = new JLabel("");
		lblAltAtDrop = new JLabel("");
		
		lblRoll.setForeground(Color.GREEN);
		lblPitch.setForeground(Color.GREEN);
		lblSpeed.setForeground(Color.GREEN);
		lblAlt.setForeground(Color.GREEN);
		lblAltAtDrop.setForeground(Color.GREEN);
		
		dataControlPanel.add(roll);
		dataControlPanel.add(lblRoll);
		dataControlPanel.add(pitch);
		dataControlPanel.add(lblPitch);
		dataControlPanel.add(speed);
		dataControlPanel.add(lblSpeed);
		dataControlPanel.add(alt);
		dataControlPanel.add(lblAlt);
		dataControlPanel.add(altAtDrop);
		dataControlPanel.add(lblAltAtDrop);
		
		JPanel commPortControlPanel = new JPanel();
		commPortControlPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		commPortControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Comm. Port"));
		commPortSelector = new JComboBox();
		updateCommPortSelector();
		commPortControlPanel.add(commPortSelector);

		topPanel.add(dataControlPanel);
		topPanel.add(commPortControlPanel);

		btnRefresh = new JButton("Refresh");
		commPortControlPanel.add(btnRefresh);
		
		btnConnect = new JButton("Connect");
		commPortControlPanel.add(btnConnect);
		
		btnClearData = new JButton("Clear");
		commPortControlPanel.add(btnClearData);
		
		JPanel servoControlPanel = new JPanel();
		servoControlPanel.setBorder(new TitledBorder(new EtchedBorder(), "Controls"));

		panel.add(servoControlPanel);
		servoControlPanel.setLayout(new GridLayout(0, 2, 0, 0));
		
		JPanel servoButtonPanel = new JPanel();
		servoButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		servoControlPanel.add(servoButtonPanel);
		
		btnEnable = new JButton("Enable/Disable Resets");
		servoButtonPanel.add(btnEnable);
		
		btnSave = new JButton("Save");
		servoButtonPanel.add(btnSave);
		
		btnDrop = new JButton("Drop");
		servoButtonPanel.add(btnDrop);
		
		btnCamLeft = new JButton("Cam Left");
		servoButtonPanel.add(btnCamLeft);
		
		btnCamRight = new JButton("Cam Right");
		servoButtonPanel.add(btnCamRight);
		
		btnCamCenter = new JButton("Cam Center");
		servoButtonPanel.add(btnCamCenter);
		
		btnSensorReset = new JButton("Sensor Reset");
		servoButtonPanel.add(btnSensorReset);
		
		btnPlaneRestart = new JButton("Plane Restart");
		servoButtonPanel.add(btnPlaneRestart);
		
		JPanel servoTextPanel = new JPanel();
		servoControlPanel.add(servoTextPanel);
		commPortControlPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

		servoControlTextBox = new JTextField();
		servoTextPanel.add(servoControlTextBox);
		servoControlTextBox.setColumns(10);
		servoTextPanel.add(new JLabel("Valid Chars are: P (drop), a (cam left), d (cam right), x (cam center)"));
		JPanel planeMessagePanel = new JPanel();
		planeMessagePanel.setBorder(new TitledBorder(new EtchedBorder(), "Plane Messages"));
		planeMessagePanel.setLayout(new BorderLayout());
		planeMessageTextArea = new JTextArea();
		JScrollPane planeMessageScroller = new JScrollPane(planeMessageTextArea);
		planeMessageConsole = new PrintStream(new TextAreaOutputStream(planeMessageTextArea));
		planeMessageScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		planeMessagePanel.add(planeMessageScroller);
		panel.add(planeMessagePanel);
		
		JPanel consolePanel = new JPanel();
		consolePanel.setBorder(new TitledBorder(new EtchedBorder(), "Console"));
		panel.add(consolePanel);
		consoleTextArea = new JTextArea();
		JScrollPane consoleScroller = new JScrollPane(consoleTextArea);
		console = new PrintStream(new TextAreaOutputStream(consoleTextArea));
		consolePanel.setLayout(new BorderLayout());
		consoleScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		System.setOut(console);
		System.setErr(console);
		consolePanel.add(consoleScroller);
		
		
		JPanel panel_1 = new JPanel();
		panel_1.setBorder(new TitledBorder(new EtchedBorder(), "Data Logger"));
		panel_1.setLayout(new BorderLayout(0, 0));
				
		dataLoggerTextArea = new JTextArea();
		JScrollPane dataLoggerScroller = new JScrollPane(dataLoggerTextArea);
		dataLogger = new PrintStream(new TextAreaOutputStream(dataLoggerTextArea));
		dataLoggerScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		panel_1.add(dataLoggerScroller, BorderLayout.CENTER);
		dataLogger.println("TIME\tROLL\tPITCH\tALT\tSPEED");

		
		this.add(panel);
		this.add(panel_1);

	}
	
	public void invalidPacketReceived(String packet) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		System.out.println(time + "s: Invalid packet recieved:" +packet);
	}

	public void packetReceived(String packet) {
		//System.out.println(packet);
		analyzePacket(packet);
	}
	
	private boolean validChar (char ch) {
		if (ch == 'P' || ch == 'a' || ch == 'd'|| ch == 'x') {
			return true;
		}
		return false;
	}
	
	private void analyzePacket (String str) {
		double time = (System.currentTimeMillis() - connectTime) / 1000.0;
		if (str.substring(0, 1).equals("p")) {
			String [] strArr = str.split("%");
			double [] dblArr = new double [5];
			try {
				for (int i = 1; i < 5; i++) {
					dblArr[i] = Double.parseDouble(strArr[i]);
					dblArr[i] *= 100;
					dblArr[i] = Math.round(dblArr[i]) / 100.0;
				}
			} catch (Exception e) {
				System.out.println(time + "s: Encountered an invalid packet: \"" + str + "\"");
			}
			lblRoll.setText(""+dblArr[1]);
			lblPitch.setText(""+dblArr[2]);
			lblAlt.setText(""+dblArr[3]);
			lblSpeed.setText(""+dblArr[4]);
			dataLogger.println(time + "\t" + dblArr[1] + "\t" + dblArr[2] + "\t" + dblArr[3] + "\t" + dblArr[4]);
		}
		else if (str.substring(0, 1).equals("s")) {
			planeMessageConsole.println(time + "s: Start");
		}
		else if (str.substring(0, 1).equals("k")) {
			planeMessageConsole.println(time + "s: Reset Acknowledge");
		}
		else if (str.substring(0, 1).equals("q")) {
			planeMessageConsole.println(time + "s: Restart Acknowledge");
		}
		else if (str.substring(0, 1).equals("x")) {
			planeMessageConsole.println(time + "s: Camera Reset Acknowledge");
		}
		else if (str.substring(0, 1).equals("e")) {
			planeMessageConsole.println(time + "s: Error");
		}
		else if (str.substring(0, 1).equals("y")) {
			planeMessageConsole.println(time + "s: Drop Acknowledge");
		}
		else if (str.substring(0, 1).equals("1")) {
			planeMessageConsole.println(time + "s: MPU6050 Ready");
		}
		else if (str.substring(0, 1).equals("2")) {
			planeMessageConsole.println(time + "s: MPU6050 Failed");
		}
		else if (str.substring(0, 1).equals("3")) {
			planeMessageConsole.println(time + "s: DMP Ready");
		}
		else if (str.substring(0, 1).equals("4")) {
			planeMessageConsole.println(time + "s: DMP Failed");
		}
		else if (str.substring(0, 1).equals("5")) {
			planeMessageConsole.println(time + "s: MPU6050 Initializing");
		}
	}
}
