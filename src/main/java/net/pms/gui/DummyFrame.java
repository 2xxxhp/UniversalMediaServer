/*
 * This file is part of Universal Media Server, based on PS3 Media Server.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.pms.gui;

import java.util.ArrayList;
import net.pms.configuration.RendererConfiguration;
import org.apache.commons.lang3.StringUtils;

public class DummyFrame implements IFrame {

	private final ArrayList<String> log;

	public DummyFrame() {
		log = new ArrayList<>();
	}

	@Override
	public void append(String msg) {
		log.add(msg);
	}

	@Override
	public void updateBuffer() {
	}

	@Override
	public void setReadValue(long v, String msg) {
	}

	@Override
	public void setConnectionState(EConnectionState connectionState) {
	}

	@Override
	public void setReloadable(boolean reload) {
	}

	@Override
	public void addEngines() {
	}

	@Override
	public void setStatusLine(String line) {
	}

	@Override
	public void setSecondaryStatusLine(String line) {
	}

	@Override
	public void addRenderer(RendererConfiguration renderer) {
	}

	@Override
	public void updateRenderer(RendererConfiguration renderer) {
	}

	@Override
	public void serverReady() {
	}

	@Override
	public void updateServerStatus() {
	}

	@Override
	public void setScanLibraryStatus(boolean enabled, boolean running) {
	}

	@Override
	public void enableWebUiButton() {
	}

	@Override
	public String getLog() {
		return StringUtils.join(log, "\n");
	}
}