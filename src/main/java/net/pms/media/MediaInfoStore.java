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
package net.pms.media;

import com.sun.jna.Platform;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import net.pms.PMS;
import net.pms.configuration.UmsConfiguration;
import net.pms.database.MediaDatabase;
import net.pms.database.MediaTableFiles;
import net.pms.database.MediaTableTVSeries;
import net.pms.database.MediaTableVideoMetadata;
import net.pms.formats.Format;
import net.pms.media.video.metadata.MediaVideoMetadata;
import net.pms.parsers.FFmpegParser;
import net.pms.parsers.Parser;
import net.pms.util.APIUtils;
import net.pms.util.FileUtil;
import net.pms.util.InputFile;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaInfoStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(MediaInfoStore.class);
	private static final UmsConfiguration CONFIGURATION = PMS.getConfiguration();
	private static final Map<String, WeakReference<MediaInfo>> STORE = new HashMap<>();

	private MediaInfoStore() {
		//should not be instantiated
	}

	public static MediaInfo getMediaInfo(String filename, File file, Format format, int type) {
		synchronized (STORE) {
			if (STORE.containsKey(filename) && STORE.get(filename).get() != null) {
				return STORE.get(filename).get();
			}
			MediaInfo mediaInfo = null;
			Connection connection = null;
			InputFile input = new InputFile();
			input.setFile(file);
			try {
				if (CONFIGURATION.getUseCache()) {
					connection = MediaDatabase.getConnectionIfAvailable();
					if (connection != null) {
						connection.setAutoCommit(false);
						try {
							mediaInfo = MediaTableFiles.getMediaInfo(connection, filename, file.lastModified());
							if (mediaInfo != null) {
								if (!mediaInfo.isMediaParsed()) {
									Parser.parse(mediaInfo, input, format, type);
									MediaTableFiles.insertOrUpdateData(connection, filename, file.lastModified(), type, mediaInfo);
								}
								//ensure we have the mime type
								if (mediaInfo.getMimeType() == null) {
									Parser.postParse(mediaInfo, type);
									MediaTableFiles.insertOrUpdateData(connection, filename, file.lastModified(), type, mediaInfo);
								}
							}
						} catch (IOException | SQLException e) {
							LOGGER.debug("Error while getting cached information about {}, reparsing information: {}", filename, e.getMessage());
							LOGGER.trace("", e);
						}
					}
				}

				if (mediaInfo == null) {
					mediaInfo = new MediaInfo();

					if (format != null) {
						Parser.parse(mediaInfo, input, format, type);
					} else {
						// Don't think that will ever happen
						FFmpegParser.parse(mediaInfo, input, format, type);
					}

					mediaInfo.waitMediaParsing(5);
					if (connection != null && mediaInfo.isMediaParsed()) {
						try {
							MediaTableFiles.insertOrUpdateData(connection, filename, file.lastModified(), type, mediaInfo);
						} catch (SQLException e) {
							LOGGER.error(
								"Database error while trying to add parsed information for \"{}\" to the cache: {}",
								filename,
								e.getMessage());
							if (LOGGER.isTraceEnabled()) {
								LOGGER.trace("SQL error code: {}", e.getErrorCode());
								if (
									e.getCause() instanceof SQLException &&
									((SQLException) e.getCause()).getErrorCode() != e.getErrorCode()
								) {
									LOGGER.trace("Cause SQL error code: {}", ((SQLException) e.getCause()).getErrorCode());
								}
								LOGGER.trace("", e);
							}
						}
					}
				}
			} catch (SQLException e) {
				LOGGER.error("Error in RealFile.resolve: {}", e.getMessage());
				LOGGER.trace("", e);
			} finally {
				try {
					if (connection != null) {
						connection.commit();
						connection.setAutoCommit(true);
					}
				} catch (SQLException e) {
					LOGGER.error("Error in commit in RealFile.resolve: {}", e.getMessage());
					LOGGER.trace("", e);
				}
				MediaDatabase.close(connection);
			}
			if (mediaInfo != null) {
				STORE.put(filename, new WeakReference<>(mediaInfo));
			}
			return mediaInfo;
		}
	}

	public static MediaVideoMetadata getMediaVideoMetadata(String filename) {
		//check on store
		synchronized (STORE) {
			if (STORE.containsKey(filename) && STORE.get(filename).get() != null) {
				return STORE.get(filename).get().getVideoMetadata();
			}
		}
		//parse db
		Connection connection = null;
		try {
			connection = MediaDatabase.getConnectionIfAvailable();
			if (connection != null) {
				return MediaTableVideoMetadata.getVideoMetadataByFilename(connection, filename);
			}
		} finally {
			MediaDatabase.close(connection);
		}
		return null;
	}

	/**
	 * Populates the mediaInfo Title, Year, Edition, TVSeason, TVEpisodeNumber and
	 * TVEpisodeName parsed from the mediaInfo file name and if enabled insert them
	 * to the database.
	 *
	 * @param file
	 * @param mediaInfo
	 */
	public static void setMetadataFromFileName(final File file, MediaInfo mediaInfo) {
		String absolutePath = file.getAbsolutePath();
		if (absolutePath == null ||
			(Platform.isMac() &&
			// skip metadata extraction and API lookups for live photos (little MP4s) backed up from iPhones
			absolutePath.contains("Photos Library.photoslibrary"))
		) {
			return;
		}

		// If the in-memory mediaInfo has not already been populated with filename metadata, we attempt it
		try {
			if (!mediaInfo.hasVideoMetadata()) {
				MediaVideoMetadata videoMetadata = new MediaVideoMetadata();
				String[] metadataFromFilename = FileUtil.getFileNameMetadata(file.getName(), absolutePath);
				String titleFromFilename = metadataFromFilename[0];
				String yearFromFilename = metadataFromFilename[1];
				String extraInformationFromFilename = metadataFromFilename[2];
				String tvSeasonFromFilename = metadataFromFilename[3];
				String tvEpisodeNumberFromFilename = metadataFromFilename[4];
				String tvEpisodeNameFromFilename = metadataFromFilename[5];
				String titleFromFilenameSimplified = FileUtil.getSimplifiedShowName(titleFromFilename);

				videoMetadata.setMovieOrShowName(titleFromFilename);
				videoMetadata.setSimplifiedMovieOrShowName(titleFromFilenameSimplified);

				// Apply the metadata from the filename.
				if (StringUtils.isNotBlank(titleFromFilename) && StringUtils.isNotBlank(tvSeasonFromFilename)) {
					videoMetadata.setTVSeason(tvSeasonFromFilename);
					if (StringUtils.isNotBlank(tvEpisodeNumberFromFilename)) {
						videoMetadata.setTVEpisodeNumber(tvEpisodeNumberFromFilename);
					}
					if (StringUtils.isNotBlank(tvEpisodeNameFromFilename)) {
						videoMetadata.setTVEpisodeName(tvEpisodeNameFromFilename);
					}

					videoMetadata.setIsTVEpisode(true);
				}

				if (yearFromFilename != null) {
					if (videoMetadata.isTVEpisode()) {
						videoMetadata.setTVSeriesStartYear(yearFromFilename);
					} else {
						videoMetadata.setYear(yearFromFilename);
					}
				}

				if (extraInformationFromFilename != null) {
					videoMetadata.setExtraInformation(extraInformationFromFilename);
				}

				if (CONFIGURATION.getUseCache() && MediaDatabase.isAvailable()) {
					try (Connection connection = MediaDatabase.getConnectionIfAvailable()) {
						if (connection != null) {
							if (videoMetadata.isTVEpisode()) {
								/**
								* Overwrite the title from the filename if it's very similar to one
								* we already have in our database. This is to avoid minor
								* grammatical differences like "Word and Word" vs. "Word & Word"
								* from creating two virtual folders.
								*/
								String titleFromDatabase = MediaTableTVSeries.getSimilarTVSeriesName(connection, titleFromFilename);
								String titleFromDatabaseSimplified = FileUtil.getSimplifiedShowName(titleFromDatabase);
								if (titleFromFilenameSimplified.equals(titleFromDatabaseSimplified)) {
									videoMetadata.setMovieOrShowName(titleFromDatabase);
								}
							}
							mediaInfo.setVideoMetadata(videoMetadata);
							MediaTableVideoMetadata.insertVideoMetadata(connection, absolutePath, file.lastModified(), mediaInfo, false);

							// Creates a minimal TV series row with just the title, that
							// might be enhanced later by the API
							if (videoMetadata.isTVEpisode()) {
								// TODO: Make this check if it already exists instead of always setting it
								MediaTableTVSeries.set(connection, videoMetadata.getMovieOrShowName());
							}
						}
					}
				} else {
					mediaInfo.setVideoMetadata(videoMetadata);
				}
			}
		} catch (SQLException e) {
			LOGGER.error("Could not update the database with information from the filename for \"{}\": {}", file.getAbsolutePath(),
				e.getMessage());
			LOGGER.trace("", e);
		} catch (Exception e) {
			LOGGER.debug("", e);
		} finally {
			// Attempt to enhance the metadata via our API.
			APIUtils.backgroundLookupAndAddMetadata(file, mediaInfo);
		}
	}

	public static void clear() {
		synchronized (STORE) {
			STORE.clear();
		}
	}

}