package gov.usgs.volcanoes.swarm;

import gov.usgs.volcanoes.core.configfile.ConfigFile;
import gov.usgs.volcanoes.core.data.file.FileType;
import gov.usgs.volcanoes.core.ui.ExtensionFileFilter;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.quakeml.EventSet;
import gov.usgs.volcanoes.swarm.data.CachedDataSource;
import gov.usgs.volcanoes.swarm.data.FileDataSource;
import gov.usgs.volcanoes.swarm.internalframe.InternalFrameListener;
import gov.usgs.volcanoes.swarm.internalframe.SwarmInternalFrames;
import gov.usgs.volcanoes.swarm.map.MapFrame;
import gov.usgs.volcanoes.swarm.wave.WaveClipboardFrame;
import gov.usgs.volcanoes.swarm.statistics.SeismicityStatisticsDialog;
import gov.usgs.volcanoes.swarm.data.SdsScanner;
import gov.usgs.volcanoes.swarm.data.SdsFileResolver;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;


/**
 * Swarm Menu.
 * 
 * @author Dan Cervelli
 */
public class SwarmMenu extends JMenuBar implements InternalFrameListener {
  private static final long serialVersionUID = 1L;
  private JMenu fileMenu;
  private JMenuItem openFile;
  private JMenuItem closeFiles;
  private JMenuItem clearCache;
  private JMenuItem options;
  private JMenuItem save;
  private JMenuItem exit;

  private String lastLayoutName;
  private JMenu layoutMenu;
  private JMenuItem saveLayout;
  private JMenuItem saveLastLayout;
  private JMenuItem removeLayouts;
  protected JCheckBoxMenuItem autoLoadLayout;

  private JMenu windowMenu;
  private JMenuItem tileWaves;
  private JMenuItem tileHelicorders;
  private JMenuItem fullScreen;
  private JCheckBoxMenuItem clipboard;
  private JCheckBoxMenuItem chooser;
  private JCheckBoxMenuItem map;
  private JMenuItem mapToFront;
  private JMenuItem closeAll;

  private JMenu helpMenu;
  private JMenuItem about;

  private AboutDialog aboutDialog;

  private Map<JInternalFrame, InternalFrameMenuItem> windows;
  private Map<SwarmLayout, JMenuItem> layouts;

  /**
   * Constructor.
   */
  public SwarmMenu() {
    super();
    windows = new HashMap<JInternalFrame, InternalFrameMenuItem>();
    layouts = new HashMap<SwarmLayout, JMenuItem>();
    createFileMenu();
    createLayoutMenu();
    createWindowMenu();
	createToolsMenu();
    createHelpMenu();
    SwarmInternalFrames.addInternalFrameListener(this);
  }

