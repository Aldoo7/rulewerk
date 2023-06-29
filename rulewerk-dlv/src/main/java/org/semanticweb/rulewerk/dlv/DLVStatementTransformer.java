package org.semanticweb.rulewerk.dlv;

import org.semanticweb.rulewerk.clingo.ClingoStatementTransformer;
import org.semanticweb.rulewerk.core.model.api.*;

import java.util.*;
import java.util.stream.Collectors;

public class DLVStatementTransformer extends ClingoStatementTransformer {

	@Override
	public String visit(Rule statement) {
		Disjunction<Conjunction<PositiveLiteral>> headDisjunction = statement.getHead();
		List<Conjunction<PositiveLiteral>> conjunctions = headDisjunction.getConjunctions();

		if (conjunctions.size() > 1) return super.visit(statement);
		return conjunctions.get(0)
			.getLiterals()
			.stream()
			.map(l -> visit(l) + STATEMENT_SEPARATOR + visitLiteralDisjunction(statement.getBody()) + LINE_SEPARATOR)
			.collect(Collectors.joining("\n"));
	}
}
