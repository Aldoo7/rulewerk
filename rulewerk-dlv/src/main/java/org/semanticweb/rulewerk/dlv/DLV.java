package org.semanticweb.rulewerk.dlv;

/*-
 * #%L
 * rulewerk-dlv
 * %%
 * Copyright (C) 2018 - 2023 Rulewerk Developers
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
