package gov.usgs.volcanoes.swarm.statistics;

import java.awt.*;
import java.awt.GridLayout;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class SeismicityStatisticsDialog extends JDialog {

    private JTextField catalogRootField;
	private JComboBox<String> startYearBox;
	private JComboBox<String> startMonthBox;
	private JComboBox<String> startDayBox;
	private JComboBox<String> startHourBox;
	private JComboBox<String> startMinuteBox;

	private JComboBox<String> endYearBox;
	private JComboBox<String> endMonthBox;
	private JComboBox<String> endDayBox;
	private JComboBox<String> endHourBox;
	private JComboBox<String> endMinuteBox;
	
	private JLabel statusLabel;
	
	private JComboBox<String> timezoneCombo;

    private JComboBox<String> stationCombo;
    private JComboBox<String> typeCombo;

    private JTable resultTable;
    private DefaultTableModel tableModel;

	private static final DateTimeFormatter DISPLAY_TIME_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private List<CatalogEvent> catalogEvents;

    public SeismicityStatisticsDialog(Frame parent) {

        super(parent, "Seismicity Statistics", true);

        setSize(950, 600);
        setLocationRelativeTo(parent);

        initUI();
    }

	private void setDateTimeBoxes(
			JComboBox<String> yearBox,
			JComboBox<String> monthBox,
			JComboBox<String> dayBox,
			JComboBox<String> hourBox,
			JComboBox<String> minuteBox,
			LocalDateTime time) {

		if (time == null) {
			return;
		}

		yearBox.setSelectedItem(String.format("%04d", time.getYear()));
		monthBox.setSelectedItem(String.format("%02d", time.getMonthValue()));
		dayBox.setSelectedItem(String.format("%02d", time.getDayOfMonth()));
		hourBox.setSelectedItem(String.format("%02d", time.getHour()));
		minuteBox.setSelectedItem(String.format("%02d", time.getMinute()));
	}


	private void initUI() {

		JPanel mainPanel = new JPanel(new BorderLayout());

		JPanel filterPanel = new JPanel(new GridBagLayout());

		GridBagConstraints gbc = new GridBagConstraints();

		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;

		// Catalog root
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 1;
		filterPanel.add(new JLabel("Catalog Root:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 1;
		gbc.weightx = 1.0;
		catalogRootField = new JTextField();
		filterPanel.add(catalogRootField, gbc);

		gbc.gridx = 2;
		gbc.weightx = 0.0;
		JButton browseButton = new JButton("Browse");
		filterPanel.add(browseButton, gbc);

		// Explanation catalog root
		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.gridwidth = 2;

		JLabel catalogHintLabel = new JLabel(
			"Example: D:\\SwarmTagger\\Catalog"
		);

		catalogHintLabel.setForeground(Color.GRAY);
		filterPanel.add(catalogHintLabel, gbc);

		// Initialize combo boxes
		startYearBox = new JComboBox<String>();
		startMonthBox = new JComboBox<String>();
		startDayBox = new JComboBox<String>();
		startHourBox = new JComboBox<String>();
		startMinuteBox = new JComboBox<String>();

		endYearBox = new JComboBox<String>();
		endMonthBox = new JComboBox<String>();
		endDayBox = new JComboBox<String>();
		endHourBox = new JComboBox<String>();
		endMinuteBox = new JComboBox<String>();

		for (int y = 2020; y <= 2035; y++) {
			startYearBox.addItem(String.valueOf(y));
			endYearBox.addItem(String.valueOf(y));
		}

		for (int m = 1; m <= 12; m++) {
			startMonthBox.addItem(String.format("%02d", m));
			endMonthBox.addItem(String.format("%02d", m));
		}

		for (int d = 1; d <= 31; d++) {
			startDayBox.addItem(String.format("%02d", d));
			endDayBox.addItem(String.format("%02d", d));
		}

		for (int h = 0; h <= 23; h++) {
			startHourBox.addItem(String.format("%02d", h));
			endHourBox.addItem(String.format("%02d", h));
		}

		for (int m = 0; m <= 59; m++) {
			startMinuteBox.addItem(String.format("%02d", m));
			endMinuteBox.addItem(String.format("%02d", m));
		}

		startYearBox.setSelectedItem("2026");
		startMonthBox.setSelectedItem("05");
		startDayBox.setSelectedItem("01");
		startHourBox.setSelectedItem("00");
		startMinuteBox.setSelectedItem("00");

		endYearBox.setSelectedItem("2026");
		endMonthBox.setSelectedItem("05");
		endDayBox.setSelectedItem("01");
		endHourBox.setSelectedItem("23");
		endMinuteBox.setSelectedItem("59");

		// Start Time
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 1;
		gbc.weightx = 0.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(new JLabel("Start:"), gbc);

		JPanel startPanel = new JPanel(new GridBagLayout());

		GridBagConstraints st = new GridBagConstraints();
		st.insets = new Insets(2, 2, 2, 2);
		st.fill = GridBagConstraints.NONE;
		st.anchor = GridBagConstraints.WEST;

		st.gridx = 0;
		st.gridy = 0;
		startPanel.add(new JLabel("Date"), st);

		st.gridx = 1;
		startPanel.add(startYearBox, st);

		st.gridx = 2;
		startPanel.add(new JLabel("-"), st);

		st.gridx = 3;
		startPanel.add(startMonthBox, st);

		st.gridx = 4;
		startPanel.add(new JLabel("-"), st);

		st.gridx = 5;
		startPanel.add(startDayBox, st);

		st.gridx = 0;
		st.gridy = 1;
		startPanel.add(new JLabel("Time"), st);

		st.gridx = 1;
		startPanel.add(startHourBox, st);

		st.gridx = 2;
		startPanel.add(new JLabel(":"), st);

		st.gridx = 3;
		startPanel.add(startMinuteBox, st);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(startPanel, gbc);

		// End Time
		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.gridwidth = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(new JLabel("End:"), gbc);

		JPanel endPanel = new JPanel(new GridBagLayout());

		GridBagConstraints et = new GridBagConstraints();
		et.insets = new Insets(2, 2, 2, 2);
		et.fill = GridBagConstraints.NONE;
		et.anchor = GridBagConstraints.WEST;

		et.gridx = 0;
		et.gridy = 0;
		endPanel.add(new JLabel("Date"), et);

		et.gridx = 1;
		endPanel.add(endYearBox, et);

		et.gridx = 2;
		endPanel.add(new JLabel("-"), et);

		et.gridx = 3;
		endPanel.add(endMonthBox, et);

		et.gridx = 4;
		endPanel.add(new JLabel("-"), et);

		et.gridx = 5;
		endPanel.add(endDayBox, et);

		et.gridx = 0;
		et.gridy = 1;
		endPanel.add(new JLabel("Time"), et);

		et.gridx = 1;
		endPanel.add(endHourBox, et);

		et.gridx = 2;
		endPanel.add(new JLabel(":"), et);

		et.gridx = 3;
		endPanel.add(endMinuteBox, et);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(endPanel, gbc);

		// TimeZone
		gbc.gridx = 0;
		gbc.gridy = 4;
		gbc.gridwidth = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(new JLabel("Time Zone:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;

		timezoneCombo = new JComboBox<String>();
		timezoneCombo.addItem("WIB - UTC+7");
		timezoneCombo.addItem("WITA - UTC+8");
		timezoneCombo.addItem("WIT - UTC+9");

		timezoneCombo.addActionListener(e -> updateTimeRangeFromCatalog());
		filterPanel.add(timezoneCombo, gbc);

		// Button Load Catalog
		gbc.gridx = 1;
		gbc.gridy = 5;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;

		JButton loadButton = new JButton("Load Catalog");
		filterPanel.add(loadButton, gbc);

		// Station
		gbc.gridx = 0;
		gbc.gridy = 6;
		gbc.gridwidth = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(new JLabel("Station:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;

		stationCombo = new JComboBox<String>();
		stationCombo.addItem("ALL");
		filterPanel.add(stationCombo, gbc);

		// Event type
		gbc.gridx = 0;
		gbc.gridy = 7;
		gbc.gridwidth = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(new JLabel("Event Type:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.WEST;

		typeCombo = new JComboBox<String>();
		typeCombo.addItem("ALL");
		filterPanel.add(typeCombo, gbc);

		// Status label
		statusLabel = new JLabel("Select time range, then click Load Catalog.");
		statusLabel.setForeground(Color.GRAY);

		gbc.gridx = 1;
		gbc.gridy = 8;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		filterPanel.add(statusLabel, gbc);

		// Buttons
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		JButton generateButton = new JButton("Generate");
		JButton closeButton = new JButton("Close");

		buttonPanel.add(generateButton);
		buttonPanel.add(closeButton);

		// Table
		String[] columns = {
			"Event Type",
			"Count",
			"Amp Min Count",
			"Amp Max Count",
			"Amp Min um/s",
			"Amp Max um/s",
			"Duration Min",
			"Duration Max"
		};

		tableModel = new DefaultTableModel(columns, 0);

		resultTable = new JTable(tableModel);
		resultTable.setAutoCreateRowSorter(true);

		JScrollPane scrollPane = new JScrollPane(resultTable);

		JPanel topPanel = new JPanel(new BorderLayout());

		topPanel.add(filterPanel, BorderLayout.CENTER);
		topPanel.add(buttonPanel, BorderLayout.SOUTH);

		mainPanel.add(topPanel, BorderLayout.NORTH);
		mainPanel.add(scrollPane, BorderLayout.CENTER);

		setContentPane(mainPanel);

		browseButton.addActionListener(e -> browseCatalogRoot());

		loadButton.addActionListener(e -> loadCatalog());

		generateButton.addActionListener(e -> generateStatistics());

		closeButton.addActionListener(e -> dispose());
	}

	private void browseCatalogRoot() {

		JFileChooser chooser = new JFileChooser();

		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select SwarmTagger Catalog Root");

		int result = chooser.showOpenDialog(this);

		if (result == JFileChooser.APPROVE_OPTION) {
			File dir = chooser.getSelectedFile();
			catalogRootField.setText(dir.getAbsolutePath());
			statusLabel.setText("Select time range, then click Load Catalog.");
			statusLabel.setForeground(Color.GRAY);
		}
	}

	private boolean isValidCatalogRoot(File dir) {

		if (dir == null || !dir.isDirectory()) {
			return false;
		}

		File[] years = dir.listFiles();

		if (years == null) {
			return false;
		}

		for (File y : years) {

			if (!y.isDirectory()) {
				continue;
			}

			if (y.getName().matches("\\d{4}")) {
				return true;
			}
		}

		return false;
	}


	private void loadCatalog() {

		try {

			File catalogRoot = new File(catalogRootField.getText());

			if (!catalogRoot.exists() || !catalogRoot.isDirectory()) {
				JOptionPane.showMessageDialog(
					this,
					"Catalog root folder not found.",
					"Error",
					JOptionPane.ERROR_MESSAGE
				);
				return;
			}

			String startDateText =
				startYearBox.getSelectedItem()
				+ "-"
				+ startMonthBox.getSelectedItem()
				+ "-"
				+ startDayBox.getSelectedItem()
				+ " "
				+ startHourBox.getSelectedItem()
				+ ":"
				+ startMinuteBox.getSelectedItem()
				+ ":00";

			String endDateText =
				endYearBox.getSelectedItem()
				+ "-"
				+ endMonthBox.getSelectedItem()
				+ "-"
				+ endDayBox.getSelectedItem()
				+ " "
				+ endHourBox.getSelectedItem()
				+ ":"
				+ endMinuteBox.getSelectedItem()
				+ ":59";

			LocalDateTime startLocal =
				CatalogStatisticsEngine.parseDateTime(startDateText);

			LocalDateTime endLocal =
				CatalogStatisticsEngine.parseDateTime(endDateText);

			if (endLocal.isBefore(startLocal)) {
				JOptionPane.showMessageDialog(
					this,
					"End time must be after start time.",
					"Invalid Time Range",
					JOptionPane.ERROR_MESSAGE
				);
				return;
			}
			
			String zoneLabel =
				(String) timezoneCombo.getSelectedItem();

			LocalDateTime startUtc =
				CatalogStatisticsEngine.localToUtc(startLocal, zoneLabel);

			LocalDateTime endUtc =
				CatalogStatisticsEngine.localToUtc(endLocal, zoneLabel);

			catalogEvents =
				CatalogStatisticsEngine.readCatalogRange(
					catalogRoot,
					startUtc,
					endUtc
				);

			File appDir = new File(System.getProperty("user.dir"));

			File metadataFile = new File(
				appDir,
				"SwarmMetadata.config"
			);

			System.out.println(
				"Swarm app dir = " + appDir.getAbsolutePath()
			);

			System.out.println(
				"Metadata file = " + metadataFile.getAbsolutePath()
			);

			System.out.println(
				"Metadata exists = " + metadataFile.exists()
			);

			Map<String, Double> multipliers =
				MetadataMultiplierReader.readMultipliers(metadataFile);
				
			System.out.println(
				"Multiplier count = " + multipliers.size()
			);

			for (CatalogEvent event : catalogEvents) {

				double multiplier =
					MetadataMultiplierReader.getMultiplier(
						multipliers,
						event.channel
					);

				if (multiplier == 1.0) {

					multiplier =
						MetadataMultiplierReader.getMultiplier(
							multipliers,
							event.station
						);
				}

				if (!Double.isNaN(event.amplitude)) {

					event.amplitudeUmPerSec =
						event.amplitude * multiplier;
				}

				System.out.println(
					"channel=" + event.channel
					+ ", station=" + event.station
					+ ", amp=" + event.amplitude
					+ ", multiplier=" + multiplier
					+ ", amp_um=" + event.amplitudeUmPerSec
				);
			}

			stationCombo.removeAllItems();
			stationCombo.addItem("ALL");

			for (String sta : CatalogStatisticsEngine.getStations(catalogEvents)) {
				stationCombo.addItem(sta);
			}

			typeCombo.removeAllItems();
			typeCombo.addItem("ALL");

			for (String type : CatalogStatisticsEngine.getEventTypes(catalogEvents)) {
				typeCombo.addItem(type);
			}

			if (catalogEvents.isEmpty()) {

				statusLabel.setText(
					"No catalog events found. Check time range or catalog folder."
				);
				statusLabel.setForeground(Color.RED);

			} else {

				statusLabel.setText(
					"Loaded " + catalogEvents.size()
					+ " events from selected time range."
				);
				statusLabel.setForeground(new Color(0, 128, 0));
			}

		} catch (Exception ex) {

			JOptionPane.showMessageDialog(
				this,
				"Failed to load catalog:\n" + ex.getMessage(),
				"Error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void updateTimeRangeFromCatalog() {

		if (catalogEvents == null || catalogEvents.isEmpty()) {
			return;
		}

		LocalDateTime minUtc = null;
		LocalDateTime maxUtc = null;

		for (CatalogEvent event : catalogEvents) {

			if (event.time == null) {
				continue;
			}

			if (minUtc == null || event.time.isBefore(minUtc)) {
				minUtc = event.time;
			}

			if (maxUtc == null || event.time.isAfter(maxUtc)) {
				maxUtc = event.time;
			}
		}

		if (minUtc == null || maxUtc == null) {
			return;
		}

		String zoneLabel =
			(String) timezoneCombo.getSelectedItem();

		LocalDateTime minLocal =
			CatalogStatisticsEngine.utcToLocal(minUtc, zoneLabel);

		LocalDateTime maxLocal =
			CatalogStatisticsEngine.utcToLocal(maxUtc, zoneLabel);

		setDateTimeBoxes(startYearBox, startMonthBox, startDayBox,
			startHourBox, startMinuteBox, minLocal);

		setDateTimeBoxes(endYearBox, endMonthBox, endDayBox,
			endHourBox, endMinuteBox, maxLocal);
		}


	private void generateStatistics() {

		try {

			if (catalogEvents == null || catalogEvents.isEmpty()) {
				loadCatalog();
			}

			if (catalogEvents == null || catalogEvents.isEmpty()) {
				return;
			}

			String startDateText =
				startYearBox.getSelectedItem()
				+ "-"
				+ startMonthBox.getSelectedItem()
				+ "-"
				+ startDayBox.getSelectedItem()
				+ " "
				+ startHourBox.getSelectedItem()
				+ ":"
				+ startMinuteBox.getSelectedItem()
				+ ":00";

			String endDateText =
				endYearBox.getSelectedItem()
				+ "-"
				+ endMonthBox.getSelectedItem()
				+ "-"
				+ endDayBox.getSelectedItem()
				+ " "
				+ endHourBox.getSelectedItem()
				+ ":"
				+ endMinuteBox.getSelectedItem()
				+ ":59";

			LocalDateTime startLocal =
				CatalogStatisticsEngine.parseDateTime(startDateText);

			LocalDateTime endLocal =
				CatalogStatisticsEngine.parseDateTime(endDateText);

			if (endLocal.isBefore(startLocal)) {
				JOptionPane.showMessageDialog(
					this,
					"End time must be after start time.",
					"Invalid Time Range",
					JOptionPane.ERROR_MESSAGE
				);
				return;
			}

			String zoneLabel =
				(String) timezoneCombo.getSelectedItem();

			LocalDateTime startUtc =
				CatalogStatisticsEngine.localToUtc(startLocal, zoneLabel);

			LocalDateTime endUtc =
				CatalogStatisticsEngine.localToUtc(endLocal, zoneLabel);

			String station =
				(String) stationCombo.getSelectedItem();

			String type =
				(String) typeCombo.getSelectedItem();

			Map<String, EventStatistics> stats =
				CatalogStatisticsEngine.calculate(
					catalogEvents,
					startUtc,
					endUtc,
					station,
					type
				);

			tableModel.setRowCount(0);

			for (EventStatistics stat : stats.values()) {
				tableModel.addRow(stat.toTableRow());
			}

			if (stats.isEmpty()) {
				JOptionPane.showMessageDialog(
					this,
					"No events found in selected period/filter.",
					"No Data",
					JOptionPane.INFORMATION_MESSAGE
				);
			}

		} catch (Exception ex) {

			JOptionPane.showMessageDialog(
				this,
				"Failed to generate statistics:\n" + ex.getMessage(),
				"Error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}
}