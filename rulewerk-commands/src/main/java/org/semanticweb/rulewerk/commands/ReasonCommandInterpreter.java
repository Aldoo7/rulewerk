package org.semanticweb.rulewerk.commands;

/*-
 * #%L
 * Rulewerk Core Components
 * %%
 * Copyright (C) 2018 - 2020 Rulewerk Developers
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

import java.io.IOException;
import java.util.concurrent.*;

import org.semanticweb.rulewerk.core.model.api.Command;
import org.semanticweb.rulewerk.core.reasoner.Timer;

public class ReasonCommandInterpreter implements CommandInterpreter {

	@Override
	public void run(Command command, Interpreter interpreter) throws CommandExecutionException {
		if (command.getArguments().size() > 0) {
			throw new CommandExecutionException("This command supports no arguments.");
		}

		interpreter.printNormal("Loading and materializing inferences ...\n");

		Timer timer = new Timer("reasoning");
		timer.start();

		// Create an ExecutorService to manage threads.
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> future = executor.submit(() -> {
			try {
				interpreter.getReasoner().reason();
			} catch (IOException e) {
				throw new RuntimeException("Exception in reason method", e);
			}
		});

		try {
			// Give the execution 30 seconds to complete.
			future.get(30, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			// Stop execution after 30 seconds.
			future.cancel(true);
			throw new CommandExecutionException("Execution took longer than 30 seconds", e);
		} catch (Exception e) {
			throw new CommandExecutionException(e.getMessage(), e);
		} finally {
			timer.stop();
			interpreter.printNormal("... finished in " + timer.getTotalWallTime() / 1000000 + "ms ("
				+ timer.getTotalCpuTime() / 1000000 + "ms CPU time).\n");
			executor.shutdownNow();
		}
	}

	@Override
	public void printHelp(String commandName, Interpreter interpreter) {
		interpreter.printNormal("Usage: @" + commandName + " .\n");
	}

	@Override
	public String getSynopsis() {
		return "load data and compute conclusions from knowledge base";
	}

}
