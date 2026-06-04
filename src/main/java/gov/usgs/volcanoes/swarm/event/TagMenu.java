package gov.usgs.volcanoes.swarm.event;

import gov.usgs.volcanoes.swarm.Swarm;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import gov.usgs.volcanoes.swarm.Version;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.Time;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Right-click menu for helicorder event tags and phase picks.
 */
public class TagMenu extends JPopupMenu {

    private static final String settingsFileName = "EventClassifications.config";
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
	private String loadedEventFileName = "";
	
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

	private void createMenu() {
		createTaggerDirectoryMenu();
		createNewSwarmTaggerMenu();
		createOpenExistingTaggerMenu();
		createOperatorMenu();
		this.addSeparator();
		createClassificationMenus();
		this.addSeparator();
		createPhasePickMenus();
		this.addSeparator();
		createNoteMenu();
		this.addSeparator();
		createExportCatalogMenu();
		this.addSeparator();
		createClearMenu();
	}

	private void createNewSwarmTaggerMenu() {

		JMenuItem createItem = new JMenuItem("Create New SwarmTagger File");

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

    JMenuItem openItem = new JMenuItem("Open Existing SwarmTagger File");

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

			JOptionPane.showMessageDialog(
					hvp,
					"Loaded SwarmTagger file:\n" + eventFileName);
		}
		
		if (!eventFileName.equals(loadedEventFileName)) {
			loadEventFile();
			loadedEventFileName = eventFileName;
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
		JMenuItem dirItem = new JMenuItem("Set Tagger Directory");
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

		String op = sanitizeFileName(operatorName);
		if (op.length() == 0) {
			op = "Unknown";
		}

		File file = new File(
				dayDir,
				"SwarmTagger_" + ymd + "_" + station + "_" + op + ".csv");

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
					+ "uncertaintyMs,eventId,operator,note,"
					+ "durationSec,maxAbsAmplitude,maxAbsTime,peakToPeakAmplitude\n");

            for (TagData data : tagData) {
                bw.write(data.toString() + "\n");
            }

