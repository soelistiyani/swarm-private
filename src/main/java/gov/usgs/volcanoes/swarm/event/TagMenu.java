package gov.usgs.volcanoes.swarm.event;

import gov.usgs.volcanoes.swarm.Swarm;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import gov.usgs.volcanoes.swarm.heli.HelicorderViewPanel;
import gov.usgs.volcanoes.swarm.wave.WaveViewPanel;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import gov.usgs.volcanoes.core.time.J2kSec;


/**
 * Right-click menu for helicorder event tags and phase picks.
 */
public class TagMenu extends JPopupMenu {

    private static final String settingsFileName = "EventClassifications.config";
	private static final String COUPLED_NOTE = "Coupled Events";
	private static final String CP1 = "CP1";
	private static final String CP2 = "CP2";
	private static final String CP3 = "CP3";
	private static final String CEND = "CEND";
    private static final long serialVersionUID = 8681764007165352268L;

    protected static final int transparency = 240;
    protected static final int durationTransparency = 60;
    protected static final Color defaultColor = new Color(255, 69, 0, transparency);

    protected static HashMap<String, Color> colors = new HashMap<String, Color>();

    private String eventFileName;
    protected static String[] classifications;

	private static final String TAGGER_DIR_CONFIG = "TaggerDirectory.config";

	private File taggerRootDir;
	private String currentEventId = "";

	private String activeYmd = "";
	private String activeFileYmd = "";
	private String loadedEventFileName = "";
	private String activeFileOperator = "";
	
	private boolean openedExistingFile = false;
	
    private static String[][] defaultClassifications = {
        { "VT", "#fc7703" },
        { "VT - distal", "#fc7703" },
        { "VT - proximal", "#fc7703" },
        { "LP", "#6b4934" },
        { "VLP", "#c79fc6" },
        { "Hybrid", "#84e087" },
        { "Explosion", "#ab2f2b" },
        { "Tremor", "#dbd976" },
        { "Lahar", "#5481a8" },
        { "Pyroclastic Flow", "#e33bdf" },
        { "Regional", "#6f5796" },
        { "Rock Fall", "#857876" },
        { "Teleseism", "#965771" },
        { "Ice quake", "#76d6db" },
        { "Noise", "#57754d" },
        { "Cultural", "#57754d" },
        { "Unclassified", "#57754d" }
    };

    static {
        try {
            ArrayList<String> list = new ArrayList<String>();

            FileReader fr = new FileReader(settingsFileName);
            BufferedReader br = new BufferedReader(fr);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().length() == 0 || line.trim().startsWith("#")) {
                    continue;
                }

                String[] tmp = line.trim().split(",");
                String classification = tmp[0].trim();
                list.add(classification);

                Color color = defaultColor;
                if (tmp.length > 1) {
                    color = Color.decode(tmp[1].trim());
                }

                colors.put(classification, color);
            }

            br.close();
            fr.close();