	private void createFileMenu() {
	  fileMenu = new JMenu("File");
	  fileMenu.setMnemonic('F');

	  openFile = new JMenuItem("Open File...");
	  openFile.setMnemonic('O');
	  openFile.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		  SwarmConfig config = SwarmConfig.getInstance();
		  FileDataSource fds = FileDataSource.getInstance();
		  JFileChooser chooser = new JFileChooser();
		  chooser.resetChoosableFileFilters();

		  for (FileType ft : FileType.getKnownTypes()) {
			ExtensionFileFilter f =
				new ExtensionFileFilter(ft.extension, ft.description);
			chooser.addChoosableFileFilter(f);
		  }

		  chooser.setFileFilter(chooser.getAcceptAllFileFilter());
		  File lastPath = new File(SwarmConfig.getInstance().lastPath);
		  chooser.setCurrentDirectory(lastPath);
		  chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		  chooser.setMultiSelectionEnabled(true);
		  chooser.setDialogTitle("Open Wave as Data Source");

		  int result = chooser.showOpenDialog(Swarm.getApplicationFrame());

		  if (result == JFileChooser.APPROVE_OPTION) {
			File[] fs = chooser.getSelectedFiles();
			config.lastPath = fs[0].getParent();
			fds.openFiles(fs);

			if (config.getSource(fds.getName()) == null) {
			  config.addSource(fds);
			}
		  }
		}
	  });

	  openFile.setAccelerator(
		  KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
	  fileMenu.add(openFile);

	  // ===============================
	  // Open SDS Archive
	  // ===============================
	  JMenuItem openSds = new JMenuItem("Open SDS Archive...");
	  openSds.setMnemonic('S');

	  openSds.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		  JFileChooser chooser = new JFileChooser();
		  chooser.setCurrentDirectory(new File(SwarmConfig.getInstance().lastPath));
		  chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		  chooser.setMultiSelectionEnabled(false);
		  chooser.setDialogTitle("Select SDS Root Directory");

		  int result = chooser.showOpenDialog(Swarm.getApplicationFrame());

		  if (result == JFileChooser.APPROVE_OPTION) {
			File sdsRoot = chooser.getSelectedFile();
			SwarmConfig.getInstance().lastPath = sdsRoot.getAbsolutePath();

			List<SdsScanner.SdsChannel> channels =
				SdsScanner.scan(sdsRoot);

			if (channels.isEmpty()) {
			  JOptionPane.showMessageDialog(
				  Swarm.getApplicationFrame(),
				  "No SDS channels found."
			  );
			  return;
			}

			JList<SdsScanner.SdsChannel> channelList =
				new JList<SdsScanner.SdsChannel>(
					channels.toArray(new SdsScanner.SdsChannel[channels.size()])
				);

			channelList.setSelectionMode(
				ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
			);

			channelList.setVisibleRowCount(20);

			JScrollPane channelScrollPane =
				new JScrollPane(channelList);

			int channelResult = JOptionPane.showConfirmDialog(
				Swarm.getApplicationFrame(),
				channelScrollPane,
				"Select SDS Channels",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE
			);

			if (channelResult != JOptionPane.OK_OPTION) {
			  return;
			}

			List<SdsScanner.SdsChannel> selectedChannels =
				channelList.getSelectedValuesList();

			if (selectedChannels.isEmpty()) {
			  JOptionPane.showMessageDialog(
				  Swarm.getApplicationFrame(),
				  "No SDS channel selected."
			  );
			  return;
			}

			JPanel timePanel = new JPanel();

			File[] yearDirs = sdsRoot.listFiles(File::isDirectory);

			List<String> yearList = new ArrayList<String>();

			if (yearDirs != null) {

			  for (File dir : yearDirs) {

				String name = dir.getName();

				if (name.matches("\\d{4}")) {
				  yearList.add(name);
				}
			  }
			}

			Collections.sort(yearList);

			String[] years =
				yearList.toArray(new String[yearList.size()]);

			String[] months = {
				"01", "02", "03", "04", "05", "06",
				"07", "08", "09", "10", "11", "12"
			};

			String[] days = {
				"01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
				"11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
				"21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31"
			};

			String[] hours = {
				"00", "01", "02", "03", "04", "05", "06",
				"07", "08", "09", "10", "11", "12",
				"13", "14", "15", "16", "17", "18",
				"19", "20", "21", "22", "23"
			};

			JComboBox<String> startYearBox =
				new JComboBox<String>(years);

			JComboBox<String> startMonthBox =
				new JComboBox<String>(months);

			JComboBox<String> startDayBox =
				new JComboBox<String>(days);

			JComboBox<String> startHourBox =
				new JComboBox<String>(hours);

			JComboBox<String> endYearBox =
				new JComboBox<String>(years);

			JComboBox<String> endMonthBox =
				new JComboBox<String>(months);

			JComboBox<String> endDayBox =
				new JComboBox<String>(days);

			JComboBox<String> endHourBox =
				new JComboBox<String>(hours);

			LocalDateTime now = LocalDateTime.now();

			String currentYear =
				String.format("%04d", now.getYear());

			String currentMonth =
				String.format("%02d", now.getMonthValue());

			String currentDay =
				String.format("%02d", now.getDayOfMonth());

			startYearBox.setSelectedItem(currentYear);
			startMonthBox.setSelectedItem(currentMonth);
			startDayBox.setSelectedItem(currentDay);
			startHourBox.setSelectedItem("00");

			endYearBox.setSelectedItem(currentYear);
			endMonthBox.setSelectedItem(currentMonth);
			endDayBox.setSelectedItem(currentDay);
			endHourBox.setSelectedItem(
				String.format("%02d", now.getHour())
			);

			timePanel.setLayout(new GridLayout(2, 1));

			JPanel startPanel = new JPanel();

			startPanel.add(new JLabel("Start UTC:"));
			startPanel.add(startYearBox);
			startPanel.add(startMonthBox);
			startPanel.add(startDayBox);
			startPanel.add(startHourBox);
			startPanel.add(new JLabel(":00:00"));

			JPanel endPanel = new JPanel();

			endPanel.add(new JLabel("End UTC:"));
			endPanel.add(endYearBox);
			endPanel.add(endMonthBox);
			endPanel.add(endDayBox);
			endPanel.add(endHourBox);
			endPanel.add(new JLabel(":59:59"));

			timePanel.add(startPanel);
			timePanel.add(endPanel);

			int timeResult = JOptionPane.showConfirmDialog(
				Swarm.getApplicationFrame(),
				timePanel,
				"Select SDS Time Range",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE
			);

			if (timeResult != JOptionPane.OK_OPTION) {
			  return;
			}

		String startDateText =
			startYearBox.getSelectedItem().toString()
				+ "-"
				+ startMonthBox.getSelectedItem().toString()
				+ "-"
				+ startDayBox.getSelectedItem().toString()
				+ "T"
				+ startHourBox.getSelectedItem().toString()
				+ ":00:00";

		String endDateText =
			endYearBox.getSelectedItem().toString()
				+ "-"
				+ endMonthBox.getSelectedItem().toString()
				+ "-"
				+ endDayBox.getSelectedItem().toString()
				+ "T"
				+ endHourBox.getSelectedItem().toString()
				+ ":59:59";

		LocalDateTime startUtc =
			LocalDateTime.parse(startDateText);

		LocalDateTime endUtc =
			LocalDateTime.parse(endDateText);

		if (endUtc.isBefore(startUtc)) {

		  JOptionPane.showMessageDialog(
			  Swarm.getApplicationFrame(),
			  "End time must be after start time.",
			  "Invalid SDS Time Range",
			  JOptionPane.ERROR_MESSAGE
		  );

		  return;
		}


		try {
			  SdsFileResolver resolver =
				  new SdsFileResolver(sdsRoot);

			  List<File> files = new ArrayList<File>();

			  for (SdsScanner.SdsChannel selectedChannel : selectedChannels) {

				List<File> channelFiles = resolver.resolve(
					selectedChannel.network,
					selectedChannel.station,
					selectedChannel.location,
					selectedChannel.channel,
					startUtc,
					endUtc
				);

				files.addAll(channelFiles);
			  }

			  if (files.isEmpty()) {
				JOptionPane.showMessageDialog(
					Swarm.getApplicationFrame(),
					"No SDS files found for selected time."
				);
				return;
			  }

			  FileDataSource fds = FileDataSource.getInstance();

			  File[] fileArray = files.toArray(new File[files.size()]);
			  fds.openFiles(fileArray);

			  SwarmConfig config = SwarmConfig.getInstance();

			  if (config.getSource(fds.getName()) == null) {
				config.addSource(fds);
			  }

			  JOptionPane.showMessageDialog(
				  Swarm.getApplicationFrame(),
				  "Selected SDS channels: " + selectedChannels.size()
					  + "\nOpened SDS files: " + files.size()
			  );

			} catch (Exception ex) {
			  JOptionPane.showMessageDialog(
				  Swarm.getApplicationFrame(),
				  ex.getMessage(),
				  "SDS Open Error",
				  JOptionPane.ERROR_MESSAGE
			  );
			  ex.printStackTrace();
			}
		  }
		}
	  });

	  fileMenu.add(openSds);


    closeFiles = new JMenuItem("Close Files");
    closeFiles.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileDataSource.getInstance().flush();
      }
    });
    closeFiles.setMnemonic('l');
    fileMenu.add(closeFiles);

    clearCache = new JMenuItem("Clear Cache");
    clearCache.setMnemonic('C');
    clearCache.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        CachedDataSource cache = CachedDataSource.getInstance();
        if (cache != null) {
          cache.flush();
        }
      }
    });
    clearCache.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F12, KeyEvent.CTRL_DOWN_MASK));
    fileMenu.add(clearCache);

    fileMenu.addSeparator();

    JMenuItem importEvent = new JMenuItem("Import Events...");
    importEvent.setMnemonic('I');
    importEvent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("QuakeML (.xml)", "xml"));
        chooser.setCurrentDirectory(new File(SwarmConfig.getInstance().lastPath));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Open QuakeML Event");
        int result = chooser.showOpenDialog(Swarm.getApplicationFrame());
        if (result == JFileChooser.APPROVE_OPTION) {
          File[] fs = chooser.getSelectedFiles();
          for (File f : fs) {
            try {
              FileInputStream fis = new FileInputStream(f);
              SwarmConfig.getInstance().lastPath = f.getParent();
              EventSet events = EventSet.parseQuakeml(fis);
              MapFrame.getInstance().getHypocenterLayer().add(events);
            } catch (FileNotFoundException e1) {
              JOptionPane.showMessageDialog(null, e1.getMessage());
            } catch (IOException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            } catch (ParserConfigurationException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            } catch (SAXException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            }

          }
        }
      }
    });
    importEvent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK));
    fileMenu.add(importEvent);

    fileMenu.addSeparator();

    options = new JMenuItem("Options...");
    options.setMnemonic('O');
    options.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        OptionsDialog od = new OptionsDialog();
        od.setVisible(true);
      }
    });
    fileMenu.add(options);

    if (SwarmConfig.getInstance().saveConfig) {
      save = new JMenuItem("Save Configuration");
      save.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          SwarmConfig config = SwarmConfig.getInstance();
          final ConfigFile configFile = config.toConfigFile();
          configFile.remove("configFile");
          configFile.writeToFile(config.configFilename);
        }
      });
      fileMenu.add(save);
    }

    fileMenu.addSeparator();

    exit = new JMenuItem("Exit");
    exit.setMnemonic('x');
    exit.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Swarm.getInstance().closeApp();
      }
    });
    fileMenu.add(exit);
    add(fileMenu);

    fileMenu.addMenuListener(new MenuListener() {
      public void menuSelected(MenuEvent e) {
        CachedDataSource cache = CachedDataSource.getInstance();
        clearCache.setEnabled(!cache.isEmpty());
      }

      public void menuDeselected(MenuEvent e) {}

      public void menuCanceled(MenuEvent e) {}
    });
  }

  private void createLayoutMenu() {
    layoutMenu = new JMenu("Layout");
    layoutMenu.setMnemonic('L');

    saveLayout = new JMenuItem("Save Layout...");
    saveLayout.setMnemonic('S');
    saveLayout.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Swarm.getInstance().saveLayout(null);
      }
    });
    saveLayout.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
    layoutMenu.add(saveLayout);

    saveLastLayout = new JMenuItem("Overwrite Last Layout...");
    saveLastLayout.setMnemonic('L');
    saveLastLayout.setEnabled(false);
    saveLastLayout.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (lastLayoutName != null) {
          Swarm.getInstance().saveLayout(lastLayoutName);
        }
      }
    });
    saveLastLayout.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
    layoutMenu.add(saveLastLayout);

    removeLayouts = new JMenuItem("Remove Layout...");
    removeLayouts.setMnemonic('R');
    removeLayouts.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        RemoveLayoutDialog d = new RemoveLayoutDialog();
        d.setVisible(true);
      }
    });
    layoutMenu.add(removeLayouts);

    autoLoadLayout = new JCheckBoxMenuItem("Auto-load Layout");
    autoLoadLayout.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        SwarmConfig.getInstance().loadLayout = autoLoadLayout.isSelected();
      }
    });
    autoLoadLayout.setToolTipText(
        "Enable if you would like to open this layout automatically next time Swarm is started.");
    layoutMenu.add(autoLoadLayout);

    layoutMenu.addSeparator();
    add(layoutMenu);
  }

  public String getLastLayoutName() {
    return lastLayoutName;
  }

  /**
   * Set last layout name.
   * 
   * @param ln layout name
   */
  public void setLastLayoutName(String ln) {
    lastLayoutName = ln;
    saveLastLayout.setEnabled(true);
    saveLastLayout.setText("Overwrite Last Layout (" + ln + ")");
  }

  /**
   * All layout.
   * 
   * @param sl swarm layout
   */
  public void addLayout(final SwarmLayout sl) {
    JMenuItem mi = new JMenuItem(sl.getName());
    mi.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        sl.process();
        setLastLayoutName(sl.getName());
        SwarmConfig.getInstance().layout = sl.getName();
      }
    });
    int i;
    for (i = 5; i < layoutMenu.getItemCount(); i++) {
      JMenuItem m = layoutMenu.getItem(i);
      if (m.getText().compareToIgnoreCase(sl.getName()) >= 0) {
        layoutMenu.add(mi, i);
        break;
      }
    }
    if (i == layoutMenu.getItemCount()) {
      layoutMenu.add(mi, i);
    }
    layouts.put(sl, mi);
  }

  /**
   * Remove layout.
   * 
   * @param sl swarm layout
   */
  public void removeLayout(SwarmLayout sl) {
    JMenuItem mi = layouts.get(sl);
    layoutMenu.remove(mi);
    layouts.remove(sl);
  }

  private void createWindowMenu() {
    windowMenu = new JMenu("Window");
    windowMenu.setMnemonic('W');

    chooser = new JCheckBoxMenuItem("Data Chooser");
    chooser.setMnemonic('D');
    chooser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Swarm.getInstance().setChooserVisible(!Swarm.getInstance().isChooserVisible());
      }
    });
    chooser.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK));
    windowMenu.add(chooser);

    clipboard = new JCheckBoxMenuItem("Wave Clipboard");
    clipboard.setMnemonic('W');
    clipboard.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        WaveClipboardFrame waveClipboard = WaveClipboardFrame.getInstance();
        waveClipboard.setVisible(!waveClipboard.isVisible());
      }
    });
    clipboard.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK));
    windowMenu.add(clipboard);

    final MapFrame mapFrame = MapFrame.getInstance();
    map = new JCheckBoxMenuItem("Map");
    map.setMnemonic('M');
    map.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mapFrame.setVisible(!mapFrame.isVisible());
      }
    });
    map.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
    windowMenu.add(map);

    mapToFront = new JMenuItem("Bring Map to Front");
    mapToFront.setMnemonic('F');
    mapToFront.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mapFrame.setVisible(true);
      }
    });
    mapToFront.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0));
    windowMenu.add(mapToFront);
    windowMenu.addSeparator();

    tileHelicorders = new JMenuItem("Tile Helicorders");
    tileHelicorders.setMnemonic('H');
    tileHelicorders.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Swarm.getInstance().tileHelicorders();
      }
    });
    windowMenu.add(tileHelicorders);

    tileWaves = new JMenuItem("Tile Waves");
    tileWaves.setMnemonic('v');
    tileWaves.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Swarm.getInstance().tileWaves();
      }
    });
    windowMenu.add(tileWaves);

    windowMenu.addSeparator();

    fullScreen = new JMenuItem("Kiosk Mode");
    fullScreen.setMnemonic('K');
    fullScreen.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Swarm.getInstance().toggleFullScreenMode();
      }
    });
    fullScreen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
    windowMenu.add(fullScreen);

    windowMenu.addMenuListener(new MenuListener() {
      public void menuSelected(MenuEvent e) {
        clipboard.setSelected(WaveClipboardFrame.getInstance().isVisible());
        chooser.setSelected(Swarm.getInstance().isChooserVisible());
        map.setSelected(MapFrame.getInstance().isVisible());
      }

      public void menuDeselected(MenuEvent e) {}

      public void menuCanceled(MenuEvent e) {}
    });

    windowMenu.addSeparator();

    closeAll = new JMenuItem("Close All");
    closeAll.setMnemonic('C');
    closeAll.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        SwarmInternalFrames.removeAllFrames();
      }
    });
    windowMenu.add(closeAll);

    add(windowMenu);
  }

	private void createToolsMenu() {

		JMenu toolsMenu = new JMenu("Tools");

		JMenuItem seismicityStatsItem =
			new JMenuItem("Seismicity Statistics");

		seismicityStatsItem.addActionListener(e -> {

			SeismicityStatisticsDialog dialog =
				new SeismicityStatisticsDialog(null);

			dialog.setVisible(true);
		});

		toolsMenu.add(seismicityStatsItem);

		add(toolsMenu);
	}


  private void createHelpMenu() {
    helpMenu = new JMenu("Help");
    helpMenu.setMnemonic('H');
    about = new JMenuItem("About...");
    about.setMnemonic('A');
    aboutDialog = new AboutDialog();
    about.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        aboutDialog.update();
        aboutDialog.setVisible(true);
      }
    });

    helpMenu.add(about);
    add(helpMenu);
  }

  private class InternalFrameMenuItem extends JMenuItem {
    private static final long serialVersionUID = 1L;
    private JInternalFrame frame;

    public InternalFrameMenuItem(JInternalFrame f) {
      frame = f;
      setText(f.getTitle());
      setIcon(f.getFrameIcon());
      addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            if (frame.isIcon()) {
              frame.setIcon(false);
            }
            frame.toFront();
            frame.setSelected(true);
          } catch (Exception ex) {
            // do nothing
          }
        }
      });
    }
  }

  private class RemoveLayoutDialog extends SwarmModalDialog {
    private static final long serialVersionUID = 1L;
    private JList<String> layoutList;
    private DefaultListModel<String> model;

    protected RemoveLayoutDialog() {
      super(Swarm.getApplicationFrame(), "Remove Layouts");
      setSizeAndLocation();
    }

    protected void createUi() {
      super.createUi();
      Set<String> keys = swarmConfig.layouts.keySet();
      List<String> sls = new ArrayList<String>();
      sls.addAll(keys);
      Collections.sort(sls, StringUtils.getCaseInsensitiveStringComparator());
      model = new DefaultListModel<String>();
      for (String sl : sls) {
        model.addElement(sl);
      }
      layoutList = new JList<String>(model);
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
      int h = Math.max(200, Math.min(350, sls.size() * 19));
      panel.setPreferredSize(new Dimension(200, h));
      panel.add(new JLabel("Select layouts to remove:"), BorderLayout.NORTH);
      panel.add(new JScrollPane(layoutList), BorderLayout.CENTER);
      mainPanel.add(panel, BorderLayout.CENTER);
    }

    public void wasOk() {
      Object[] toRemove = layoutList.getSelectedValuesList().toArray();

      for (Object key : toRemove) {
        SwarmLayout layout = swarmConfig.layouts.get((String) key);
        if (layout != null) {
          JMenuItem mi = layouts.get(layout);
          layoutMenu.remove(mi);
          swarmConfig.removeLayout(layout);
        }
      }
    }
  }

  /**
   * Call when internal frame added.
   * 
   * @see gov.usgs.volcanoes.swarm.internalframe.InternalFrameListener
   *      #internalFrameAdded(javax.swing.JInternalFrame)
   */
  public void internalFrameAdded(JInternalFrame f) {
    InternalFrameMenuItem mi = new InternalFrameMenuItem(f);
    windows.put(f, mi);
    windowMenu.add(mi);
  }

  /**
   * Call when internal frame removed.
   * 
   * @see gov.usgs.volcanoes.swarm.internalframe.InternalFrameListener
   *      #internalFrameRemoved(javax.swing.JInternalFrame)
   */
  public void internalFrameRemoved(JInternalFrame f) {
    InternalFrameMenuItem mi = windows.get(f);
    windows.remove(f);
    windowMenu.remove(mi);
  }
}
