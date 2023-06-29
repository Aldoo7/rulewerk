package org.semanticweb.rulewerk.commands;

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

		long millis = timer.getTotalCpuTime() / 1000 / 1000;
		double seconds = millis / 1000.0;
		interpreter.printNormal("Took " + seconds + " seconds.\n");

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
