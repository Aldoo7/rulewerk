package org.semanticweb.rulewerk.dlv;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class DLV implements DLVSolver {

	private final Integer timeout;

	private Process process;
	private BufferedReader reader;
	private BufferedWriter writer;

	/**
	 * Constructor. Creates a representation of a DLV process.
	 * @param timeout an optional timeout
	 *
	 */
	public DLV(Integer timeout) {
		this.timeout = timeout;
	}

	@Override
	public void exec() throws IOException {
		process = Runtime.getRuntime().exec("/Users/aldo/Downloads/dlv --stdin");
		writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
		reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

		BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		new Thread(() -> {
			String line;
			try {
				while ((line = errorReader.readLine()) != null) {
					System.err.println(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}).start();
	}

	/**
	 * Waits until underlying solver process returns. A timeout might be specified.
	 *
	 * @throws InterruptedException exception if the solving process was interrupted
	 */
	private void waitFor() throws InterruptedException {
		if (timeout == null) {
			process.waitFor();
		} else {
			process.waitFor(timeout, TimeUnit.SECONDS);
		}
	}

	@Override
	public void close() {
		process.destroy();
	}

	@Override
	public BufferedWriter getWriterToSolver() {
		return writer;
	}

	@Override
	public BufferedReader getReaderFromSolver() {
		return reader;
	}

	@Override
	public void solve() throws IOException, InterruptedException {
		writer.close();
		waitFor();
	}
}