            bw.close();
            fw.close();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    Swarm.getApplicationFrame(),
                    e.getMessage(),
                    "Error writing tags",
                    JOptionPane.ERROR_MESSAGE);
        }
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

    private void createOperatorMenu() {
        JMenuItem operatorItem = new JMenuItem("Set Operator Name");

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

		JMenu classMenu = new JMenu("Event Classification");
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
					hvp.getTagData().add(tag);
					repaintPanels();
					write();
				}
			});
			classMenu.add(menuItem);
		}
	}

	private void createPhasePickMenus() {

		JMenu phaseMenu = new JMenu("Phase Pick");
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

	private void createPMenu(JMenu parentMenu) {

		JMenu pMenu = new JMenu("P");
		parentMenu.add(pMenu);

		String[] onsets = { "IMPULSIVE", "EMERGENT" };
		String[] polarities = { "+", "-", "UNKNOWN" };

		for (final String onset : onsets) {

			JMenu onsetMenu = new JMenu(onset);
			pMenu.add(onsetMenu);

			for (final String polarity : polarities) {

				JMenu polarityMenu = new JMenu(polarity);
				onsetMenu.add(polarityMenu);

				for (int i = 1; i <= 4; i++) {

					final int uncertaintyMs = i;
					JMenuItem uncertaintyItem = new JMenuItem(uncertaintyMs + " ms");

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


	private void createPhasePick(
			String phase,
			String onset,
			String polarity,
			int uncertaintyMs) {

		ensureEventFileForTime(j2k);

		String eventId = currentEventId;

		if (eventId == null || eventId.trim().length() == 0) {

			eventId = TagData.makeEventId(
					hvp.getSettings().channel,
					j2k);

			currentEventId = eventId;
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

//		hvp.getTagData().add(tag);
		replaceSamePhasePick(tag);

		if ("END".equalsIgnoreCase(phase)) {
			hvp.calculateAmplitudeForEvent(eventId);
		}

		repaintPanels();

		write();
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

/**
 * Export Katalog Harian.
 */ 
	private void createExportCatalogMenu() {

		JMenu exportMenu = new JMenu("Export Event Catalog");

		JMenuItem exportByDay = new JMenuItem("Export All by Day");
		exportByDay.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				exportEventCatalogByDay();
			}
		});

		exportMenu.add(exportByDay);

		this.add(exportMenu);
	}

/**
 * Metode Export Katalog Harian by Day 
 */ 
private void exportEventCatalogByDay() {

    if (taggerRootDir == null) {
        loadTaggerRootDir();
    }

    Map<String, ArrayList<String>> linesByDayAll =
            new LinkedHashMap<String, ArrayList<String>>();

    Map<String, Map<String, ArrayList<String>>> linesByDayStation =
            new LinkedHashMap<String, Map<String, ArrayList<String>>>();

    /*
     * GROUP TAGS BY EVENT ID FIRST.
     * This avoids scanning all tagData repeatedly for every event.
     */
    Map<String, ArrayList<TagData>> eventsById =
            new LinkedHashMap<String, ArrayList<TagData>>();

    for (TagData tag : hvp.getTagData()) {

        if (tag.eventId == null || tag.eventId.length() == 0) {
            continue;
        }

        ArrayList<TagData> eventTags = eventsById.get(tag.eventId);

        if (eventTags == null) {
            eventTags = new ArrayList<TagData>();
            eventsById.put(tag.eventId, eventTags);
        }

        eventTags.add(tag);
    }

    for (String eventId : eventsById.keySet()) {

        ArrayList<TagData> eventTags = eventsById.get(eventId);

        TagData classTag = null;
        TagData pTag = null;
        TagData sTag = null;
        TagData endTag = null;

        for (TagData tag : eventTags) {

            if (!tag.isPhasePick()) {
                classTag = tag;
            }

            if (tag.isP()) {
                if (pTag == null || tag.startTime < pTag.startTime) {
                    pTag = tag;
                }
            }

            if (tag.isS()) {
                if (sTag == null || tag.startTime < sTag.startTime) {
                    sTag = tag;
                }
            }

            if ("END".equalsIgnoreCase(tag.phase)
                    || "CODA".equalsIgnoreCase(tag.phase)) {
                if (endTag == null || tag.startTime > endTag.startTime) {
                    endTag = tag;
                }
            }
        }

        TagData ref =
                pTag != null ? pTag :
                classTag != null ? classTag :
                sTag != null ? sTag :
                endTag;

        if (ref == null) {
            continue;
        }

        String ymd = formatJ2k(ref.startTime, "yyyyMMdd");
        String station = getStationCode(ref);

        double dominantFrequencyHz = Double.NaN;

        if (pTag != null && endTag != null && endTag.startTime > pTag.startTime) {
            dominantFrequencyHz =
                    hvp.calculateDominantFrequencyForWindow(
                            pTag.startTime,
                            endTag.startTime);
        }

        String line =
                csv(eventId) + ","
                + csv(pTag == null ? ref.getTimeString() : pTag.getTimeString()) + ","
                + csv(station) + ","
                + csv(ref.channel) + ","
                + csv(ref.classification) + ","
                + csv(pTag == null ? "" : pTag.getTimeString()) + ","
                + csv(sTag == null ? "" : sTag.getTimeString()) + ","
                + csv(endTag == null ? "" : endTag.getTimeString()) + ","
                + csv(endTag == null ? "" : doubleString(endTag.durationSec)) + ","
                + csv(endTag == null ? "" : doubleString(endTag.maxAbsAmplitude)) + ","
                + csv(endTag == null ? "" : endTag.maxAbsTime) + ","
                + csv(endTag == null ? "" : doubleString(endTag.peakToPeakAmplitude)) + ","
                + csv(doubleString(dominantFrequencyHz)) + ","
                + csv(ref.operator) + ","
                + csv(ref.note);

        ArrayList<String> allLines = linesByDayAll.get(ymd);

        if (allLines == null) {
            allLines = new ArrayList<String>();
            linesByDayAll.put(ymd, allLines);
        }

        allLines.add(line);

        Map<String, ArrayList<String>> stationMap =
                linesByDayStation.get(ymd);

        if (stationMap == null) {
            stationMap = new LinkedHashMap<String, ArrayList<String>>();
            linesByDayStation.put(ymd, stationMap);
        }

        ArrayList<String> stationLines = stationMap.get(station);

        if (stationLines == null) {
            stationLines = new ArrayList<String>();
            stationMap.put(station, stationLines);
        }

        stationLines.add(line);
    }

    try {

        for (String ymd : linesByDayAll.keySet()) {

            String year = ymd.substring(0, 4);

            File dayCatalogDir =
                    new File(
                            new File(
                                    new File(taggerRootDir, "catalog"),
                                    year),
                            ymd);

            if (!dayCatalogDir.exists()) {
                dayCatalogDir.mkdirs();
            }

            writeCatalogFile(
                    new File(
                            dayCatalogDir,
                            "Swarm_EventCatalog_" + ymd + "_ALL.csv"),
                    linesByDayAll.get(ymd));

            File stationDir = new File(dayCatalogDir, "station");

            if (!stationDir.exists()) {
                stationDir.mkdirs();
            }

            Map<String, ArrayList<String>> stationMap =
                    linesByDayStation.get(ymd);

            for (String station : stationMap.keySet()) {
                writeCatalogFile(
                        new File(
                                stationDir,
                                "Swarm_EventCatalog_" + ymd + "_" + station + ".csv"),
                        stationMap.get(station));
            }
        }

        JOptionPane.showMessageDialog(
                hvp,
                "Event catalog exported by day to:\n"
                + new File(taggerRootDir, "catalog").getAbsolutePath());

    } catch (IOException ex) {

        JOptionPane.showMessageDialog(
                hvp,
                ex.getMessage(),
                "Error exporting catalog",
                JOptionPane.ERROR_MESSAGE);
    }
}

	private void writeCatalogFile(
			File outFile,
			ArrayList<String> lines)
			throws IOException {

		BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));

		bw.write("eventId,originTime,station,channel,classification,pTime,sTime,endTime,"
				+ "durationSec,maxAbsAmplitude,maxAbsTime,peakToPeakAmplitude,"
				+ "dominantFrequencyHz,"
				+ "operator,note\n");

		for (String line : lines) {
			bw.write(line + "\n");
		}

		bw.close();
	}

	private static String csv(String s) {

		if (s == null) {
			return "";
		}

		s = s.replace("\"", "\"\"");

		if (s.indexOf(",") >= 0
				|| s.indexOf("\"") >= 0
				|| s.indexOf("\n") >= 0
				|| s.indexOf("\r") >= 0) {

			return "\"" + s + "\"";
		}

		return s;
	}

	private static String doubleString(double d) {

		if (Double.isNaN(d)) {
			return "";
		}

		return Double.toString(d);
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
        }
    }

    private void repaintPanels() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                hvp.repaint();

                WaveViewPanel wvp = hvp.getInsetPanel();
                if (wvp != null) {
                    wvp.repaint();
                }
            }
        });
    }

    public double getJ2k() {
        return j2k;
    }

    public void setJ2k(double j2k) {
        this.j2k = j2k;
    }
}