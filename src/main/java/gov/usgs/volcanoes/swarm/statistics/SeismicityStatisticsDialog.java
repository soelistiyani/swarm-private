package gov.usgs.volcanoes.swarm.statistics;

import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class SeismicityStatisticsDialog extends JDialog {

    private JTextField catalogRootField;
    private JTextField startField;
    private JTextField endField;
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

    private void initUI() {

        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel filterPanel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

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

		// Start time
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 1;
		filterPanel.add(new JLabel("Start Time:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		startField = new JTextField("2026-05-01 00:00:00");
		filterPanel.add(startField, gbc);

		// End time
		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.gridwidth = 1;
		filterPanel.add(new JLabel("End Time:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		endField = new JTextField("2026-05-01 23:59:59");
		filterPanel.add(endField, gbc);

		// Button Load Catalog
		gbc.gridx = 1;
		gbc.gridy = 4;
		gbc.gridwidth = 2;

		JButton loadButton = new JButton("Load Catalog");
		filterPanel.add(loadButton, gbc);

		// TimeZone
		gbc.gridx = 0;
		gbc.gridy = 5;
		gbc.gridwidth = 1;
		filterPanel.add(new JLabel("Time Zone:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;

		timezoneCombo = new JComboBox<String>();
		timezoneCombo.addItem("WIB - UTC+7");
		timezoneCombo.addItem("WITA - UTC+8");
		timezoneCombo.addItem("WIT - UTC+9");

		timezoneCombo.addActionListener(e -> updateTimeRangeFromCatalog());
		filterPanel.add(timezoneCombo, gbc);

		// Station
		gbc.gridx = 0;
		gbc.gridy = 6;
		gbc.gridwidth = 1;
		filterPanel.add(new JLabel("Station:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		stationCombo = new JComboBox<String>();
		stationCombo.addItem("ALL");
		filterPanel.add(stationCombo, gbc);

		// Event type
		gbc.gridx = 0;
		gbc.gridy = 7;
		gbc.gridwidth = 1;
		filterPanel.add(new JLabel("Event Type:"), gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 2;
		typeCombo = new JComboBox<String>();
		typeCombo.addItem("ALL");
		filterPanel.add(typeCombo, gbc);

		// Status label
		statusLabel = new JLabel("Select time range, then click Load Catalog.");
		statusLabel.setForeground(Color.GRAY);

		gbc.gridx = 1;
		gbc.gridy = 8;
		gbc.gridwidth = 2;
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
            "Amp Min",
            "Amp Max",
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

			LocalDateTime startLocal =
				CatalogStatisticsEngine.parseDateTime(startField.getText());

			LocalDateTime endLocal =
				CatalogStatisticsEngine.parseDateTime(endField.getText());

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

		startField.setText(DISPLAY_TIME_FORMAT.format(minLocal));
		endField.setText(DISPLAY_TIME_FORMAT.format(maxLocal));
	}


    private void generateStatistics() {

        try {

            if (catalogEvents == null || catalogEvents.isEmpty()) {
                loadCatalog();
            }

            if (catalogEvents == null || catalogEvents.isEmpty()) {
                return;
            }

			LocalDateTime startLocal =
				CatalogStatisticsEngine.parseDateTime(startField.getText());

			LocalDateTime endLocal =
				CatalogStatisticsEngine.parseDateTime(endField.getText());

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