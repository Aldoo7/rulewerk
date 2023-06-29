package org.semanticweb.rulewerk.clingo;

/*-
 * #%L
 * rulewerk-clingo
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

import org.semanticweb.rulewerk.core.model.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.semanticweb.rulewerk.core.model.api.TermType.EXISTENTIAL_VARIABLE;
import static org.semanticweb.rulewerk.core.model.api.TermType.UNIVERSAL_VARIABLE;

public class ClingoStatementTransformer implements StatementVisitor<String> {

	private int currentLineNumber = 1;

	private final Map<String, String> skolemizedExistentialVariables = new HashMap<>();

	public final String LINE_SEPARATOR = " .";
	public final String STATEMENT_SEPARATOR = " :- ";

	private final String TERM_SEPARATOR = ", ";
	private final String TERM_LIST_PREFIX = "(";
	private final String TERM_LIST_SUFFIX = ")";
	private final String CONJ_SEPARATOR = " | ";

	@Override
	public String visit(Fact statement) {
		String transformedPredicate = visit(statement.getPredicate());
		String transformedTerms = visit(statement.getTerms());
		return transformedPredicate + transformedTerms + LINE_SEPARATOR;
	}

	@Override
	public String visit(Rule statement) {
		String transformedHead = visitPositiveLiteralDisjunction(statement.getHead());
		String transformedBody = visitLiteralDisjunction(statement.getBody());
		return transformedHead + STATEMENT_SEPARATOR + transformedBody + LINE_SEPARATOR;
	}

	@Override
	public String visit(DataSourceDeclaration statement) {
		return "";
	}

	public void setCurrentLineNumber(int lineNumber) {
		skolemizedExistentialVariables.clear();
		currentLineNumber = lineNumber + 1;
	}

	private String visit(Predicate predicate) {
		return transformSyntax(predicate.getName());
	}

	private String visit(List<Term> terms) {
		StringBuilder sb = new StringBuilder();
		sb.append(TERM_LIST_PREFIX);

		int termsSize = terms.size();
		for (int i = 0; i < termsSize; i++) {
			Term term = terms.get(i);
			String termName = transformSyntax(term.getName());
			if (term.getType() == EXISTENTIAL_VARIABLE) {
				if (skolemizedExistentialVariables.containsKey(termName)) {
					termName = skolemizedExistentialVariables.get(termName);
				} else {
					Optional<Term> uv = terms.stream().filter(t -> t.getType() == UNIVERSAL_VARIABLE).findFirst();
					if (uv.isPresent()) {
						termName = "f" + currentLineNumber + TERM_LIST_PREFIX + uv.get().getName() + TERM_LIST_SUFFIX;
						skolemizedExistentialVariables.put(term.getName(), termName);
					}
				}
			}
			sb.append(termName);
			if (i < termsSize - 1) sb.append(TERM_SEPARATOR);
		}

		sb.append(TERM_LIST_SUFFIX);
		return sb.toString();
	}

	private String visit(Stream<Term> terms) {
		return visit(terms.collect(Collectors.toList()));
	}

	private String visitPositiveLiteralDisjunction(Disjunction<Conjunction<PositiveLiteral>> disjunction) {
		List<Conjunction<PositiveLiteral>> conjunctions = disjunction.getConjunctions();

		StringBuilder sb = new StringBuilder();

		int conjunctionsSize = conjunctions.size();
		for (int i = 0; i < conjunctionsSize; i++) {
			Conjunction<PositiveLiteral> conjunction = conjunctions.get(i);
			sb.append(visitPositiveLiteralConjuction(conjunction));
			if (i < conjunctionsSize - 1) sb.append(CONJ_SEPARATOR);
		}

		return sb.toString();
	}

	public String visitLiteralDisjunction(Disjunction<Conjunction<Literal>> disjunction) {
		List<Conjunction<Literal>> conjunctions = disjunction.getConjunctions();

		StringBuilder sb = new StringBuilder();

		int conjunctionsSize = conjunctions.size();
		for (int i = 0; i < conjunctionsSize; i++) {
			Conjunction<Literal> conjunction = conjunctions.get(i);
			sb.append(visitLiteralConjuction(conjunction));
			if (i < conjunctionsSize - 1) sb.append(CONJ_SEPARATOR);
		}

		return sb.toString();
	}

	private String visitPositiveLiteralConjuction(Conjunction<PositiveLiteral> conjuction) {
		Stream<PositiveLiteral> literals = conjuction.getLiterals().stream();
		return visitPositiveLiteralsStream(literals);
	}

	private String visitLiteralConjuction(Conjunction<Literal> conjuction) {
		Stream<PositiveLiteral> literals = conjuction
			.getLiterals()
			.stream()
			.filter(literal -> literal instanceof PositiveLiteral)
			.map(literal -> (PositiveLiteral) literal);
		return visitPositiveLiteralsStream(literals);
	}

	private String visitPositiveLiteralsStream(Stream<PositiveLiteral> literals) {
		List<PositiveLiteral> positiveLiterals = literals.collect(Collectors.toList());
		StringBuilder sb = new StringBuilder();

		int literalsSize = positiveLiterals.size();
		for (int i = 0; i < literalsSize; i++) {
			sb.append(visit(positiveLiterals.get(i)));
			if (i < literalsSize - 1) sb.append(TERM_SEPARATOR);
		}

		return sb.toString();
	}

	public String visit(PositiveLiteral literal) {
		return visit(literal.getPredicate()) + visit(literal.getTerms());
	}

	private String transformSyntax(String line) {
		return line
			.replace("://", "_")
			.replace("/", "_")
			.replace("#", "_")
			.replace(".", "_")
			.replace("-", "_")
			.replace("*", "_")
			.replace(":", "_")
			.replace("~", "_")
			.replace(";", "_")
			.replace("&", "_")
			.replace("%", "_")
			.replaceAll("\\P{Alnum}", "_");
	}
}
