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

import org.semanticweb.rulewerk.clingo.Clingo;
import org.semanticweb.rulewerk.clingo.ClingoSolver;
import org.semanticweb.rulewerk.clingo.ClingoStatementTransformer;
import org.semanticweb.rulewerk.core.model.api.Command;
import org.semanticweb.rulewerk.core.model.api.Statement;
import org.semanticweb.rulewerk.core.reasoner.KnowledgeBase;
import org.semanticweb.rulewerk.core.reasoner.Timer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ClingoCommandInterpreter implements CommandInterpreter {

	@Override
	public void run(Command command, Interpreter interpreter) throws CommandExecutionException {
		final Timer timer = new Timer("clingo");
		KnowledgeBase kb = interpreter.getKnowledgeBase();
		timer.start();

		try (ClingoSolver solver = instantiateSolver()) {
			solver.exec();

			ClingoStatementTransformer clingoTransformer = new ClingoStatementTransformer();
			BufferedWriter writer = solver.getWriterToSolver();

			List<Statement> statements = new ArrayList<>(kb.getStatements());

			for (int i = 0; i < statements.size(); i++) {
				Statement statement = statements.get(i);
				clingoTransformer.setCurrentLineNumber(i);
				String transformedStatement = statement.accept(clingoTransformer);
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
	}

	@Override
	public void printHelp(String commandName, Interpreter interpreter) {
		interpreter.printNormal("Usage: @" + commandName + "\n");
	}

	@Override
	public String getSynopsis() {
		return "Run skolemization and then Clingo on a loaded knowledge base.";
	}

	public ClingoSolver instantiateSolver() throws IOException {
		return new Clingo(0);
	}
}
