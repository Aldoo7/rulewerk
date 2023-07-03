package org.semanticweb.rulewerk.commands;

/*-
 * #%L
 * Rulewerk command execution support
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

import org.semanticweb.rulewerk.core.model.api.Command;
import org.semanticweb.rulewerk.core.model.api.Statement;
import org.semanticweb.rulewerk.core.reasoner.KnowledgeBase;
import org.semanticweb.rulewerk.core.reasoner.Timer;
import org.semanticweb.rulewerk.dlv.DLV;
import org.semanticweb.rulewerk.dlv.DLVSolver;
import org.semanticweb.rulewerk.dlv.DLVStatementTransformer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DLVCommandInterpreter implements CommandInterpreter {

	@Override
	public void run(Command command, Interpreter interpreter) {
		final Timer timer = new Timer("dlv");
		KnowledgeBase kb = interpreter.getKnowledgeBase();
		timer.start();

		try (DLVSolver solver = instantiateSolver()) {
			solver.exec();

			DLVStatementTransformer dlvTransformer = new DLVStatementTransformer();
			BufferedWriter writer = solver.getWriterToSolver();

			List<Statement> statements = new ArrayList<>(kb.getStatements());

			for (int i = 0; i < statements.size(); i++) {
				Statement statement = statements.get(i);
				dlvTransformer.setCurrentLineNumber(i);
				String transformedStatement = statement.accept(dlvTransformer);
				try {
					writer.write(transformedStatement + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			solver.solve();
			solver.getReaderFromSolver().lines().forEach(System.out::println);
		} catch (InterruptedException | IOException interruptedException) {
			interruptedException.printStackTrace();
		}

		timer.stop();

		interpreter.printNormal("... finished in " + timer.getTotalWallTime() / 1000000 + "ms ("
			+ timer.getTotalCpuTime() / 1000000 + "ms CPU time).\n");


		//long millis = timer.getTotalCpuTime() / 1000 / 1000;
		//double seconds = millis / 1000.0;
		//interpreter.printNormal("Took " + seconds + " seconds.\n");

	}

	@Override
	public void printHelp(String commandName, Interpreter interpreter) {
		interpreter.printNormal("Usage: @" + commandName + "\n");
	}

	@Override
	public String getSynopsis() {
		return "Run DLV solver on a loaded knowledge base.";
	}

	public DLVSolver instantiateSolver() throws IOException {
		return new DLV(0);
	}
}
