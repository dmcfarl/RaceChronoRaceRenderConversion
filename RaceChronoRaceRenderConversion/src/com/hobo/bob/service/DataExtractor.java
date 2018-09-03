package com.hobo.bob.service;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.hobo.bob.ConversionConstants;
import com.hobo.bob.model.DataRow;
import com.hobo.bob.model.Lap;
import com.hobo.bob.model.Sector;
import com.hobo.bob.model.Session;

public class DataExtractor {
	private String sessionFile;
	private String lapsFile;

	public DataExtractor(String sessionFile, String lapsFile) {
		this.sessionFile = sessionFile;
		this.lapsFile = lapsFile;
	}

	public Session extract() throws IOException {
		Session session = extractLaps(lapsFile);

		try (BufferedReader sessionReader = new BufferedReader(new FileReader(sessionFile))) {
			clearUnusedHeaderData(sessionReader);

			Deque<DataRow> dataBuffer = new LinkedList<>();

			session.setHeaders(extractHeaders(sessionReader));

			DataRow.setRowConf(session.getHeaders().indexOf(ConversionConstants.TIME_HEADER),
					session.getHeaders().indexOf(ConversionConstants.LAP_HEADER),
					session.getHeaders().indexOf(ConversionConstants.TRAP_HEADER));
			String line;
			while (session.getBest().getLapData() == null && (line = sessionReader.readLine()) != null) {
				DataRow row = new DataRow(line);
				dataBuffer.add(row);
				while (dataBuffer.peek().getTime() < row.getTime() - ConversionConstants.LAP_BUFFER) {
					dataBuffer.pop();
				}

				if (session.getBest().getLapNum() == row.getLap()) {
					readLap(session.getBest(), sessionReader, dataBuffer, row);
				} else if (session.getGhost() != null && session.getGhost().getLapNum() == row.getLap()) {
					readLap(session.getGhost(), sessionReader, dataBuffer, row);

					if (session.getBest().getLapNum() == dataBuffer.peekLast().getLap()) {
						Iterator<DataRow> iter = dataBuffer.descendingIterator();
						DataRow bestStart = iter.next();
						while (iter.hasNext() && bestStart != null) {
							if (session.getBest().getLapNum() != bestStart.getLap()) {
								break;
							}
							bestStart = iter.next();
						}

						readLap(session.getBest(), sessionReader, dataBuffer, bestStart);
					}
				}
			}
		}

		return session;
	}

	private void clearUnusedHeaderData(BufferedReader sessionReader) throws IOException {
		String line = "a";
		while (!line.isEmpty() && !line.startsWith(",")) {
			line = sessionReader.readLine();
		}
	}

	private List<String> extractHeaders(BufferedReader sessionReader) throws IOException {
		String line;
		if ((line = sessionReader.readLine()).isEmpty()) {
			line = sessionReader.readLine();
		}

		return Arrays.asList(line.split(",", -1));
	}

	private Session extractLaps(String lapsFile) throws FileNotFoundException, IOException {
		Session session = new Session();
		int currentLap = 1;
		try (BufferedReader lapReader = new BufferedReader(new FileReader(lapsFile))) {
			Lap lap = new Lap(currentLap);
			String line = lapReader.readLine();
			try {
				lap.setLapTime(Double.parseDouble(line));
			} catch (NumberFormatException e) {
				line = lapReader.readLine();
				try {
					lap.setLapTime(Double.parseDouble(line));
				} catch (NumberFormatException e1) {
					throw new IllegalArgumentException("Unable to parse laps file.", e1);
				}
			}
			session.addLap(lap);

			while ((line = lapReader.readLine()) != null) {
				currentLap++;
				lap = new Lap(currentLap);
				try {
					lap.setLapTime(Double.parseDouble(line));
				} catch (NumberFormatException e1) {
					throw new IllegalArgumentException("Unable to parse laps file.", e1);
				}
				session.addLap(lap);
			}
		}

		return session;
	}

	private void readLap(Lap lap, BufferedReader sessionReader, Deque<DataRow> dataBuffer, DataRow lapStart)
			throws IOException {
		lap.setLapStart(lapStart.getTime());

		String line;
		DataRow row = null;
		while ((line = sessionReader.readLine()) != null && (row = new DataRow(line)).getLap() == lapStart.getLap()) {
			dataBuffer.add(row);
			if (row.getTrap() != null && !row.getTrap().isEmpty()) {
				lap.addSector(new Sector(row.getTime()));
			}
		}

		lap.setLapFinish(dataBuffer.peekLast().getTime());

		if (row != null) {
			if (!lap.getSectors().isEmpty()
					&& row.getTime() < lap.getSectors().get(lap.getSectors().size() - 1).getTime() + 2) {
				lap.getSectors().remove(lap.getSectors().size() - 1);
			}
			dataBuffer.add(row);

			Deque<DataRow> lapCooldown = new LinkedList<>();
			while ((line = sessionReader.readLine()) != null
					&& (row = new DataRow(line)).getTime() < lap.getLapFinish() + ConversionConstants.LAP_BUFFER) {
				lapCooldown.add(row);
			}

			lap.addLapData(dataBuffer);
			lap.addLapData(lapCooldown);

			dataBuffer.clear();
			dataBuffer.addAll(lapCooldown);
		}
	}
}