/*
 * This file is part of Universal Media Server, based on PS3 Media Server.
 *
 * This program is a free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package net.pms.swing.gui.tabs.traces;

import ch.qos.logback.classic.Level;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import net.pms.Messages;
import net.pms.PMS;
import net.pms.configuration.UmsConfiguration;
import net.pms.logging.LoggingConfig;
import net.pms.swing.components.CustomJButton;
import net.pms.swing.components.CustomJSpinner;
import net.pms.swing.components.SpinnerIntModel;
import net.pms.swing.gui.FormLayoutUtil;
import net.pms.swing.gui.JavaGui;
import net.pms.swing.gui.UmsFormBuilder;
import net.pms.swing.gui.ViewLevel;
import net.pms.util.ProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracesTab {

	private static final Logger LOGGER = LoggerFactory.getLogger(TracesTab.class);
	private final UmsConfiguration configuration;
	private JTextField jSearchBox;
	private JTextField jSyslogHost;
	private JComboBox<String> jSyslogFacility;
	private JCheckBox jCSSearch;
	private JCheckBox jRESearch;
	private JCheckBox jMLSearch;
	private JCheckBox jShowOptions;
	private JCheckBox jUseSyslog;
	private JSpinner jLineBuffer;
	private JSpinner jSyslogPort;
	private Pattern searchPattern = null;
	private final JLabel jSearchOutput = new JLabel();
	private TextAreaFIFO jList;
	private JPanel jOptionsPanel;
	private JLabel jBufferLabel;
	private JSeparator jBufferSeparator;
	private Component jBufferSpace1;
	private Component jBufferSpace2;
	private Component jBufferSpace3;
	private Component jCSSpace;
	private Component jRESpace;
	private Component jMLSpace;
	protected JScrollPane jListPane;
	private final String[] levelStrings = {
		Messages.getString("Error"),
		Messages.getString("Warning"),
		Messages.getString("Info"),
		Messages.getString("Debug"),
		Messages.getString("Trace"),
		Messages.getString("All"),
		Messages.getString("Off")
	};
	private final Level[] logLevels = {
		Level.ERROR,
		Level.WARN,
		Level.INFO,
		Level.DEBUG,
		Level.TRACE,
		Level.ALL,
		Level.OFF
	};
	private final String[] syslogFacilities = {
		"AUTH", "AUTHPRIV", "DAEMON", "CRON", "FTP", "LPR", "KERN",
		"MAIL", "NEWS", "SYSLOG", "USER", "UUCP", "LOCAL0",
		"LOCAL1", "LOCAL2", "LOCAL3", "LOCAL4", "LOCAL5", "LOCAL6", "LOCAL7"
	};

	private int findSyslogFacilityIdx(String facility) {
		for (int i = 0; i < syslogFacilities.length; i++) {
			if (facility.equalsIgnoreCase(syslogFacilities[i])) {
				return i;
			}
		}
		return -1;
	}

	static class PopupTriggerMouseListener extends MouseAdapter {
		private final JPopupMenu popup;
		private final JComponent component;

		public PopupTriggerMouseListener(JPopupMenu popup, JComponent component) {
			this.popup = popup;
			this.component = component;
		}

		// Some systems trigger popup on mouse press, others on mouse release, we want to cater for both
		private void showMenuIfPopupTrigger(MouseEvent e) {
			if (e.isPopupTrigger()) {
				popup.show(component, e.getX() + 3, e.getY() + 3);
			}
		}

		// According to the javadocs on isPopupTrigger, checking for popup trigger on mousePressed and mouseReleased
		// Should be all that is required

		@Override
		public void mousePressed(MouseEvent e) {
			showMenuIfPopupTrigger(e);
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			showMenuIfPopupTrigger(e);
		}
	}

	private final JavaGui looksFrame;

	public TracesTab(UmsConfiguration configuration, JavaGui looksFrame) {
		this.configuration = configuration;
		this.looksFrame = looksFrame;
	}

	/**
	 * Set/update the visibility of all components affected by view level
	 */
	public void applyViewLevel() {
		ViewLevel viewLevel = looksFrame.getViewLevel();

		jCSSpace.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jCSSearch.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jRESpace.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jRESearch.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jMLSpace.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jMLSearch.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jLineBuffer.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jShowOptions.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jOptionsPanel.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED) && jShowOptions.isSelected());
		jBufferLabel.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jBufferSeparator.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jBufferSpace1.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jBufferSpace2.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));
		jBufferSpace3.setVisible(viewLevel.isGreaterOrEqual(ViewLevel.ADVANCED));

		// Turn off some options for Normal/Novice
		if (viewLevel == ViewLevel.NORMAL) {
			jCSSearch.setSelected(false);
			configuration.setGUILogSearchCaseSensitive(false);
			jRESearch.setSelected(false);
			configuration.setGUILogSearchRegEx(false);
			jMLSearch.setSelected(false);
			configuration.setGUILogSearchMultiLine(false);
			jShowOptions.setSelected(false);
			jUseSyslog.setSelected(false);
			configuration.setLoggingUseSyslog(false);
		}
	}

	public JTextArea getList() {
		return jList;
	}

	public void append(String msg) {
		getList().append(msg);
	}

	private int findLevelsIdx(Level level) {
		for (int i = 0; i < logLevels.length; i++) {
			if (logLevels[i] == level) {
				return i;
			}
		}
		return -1;
	}

	private void searchTraces() {
		boolean found;
		Matcher match;
		Document document = jList.getDocument();
		int flags = Pattern.UNICODE_CASE;
		String find = jSearchBox.getText();

		if (find.isEmpty()) {
			jSearchOutput.setText("");
		} else if (document.getLength() > 0) {
			if (jMLSearch.isSelected()) {
				flags += Pattern.DOTALL + Pattern.MULTILINE;
			}
			if (!jRESearch.isSelected()) {
				flags += Pattern.LITERAL;
			}
			if (!jCSSearch.isSelected()) {
				flags += Pattern.CASE_INSENSITIVE;
			}
			try {
				if (searchPattern == null || !find.equals(searchPattern.pattern()) || flags != searchPattern.flags()) {
					searchPattern = Pattern.compile(find, flags);
				}
				match = searchPattern.matcher(document.getText(0, document.getLength()));
				found = match.find(jList.getCaretPosition());
				if (!found && match.hitEnd()) {
					found = match.find(0);
				}

				if (found) {
					jList.requestFocusInWindow();
					Rectangle viewRect = jList.modelToView2D(match.start()).getBounds();
					Rectangle viewRectEnd = jList.modelToView2D(match.end()).getBounds();
					if (viewRectEnd.x < viewRect.x) {
						viewRectEnd.x = jList.getWidth();
					}
					viewRect.width = viewRectEnd.x - viewRect.x;
					viewRect.height += viewRectEnd.y - viewRect.y;
					jList.scrollRectToVisible(viewRect);
					jList.setCaretPosition(match.start());
					jList.moveCaretPosition(match.end());
					jSearchOutput.setText("");
				} else {
					jSearchOutput.setText(String.format(Messages.getString("XNotFound"), jSearchBox.getText()));
				}
			} catch (PatternSyntaxException pe) {
				jSearchOutput.setText(String.format(Messages.getString("RegexErrorX"), pe.getLocalizedMessage()));
			} catch (BadLocationException ex) {
				LOGGER.debug("Exception caught while searching traces list: " + ex);
				jSearchOutput.setText(Messages.getString("SearchError"));
			}
		}
	}

	@SuppressWarnings("serial")
	public JComponent build() {
		// Apply the orientation for the locale
		ComponentOrientation orientation = ComponentOrientation.getOrientation(PMS.getLocale());
		String colSpec = FormLayoutUtil.getColSpec("pref, pref:grow, pref, 3dlu, pref, pref, pref", orientation);

		int cols = colSpec.split(",").length;

		FormLayout layout = new FormLayout(
			colSpec,
			"p, fill:10:grow, p, p"
		);
		UmsFormBuilder builder = UmsFormBuilder.create().layout(layout);
		builder.opaque(true);

		CellConstraints cc = new CellConstraints();

		// Create the search box
		JPanel jSearchPanel = new JPanel();
		jSearchPanel.setLayout(new BoxLayout(jSearchPanel, BoxLayout.LINE_AXIS));
		jSearchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		JLabel jFilterLabel = new JLabel(Messages.getString("Filter") + ":");
		jFilterLabel.setDisplayedMnemonic(KeyEvent.VK_F);
		jFilterLabel.setToolTipText(Messages.getString("FilterLogMessagesLogWindow"));
		JComboBox<String> jTracesFilter = new JComboBox<>(levelStrings);
		jTracesFilter.setSelectedIndex(findLevelsIdx(configuration.getLoggingFilterLogsTab()));
		jFilterLabel.setLabelFor(jTracesFilter);
		jTracesFilter.setToolTipText(Messages.getString("FilterLogMessagesLogWindow"));
		jTracesFilter.addActionListener((ActionEvent e) -> {
			configuration.setLoggingFilterLogsTab(logLevels[jTracesFilter.getSelectedIndex()]);
			LoggingConfig.setTracesFilter();
		});
		jSearchPanel.add(jFilterLabel);
		jSearchPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		jSearchPanel.add(jTracesFilter);

		jSearchPanel.add(Box.createRigidArea(new Dimension(5, 0)));

		jSearchBox = new JTextField();
		jSearchBox.setBackground(new Color(248, 248, 248));
		jSearchBox.setToolTipText(Messages.getString("SearchLogBelowCurrentPosition"));
		jSearchPanel.add(jSearchBox);
		jSearchPanel.add(Box.createRigidArea(new Dimension(5, 0)));

		JButton jSearchButton = new JButton(Messages.getString("Search"));
		jSearchButton.setMnemonic(KeyEvent.VK_S);
		jSearchButton.setToolTipText(Messages.getString("SearchLogBelowCurrentPosition"));
		jSearchPanel.add(jSearchButton);
		jCSSpace = Box.createRigidArea(new Dimension(5, 0));
		jSearchPanel.add(jCSSpace);
		jCSSearch = new JCheckBox(Messages.getString("CaseSensitive"), configuration.getGUILogSearchCaseSensitive());
		jCSSearch.setMnemonic(KeyEvent.VK_C);
		jCSSearch.setToolTipText(Messages.getString("SearchOnlyTextMatchesSearch"));
		jSearchPanel.add(jCSSearch);
		jRESpace = Box.createRigidArea(new Dimension(5, 0));
		jSearchPanel.add(jRESpace);
		jRESearch = new JCheckBox("RegEx", configuration.getGUILogSearchRegEx());
		jRESearch.setMnemonic(KeyEvent.VK_R);
		jRESearch.setToolTipText(Messages.getString("SearchUsingRegularExpressions"));
		jSearchPanel.add(jRESearch);
		jMLSpace = Box.createRigidArea(new Dimension(5, 0));
		jSearchPanel.add(jMLSpace);
		jMLSearch = new JCheckBox(Messages.getString("Multiline"), configuration.getGUILogSearchMultiLine());
		jMLSearch.setMnemonic(KeyEvent.VK_M);
		jMLSearch.setToolTipText(Messages.getString("EnableSearchSpanMultipleLines"));
		jSearchPanel.add(jMLSearch);
		jBufferSpace1 = Box.createRigidArea(new Dimension(4, 0));
		jBufferSeparator = new JSeparator(SwingConstants.VERTICAL);
		jBufferSpace2 = Box.createRigidArea(new Dimension(4, 0));
		jSearchPanel.add(jBufferSpace1);
		jSearchPanel.add(jBufferSeparator);
		jSearchPanel.add(jBufferSpace2);
		jBufferLabel = new JLabel(Messages.getString("LineBuffer"));
		jBufferLabel.setDisplayedMnemonic(KeyEvent.VK_B);
		jBufferLabel.setToolTipText(Messages.getString("NumberLinesLogWindowBelow"));
		jLineBuffer = new CustomJSpinner(new SpinnerIntModel(
			configuration.getLoggingLogsTabLinebuffer(),
			UmsConfiguration.getLoggingLogsTabLinebufferMin(),
			UmsConfiguration.getLoggingLogsTabLinebufferMax(),
			UmsConfiguration.getLoggingLogsTabLinebufferStep()
		), true);
		jLineBuffer.setToolTipText(Messages.getString("NumberLinesLogWindowBelow"));
		jBufferLabel.setLabelFor(jLineBuffer);
		jSearchPanel.add(jBufferLabel);
		jBufferSpace3 = Box.createRigidArea(new Dimension(5, 0));
		jSearchPanel.add(jBufferSpace3);
		jSearchPanel.add(jLineBuffer);

		jLineBuffer.addChangeListener((ChangeEvent e) -> {
			jList.setMaxLines((Integer) jLineBuffer.getValue());
			configuration.setLoggingLogsTabLinebuffer(jList.getMaxLines());
		});

		jSearchBox.addActionListener((ActionEvent e) -> searchTraces());

		jSearchButton.addActionListener((ActionEvent e) -> searchTraces());

		jCSSearch.addActionListener((ActionEvent e) -> configuration.setGUILogSearchCaseSensitive(jCSSearch.isSelected()));

		jRESearch.addActionListener((ActionEvent e) -> configuration.setGUILogSearchRegEx(jRESearch.isSelected()));

		jMLSearch.addActionListener((ActionEvent e) -> configuration.setGUILogSearchMultiLine(jMLSearch.isSelected()));

		builder.add(jSearchPanel).at(cc.xyw(1, 1, cols));

		// Create traces text box
		jList = new TextAreaFIFO(configuration.getLoggingLogsTabLinebuffer(), 500);
		jList.setEditable(false);
		jList.setBackground(Color.WHITE);
		jList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, jList.getFont().getSize()));

		final JPopupMenu popup = new JPopupMenu();
		Action copy = jList.getActionMap().get("copy-to-clipboard");
		JMenuItem copyItem = new JMenuItem(copy);
		copyItem.setText(Messages.getString("Copy"));
		popup.add(copyItem);
		popup.addSeparator();
		JMenuItem clearItem = new JMenuItem(Messages.getString("Clear"));
		clearItem.addActionListener((ActionEvent e) -> jList.setText(""));
		popup.add(clearItem);
		jList.addMouseListener(new PopupTriggerMouseListener(popup, jList));

		jList.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					searchTraces();
				}
			}
			@Override
			public void keyReleased(KeyEvent e) {
				//nothing to do
			}
			@Override
			public void keyTyped(KeyEvent e) {
				//nothing to do
			}
		});

		jListPane = new JScrollPane(jList, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		jListPane.setBorder(BorderFactory.createEmptyBorder());
		new SmartScroller(jListPane);
		builder.add(jListPane).at(cc.xyw(1, 2, cols));

		// Create the logging options panel
		jOptionsPanel = new JPanel();
		jOptionsPanel.setLayout(new BoxLayout(jOptionsPanel, BoxLayout.LINE_AXIS));
		jOptionsPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(5, 5, 5, 5),
			BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(
					BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
					Messages.getString("LoggingOptions")
				),
				BorderFactory.createEmptyBorder(10, 5, 10, 5)
			)
		));

		JCheckBox jBuffered = new JCheckBox(Messages.getString("BufferedLogging"), configuration.getLoggingBuffered());
		jBuffered.setMnemonic(KeyEvent.VK_U);
		jBuffered.setToolTipText(Messages.getString("EnableBufferedFileLogging"));
		jBuffered.setHorizontalTextPosition(SwingConstants.LEADING);
		jBuffered.addActionListener((ActionEvent e) -> {
			if (PMS.getTraceMode() == 2 && jBuffered.isSelected()) {
				jBuffered.setSelected(false);
				return;
			}
			configuration.setLoggingBuffered(jBuffered.isSelected());
			LoggingConfig.setBuffered(jBuffered.isSelected());
		});
		jOptionsPanel.add(jBuffered);

		jOptionsPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		boolean useSyslog = configuration.getLoggingUseSyslog();
		jUseSyslog = new JCheckBox(Messages.getString("LogToSyslog"), useSyslog);
		jUseSyslog.setMnemonic(KeyEvent.VK_Y);
		jUseSyslog.setToolTipText(Messages.getString("SendLogSyslogServer"));
		jUseSyslog.setHorizontalTextPosition(SwingConstants.LEADING);
		jUseSyslog.addActionListener((ActionEvent e) -> {
			if (PMS.getTraceMode() == 2 && jUseSyslog.isSelected()) {
				jUseSyslog.setSelected(false);
				return;
			} else if (jSyslogHost.getText().trim().isEmpty()) {
				jSearchOutput.setText(Messages.getString("SyslogHostnameCantBeEmpty"));
				jUseSyslog.setSelected(false);
				return;
			}
			jSearchOutput.setText("");
			boolean useSyslog1 = jUseSyslog.isSelected();
			configuration.setLoggingUseSyslog(useSyslog1);
			LoggingConfig.setSyslog();
			jSyslogHost.setEnabled(!useSyslog1);
			jSyslogPort.setEnabled(!useSyslog1);
			jSyslogFacility.setEnabled(!useSyslog1);
		});
		jOptionsPanel.add(jUseSyslog);

		jOptionsPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		JLabel jSyslogHostLabel = new JLabel(Messages.getString("SyslogHostname"));
		jSyslogHostLabel.setDisplayedMnemonic(KeyEvent.VK_N);
		jSyslogHostLabel.setToolTipText(Messages.getString("HostNameIpAddressSend"));
		jOptionsPanel.add(jSyslogHostLabel);
		jOptionsPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		jSyslogHost = new JTextField(configuration.getLoggingSyslogHost(), 10);
		jSyslogHostLabel.setLabelFor(jSyslogHost);
		jSyslogHost.setToolTipText(Messages.getString("HostNameIpAddressSend"));
		jSyslogHost.setEnabled(!useSyslog);
		jSyslogHost.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) { }
			@Override
			public void keyReleased(KeyEvent e) { }
			@Override
			public void keyPressed(KeyEvent e) {
				if  (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					jSyslogHost.setText(configuration.getLoggingSyslogHost());
				} else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					FocusManager.getCurrentManager().focusNextComponent();
				}
			}
		});
		jSyslogHost.setInputVerifier(new InputVerifier() {
			@Override
			public boolean verify(JComponent input) {
				String s = ((JTextField) input).getText().trim();
				if (!s.isEmpty() && !(
					// Hostname or IPv4
					s.matches("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$") ||
					// Simplified IPv6
					s.matches("(^([0-9a-fA-F]{0,4}:)+[0-9a-fA-F]{1,4}([0-9a-fA-F]{0,4}:)*$)|(^([0-9a-fA-F]{0,4}:)*[0-9a-fA-F]{1,4}([0-9a-fA-F]{0,4}:)+$)|(^::$)")
				)) {
					jSearchOutput.setText(String.format(Messages.getString("XIsntValidHostnameIp"), s));
					return false;
				}
				jSearchOutput.setText("");
				configuration.setLoggingSyslogHost(jSyslogHost.getText().trim());
				return true;
			}
		});
		jOptionsPanel.add(jSyslogHost);

		jOptionsPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		JLabel jSyslogPortLabel = new JLabel(Messages.getString("SyslogPort"));
		jSyslogPortLabel.setToolTipText(Messages.getString("UdpPortSendSyslogMessages"));
		jOptionsPanel.add(jSyslogPortLabel);
		jOptionsPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		jSyslogPort = new CustomJSpinner(new SpinnerIntModel(configuration.getLoggingSyslogPort(), 1, 65535), true);
		jSyslogPort.setToolTipText(Messages.getString("UdpPortSendSyslogMessages"));
		jSyslogPort.setEnabled(!useSyslog);
		jSyslogPortLabel.setLabelFor(jSyslogPort);
		jSyslogPort.addChangeListener((ChangeEvent e) -> configuration.setLoggingSyslogPort((Integer) jSyslogPort.getValue()));
		jOptionsPanel.add(jSyslogPort);

		jOptionsPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		JLabel jSyslogFacilityLabel = new JLabel(Messages.getString("SyslogFacility"));
		jSyslogFacilityLabel.setDisplayedMnemonic(KeyEvent.VK_A);
		jSyslogFacilityLabel.setToolTipText(Messages.getString("SyslogFacilityMeantIdentifySource"));
		jOptionsPanel.add(jSyslogFacilityLabel);
		jOptionsPanel.add(Box.createRigidArea(new Dimension(4, 0)));
		jSyslogFacility = new JComboBox<>(syslogFacilities);
		jSyslogFacility.setToolTipText(Messages.getString("SyslogFacilityMeantIdentifySource"));
		jSyslogFacility.setEnabled(!useSyslog);
		jSyslogFacility.setSelectedIndex(findSyslogFacilityIdx(configuration.getLoggingSyslogFacility()));
		jSyslogFacilityLabel.setLabelFor(jSyslogFacility);
		jSyslogFacility.addActionListener((ActionEvent e) -> configuration.setLoggingSyslogFacility(syslogFacilities[jSyslogFacility.getSelectedIndex()]));
		jOptionsPanel.add(jSyslogFacility);

		jOptionsPanel.setFocusTraversalPolicyProvider(true);
		builder.add(jOptionsPanel).at(cc.xyw(1, 3, cols));

		jShowOptions = new JCheckBox(Messages.getString("ShowLoggingOptions"), PMS.getTraceMode() != 2 && configuration.getLoggingUseSyslog());
		jShowOptions.setHorizontalTextPosition(SwingConstants.LEADING);
		jShowOptions.setToolTipText(Messages.getString("ShowLoggingConfigurationOptions"));
		jShowOptions.setMnemonic(KeyEvent.VK_G);
		jShowOptions.setEnabled(PMS.getTraceMode() != 2);
		jShowOptions.addActionListener((ActionEvent e) -> {
			jOptionsPanel.setVisible(jShowOptions.isSelected());
			if (jShowOptions.isSelected()) {
				jBuffered.requestFocusInWindow();
			}
		});
		builder.add(jShowOptions).at(cc.xy(3, 4));

		// Add buttons to open logfiles (there may be more than one)
		JPanel pLogFileButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		Map<String, String> logFiles = LoggingConfig.getLogFilePaths();
		for (Map.Entry<String, String> logger : logFiles.entrySet()) {
			String loggerNameDisplay = logger.getKey();
			if (logger.getKey().toLowerCase().startsWith("default.log")) {
				loggerNameDisplay = Messages.getString("OpenFullDebugLog");
			}
			CustomJButton b = new CustomJButton(loggerNameDisplay);
			if (!logger.getKey().equals(loggerNameDisplay)) {
				b.setMnemonic(KeyEvent.VK_O);
			}
			b.setToolTipText(logger.getValue());
			b.addActionListener((ActionEvent e) -> {
				File logFile = new File(((CustomJButton) e.getSource()).getToolTipText());
				try {
					Desktop.getDesktop().open(logFile);
				} catch (IOException | UnsupportedOperationException ioe) {
					LOGGER.error("Failed to open file \"{}\" in default editor: {}", logFile, ioe);
				}
			});
			pLogFileButtons.add(b);
		}
		builder.add(pLogFileButtons).at(cc.xy(cols, 4));

		final ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

		JLabel rootLevelLabel = new JLabel(Messages.getString("LogLevelColon") + ": ");
		rootLevelLabel.setDisplayedMnemonic(KeyEvent.VK_L);
		rootLevelLabel.setToolTipText(Messages.getString("SetRootLoggingLevelDecides"));

		JComboBox<String> rootLevel = new JComboBox<>(levelStrings);
		rootLevelLabel.setLabelFor(rootLevel);
		rootLevel.setSelectedIndex(findLevelsIdx(rootLogger.getLevel()));
		rootLevel.setToolTipText(Messages.getString("SetRootLoggingLevelDecides"));
		rootLevel.addActionListener((ActionEvent e) -> {
			JComboBox<?> cb = (JComboBox<?>) e.getSource();
			rootLogger.setLevel(logLevels[cb.getSelectedIndex()]);
			Level newLevel = rootLogger.getLevel();
			if (newLevel.toInt() > Level.INFO_INT) {
				rootLogger.setLevel(Level.INFO);
			}
			LOGGER.info("Changed debug level to " + newLevel);
			if (newLevel != rootLogger.getLevel()) {
				rootLogger.setLevel(newLevel);
			}
			configuration.setRootLogLevel(newLevel);
		});

		builder.add(rootLevelLabel).at(cc.xy(5, 4));
		builder.add(rootLevel).at(cc.xy(6, 4));
		if (PMS.getTraceMode() == 2) {
			// Forced trace mode
			rootLevel.setEnabled(false);
		}

		// Add buttons to pack logs (there may be more than one)
		JPanel pLogPackButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));

		if (PMS.getTraceMode() == 0) {
			// UMS was not started in trace mode
			CustomJButton rebootTrace = new CustomJButton(Messages.getString("CreateTraceLogs"));
			rebootTrace.setToolTipText(Messages.getString("RestartUniversalMediaServerTrace"));
			rebootTrace.setMnemonic(KeyEvent.VK_T);
			rebootTrace.addActionListener((ActionEvent e) -> {
				int opt = JOptionPane.showConfirmDialog(null, Messages.getString("ForReportingMostIssuesBest"),
						Messages.getString("StartupLogLevelNotTrace"), JOptionPane.YES_NO_OPTION);
				if (opt == JOptionPane.YES_OPTION) {
					ProcessUtil.reboot("trace");
				}
			});
			pLogPackButtons.add(rebootTrace);
		}

		CustomJButton packDbg = new CustomJButton(Messages.getString("PackDebugFiles"));
		packDbg.setMnemonic(KeyEvent.VK_P);
		packDbg.setToolTipText(Messages.getString("PackLogConfigurationFileCompressed"));
		packDbg.addActionListener((ActionEvent e) -> {
			JComponent comp = new DbgPackerComponent(PMS.get().debugPacker()).config();
			String[] cancelStr = {Messages.getString("Close")};
			JOptionPane.showOptionDialog(looksFrame,
					comp, Messages.getString("Options"), JOptionPane.CLOSED_OPTION, JOptionPane.PLAIN_MESSAGE, null, cancelStr, null);
		});
		pLogPackButtons.add(packDbg);
		builder.add(pLogPackButtons).at(cc.xy(1, 4));
		builder.add(jSearchOutput).at(cc.xy(2, 4));

		JPanel builtPanel = builder.getPanel();
		// Add a Ctrl + F shortcut to search field
		builtPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "Ctrl_F");
		builtPanel.getActionMap().put("Ctrl_F", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				jSearchBox.requestFocusInWindow();
			}
		});
		builtPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "F3");
		builtPanel.getActionMap().put("F3", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchTraces();
			}
		});
		applyViewLevel();
		return builtPanel;
	}
}