package org.semanticweb.rulewerk.dlv;

import java.io.*;

public interface DLVSolver extends AutoCloseable {

	/**
	 * Gets the writer which writes to the solver.
	 *
	 * @return a buffered writer
	 */
	BufferedWriter getWriterToSolver();

	/**
	 * Gets the reader to which the solver writes its results.
	 *
	 * @return a buffered reader
	 */
	BufferedReader getReaderFromSolver();

	/**
	 * Executes the solver process.
	 *
	 * @throws IOException an IO exception
	 */
	void exec() throws IOException;

	/**
	 * Performs the solving process.
	 *
	 * @throws IOException an IO exception
	 * @throws InterruptedException if the solving process was interrupted
	 */
	void solve() throws IOException, InterruptedException;

	@Override
	void close();
}
