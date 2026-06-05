package gov.usgs.volcanoes.swarm.event;

import gov.usgs.volcanoes.swarm.Swarm;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import gov.usgs.volcanoes.swarm.Version;
import gov.usgs.volcanoes.swarm.heli.HelicorderViewPanel;
import gov.usgs.volcanoes.swarm.wave.WaveViewPanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
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
	private String activeFileYmd = "";
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
		createNoteMenu();
		this.addSeparator();
		createCatalogToolsMenu();
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
					+ "durationSec,maxAbsAmplitude,maxAbsTime,peakToPeakAmplitude,"
					+ "dominantFrequencyHz\n");

			for (TagData data : tagData) {

				if (activeFileYmd != null && activeFileYmd.length() > 0) {
					String dataYmd = formatJ2k(data.startTime, "yyyyMMdd");

					if (!activeFileYmd.equals(dataYmd)) {
						continue;
					}
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

			TagData pTag = findPhaseTag(eventId, "P");

			if (pTag != null && tag.startTime > pTag.startTime) {
				tag.dominantFrequencyHz =
						hvp.calculateDominantFrequencyForWindow(
								pTag.startTime,
								tag.startTime);
			}

			write();

			exportEventCatalogByDaySilent();
			setStatus(
			    "Tagger saved | Catalog updated | "
			    + activeFileYmd
			);
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

	private void createDeletePickMenu() {

		JMenu deleteMenu = new JMenu("Delete Phase Pick");

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

		int result = JOptionPane.showConfirmDialog(
				hvp,
				"Delete " + phase + " pick?",
				"Confirm Delete",
				JOptionPane.YES_NO_OPTION);

		if (result == JOptionPane.YES_OPTION) {
			hvp.getTagData().remove(deleteTag);
			repaintPanels();
			write();
		}
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

		JMenuItem deleteEvent = new JMenuItem("Delete Current Event");

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

		hvp.getTagData().removeAll(removeList);

		if (eventId.equals(currentEventId)) {
			currentEventId = "";
		}

		repaintPanels();
		write();
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
	private void createCatalogToolsMenu() {

		JMenu catalogMenu = new JMenu("Catalog Tools");

		JMenuItem rebuildCurrentDay =
				new JMenuItem("Rebuild Current Day");

		rebuildCurrentDay.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				rebuildCatalogForCurrentDay();
			}
		});

		catalogMenu.add(rebuildCurrentDay);

		JMenuItem rebuildDateRange =
				new JMenuItem("Rebuild Date Range");

		rebuildDateRange.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				rebuildCatalogForDateRange();
			}
		});

		catalogMenu.add(rebuildDateRange);

		JMenuItem validateCatalog =
				new JMenuItem("Validate Catalog");

		validateCatalog.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				validateCatalog();
			}
		});

		catalogMenu.add(validateCatalog);

		this.add(catalogMenu);
	}

	private void rebuildCatalogForCurrentDay() {

		String ymd = formatJ2k(j2k, "yyyyMMdd");

		rebuildCatalogForDay(ymd);

		JOptionPane.showMessageDialog(
				hvp,
				"Catalog rebuilt for current day:\n" + ymd);
	}

	private void rebuildCatalogForDateRange() {

		String start =
				JOptionPane.showInputDialog(
						hvp,
						"Start date (YYYYMMDD):",
						formatJ2k(j2k, "yyyyMMdd"));

		if (start == null || start.trim().length() == 0) {
			return;
		}

		String end =
				JOptionPane.showInputDialog(
						hvp,
						"End date (YYYYMMDD):",
						start);

		if (end == null || end.trim().length() == 0) {
			return;
		}

		start = start.trim();
		end = end.trim();

		try {
			java.text.SimpleDateFormat sdf =
					new java.text.SimpleDateFormat("yyyyMMdd");

			sdf.setLenient(false);

			java.util.Date startDate = sdf.parse(start);
			java.util.Date endDate = sdf.parse(end);

			if (endDate.before(startDate)) {
				JOptionPane.showMessageDialog(
						hvp,
						"End date must be after start date.",
						"Invalid Date Range",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTime(startDate);

			int count = 0;

			while (!cal.getTime().after(endDate)) {
				String ymd = sdf.format(cal.getTime());
				rebuildCatalogForDay(ymd);
				count++;

				cal.add(java.util.Calendar.DATE, 1);
			}

			JOptionPane.showMessageDialog(
					hvp,
					"Catalog rebuilt for "
					+ count
					+ " day(s).\n"
					+ start
					+ " to "
					+ end);

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Invalid Date",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void rebuildCatalogForDay(String ymd) {

		if (taggerRootDir == null) {
			loadTaggerRootDir();
		}

		try {
			backupCatalogDay(ymd);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					"Failed to backup catalog:\n" + ex.getMessage(),
					"Backup Warning",
					JOptionPane.WARNING_MESSAGE);
		}

		String year = ymd.substring(0, 4);

		File dayTaggerDir = new File(
				new File(
						new File(taggerRootDir, "tagger"),
						year),
				ymd);

		if (!dayTaggerDir.exists() || !dayTaggerDir.isDirectory()) {
			return;
		}

		ArrayList<File> csvFiles = new ArrayList<File>();
		collectTaggerCsvFiles(dayTaggerDir, csvFiles);

		ArrayList<TagData> tags = new ArrayList<TagData>();

		for (File csvFile : csvFiles) {
			loadTagsFromFile(csvFile, tags);
		}

		rebuildCatalogFromTags(tags);
	}

	private void collectTaggerCsvFiles(File dir, ArrayList<File> files) {

		File[] list = dir.listFiles();

		if (list == null) {
			return;
		}

		for (File f : list) {

			if (f.isDirectory()) {
				collectTaggerCsvFiles(f, files);

			} else if (f.isFile()
					&& f.getName().toLowerCase().endsWith(".csv")
					&& f.getName().startsWith("SwarmTagger_")) {

				files.add(f);
			}
		}
	}

	private void backupCatalogDay(String ymd) throws IOException {

		String year = ymd.substring(0, 4);

		File sourceDir = new File(
				new File(
						new File(taggerRootDir, "catalog"),
						year),
				ymd);

		if (!sourceDir.exists()) {
			return;
		}

		String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new java.util.Date());

		File backupDir = new File(
				new File(
						new File(taggerRootDir, "backup"),
						"catalog_backup_" + stamp),
				year + File.separator + ymd);

		copyDirectory(sourceDir, backupDir);
	}

	private void copyDirectory(File source, File target) throws IOException {

		if (source.isDirectory()) {
			if (!target.exists()) {
				target.mkdirs();
			}

			File[] files = source.listFiles();

			if (files != null) {
				for (File f : files) {
					copyDirectory(f, new File(target, f.getName()));
				}
			}

		} else {
			BufferedInputStream in =
					new BufferedInputStream(new FileInputStream(source));

			BufferedOutputStream out =
					new BufferedOutputStream(new FileOutputStream(target));

			byte[] buffer = new byte[8192];
			int len;

			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			in.close();
			out.close();
		}
	}


	private void validateCatalog() {

		if (taggerRootDir == null) {
			loadTaggerRootDir();
		}

		File taggerDir = new File(taggerRootDir, "tagger");

		if (!taggerDir.exists() || !taggerDir.isDirectory()) {
			JOptionPane.showMessageDialog(
					hvp,
					"Tagger folder not found:\n" + taggerDir.getAbsolutePath(),
					"Validate Catalog",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		ArrayList<File> csvFiles = new ArrayList<File>();
		collectTaggerCsvFiles(taggerDir, csvFiles);

		ArrayList<TagData> allTags = new ArrayList<TagData>();

		for (File csvFile : csvFiles) {
			loadTagsFromFile(csvFile, allTags);
		}

		String report = validateTags(allTags);

		JTextArea textArea = new JTextArea(report);
		textArea.setEditable(false);
		textArea.setLineWrap(false);

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(850, 500));

		JOptionPane.showMessageDialog(
				hvp,
				scrollPane,
				"Validate Catalog",
				JOptionPane.INFORMATION_MESSAGE);
	}

	private String validateTags(ArrayList<TagData> tags) {

		StringBuilder sb = new StringBuilder();

		Map<String, ArrayList<TagData>> eventsById =
				new LinkedHashMap<String, ArrayList<TagData>>();

		for (TagData tag : tags) {

			if (tag.eventId == null || tag.eventId.length() == 0) {
				sb.append("Missing eventId: ")
						.append(tag.toString())
						.append("\n");
				continue;
			}

			ArrayList<TagData> eventTags = eventsById.get(tag.eventId);

			if (eventTags == null) {
				eventTags = new ArrayList<TagData>();
				eventsById.put(tag.eventId, eventTags);
			}

			eventTags.add(tag);
		}

		int warningCount = 0;

		for (String eventId : eventsById.keySet()) {

			ArrayList<TagData> eventTags = eventsById.get(eventId);

			TagData classTag = null;
			TagData pTag = null;
			TagData sTag = null;
			TagData endTag = null;

			int pCount = 0;
			int sCount = 0;
			int endCount = 0;

			for (TagData tag : eventTags) {

				if (!tag.isPhasePick()) {
					classTag = tag;
				}

				if (tag.isP()) {
					pCount++;
					if (pTag == null || tag.startTime < pTag.startTime) {
						pTag = tag;
					}
				}

				if (tag.isS()) {
					sCount++;
					if (sTag == null || tag.startTime < sTag.startTime) {
						sTag = tag;
					}
				}

				if (tag.isCoda()) {
					endCount++;
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

			String time = ref == null ? "" : ref.getTimeString();
			String station = ref == null ? "" : getStationCode(ref);
			String classification = ref == null ? "" : ref.classification;
			
			if (classification == null || classification.trim().length() == 0) {
			    warningCount++;
			    sb.append("[WARNING] Empty classification | ")
			            .append(time).append(" | ")
			            .append(station).append(" | ")
			            .append(eventId).append("\n");
			}

			if (classTag == null) {
				warningCount++;
				sb.append("[WARNING] Missing classification | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag == null) {
				warningCount++;
				sb.append("[WARNING] Missing P | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(classification).append(" | ")
						.append(eventId).append("\n");
			}

			if (endTag == null) {
				warningCount++;
				sb.append("[WARNING] Missing END | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(classification).append(" | ")
						.append(eventId).append("\n");
			}

			if (pCount > 1) {
				warningCount++;
				sb.append("[WARNING] Multiple P picks | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (sCount > 1) {
				warningCount++;
				sb.append("[WARNING] Multiple S picks | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (endCount > 1) {
				warningCount++;
				sb.append("[WARNING] Multiple END picks | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag != null && sTag != null && sTag.startTime < pTag.startTime) {
				warningCount++;
				sb.append("[WARNING] S before P | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag != null && endTag != null && endTag.startTime <= pTag.startTime) {
				warningCount++;
				sb.append("[WARNING] END before/equal P | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag != null && endTag != null && endTag.startTime > pTag.startTime) {
				double duration = endTag.startTime - pTag.startTime;

				if (duration < 0.5) {
					warningCount++;
					sb.append("[WARNING] Duration too short (<0.5 s) | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(duration).append(" s | ")
							.append(eventId).append("\n");
				}

				if (duration > 600) {
					warningCount++;
					sb.append("[WARNING] Duration too long (>600 s) | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(duration).append(" s | ")
							.append(eventId).append("\n");
				}
				
				if (Double.isNaN(endTag.maxAbsAmplitude)) {
					warningCount++;
					sb.append("[WARNING] Missing max amplitude | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(eventId).append("\n");
				}

				if (Double.isNaN(endTag.peakToPeakAmplitude)) {
					warningCount++;
					sb.append("[WARNING] Missing peak-to-peak amplitude | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(eventId).append("\n");
				}

				if (Double.isNaN(endTag.dominantFrequencyHz)) {
					warningCount++;
					sb.append("[WARNING] Missing dominant frequency | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(eventId).append("\n");
				}

				if (!Double.isNaN(endTag.dominantFrequencyHz)
						&& endTag.dominantFrequencyHz <= 0) {
					warningCount++;
					sb.append("[WARNING] Invalid dominant frequency | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(endTag.dominantFrequencyHz).append(" Hz | ")
							.append(eventId).append("\n");
				}
			}
		}

		if (warningCount == 0) {
			return "Validation complete.\nNo issues found.\n\nTotal events: "
					+ eventsById.size()
					+ "\nTotal tags: "
					+ tags.size();
		}

		return "Validation complete.\n"
				+ "Total events: " + eventsById.size() + "\n"
				+ "Total tags: " + tags.size() + "\n"
				+ "Warnings: " + warningCount + "\n\n"
				+ sb.toString();
	}


	private void loadTagsFromFile(File file, ArrayList<TagData> tags) {

		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;

			while ((line = br.readLine()) != null) {

				line = line.trim();

				if (line.length() == 0) {
					continue;
				}

				if (line.toLowerCase().startsWith("time,")) {
					continue;
				}

				try {
					TagData tag = new TagData(line);

					if (!containsTag(tags, tag)) {
						tags.add(tag);
					}

				} catch (Exception ex) {
					System.err.println(
							"Skipping bad tagger line in "
							+ file.getAbsolutePath()
							+ ": "
							+ line);
				}
			}

			br.close();

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Error reading tagger file",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private boolean containsTag(ArrayList<TagData> tags, TagData newTag) {

		for (TagData tag : tags) {
			if (tag.channel.equals(newTag.channel)
					&& tag.eventId.equals(newTag.eventId)
					&& tag.phase.equals(newTag.phase)
					&& Math.abs(tag.startTime - newTag.startTime) < 0.001) {
				return true;
			}
		}

		return false;
	}

	private void rebuildCatalogFromTags(ArrayList<TagData> tags) {

		Map<String, ArrayList<String>> linesByDayAll =
				new LinkedHashMap<String, ArrayList<String>>();

		Map<String, Map<String, ArrayList<String>>> linesByDayStation =
				new LinkedHashMap<String, Map<String, ArrayList<String>>>();

		Map<String, ArrayList<TagData>> eventsById =
				new LinkedHashMap<String, ArrayList<TagData>>();

		for (TagData tag : tags) {

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

				if (tag.isCoda()) {
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

			double dominantFrequencyHz =
					endTag == null ? Double.NaN : endTag.dominantFrequencyHz;

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

				File dayCatalogDir = new File(
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

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Error rebuilding catalog",
					JOptionPane.ERROR_MESSAGE);
		}
	}

/**
 * Metode Export Katalog Harian by Day 
 */ 

private void exportEventCatalogByDay() {
    exportEventCatalogByDay(false);
}

private void exportEventCatalogByDaySilent() {
    exportEventCatalogByDay(true);
}
 
private void exportEventCatalogByDay(boolean silent) {

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

		double dominantFrequencyHz =
				endTag == null ? Double.NaN : endTag.dominantFrequencyHz;

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

		if (!silent) {
			JOptionPane.showMessageDialog(
					hvp,
					"Event catalog exported by day to:\n"
					+ new File(taggerRootDir, "catalog").getAbsolutePath());
		}

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

    public double getJ2k() {
        return j2k;
    }

    public void setJ2k(double j2k) {
        this.j2k = j2k;
    }
}