            classifications = new String[list.size()];
            list.toArray(classifications);

        } catch (FileNotFoundException e) {
            System.out.println("EventClassifications.config not found. Using default classifications.");

            classifications = new String[defaultClassifications.length];

            for (int i = 0; i < classifications.length; i++) {
                classifications[i] = defaultClassifications[i][0];
                colors.put(defaultClassifications[i][0], Color.decode(defaultClassifications[i][1]));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private HelicorderViewPanel hvp;
    private double j2k;

    private String operatorName = "";
    private String lastNote = "";
    private String lastMmi = "";
    private String lastAudible = "";
    private String lastVisible = "";
	private static final String[] MMI_CHOICES = {
			"I",
			"II",
			"III",
			"IV",
			"V",
			"VI",
			"VII",
			"VIII",
			"IX",
			"X",
			"XI",
			"XII"
	};
	private static final String[] AUDIBLE_CHOICES = {
			"Soft",
			"Moderate",
			"Loud"
	};

	private void createMenu() {
		createTaggerDirectoryMenu();
		createNewSwarmTaggerMenu();
		createOpenExistingTaggerMenu();
		this.addSeparator();
		createOperatorMenu();
		this.addSeparator();
		createClassificationMenus();
		createDeleteCurrentEventMenu();
		this.addSeparator();
		createPhasePickMenus();
		createDeletePickMenu();
		createClearMenu();
		this.addSeparator();
		createCoupledEventMenu();
		this.addSeparator();
		createObservationMenu();
		this.addSeparator();
		createNoteMenu();
	}

	private void createNewSwarmTaggerMenu() {

		JMenuItem createItem = new JMenuItem("Create new swarm tagger file");

		createItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				askOperatorName(false);
				ensureEventFileForTime(j2k);
				write();

				JOptionPane.showMessageDialog(
						hvp,
						"New SwarmTagger file active:\n" + eventFileName);
			}
		});

		this.add(createItem);
	}
	
	private void createOpenExistingTaggerMenu() {

    JMenuItem openItem = new JMenuItem("Open existing swarmtagger file");

    openItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            openExistingTaggerFile();
        }
    });

    this.add(openItem);
	}

	private void openExistingTaggerFile() {

		final FileFilter filter = new FileNameExtensionFilter("CSV (.csv)", "CSV");

		JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(taggerRootDir);

		if (filter != null) {
			chooser.setFileFilter(filter);
		}

		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setMultiSelectionEnabled(false);
		chooser.setDialogTitle("Open Existing SwarmTagger File...");

		int result = chooser.showOpenDialog(Swarm.getApplicationFrame());

		if (result == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			eventFileName = file.getAbsolutePath();
			openedExistingFile = true;

			loadEventFile();

			JOptionPane.showMessageDialog(
					hvp,
					"Loaded SwarmTagger file for review/edit:\n"
					+ eventFileName
					+ "\n\nNew picks will be saved automatically to daily files "
					+ "based on pick time.");
					
			if (eventFileName != null
					&& !eventFileName.equals(loadedEventFileName)) {
				loadEventFile();
				loadedEventFileName = eventFileName;
			}
		}		
	}
	
	private boolean containsTag(TagData newTag) {
    for (TagData tag : hvp.getTagData()) {
        if (tag.channel.equals(newTag.channel)
                && tag.eventId.equals(newTag.eventId)
                && tag.phase.equals(newTag.phase)
                && Math.abs(tag.startTime - newTag.startTime) < 0.001) {
            return true;
        }
    }
    return false;
}

	private void createTaggerDirectoryMenu() {
		JMenuItem dirItem = new JMenuItem("Set tagger directory");
		dirItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseTaggerDirectory();
			}
		});
		this.add(dirItem);
	}

	private void loadTaggerRootDir() {

		File config = new File(TAGGER_DIR_CONFIG);

		if (config.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(config));
				String path = br.readLine();
				br.close();

				if (path != null && path.trim().length() > 0) {
					taggerRootDir = new File(path.trim());
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 * DEFAULT DIRECTORY
		 */
		if (taggerRootDir == null) {

			File defaultDir =
					new File(
							System.getProperty("user.home"),
							"SwarmTags");

			taggerRootDir = defaultDir;
		}

		if (!taggerRootDir.exists()) {
			taggerRootDir.mkdirs();
		}
	}

	private void setStatus(String message) {
		if (hvp != null && hvp.getFrame() != null) {
			hvp.getFrame().setStatus(message);
		}
	}

	private void saveTaggerRootDir() {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(TAGGER_DIR_CONFIG));
			bw.write(taggerRootDir.getAbsolutePath());
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void chooseTaggerDirectory() {

		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select Tagger Root Directory");

		if (taggerRootDir != null) {
			chooser.setCurrentDirectory(taggerRootDir);
		}

		int result = chooser.showOpenDialog(Swarm.getApplicationFrame());

		if (result == JFileChooser.APPROVE_OPTION) {
			taggerRootDir = chooser.getSelectedFile();
			saveTaggerRootDir();

			JOptionPane.showMessageDialog(
					hvp,
					"Tagger directory set to:\n" + taggerRootDir.getAbsolutePath());
		}
	}

	private void ensureEventFileForTime(double j2kTime) {
		ensureEventFileForTime(j2kTime, false);
	}

	private void ensureCoupledEventFileForTime(double j2kTime) {
		ensureEventFileForTime(j2kTime, true);
	}

	private void ensureEventFileForTime(double j2kTime, boolean coupled) {

		askOperatorName(false);

		String ymd = formatJ2k(j2kTime, "yyyyMMdd");
		String year = formatJ2k(j2kTime, "yyyy");

		/*
		 * Jangan clear tagData saat tanggal berubah.
		 * User boleh melanjutkan picking data lampau.
		 * Cukup reset event aktif agar event tidak nyambung lintas hari.
		 */
		if (activeYmd.length() > 0 && !activeYmd.equals(ymd)) {
			currentEventId = "";
		}

		activeYmd = ymd;
		activeFileYmd = ymd;

		String station = getStationCode();

		File dayDir = new File(
				new File(
						new File(
								new File(taggerRootDir, "tagger"),
								year),
						ymd),
				station);

		if (!dayDir.exists()) {
			dayDir.mkdirs();
		}

		String filePrefix = coupled ? "SwarmTaggerCoupled_" : "SwarmTagger_";
		String stationPart = coupled ? getStationChannelNetworkLocationCode() : station;

		String op = sanitizeFileName(operatorName);
		if (coupled) {
			op = normalizeCoupledOperatorName(op);
		}

		if (op.length() == 0) {
			op = "Unknown";
		}

		activeFileOperator = op;
		
		File file = new File(
				dayDir,
				filePrefix + ymd + "_" + stationPart + "_" + op + ".csv");

		String newEventFileName = file.getAbsolutePath();

		/*
		 * Autosave selalu mengikuti waktu pick.
		 * Jadi kalau user membuka file 20260602 lalu pick di 20260603,
		 * file aktif otomatis pindah ke SwarmTagger_20260603_....
		 */
		if (eventFileName == null || !eventFileName.equals(newEventFileName)) {

			eventFileName = newEventFileName;

			if (file.exists()) {
				loadEventFile();
			} else {
				write();
			}
		}
	}

	private String getStationChannelNetworkLocationCode() {
		String ch = hvp.getSettings().channel;

		if (ch == null || ch.trim().length() == 0) {
			return "UNKNOWN_UNKNOWN_UNKNOWN_UNKNOWN";
		}

		String normalized = sanitizeFileName(ch);
		String[] parts = normalized.split("_");

		ArrayList<String> meaningful = new ArrayList<String>();

		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();

			if (part.length() == 0) {
				continue;
			}

			if (meaningful.size() == 0 && "UNKNOWN".equalsIgnoreCase(part)) {
				continue;
			}

			meaningful.add(part);
		}

		while (meaningful.size() > 4
				&& "UNKNOWN".equalsIgnoreCase(
						meaningful.get(meaningful.size() - 1))) {
			meaningful.remove(meaningful.size() - 1);
		}

		if (meaningful.size() >= 4) {
			return meaningful.get(0)
					+ "_" + meaningful.get(1)
					+ "_" + meaningful.get(2)
					+ "_" + meaningful.get(3);
		}

		return normalized;
	}

	private String normalizeCoupledOperatorName(String op) {
		if (op == null || op.length() == 0) {
			return op;
		}

		String user = sanitizeFileName(SwarmConfig.getInstance().getUser());

		if (user.length() > 0 && op.endsWith("_" + user)) {
			return user;
		}

		if (op.indexOf("UNKNOWN") >= 0) {
			String[] parts = op.split("_");

			for (int i = parts.length - 1; i >= 0; i--) {
				if (parts[i].length() > 0
						&& !"UNKNOWN".equalsIgnoreCase(parts[i])) {
					return parts[i];
				}
			}
		}

		return op;
	}

	private static String formatJ2k(double j2k, String pattern) {
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date d = J2kSec.asDate(j2k);
		return sdf.format(d);
	}

	private static String sanitizeFileName(String s) {
		if (s == null) {
			return "";
		}

		return s.trim().replaceAll("[^A-Za-z0-9_-]", "_");
	}

	private String getStationCode() {
		return getStationCode(null);
	}

	private String getStationCode(TagData tag) {
		String ch = tag == null ? hvp.getSettings().channel : tag.channel;

		if (ch == null || ch.trim().length() == 0) {
			return "UNKNOWN";
		}

		String[] parts = ch.split("\\.");

		if (parts.length >= 2) {
			return sanitizeFileName(parts[1]);
		}

		return sanitizeFileName(ch);
	}

    public static Color getColor(String classification) {
        Color base = colors.get(classification);

        if (base == null) {
            base = defaultColor;
        }

        return new Color(base.getRed(), base.getGreen(), base.getBlue(), transparency);
    }

    public static Color getDurationColor(String classification) {
        Color base = colors.get(classification);

        if (base == null) {
            base = defaultColor;
        }

        return new Color(base.getRed(), base.getGreen(), base.getBlue(), durationTransparency);
    }

	public void browseForEventFileName() {
		// Jangan otomatis buka file chooser.
		// File akan dibuat saat user memilih Create New atau saat pick pertama.
		askOperatorName(false);
		ensureEventFileForTime(j2k);
		JOptionPane.showMessageDialog(
				hvp,
				"SwarmTagger file active:\n" + eventFileName);
	}

	public TagMenu(HelicorderViewPanel hvp) {
		super("Tag Menu");
		this.hvp = hvp;

		loadTaggerRootDir();

		createMenu();

		eventFileName = null;
	}

    private void loadEventFile() {
        FileReader fr;

        try {
            fr = new FileReader(eventFileName);
            BufferedReader br = new BufferedReader(fr);

            String line;
            while ((line = br.readLine()) != null && !line.equals("")) {
                if (line.toLowerCase().startsWith("time,")) {
                    continue;
                }

                TagData tag = new TagData(line);
//                hvp.getTagData().add(tag);

					if (!containsTag(tag)) {
						hvp.getTagData().add(tag);
					}

                if (operatorName.length() == 0 && tag.operator != null && tag.operator.length() > 0) {
                    operatorName = tag.operator;
                }
            }

            br.close();
            fr.close();

        } catch (FileNotFoundException e) {
            // New file will be created on write.
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void write() {
        FileWriter fw;

		if (eventFileName == null) {
			return;
		}

        try {
            fw = new FileWriter(eventFileName, false);
            BufferedWriter bw = new BufferedWriter(fw);

            ArrayList<TagData> tagData = hvp.getTagData();
            Collections.sort(tagData, new TagData());

			bw.write(
					"time,channel,classification,phase,onset,polarity,"
					+ "uncertaintyMs,eventId,operator,note,mmi,audible,visible,"
					+ "durationSec,Md,maxAbsAmplitude,maxAbsTime,peakToPeakAmplitude,"
					+ "dominantFrequencyHz,f1,f2,f3\n");

			for (TagData data : tagData) {

				if (activeFileYmd != null && activeFileYmd.length() > 0) {
					String dataYmd = formatJ2k(data.startTime, "yyyyMMdd");

					if (!activeFileYmd.equals(dataYmd)) {
						continue;
					}
				}

				if (activeFileOperator != null && activeFileOperator.length() > 0) {
					String dataOperator = sanitizeFileName(data.operator);

					if (!activeFileOperator.equals(dataOperator)) {
						continue;
					}
				}

				if (isActiveCoupledTaggerFile()) {
					if (!isCoupledEvent(data.eventId)) {
						continue;
					}
				} else if (isCoupledEvent(data.eventId)) {
					continue;
				}

				bw.write(data.toString() + "\n");
			}

            bw.close();
            fw.close();
			
			setStatus(
			    "Tagger saved | "
			    + activeFileYmd
			    + " | "
			    + getStationCode()
			);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    Swarm.getApplicationFrame(),
                    e.getMessage(),
                    "Error writing tags",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

	private boolean isActiveCoupledTaggerFile() {
		if (eventFileName == null) {
			return false;
		}

		return new File(eventFileName).getName().startsWith("SwarmTaggerCoupled_");
	}

	private boolean isCoupledPhase(String phase) {
		return CP1.equalsIgnoreCase(phase)
				|| CP2.equalsIgnoreCase(phase)
				|| CP3.equalsIgnoreCase(phase)
				|| CEND.equalsIgnoreCase(phase);
	}

	private boolean isCoupledEvent(String eventId) {
		if (eventId == null || eventId.length() == 0) {
			return false;
		}

		for (TagData tag : hvp.getTagData()) {
			if (eventId.equals(tag.eventId) && isCoupledPhase(tag.phase)) {
				return true;
			}
		}

		return false;
	}

	public void quickPick(String phase, double pickTime) {

		setJ2k(pickTime);

		if ("P".equalsIgnoreCase(phase)) {

			createPhasePick(
					"P",
					"IMPULSIVE",
					"UNKNOWN",
					1);

		} else if ("S".equalsIgnoreCase(phase)) {

			createPhasePick(
					"S",
					"",
					"",
					1);

		} else if ("END".equalsIgnoreCase(phase)
				|| "CODA".equalsIgnoreCase(phase)) {

			createPhasePick(
					"END",
					"",
					"",
					1);
		}

		repaintPanels();

		hvp.invalidate();
		hvp.repaint();
	}

	private TagData findPhaseTag(String eventId, String phase) {
		for (TagData tag : hvp.getTagData()) {
			if (!eventId.equals(tag.eventId)) {
				continue;
			}

			if ("END".equalsIgnoreCase(phase)) {
				if (tag.isCoda()) {
					return tag;
				}
			} else if (phase.equalsIgnoreCase(tag.phase)) {
				return tag;
			}
		}

		return null;
	}

    private void createOperatorMenu() {
        JMenuItem operatorItem = new JMenuItem("Set operator name");

        operatorItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                askOperatorName(true);
            }
        });

        this.add(operatorItem);
    }

    private void createNoteMenu() {
        JMenuItem noteItem = new JMenuItem("Add/Edit Note");

        noteItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String input = JOptionPane.showInputDialog(
                        hvp,
                        "Catatan untuk pick/event ini:",
                        lastNote
                );

                if (input != null) {
                    lastNote = input.trim();
                }
            }
        });

        this.add(noteItem);
    }

	private void createObservationMenu() {
		JMenu observationMenu = new JMenu("Observation");

		JMenuItem feltItem = new JMenuItem("Felt");
		feltItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String mmi = (String) JOptionPane.showInputDialog(
						hvp,
						"Select MMI:",
						"Observation",
						JOptionPane.QUESTION_MESSAGE,
						null,
						MMI_CHOICES,
						lastMmi.length() == 0 ? MMI_CHOICES[0] : lastMmi);

				if (mmi != null) {
					applyObservation("mmi", mmi);
				}
			}
		});
		observationMenu.add(feltItem);

		JMenuItem audibleItem = new JMenuItem("Audible");
		audibleItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String intensity = (String) JOptionPane.showInputDialog(
						hvp,
						"Select audible intensity:",
						"Observation",
						JOptionPane.QUESTION_MESSAGE,
						null,
						AUDIBLE_CHOICES,
						lastAudible.length() == 0 ? AUDIBLE_CHOICES[0] : lastAudible);

				if (intensity != null) {
					applyObservation("audible", intensity);
				}
			}
		});
		observationMenu.add(audibleItem);

		JMenuItem visibleItem = new JMenuItem("Visible");
		visibleItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String input = JOptionPane.showInputDialog(
						hvp,
						"Visible observation:",
						lastVisible);

				if (input != null && input.trim().length() > 0) {
					applyObservation("visible", sanitizeNote(input.trim()));
				}
			}
		});
		observationMenu.add(visibleItem);

		this.add(observationMenu);
	}

	private void applyObservation(String field, String value) {
		if (field == null || value == null || value.trim().length() == 0) {
			return;
		}

		value = sanitizeNote(value.trim());
		setLastObservation(field, value);
		String eventId = getActiveOrNearestEventId();

		if (eventId == null || eventId.length() == 0) {
			setStatus("Observation set for next event | " + field + ": " + value);
			return;
		}

		Set<String> affectedYmds = new HashSet<String>();

		for (TagData tag : hvp.getTagData()) {
			if (eventId.equals(tag.eventId)) {
				setObservationField(tag, field, value);
				affectedYmds.add(formatJ2k(tag.startTime, "yyyyMMdd"));
			}
		}

		updateEventObservationInTaggerFiles(eventId, field, value, affectedYmds);
		write();

		for (String ymd : affectedYmds) {
			CatalogToolsMenu.rebuildCatalogForDaySilent(
					hvp,
					taggerRootDir,
					ymd);
		}

		repaintPanels();
		setStatus("Observation saved | Catalog updated | " + field + ": " + value);
	}

	private void setLastObservation(String field, String value) {
		if ("mmi".equalsIgnoreCase(field)) {
			lastMmi = value;
		} else if ("audible".equalsIgnoreCase(field)) {
			lastAudible = value;
		} else if ("visible".equalsIgnoreCase(field)) {
			lastVisible = value;
		}
	}

	private void setObservationField(TagData tag, String field, String value) {
		if (tag == null) {
			return;
		}
		if ("mmi".equalsIgnoreCase(field)) {
			tag.mmi = value;
		} else if ("audible".equalsIgnoreCase(field)) {
			tag.audible = value;
		} else if ("visible".equalsIgnoreCase(field)) {
			tag.visible = value;
		}
	}

	private void applyLastObservations(TagData tag) {
		if (tag == null) {
			return;
		}
		tag.mmi = lastMmi;
		tag.audible = lastAudible;
		tag.visible = lastVisible;
	}

	private void applyEventObservations(TagData tag, String eventId) {
		if (tag == null) {
			return;
		}

		for (TagData existing : hvp.getTagData()) {
			if (eventId != null && eventId.equals(existing.eventId)) {
				tag.mmi = existing.mmi;
				tag.audible = existing.audible;
				tag.visible = existing.visible;
				return;
			}
		}

		applyLastObservations(tag);
	}

	private String sanitizeNote(String note) {
		if (note == null) {
			return "";
		}
		return note.replace(",", ";").replace("\n", " ").replace("\r", " ").trim();
	}

	private void createResetEventMenu() {
		JMenuItem resetItem = new JMenuItem("Reset Active Event");
		resetItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				currentEventId = "";
				JOptionPane.showMessageDialog(
						hvp,
						"Active event reset.\n"
						+ "Next classification will create a new event.");
			}
		});
		this.add(resetItem);
	}

	private void createClassificationMenus() {

		JMenu classMenu = new JMenu("Event classification");
		this.add(classMenu);

		for (final String c : classifications) {
			JMenuItem menuItem = new JMenuItem(c);
			menuItem.setBackground(colors.get(c));
			menuItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					askOperatorName(false);
					/*
					 * CREATE NEW ACTIVE EVENT ID
					 */
					ensureEventFileForTime(j2k);
					currentEventId = TagData.makeEventId(
							hvp.getSettings().channel,
							j2k);
					TagData tag = new TagData(
							hvp.getSettings().channel,
							j2k,
							c,
							"",
							"",
							"",
							1,
							currentEventId,
							operatorName,
							lastNote);
					applyLastObservations(tag);
					hvp.getTagData().add(tag);
					repaintPanels();
					write();
				}
			});
			classMenu.add(menuItem);
		}
	}

	private void createPhasePickMenus() {

		JMenu phaseMenu = new JMenu("Phase pick");
		this.add(phaseMenu);

		/*
		 * P PHASE: tetap lengkap
		 * onset: IMPULSIVE / EMERGENT
		 * polarity: + / - / UNKNOWN
		 * uncertainty: 1-4 ms
		 */
		createPMenu(phaseMenu);

		/*
		 * S PHASE: sederhana, langsung pick S
		 */
		JMenuItem sItem = new JMenuItem("S");
		sItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createPhasePick("S", "", "", 1);
			}
		});
		phaseMenu.add(sItem);

		/*
		 * END: pengganti CODA
		 */
		JMenuItem endItem = new JMenuItem("END");
		endItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createPhasePick("END", "", "", 1);
			}
		});
		phaseMenu.add(endItem);
	}

	private void createCoupledEventMenu() {

		JMenu coupledMenu = new JMenu("Coupled event");
		this.add(coupledMenu);

		addCoupledPickMenuItem(coupledMenu, "P1", CP1);
		addCoupledPickMenuItem(coupledMenu, "P2", CP2);
		addCoupledPickMenuItem(coupledMenu, "P3", CP3);
		addCoupledPickMenuItem(coupledMenu, "END", CEND);

		JMenu deleteMenu = new JMenu("Delete pick");
		coupledMenu.add(deleteMenu);

		addCoupledDeleteMenuItem(deleteMenu, "Delete P1", CP1);
		addCoupledDeleteMenuItem(deleteMenu, "Delete P2", CP2);
		addCoupledDeleteMenuItem(deleteMenu, "Delete P3", CP3);
		addCoupledDeleteMenuItem(deleteMenu, "Delete END", CEND);

		JMenuItem deleteEvent = new JMenuItem("Delete Coupled Event");
		deleteEvent.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteCoupledEvent();
			}
		});
		coupledMenu.add(deleteEvent);
	}

	private void addCoupledPickMenuItem(
			JMenu menu,
			String label,
			final String phase) {

		JMenuItem item = new JMenuItem(label);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createCoupledPhasePick(phase);
			}
		});
		menu.add(item);
	}

	private void addCoupledDeleteMenuItem(
			JMenu menu,
			String label,
			final String phase) {

		JMenuItem item = new JMenuItem(label);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteCoupledPhasePick(phase);
			}
		});
		menu.add(item);
	}

	private void createPMenu(JMenu parentMenu) {

		JMenu pMenu = new JMenu("P");
		parentMenu.add(pMenu);

		String[] onsets = { "IMPULSIVE", "EMERGENT" };
		String[] polarities = { "+", "-", "UNKNOWN" };

		for (final String onset : onsets) {

			JMenu onsetMenu = new JMenu(onset);
			pMenu.add(onsetMenu);

			for (final String polarity : polarities) {

				if ("UNKNOWN".equalsIgnoreCase(polarity)) {

					JMenuItem unknownItem = new JMenuItem("UNKNOWN");

					unknownItem.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							createPhasePick(
									"P",
									onset,
									"UNKNOWN",
									0);
						}
					});

					onsetMenu.add(unknownItem);

				} else {

					JMenu polarityMenu = new JMenu(polarity);
					onsetMenu.add(polarityMenu);

					for (int i = 1; i <= 4; i++) {

						final int uncertaintyMs = i;
						JMenuItem uncertaintyItem =
								new JMenuItem(uncertaintyMs + " ms");

						uncertaintyItem.addActionListener(new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								createPhasePick(
										"P",
										onset,
										polarity,
										uncertaintyMs);
							}
						});

						polarityMenu.add(uncertaintyItem);
					}
				}
			}
		}
	}

	private static class FrequencyBand {
		double f1 = Double.NaN;
		double f2 = Double.NaN;
		double f3 = Double.NaN;
	}

	private void createCoupledPhasePick(String phase) {

		ensureCoupledEventFileForTime(j2k);

		String eventId = currentEventId;

		if (eventId == null || eventId.trim().length() == 0) {
			JOptionPane.showMessageDialog(
					hvp,
					"Please select Event Classification first.",
					"No Active Event",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (!isCoupledPickAllowed(eventId, phase)) {
			return;
		}

		if (CP1.equalsIgnoreCase(phase)) {
			Set<String> affectedYmds = new HashSet<String>();
			affectedYmds.add(formatJ2k(j2k, "yyyyMMdd"));
			deleteEventFromNormalTaggerFiles(eventId, affectedYmds);
		}

		String classification = findClassificationForEvent(eventId);

		TagData tag = new TagData(
				hvp.getSettings().channel,
				j2k,
				classification,
				phase,
				"",
				"",
				1,
				eventId,
				operatorName,
				COUPLED_NOTE);

		applyEventObservations(tag, eventId);
		replaceSamePhasePick(tag);
		populateCoupledMeasurementFields(eventId, tag);
		markCoupledEventNote(eventId);
		write();

		if (CEND.equalsIgnoreCase(phase)) {
			CatalogToolsMenu.rebuildCatalogForDaySilent(
					hvp,
					taggerRootDir,
					activeFileYmd);
			currentEventId = "";
		}

		repaintPanels();

		setStatus(
				"Coupled event saved | "
				+ activeFileYmd);
	}

	private boolean isCoupledPickAllowed(String eventId, String phase) {

		TagData p1 = findPhaseTag(eventId, CP1);
		TagData p2 = findPhaseTag(eventId, CP2);
		TagData p3 = findPhaseTag(eventId, CP3);

		if (CP1.equalsIgnoreCase(phase)) {
			return true;
		}

		if (CP2.equalsIgnoreCase(phase)) {
			if (p1 == null) {
				showMissingCoupledPick("P1", "P2");
				return false;
			}
			if (j2k <= p1.startTime) {
				showCoupledOrderWarning("P2", "P1");
				return false;
			}
			return true;
		}

		if (CP3.equalsIgnoreCase(phase)) {
			if (p2 == null) {
				showMissingCoupledPick("P2", "P3");
				return false;
			}
			if (j2k <= p2.startTime) {
				showCoupledOrderWarning("P3", "P2");
				return false;
			}
			return true;
		}

		if (CEND.equalsIgnoreCase(phase)) {
			if (p2 == null) {
				showMissingCoupledPick("P2", "END");
				return false;
			}

			TagData previous = p3 != null ? p3 : p2;
			String previousLabel = p3 != null ? "P3" : "P2";

			if (j2k <= previous.startTime) {
				showCoupledOrderWarning("END", previousLabel);
				return false;
			}
			return true;
		}

		return false;
	}

	private void showMissingCoupledPick(String required, String phase) {
		JOptionPane.showMessageDialog(
				hvp,
				"Please pick " + required
						+ " before picking " + phase + ".",
				"Missing Coupled Pick",
				JOptionPane.WARNING_MESSAGE);
	}

	private void showCoupledOrderWarning(String phase, String previous) {
		JOptionPane.showMessageDialog(
				hvp,
				phase + " must be after " + previous + ".",
				"Invalid Coupled Pick Order",
				JOptionPane.WARNING_MESSAGE);
	}

	private void markCoupledEventNote(String eventId) {
		for (TagData tag : hvp.getTagData()) {
			if (eventId.equals(tag.eventId)) {
				tag.note = COUPLED_NOTE;
			}
		}
	}

	private void populateCoupledMeasurementFields(String eventId, TagData tag) {

		if (tag == null || eventId == null) {
			return;
		}

		TagData p1 = findPhaseTag(eventId, CP1);
		TagData p2 = findPhaseTag(eventId, CP2);
		TagData p3 = findPhaseTag(eventId, CP3);

		if (CP2.equalsIgnoreCase(tag.phase) && p1 != null) {
			populateWindowMeasurement(tag, p1.startTime, tag.startTime, false);

		} else if (CP3.equalsIgnoreCase(tag.phase) && p2 != null) {
			populateWindowMeasurement(tag, p2.startTime, tag.startTime, false);

		} else if (CEND.equalsIgnoreCase(tag.phase) && p1 != null && p2 != null) {
			populateWindowMeasurement(tag, p2.startTime, tag.startTime, true);
			tag.durationSec = tag.startTime - p1.startTime;
		}

		if (p3 != null && CP3.equalsIgnoreCase(tag.phase)) {
			tag.durationSec = tag.startTime - p2.startTime;
		}
	}

	private void populateWindowMeasurement(
			TagData tag,
			double startTime,
			double endTime,
			boolean keepExistingDuration) {

		if (endTime <= startTime) {
			return;
		}

		if (!keepExistingDuration) {
			tag.durationSec = endTime - startTime;
		}

		tag.peakToPeakAmplitude =
				hvp.calculatePeakToPeakAmplitudeForWindow(startTime, endTime);

		double[] band =
				hvp.calculateHalfPowerFrequencyBandForWindow(startTime, endTime);

		if (band != null && band.length >= 3) {
			tag.f1 = band[0];
			tag.f2 = band[1];
			tag.f3 = band[2];
			tag.dominantFrequencyHz = band[1];
		}
	}

	private void createPhasePick(
			String phase,
			String onset,
			String polarity,
			int uncertaintyMs) {

		ensureEventFileForTime(j2k);

		String eventId = currentEventId;

		if (eventId == null || eventId.trim().length() == 0) {
			JOptionPane.showMessageDialog(
					hvp,
					"Please select Event Classification first.",
					"No Active Event",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		if ("S".equalsIgnoreCase(phase)
				|| "END".equalsIgnoreCase(phase)) {

			TagData pTag = findPhaseTag(eventId, "P");

			if (pTag == null) {
				JOptionPane.showMessageDialog(
						hvp,
						"Please pick P phase first before picking "
								+ phase + ".",
						"Missing P Pick",
						JOptionPane.WARNING_MESSAGE);
				return;
			}
		}

		String classification = findClassificationForEvent(eventId);

		TagData tag = new TagData(
				hvp.getSettings().channel,
				j2k,
				classification,
				phase,
				onset,
				polarity,
				uncertaintyMs,
				eventId,
				operatorName,
				lastNote);

		applyEventObservations(tag, eventId);
		replaceSamePhasePick(tag);

		if ("END".equalsIgnoreCase(phase)) {

			TagData endTag = findPhaseTag(eventId, "END");
			TagData pTag = findPhaseTag(eventId, "P");

			if (endTag == null || pTag == null) {
				return;
			}

			/*
			 * Calculate
			 * amplitude
			 * after
			 * END
			 * is
			 * already
			 * stored
			 * in
			 * tagData.
			 */
			hvp.calculateAmplitudeForEvent(eventId);

			/*
			 * Re-fetch
			 * END
			 * tag
			 * after
			 * amplitude
			 * calculation.
			 */
			endTag = findPhaseTag(eventId, "END");

			if (endTag != null
					&& pTag != null
					&& endTag.startTime > pTag.startTime
					&& !EventClassificationRules
							.skipsDurationMagnitudeAndFrequency(classification)) {

				double[] band =
						hvp.calculateHalfPowerFrequencyBandForWindow(
								pTag.startTime,
								endTag.startTime);

				if (band != null && band.length >= 3) {
					endTag.f1 = band[0];
					endTag.f2 = band[1];
					endTag.f3 = band[2];
					endTag.dominantFrequencyHz = band[1];
				}
			}

			write();

			setStatus(
					"Tagger saved | Catalog updated | "
					+ activeFileYmd);

			currentEventId = "";

			repaintPanels();
			return;
		}
	}

	private FrequencyBand calculateHalfPowerFrequencyBandForWindow(
			double startTime,
			double endTime) {

		FrequencyBand band = new FrequencyBand();

		if (endTime <= startTime) {
			return band;
		}

		/*
		 * Tahap pertama:
		 * sementara tetap gunakan fungsi lama agar tidak merusak flow.
		 * Ini menjadikan f2 sama dengan dominantFrequencyHz lama.
		 *
		 * Tahap berikutnya:
		 * ganti bagian ini dengan fungsi baru di HelicorderViewPanel
		 * yang benar-benar mengambil waveform P-END,
		 * detrend, taper, high-pass 0.5 Hz, lalu half-power spectrum.
		 */
		double peak =
				hvp.calculateDominantFrequencyForWindow(
						startTime,
						endTime);

		band.f2 = peak;

		/*
		 * f1 dan f3 belum bisa dihitung langsung dari TagMenu
		 * kecuali kita punya akses sample waveform dan sample rate.
		 * Jadi sebaiknya perhitungan final dibuat di HelicorderViewPanel,
		 * bukan hanya di TagMenu.
		 */
		band.f1 = Double.NaN;
		band.f3 = Double.NaN;

		return band;
	}

	private void replaceSamePhasePick(TagData newTag) {

		if (newTag == null) {
			return;
		}

		/*
		 * Untuk classification tag, jangan replace.
		 * Classification tag adalah penanda event baru.
		 */
		if (!newTag.isPhasePick()) {
			hvp.getTagData().add(newTag);
			return;
		}

		TagData oldTag = null;

		for (TagData tag : hvp.getTagData()) {
			if (tag.channel.equals(newTag.channel)
					&& tag.eventId.equals(newTag.eventId)
					&& tag.phase.equals(newTag.phase)) {
				oldTag = tag;
				break;
			}
		}

		if (oldTag != null) {
			hvp.getTagData().remove(oldTag);
		}

		hvp.getTagData().add(newTag);
	}
	
    private String findNearestEventIdOrCreate() {
        TagData nearest = null;
        double mindiff = Double.MAX_VALUE;

        for (TagData tag : hvp.getTagData()) {
            if (!tag.channel.equals(hvp.getSettings().channel)) {
                continue;
            }

            double diff = Math.abs(j2k - tag.startTime);

            if (diff < 300 && diff < mindiff) {
                mindiff = diff;
                nearest = tag;
            }
        }

        if (nearest != null && nearest.eventId != null && nearest.eventId.length() > 0) {
            return nearest.eventId;
        }

        TagData previousP = null;

        for (TagData tag : hvp.getTagData()) {
            if (!tag.channel.equals(hvp.getSettings().channel)) {
                continue;
            }

            if (tag.isP() && tag.startTime <= j2k) {
                if (previousP == null || tag.startTime > previousP.startTime) {
                    previousP = tag;
                }
            }
        }

        if (previousP != null && previousP.eventId != null && previousP.eventId.length() > 0) {
            return previousP.eventId;
        }

        return TagData.makeEventId(hvp.getSettings().channel, j2k);
    }

    private String findClassificationForEvent(String eventId) {
        String classification = "Unclassified";

        for (TagData tag : hvp.getTagData()) {
            if (eventId.equals(tag.eventId)
                    && tag.classification != null
                    && tag.classification.length() > 0) {
                classification = tag.classification;
            }
        }

        return classification;
    }

	public void onAmplitudeCalculationFinished(String eventId) {

		write();

		CatalogToolsMenu.rebuildCatalogForDaySilent(
				hvp,
				taggerRootDir,
				activeFileYmd);

		setStatus(
				"Tagger saved | Catalog updated | "
				+ activeFileYmd);
	}


	private void createDeletePickMenu() {

		JMenu deleteMenu = new JMenu("Delete phase pick");

		JMenuItem deleteP = new JMenuItem("Delete P");
		deleteP.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deletePhasePick("P");
			}
		});

		JMenuItem deleteS = new JMenuItem("Delete S");
		deleteS.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deletePhasePick("S");
			}
		});

		JMenuItem deleteEnd = new JMenuItem("Delete END");
		deleteEnd.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deletePhasePick("END");
			}
		});

		deleteMenu.add(deleteP);
		deleteMenu.add(deleteS);
		deleteMenu.add(deleteEnd);

		this.add(deleteMenu);
	}

	private void deletePhasePick(String phase) {

		String eventId = getActiveOrNearestEventId();

		if (eventId == null || eventId.length() == 0) {
			JOptionPane.showMessageDialog(hvp, "No event found near cursor.");
			return;
		}

		TagData deleteTag = null;

		for (TagData tag : hvp.getTagData()) {
			if (!eventId.equals(tag.eventId)) {
				continue;
			}

			if ("END".equalsIgnoreCase(phase)) {
				if (tag.isCoda()) {
					deleteTag = tag;
					break;
				}
			} else if (phase.equalsIgnoreCase(tag.phase)) {
				deleteTag = tag;
				break;
			}
		}

		if (deleteTag == null) {
			JOptionPane.showMessageDialog(hvp, "No " + phase + " pick found.");
			return;
		}

		String confirmMessage;

		if ("P".equalsIgnoreCase(phase)) {
			confirmMessage =
					"Delete P pick?\n\n"
					+ "S and END picks for this event will also be deleted.";
		} else {
			confirmMessage = "Delete " + phase + " pick?";
		}

		int result = JOptionPane.showConfirmDialog(
				hvp,
				confirmMessage,
				"Confirm Delete",
				JOptionPane.YES_NO_OPTION);

		if (result != JOptionPane.YES_OPTION) {
			return;
		}

		Set<String> affectedYmds = new HashSet<String>();
		affectedYmds.add(formatJ2k(deleteTag.startTime, "yyyyMMdd"));

		/*
		 * Kalau P dihapus, hapus juga S dan END.
		 * Karena S dan END bergantung pada P untuk durasi, amplitudo,
		 * dan band frekuensi P-END.
		 */
		if ("P".equalsIgnoreCase(phase)) {

			removePhasesFromMemory(
					deleteTag.eventId,
					new String[] { "P", "S", "END" });

			deletePhaseFromAllTaggerFiles(
					deleteTag.eventId,
					"P",
					affectedYmds);

			deletePhaseFromAllTaggerFiles(
					deleteTag.eventId,
					"S",
					affectedYmds);

			deletePhaseFromAllTaggerFiles(
					deleteTag.eventId,
					"END",
					affectedYmds);

		} else {

			hvp.getTagData().remove(deleteTag);

			deletePhaseFromAllTaggerFiles(
					deleteTag.eventId,
					phase,
					affectedYmds);
		}

		repaintPanels();

		for (String ymd : affectedYmds) {
			CatalogToolsMenu.rebuildCatalogForDaySilent(
					hvp,
					taggerRootDir,
					ymd);
		}

		setStatus(
				"Phase pick deleted | Catalog updated | "
				+ affectedYmds.toString());
	}

	private void removePhasesFromMemory(
			String eventId,
			String[] phases) {

		if (eventId == null || phases == null) {
			return;
		}

		ArrayList<TagData> removeList = new ArrayList<TagData>();

		for (TagData tag : hvp.getTagData()) {

			if (!eventId.equals(tag.eventId)) {
				continue;
			}

			for (int i = 0; i < phases.length; i++) {
				if (isSameDeletePhase(tag, phases[i])) {
					removeList.add(tag);
					break;
				}
			}
		}

		hvp.getTagData().removeAll(removeList);
	}

	private void deletePhaseFromAllTaggerFiles(
			String eventId,
			String phase,
			Set<String> affectedYmds) {

		if (eventId == null || eventId.trim().length() == 0) {
			return;
		}

		if (phase == null || phase.trim().length() == 0) {
			return;
		}

		if (taggerRootDir == null) {
			return;
		}

		if (affectedYmds == null || affectedYmds.size() == 0) {
			return;
		}

		for (String ymd : affectedYmds) {

			if (ymd == null || ymd.length() < 8) {
				continue;
			}

			String year = ymd.substring(0, 4);

			File dayTaggerDir =
					new File(
							new File(
									new File(taggerRootDir, "tagger"),
									year),
							ymd);

			if (!dayTaggerDir.exists() || !dayTaggerDir.isDirectory()) {
				continue;
			}

			ArrayList<File> files = new ArrayList<File>();
			collectSwarmTaggerFiles(dayTaggerDir, files);

			for (File file : files) {
				removePhaseFromTaggerFile(file, eventId, phase);
			}
		}
	}

	private void deleteCoupledPhasePick(String phase) {

		String eventId = getActiveOrNearestEventId();

		if (eventId == null || eventId.length() == 0) {
			JOptionPane.showMessageDialog(hvp, "No coupled event found near cursor.");
			return;
		}

		TagData deleteTag = findPhaseTag(eventId, phase);

		if (deleteTag == null) {
			JOptionPane.showMessageDialog(
					hvp,
					"No " + coupledPhaseLabel(phase)
							+ " pick found for this coupled event.");
			return;
		}

		String[] phases = coupledDeleteCascade(phase);

		int result = JOptionPane.showConfirmDialog(
				hvp,
				"Delete " + coupledPhaseLabel(phase)
						+ " coupled pick?",
				"Confirm Delete",
				JOptionPane.YES_NO_OPTION);

		if (result != JOptionPane.YES_OPTION) {
			return;
		}

		Set<String> affectedYmds = getEventYmds(eventId);
		removePhasesFromMemory(eventId, phases);

		for (int i = 0; i < phases.length; i++) {
			deletePhaseFromAllTaggerFiles(eventId, phases[i], affectedYmds);
		}

		repaintPanels();

		for (String ymd : affectedYmds) {
			CatalogToolsMenu.rebuildCatalogForDaySilent(
					hvp,
					taggerRootDir,
					ymd);
		}

		setStatus(
				"Coupled pick deleted | Catalog updated | "
				+ affectedYmds.toString());
	}

	private String[] coupledDeleteCascade(String phase) {
		if (CP1.equalsIgnoreCase(phase)) {
			return new String[] { CP1, CP2, CP3, CEND };
		}

		if (CP2.equalsIgnoreCase(phase)) {
			return new String[] { CP2, CP3, CEND };
		}

		if (CP3.equalsIgnoreCase(phase)) {
			return new String[] { CP3, CEND };
		}

		return new String[] { CEND };
	}

	private String coupledPhaseLabel(String phase) {
		if (CP1.equalsIgnoreCase(phase)) {
			return "P1";
		}
		if (CP2.equalsIgnoreCase(phase)) {
			return "P2";
		}
		if (CP3.equalsIgnoreCase(phase)) {
			return "P3";
		}
		if (CEND.equalsIgnoreCase(phase)) {
			return "END";
		}
		return phase;
	}

	private void deleteCoupledEvent() {

		String eventId = getActiveOrNearestEventId();

		if (eventId == null || eventId.length() == 0 || !isCoupledEvent(eventId)) {
			JOptionPane.showMessageDialog(hvp, "No coupled event found near cursor.");
			return;
		}

		int result = JOptionPane.showConfirmDialog(
				hvp,
				"Delete coupled event, classification tag, and all coupled picks?",
				"Confirm Delete Coupled Event",
				JOptionPane.YES_NO_OPTION);

		if (result != JOptionPane.YES_OPTION) {
			return;
		}

		Set<String> affectedYmds = getEventYmds(eventId);

		ArrayList<TagData> removeList = new ArrayList<TagData>();

		for (TagData tag : hvp.getTagData()) {
			if (eventId.equals(tag.eventId)) {
				removeList.add(tag);
			}
		}

		hvp.getTagData().removeAll(removeList);
		deleteEventFromAllTaggerFiles(eventId, affectedYmds);

		if (eventId.equals(currentEventId)) {
			currentEventId = "";
		}

		repaintPanels();

		for (String ymd : affectedYmds) {
			CatalogToolsMenu.rebuildCatalogForDaySilent(
					hvp,
					taggerRootDir,
					ymd);
		}

		setStatus(
				"Coupled event deleted | Catalog updated | "
				+ affectedYmds.toString());
	}

	private Set<String> getEventYmds(String eventId) {
		Set<String> affectedYmds = new HashSet<String>();

		for (TagData tag : hvp.getTagData()) {
			if (eventId.equals(tag.eventId)) {
				affectedYmds.add(formatJ2k(tag.startTime, "yyyyMMdd"));
			}
		}

		if (affectedYmds.size() == 0 && activeFileYmd != null
				&& activeFileYmd.length() > 0) {
			affectedYmds.add(activeFileYmd);
		}

		return affectedYmds;
	}
	
	private void removePhaseFromTaggerFile(
			File file,
			String eventId,
			String phase) {

		if (file == null || eventId == null || phase == null) {
			return;
		}

		ArrayList<String> keptLines = new ArrayList<String>();
		boolean changed = false;

		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;

			while ((line = br.readLine()) != null) {

				if (line.trim().length() == 0) {
					continue;
				}

				/*
				 * Header
				 * tetap
				 * disimpan.
				 */
				if (line.toLowerCase().startsWith("time,")) {
					keptLines.add(line);
					continue;
				}

				boolean removeLine = false;

				try {
					TagData tag = new TagData(line);

					if (eventId.equals(tag.eventId)
							&& isSameDeletePhase(tag, phase)) {
						removeLine = true;
						changed = true;
					}

				} catch (Exception ex) {
					/*
					 * Kalau
					 * ada
					 * baris
					 * rusak,
					 * jangan
					 * dihapus
					 * otomatis.
					 */
					removeLine = false;
				}

				if (!removeLine) {
					keptLines.add(line);
				}
			}

			br.close();

			if (!changed) {
				return;
			}

			BufferedWriter bw = new BufferedWriter(new FileWriter(file, false));

			for (String keptLine : keptLines) {
				bw.write(keptLine);
				bw.write("\n");
			}

			bw.close();

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					"Failed to delete phase from tagger file:\n"
					+ file.getAbsolutePath()
					+ "\n\n"
					+ ex.getMessage(),
					"Delete Phase Warning",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private boolean isSameDeletePhase(TagData tag, String phase) {

		if (tag == null || phase == null) {
			return false;
		}

		if ("END".equalsIgnoreCase(phase)
				|| "CODA".equalsIgnoreCase(phase)) {

			return tag.isCoda();
		}

		return phase.equalsIgnoreCase(tag.phase);
	}

	private String getActiveOrNearestEventId() {

		if (currentEventId != null && currentEventId.length() > 0) {
			return currentEventId;
		}

		TagData nearest = null;
		double minDiff = Double.MAX_VALUE;

		for (TagData tag : hvp.getTagData()) {

			if (!tag.channel.equals(hvp.getSettings().channel)) {
				continue;
			}

			if (tag.eventId == null || tag.eventId.length() == 0) {
				continue;
			}

			double diff = Math.abs(j2k - tag.startTime);

			if (diff < 120 && diff < minDiff) {
				minDiff = diff;
				nearest = tag;
			}
		}

		if (nearest != null) {
			currentEventId = nearest.eventId;
			return currentEventId;
		}

		return "";
	}

	private void createDeleteCurrentEventMenu() {

		JMenuItem deleteEvent = new JMenuItem("Delete current event");

		deleteEvent.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteCurrentEvent();
			}
		});

		this.add(deleteEvent);
	}

	private void deleteCurrentEvent() {

		String eventId = getActiveOrNearestEventId();

		if (eventId == null || eventId.length() == 0) {
			JOptionPane.showMessageDialog(hvp, "No event found near cursor.");
			return;
		}

		int result = JOptionPane.showConfirmDialog(
				hvp,
				"Delete current event and all its P/S/END picks?",
				"Confirm Delete Event",
				JOptionPane.YES_NO_OPTION);

		if (result != JOptionPane.YES_OPTION) {
			return;
		}

		ArrayList<TagData> removeList = new ArrayList<TagData>();

		for (TagData tag : hvp.getTagData()) {
			if (eventId.equals(tag.eventId)) {
				removeList.add(tag);
			}
		}

	/*
	 * Simpan tanggal-tanggal yang terdampak sebelum tag dihapus dari memory.
	 */
	Set<String> affectedYmds = new HashSet<String>();

	for (TagData tag : removeList) {
		affectedYmds.add(formatJ2k(tag.startTime, "yyyyMMdd"));
	}

	hvp.getTagData().removeAll(removeList);

	/*
	 * Hapus event ini juga dari semua file SwarmTagger harian.
	 * Ini penting agar rebuild catalog tidak memunculkan lagi event yang sudah dihapus.
	 */
	deleteEventFromAllTaggerFiles(eventId, affectedYmds);

	if (eventId.equals(currentEventId)) {
		currentEventId = "";
	}

	repaintPanels();

	/*
	 * Jangan hanya write() file aktif, karena event bisa berasal dari file operator lain.
	 * Setelah file tagger dibersihkan, rebuild catalog silent untuk hari terdampak.
	 */
	for (String ymd : affectedYmds) {
		CatalogToolsMenu.rebuildCatalogForDaySilent(
				hvp,
				taggerRootDir,
				ymd);
	}

	setStatus(
			"Event deleted | Catalog updated | "
			+ affectedYmds.toString());
	}

	private void deleteEventFromAllTaggerFiles(
			String eventId,
			Set<String> affectedYmds) {

		if (eventId == null || eventId.trim().length() == 0) {
			return;
		}

		if (taggerRootDir == null) {
			return;
		}

		if (affectedYmds == null || affectedYmds.size() == 0) {
			return;
		}

		for (String ymd : affectedYmds) {

			if (ymd == null || ymd.length() < 8) {
				continue;
			}

			String year = ymd.substring(0, 4);

			File dayTaggerDir =
					new File(
							new File(
									new File(taggerRootDir, "tagger"),
									year),
							ymd);

			if (!dayTaggerDir.exists() || !dayTaggerDir.isDirectory()) {
				continue;
			}

			ArrayList<File> files = new ArrayList<File>();
			collectSwarmTaggerFiles(dayTaggerDir, files);

			for (File file : files) {
				removeEventIdFromTaggerFile(file, eventId);
			}
		}
	}

	private void deleteEventFromNormalTaggerFiles(
			String eventId,
			Set<String> affectedYmds) {

		if (eventId == null || eventId.trim().length() == 0) {
			return;
		}

		if (taggerRootDir == null || affectedYmds == null) {
			return;
		}

		for (String ymd : affectedYmds) {
			if (ymd == null || ymd.length() < 8) {
				continue;
			}

			String year = ymd.substring(0, 4);

			File dayTaggerDir =
					new File(
							new File(
									new File(taggerRootDir, "tagger"),
									year),
							ymd);

			if (!dayTaggerDir.exists() || !dayTaggerDir.isDirectory()) {
				continue;
			}

			ArrayList<File> files = new ArrayList<File>();
			collectNormalSwarmTaggerFiles(dayTaggerDir, files);

			for (File file : files) {
				removeEventIdFromTaggerFile(file, eventId);
			}
		}
	}

	private void updateEventObservationInTaggerFiles(
			String eventId,
			String field,
			String value,
			Set<String> affectedYmds) {

		if (eventId == null || eventId.trim().length() == 0) {
			return;
		}

		if (taggerRootDir == null || affectedYmds == null || affectedYmds.size() == 0) {
			return;
		}

		for (String ymd : affectedYmds) {
			if (ymd == null || ymd.length() < 8) {
				continue;
			}

			String year = ymd.substring(0, 4);

			File dayTaggerDir =
					new File(
							new File(
									new File(taggerRootDir, "tagger"),
									year),
							ymd);

			if (!dayTaggerDir.exists() || !dayTaggerDir.isDirectory()) {
				continue;
			}

			ArrayList<File> files = new ArrayList<File>();
			collectNormalSwarmTaggerFiles(dayTaggerDir, files);

			for (File file : files) {
				updateEventObservationInTaggerFile(file, eventId, field, value);
			}
		}
	}

	private void updateEventObservationInTaggerFile(
			File file,
			String eventId,
			String field,
			String value) {

		if (file == null || eventId == null) {
			return;
		}

		ArrayList<String> updatedLines = new ArrayList<String>();
		boolean changed = false;

		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;

			while ((line = br.readLine()) != null) {

				if (line.trim().length() == 0) {
					continue;
				}

				if (line.toLowerCase().startsWith("time,")) {
					updatedLines.add(line);
					continue;
				}

				try {
					TagData tag = new TagData(line);

					if (eventId.equals(tag.eventId)) {
						setObservationField(tag, field, value);
						updatedLines.add(tag.toString());
						changed = true;
					} else {
						updatedLines.add(line);
					}

				} catch (ParseException ex) {
					updatedLines.add(line);
				}
			}

			br.close();

			if (!changed) {
				return;
			}

			BufferedWriter bw = new BufferedWriter(new FileWriter(file, false));

			for (String updatedLine : updatedLines) {
				bw.write(updatedLine);
				bw.write("\n");
			}

			bw.close();

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					"Failed to update observation in tagger file:\n"
					+ file.getAbsolutePath()
					+ "\n\n"
					+ ex.getMessage(),
					"Observation Warning",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private void collectSwarmTaggerFiles(
			File dir,
			ArrayList<File> files) {

		if (dir == null || files == null) {
			return;
		}

		File[] list = dir.listFiles();

		if (list == null) {
			return;
		}

		for (File f : list) {

			if (f.isDirectory()) {
				collectSwarmTaggerFiles(f, files);

			} else if (f.isFile()
					&& f.getName().toLowerCase().endsWith(".csv")
					&& (f.getName().startsWith("SwarmTagger_")
							|| f.getName().startsWith("SwarmTaggerCoupled_"))) {

				files.add(f);
			}
		}
	}

	private void collectNormalSwarmTaggerFiles(
			File dir,
			ArrayList<File> files) {

		if (dir == null || files == null) {
			return;
		}

		File[] list = dir.listFiles();

		if (list == null) {
			return;
		}

		for (File f : list) {
			if (f.isDirectory()) {
				collectNormalSwarmTaggerFiles(f, files);

			} else if (f.isFile()
					&& f.getName().toLowerCase().endsWith(".csv")
					&& f.getName().startsWith("SwarmTagger_")) {

				files.add(f);
			}
		}
	}

	private void removeEventIdFromTaggerFile(
			File file,
			String eventId) {

		if (file == null || eventId == null) {
			return;
		}

		ArrayList<String> keptLines = new ArrayList<String>();
		boolean changed = false;

		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;

			while ((line = br.readLine()) != null) {

				if (line.trim().length() == 0) {
					continue;
				}

				/*
				 * Header
				 * tetap
				 * disimpan.
				 */
				if (line.toLowerCase().startsWith("time,")) {
					keptLines.add(line);
					continue;
				}

				boolean removeLine = false;

				try {
					TagData tag = new TagData(line);

					if (eventId.equals(tag.eventId)) {
						removeLine = true;
						changed = true;
					}

				} catch (Exception ex) {
					/*
					 * Kalau
					 * ada
					 * baris
					 * rusak,
					 * jangan
					 * dihapus
					 * otomatis.
					 * Simpan
					 * agar
					 * tidak
					 * kehilangan
					 * data.
					 */
					removeLine = false;
				}

				if (!removeLine) {
					keptLines.add(line);
				}
			}

			br.close();

			if (!changed) {
				return;
			}

			BufferedWriter bw = new BufferedWriter(new FileWriter(file, false));

			for (String keptLine : keptLines) {
				bw.write(keptLine);
				bw.write("\n");
			}

			bw.close();

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					"Failed to delete event from tagger file:\n"
					+ file.getAbsolutePath()
					+ "\n\n"
					+ ex.getMessage(),
					"Delete Event Warning",
					JOptionPane.WARNING_MESSAGE);
		}
	}

    private void createClearMenu() {
        JMenuItem menuItem = new JMenuItem("Clear nearest tag");

        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                double mindiff = Double.MAX_VALUE;
                TagData deleteTag = null;

                for (TagData tag : hvp.getTagData()) {
                    if (tag.channel.equals(hvp.getSettings().channel)) {
                        double diff = Math.abs(j2k - tag.startTime);

                        if (diff < 60 && diff < mindiff) {
                            mindiff = diff;
                            deleteTag = tag;
                        }
                    }
                }

                if (deleteTag != null) {
                    int result = JOptionPane.showConfirmDialog(
                            hvp,
                            "Delete tag " + deleteTag + "?",
                            "Confirm delete",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);

                    if (result == JOptionPane.YES_OPTION) {
                        hvp.getTagData().remove(deleteTag);
                        repaintPanels();
                        write();
                    }

                } else {
                    JOptionPane.showMessageDialog(hvp, "No tag to delete.");
                }
            }
        });

        this.add(menuItem);
    }

    private void askOperatorName(boolean force) {
        if (!force && operatorName != null && operatorName.trim().length() > 0) {
            return;
        }

        String input = JOptionPane.showInputDialog(
                hvp,
                "Nama operator:",
                operatorName == null || operatorName.length() == 0 ? SwarmConfig.getInstance().getUser() : operatorName
        );

        if (input == null || input.trim().length() == 0) {
            if (operatorName == null || operatorName.trim().length() == 0) {
                operatorName = SwarmConfig.getInstance().getUser();
            }
        } else {
            operatorName = input.trim();
			currentEventId = "";
			eventFileName = null;
			activeFileOperator = sanitizeFileName(operatorName);
        }
    }

	private void repaintPanels() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				hvp.invalidate();
				hvp.repaint();

				WaveViewPanel wvp = hvp.getInsetPanel();
				if (wvp != null) {
					wvp.invalidate();
					wvp.repaint();
				}
			}
		});
	}

	public File getTaggerRootDir() {
		return taggerRootDir;
	}

	public String getActiveFileYmd() {
		return activeFileYmd;
	}

    public double getJ2k() {
        return j2k;
    }

    public void setJ2k(double j2k) {
        this.j2k = j2k;
    }
}
