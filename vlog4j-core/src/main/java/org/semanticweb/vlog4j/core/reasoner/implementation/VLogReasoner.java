package org.semanticweb.vlog4j.core.reasoner.implementation;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.semanticweb.vlog4j.core.exceptions.EdbIdbSeparationException;
import org.semanticweb.vlog4j.core.exceptions.IncompatiblePredicateArityException;
import org.semanticweb.vlog4j.core.exceptions.ReasonerStateException;
import org.semanticweb.vlog4j.core.model.api.DataSource;
import org.semanticweb.vlog4j.core.model.api.DataSourceDeclaration;
import org.semanticweb.vlog4j.core.model.api.Fact;
import org.semanticweb.vlog4j.core.model.api.Literal;
import org.semanticweb.vlog4j.core.model.api.PositiveLiteral;
import org.semanticweb.vlog4j.core.model.api.Predicate;
import org.semanticweb.vlog4j.core.model.api.Rule;
import org.semanticweb.vlog4j.core.model.api.Statement;
import org.semanticweb.vlog4j.core.model.api.StatementVisitor;
import org.semanticweb.vlog4j.core.model.api.Term;
import org.semanticweb.vlog4j.core.model.implementation.ConjunctionImpl;
import org.semanticweb.vlog4j.core.model.implementation.PositiveLiteralImpl;
import org.semanticweb.vlog4j.core.model.implementation.PredicateImpl;
import org.semanticweb.vlog4j.core.model.implementation.RuleImpl;
import org.semanticweb.vlog4j.core.model.implementation.VariableImpl;
import org.semanticweb.vlog4j.core.reasoner.AcyclicityNotion;
import org.semanticweb.vlog4j.core.reasoner.Algorithm;
import org.semanticweb.vlog4j.core.reasoner.CyclicityResult;
import org.semanticweb.vlog4j.core.reasoner.KnowledgeBase;
import org.semanticweb.vlog4j.core.reasoner.LogLevel;
import org.semanticweb.vlog4j.core.reasoner.MaterialisationState;
import org.semanticweb.vlog4j.core.reasoner.Reasoner;
import org.semanticweb.vlog4j.core.reasoner.ReasonerState;
import org.semanticweb.vlog4j.core.reasoner.RuleRewriteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import karmaresearch.vlog.AlreadyStartedException;
import karmaresearch.vlog.EDBConfigurationException;
import karmaresearch.vlog.MaterializationException;
import karmaresearch.vlog.NonExistingPredicateException;
import karmaresearch.vlog.NotStartedException;
import karmaresearch.vlog.TermQueryResultIterator;
import karmaresearch.vlog.VLog;
import karmaresearch.vlog.VLog.CyclicCheckResult;

/*
 * #%L
 * VLog4j Core Components
 * %%
 * Copyright (C) 2018 VLog4j Developers
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

/**
 * Reasoner implementation using the VLog backend.
 * 
 * @TODO Due to automatic predicate renaming, it can happen that an EDB
 *       predicate cannot be queried after loading unless reasoning has already
 *       been invoked (since the auxiliary rule that imports the EDB facts to
 *       the "real" predicate must be used). This issue could be weakened by
 *       rewriting queries to (single-source) EDB predicates internally when in
 *       such a state,
 * 
 * @author Markus Kroetzsch
 *
 */
public class VLogReasoner implements Reasoner {
	private static Logger LOGGER = LoggerFactory.getLogger(VLogReasoner.class);

	/**
	 * Dummy data source declaration for predicates for which we have explicit local
	 * facts in the input.
	 * 
	 * @author Markus Kroetzsch
	 *
	 */
	class LocalFactsDataSourceDeclaration implements DataSourceDeclaration {

		final Predicate predicate;

		public LocalFactsDataSourceDeclaration(Predicate predicate) {
			this.predicate = predicate;
		}

		@Override
		public <T> T accept(StatementVisitor<T> statementVisitor) {
			return statementVisitor.visit(this);
		}

		@Override
		public Predicate getPredicate() {
			return this.predicate;
		}

		@Override
		public DataSource getDataSource() {
			return null;
		}

		@Override
		public int hashCode() {
			return predicate.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final LocalFactsDataSourceDeclaration other = (LocalFactsDataSourceDeclaration) obj;
			return predicate.equals(other.predicate);
		}

	}

	/**
	 * Local visitor implementation for processing statements upon loading. Internal
	 * index structures are updated based on the statements that are detected.
	 * 
	 * @author Markus Kroetzsch
	 *
	 */
	class LoadKbVisitor implements StatementVisitor<Void> {

		public void clearIndexes() {
			edbPredicates.clear();
			idbPredicates.clear();
			aliasedEdbPredicates.clear();
			aliasesForEdbPredicates.clear();
			directEdbFacts.clear();
			rules.clear();
		}

		@Override
		public Void visit(Fact statement) {
			final Predicate predicate = statement.getPredicate();
			registerEdbDeclaration(new LocalFactsDataSourceDeclaration(predicate));
			if (!directEdbFacts.containsKey(predicate)) {
				final List<Fact> facts = new ArrayList<Fact>();
				facts.add(statement);
				directEdbFacts.put(predicate, facts);
			} else {
				directEdbFacts.get(predicate).add(statement);
			}
			return null;
		}

		@Override
		public Void visit(Rule statement) {
			rules.add(statement);
			for (final PositiveLiteral positiveLiteral : statement.getHead()) {
				final Predicate predicate = positiveLiteral.getPredicate();
				if (!idbPredicates.contains(predicate)) {
					if (edbPredicates.containsKey(predicate)) {
						addEdbAlias(edbPredicates.get(predicate));
						edbPredicates.remove(predicate);
					}
					idbPredicates.add(predicate);
				}
			}
			return null;
		}

		@Override
		public Void visit(DataSourceDeclaration statement) {
			registerEdbDeclaration(statement);
			return null;
		}

		void registerEdbDeclaration(DataSourceDeclaration dataSourceDeclaration) {
			final Predicate predicate = dataSourceDeclaration.getPredicate();
			if (idbPredicates.contains(predicate) || aliasedEdbPredicates.contains(predicate)) {
				if (!aliasesForEdbPredicates.containsKey(dataSourceDeclaration)) {
					addEdbAlias(dataSourceDeclaration);
				}
			} else {
				final DataSourceDeclaration currentMainDeclaration = edbPredicates.get(predicate);
				if (currentMainDeclaration == null) {
					edbPredicates.put(predicate, dataSourceDeclaration);
				} else if (!(currentMainDeclaration.equals(dataSourceDeclaration))) {
					addEdbAlias(currentMainDeclaration);
					addEdbAlias(dataSourceDeclaration);
					edbPredicates.remove(predicate);
				} // else: predicate already known to have local facts (only)
			}
		}

		void addEdbAlias(DataSourceDeclaration dataSourceDeclaration) {
			final Predicate predicate = dataSourceDeclaration.getPredicate();
			Predicate aliasPredicate;
			if (dataSourceDeclaration instanceof LocalFactsDataSourceDeclaration) {
				aliasPredicate = new PredicateImpl(predicate.getName() + "##FACT", predicate.getArity());
			} else {
				aliasPredicate = new PredicateImpl(predicate.getName() + "##" + dataSourceDeclaration.hashCode(),
						predicate.getArity());
			}
			aliasesForEdbPredicates.put(dataSourceDeclaration, aliasPredicate);
			aliasedEdbPredicates.add(predicate);

			final List<Term> terms = new ArrayList<>();
			for (int i = 1; i <= predicate.getArity(); i++) {
				terms.add(new VariableImpl("X" + i));
			}
			final Literal body = new PositiveLiteralImpl(aliasPredicate, terms);
			final PositiveLiteral head = new PositiveLiteralImpl(predicate, terms);
			final Rule rule = new RuleImpl(new ConjunctionImpl<PositiveLiteral>(Arrays.asList(head)),
					new ConjunctionImpl<Literal>(Arrays.asList(body)));
			rules.add(rule);
		}

	}

	final KnowledgeBase knowledgeBase;
	final VLog vLog = new VLog();

	final Map<DataSourceDeclaration, Predicate> aliasesForEdbPredicates = new HashMap<>();
	final Set<Predicate> idbPredicates = new HashSet<>();
	final Map<Predicate, DataSourceDeclaration> edbPredicates = new HashMap<>();
	final Set<Predicate> aliasedEdbPredicates = new HashSet<>();
	final Map<Predicate, List<Fact>> directEdbFacts = new HashMap<>();
	final Set<Rule> rules = new HashSet<>();

	private ReasonerState reasonerState = ReasonerState.BEFORE_LOADING;
	private MaterialisationState materialisationState = MaterialisationState.INCOMPLETE;

	private LogLevel internalLogLevel = LogLevel.WARNING;
	private Algorithm algorithm = Algorithm.RESTRICTED_CHASE;
	private Integer timeoutAfterSeconds;
	private RuleRewriteStrategy ruleRewriteStrategy = RuleRewriteStrategy.NONE;

	/**
	 * Holds the state of the reasoning result. Has value {@code true} if reasoning
	 * has completed, {@code false} if it has been interrupted.
	 */
	private boolean reasoningCompleted;

	public VLogReasoner(KnowledgeBase knowledgeBase) {
		super();
		this.knowledgeBase = knowledgeBase;
		this.knowledgeBase.addListener(this);
	}

	@Override
	public KnowledgeBase getKnowledgeBase() {
		return this.knowledgeBase;
	}

	@Override
	public void setAlgorithm(final Algorithm algorithm) {
		Validate.notNull(algorithm, "Algorithm cannot be null!");
		this.algorithm = algorithm;
		if (this.reasonerState.equals(ReasonerState.AFTER_CLOSING)) {
			LOGGER.warn("Setting algorithm on a closed reasoner.");
		}
	}

	@Override
	public Algorithm getAlgorithm() {
		return this.algorithm;
	}

	@Override
	public void setReasoningTimeout(Integer seconds) {
		if (seconds != null) {
			Validate.isTrue(seconds > 0, "Only strictly positive timeout period alowed!", seconds);
		}
		this.timeoutAfterSeconds = seconds;
		if (this.reasonerState.equals(ReasonerState.AFTER_CLOSING)) {
			LOGGER.warn("Setting timeout on a closed reasoner.");
		}
	}

	@Override
	public Integer getReasoningTimeout() {
		return this.timeoutAfterSeconds;
	}

	@Override
	public void setRuleRewriteStrategy(RuleRewriteStrategy ruleRewritingStrategy) throws ReasonerStateException {
		Validate.notNull(ruleRewritingStrategy, "Rewrite strategy cannot be null!");
		if (this.reasonerState != ReasonerState.BEFORE_LOADING) {
			throw new ReasonerStateException(this.reasonerState,
					"Rules cannot be re-writen after the reasoner has been loaded! Call reset() to undo loading and reasoning.");
		}
		this.ruleRewriteStrategy = ruleRewritingStrategy;
		LOGGER.warn("Setting rule rewrite strategy on a closed reasoner.");
	}

	@Override
	public RuleRewriteStrategy getRuleRewriteStrategy() {
		return this.ruleRewriteStrategy;
	}

	@Override
	public void load() throws IOException, IncompatiblePredicateArityException, ReasonerStateException {
		if (this.reasonerState == ReasonerState.AFTER_CLOSING) {
			throw new ReasonerStateException(this.reasonerState, "Loading is not allowed after closing.");
		}

		final LoadKbVisitor visitor = new LoadKbVisitor();
		visitor.clearIndexes();
		for (final Statement statement : knowledgeBase) {
			statement.accept(visitor);
		}

		if (edbPredicates.isEmpty() && aliasedEdbPredicates.isEmpty()) {
			LOGGER.warn("No facts have been provided.");
		}

		try {
			this.vLog.start(getDataSourceConfigurationString(), false);
		} catch (final AlreadyStartedException e) {
			throw new RuntimeException("Inconsistent reasoner state.", e);
		} catch (final EDBConfigurationException e) {
			throw new RuntimeException("Invalid data sources configuration.", e);
		}
		// TODO: can't we set this earlier? Why here?
		setLogLevel(this.internalLogLevel);

		validateDataSourcePredicateArities();

		loadFacts();
		loadRules();

		this.reasonerState = ReasonerState.AFTER_LOADING;
	}

	String getDataSourceConfigurationString() {
		final StringBuilder configStringBuilder = new StringBuilder();
		final Formatter formatter = new Formatter(configStringBuilder);
		int dataSourceIndex = 0;
		for (final Predicate predicate : this.edbPredicates.keySet()) {
			final DataSourceDeclaration dataSourceDeclaration = this.edbPredicates.get(predicate);
			if (dataSourceDeclaration.getDataSource() != null) {
				formatter.format(dataSourceDeclaration.getDataSource().toConfigString(), dataSourceIndex,
						ModelToVLogConverter.toVLogPredicate(predicate));
				dataSourceIndex++;
			}
		}
		for (final DataSourceDeclaration dataSourceDeclaration : this.aliasesForEdbPredicates.keySet()) {
			final Predicate aliasPredicate = this.aliasesForEdbPredicates.get(dataSourceDeclaration);
			if (dataSourceDeclaration.getDataSource() != null) {
				formatter.format(dataSourceDeclaration.getDataSource().toConfigString(), dataSourceIndex,
						ModelToVLogConverter.toVLogPredicate(aliasPredicate));
				dataSourceIndex++;
			}
		}
		formatter.close();
		return configStringBuilder.toString();
	}

	void validateDataSourcePredicateArities() throws IncompatiblePredicateArityException {
		for (final Predicate predicate : edbPredicates.keySet()) {
			validateDataSourcePredicateArity(predicate, edbPredicates.get(predicate).getDataSource());
		}
		for (final DataSourceDeclaration dataSourceDeclaration : aliasesForEdbPredicates.keySet()) {
			validateDataSourcePredicateArity(aliasesForEdbPredicates.get(dataSourceDeclaration),
					dataSourceDeclaration.getDataSource());
		}
	}

	void validateDataSourcePredicateArity(Predicate predicate, DataSource dataSource)
			throws IncompatiblePredicateArityException {
		if (dataSource == null)
			return;
		try {
			final int dataSourcePredicateArity = this.vLog
					.getPredicateArity(ModelToVLogConverter.toVLogPredicate(predicate));
			if (dataSourcePredicateArity == -1) {
				LOGGER.warn("Data source {} for predicate {} is empty: ", dataSource, predicate);
			} else if (predicate.getArity() != dataSourcePredicateArity) {
				throw new IncompatiblePredicateArityException(predicate, dataSourcePredicateArity, dataSource);
			}
		} catch (final NotStartedException e) {
			throw new RuntimeException("Inconsistent reasoner state.", e);
		}
	}

	void loadFacts() {
		for (final Predicate predicate : directEdbFacts.keySet()) {
			Predicate aliasPredicate;
			if (edbPredicates.containsKey(predicate)) {
				aliasPredicate = predicate;
			} else {
				aliasPredicate = aliasesForEdbPredicates.get(new LocalFactsDataSourceDeclaration(predicate));
			}
			try {
				String vLogPredicateName = ModelToVLogConverter.toVLogPredicate(aliasPredicate);
				String[][] vLogPredicateTuples = ModelToVLogConverter.toVLogFactTuples(directEdbFacts.get(predicate));
				this.vLog.addData(vLogPredicateName, vLogPredicateTuples);
				if (LOGGER.isDebugEnabled()) {
					for (String[] tuple : vLogPredicateTuples) {
						LOGGER.debug(
								"Loaded direct fact " + vLogPredicateName + "(" + Arrays.deepToString(tuple) + ")");
					}
				}
			} catch (final EDBConfigurationException e) {
				throw new RuntimeException("Invalid data sources configuration.", e);
			}
		}
	}

	void loadRules() {
		final karmaresearch.vlog.Rule[] vLogRuleArray = ModelToVLogConverter.toVLogRuleArray(rules);
		final karmaresearch.vlog.VLog.RuleRewriteStrategy vLogRuleRewriteStrategy = ModelToVLogConverter
				.toVLogRuleRewriteStrategy(this.ruleRewriteStrategy);
		try {
			this.vLog.setRules(vLogRuleArray, vLogRuleRewriteStrategy);
			if (LOGGER.isDebugEnabled()) {
				for (karmaresearch.vlog.Rule rule : vLogRuleArray) {
					LOGGER.debug("Loaded rule " + rule.toString());
				}
			}
		} catch (final NotStartedException e) {
			throw new RuntimeException("Inconsistent reasoner state.", e);
		}
	}

	@Override
	public boolean reason()
			throws IOException, ReasonerStateException, EdbIdbSeparationException, IncompatiblePredicateArityException {
		switch (this.reasonerState) {
		case BEFORE_LOADING:
			load();
			runChase();
			break;
		case AFTER_LOADING:
			runChase();
			break;

		case KNOWLEDGE_BASE_CHANGED:
		case AFTER_REASONING:
			resetReasoner();
			load();
			runChase();
			break;
		case AFTER_CLOSING:
			throw new ReasonerStateException(this.reasonerState, "Reasoning is not allowed after closing.");
		}
		return this.reasoningCompleted;
	}

	private void runChase() {
		this.reasonerState = ReasonerState.AFTER_REASONING;

		final boolean skolemChase = this.algorithm == Algorithm.SKOLEM_CHASE;
		try {
			if (this.timeoutAfterSeconds == null) {
				this.vLog.materialize(skolemChase);
				this.reasoningCompleted = true;
			} else {
				this.reasoningCompleted = this.vLog.materialize(skolemChase, this.timeoutAfterSeconds);
			}
			this.materialisationState = this.reasoningCompleted ? MaterialisationState.COMPLETE
					: MaterialisationState.INCOMPLETE;

		} catch (final NotStartedException e) {
			throw new RuntimeException("Inconsistent reasoner state.", e);
		} catch (final MaterializationException e) {
			throw new RuntimeException(
					"Knowledge base incompatible with stratified negation: either the Rules are not stratifiable, or the variables in negated atom cannot be bound.",
					e);
		}
	}

	@Override
	public QueryResultIterator answerQuery(PositiveLiteral query, boolean includeBlanks) throws ReasonerStateException {
		final boolean filterBlanks = !includeBlanks;
		if (this.reasonerState == ReasonerState.BEFORE_LOADING) {
			throw new ReasonerStateException(this.reasonerState, "Querying is not alowed before reasoner is loaded!");
		} else if (this.reasonerState.equals(ReasonerState.AFTER_CLOSING)) {
			throw new ReasonerStateException(this.reasonerState, "Querying is not allowed after closing.");
		}
		Validate.notNull(query, "Query atom must not be null!");

		final karmaresearch.vlog.Atom vLogAtom = ModelToVLogConverter.toVLogAtom(query);

		TermQueryResultIterator stringQueryResultIterator;
		try {
			stringQueryResultIterator = this.vLog.query(vLogAtom, true, filterBlanks);
		} catch (final NotStartedException e) {
			throw new RuntimeException("Inconsistent reasoner state.", e);
		} catch (final NonExistingPredicateException e1) {
			throw new IllegalArgumentException(MessageFormat.format(
					"The query predicate does not occur in the loaded Knowledge Base: {0}!", query.getPredicate()), e1);
		}

		return new QueryResultIterator(stringQueryResultIterator, this.materialisationState);
	}

	@Override
	public MaterialisationState exportQueryAnswersToCsv(final PositiveLiteral query, final String csvFilePath,
			final boolean includeBlanks) throws ReasonerStateException, IOException {
		final boolean filterBlanks = !includeBlanks;
		if (this.reasonerState == ReasonerState.BEFORE_LOADING) {
			throw new ReasonerStateException(this.reasonerState, "Querying is not alowed before reasoner is loaded!");
		} else if (this.reasonerState.equals(ReasonerState.AFTER_CLOSING)) {
			throw new ReasonerStateException(this.reasonerState, "Querying is not allowed after closing.");
		}
		Validate.notNull(query, "Query atom must not be null!");
		Validate.notNull(csvFilePath, "File to export query answer to must not be null!");
		Validate.isTrue(csvFilePath.endsWith(".csv"), "Expected .csv extension for file [%s]!", csvFilePath);

		final karmaresearch.vlog.Atom vLogAtom = ModelToVLogConverter.toVLogAtom(query);
		try {
			this.vLog.writeQueryResultsToCsv(vLogAtom, csvFilePath, filterBlanks);
		} catch (final NotStartedException e) {
			throw new RuntimeException("Inconsistent reasoner state.", e);
		} catch (final NonExistingPredicateException e1) {
			throw new IllegalArgumentException(MessageFormat.format(
					"The query predicate does not occur in the loaded Knowledge Base: {0}!", query.getPredicate()), e1);
		}
		return this.materialisationState;
	}

	@Override
	public void resetReasoner() throws ReasonerStateException {
		// TODO what should happen to the KB?
		if (this.reasonerState.equals(ReasonerState.AFTER_CLOSING)) {
			throw new ReasonerStateException(this.reasonerState, "Resetting is not allowed after closing.");
		}
		this.reasonerState = ReasonerState.BEFORE_LOADING;
		this.vLog.stop();
		LOGGER.info("Reasoner has been reset. All inferences computed during reasoning have been discarded.");
	}

	@Override
	public void close() {
		this.reasonerState = ReasonerState.AFTER_CLOSING;
		this.knowledgeBase.deleteListener(this);
		this.vLog.stop();
	}

	@Override
	public void setLogLevel(LogLevel logLevel) throws ReasonerStateException {
		if (this.reasonerState.equals(ReasonerState.AFTER_CLOSING)) {
			throw new ReasonerStateException(this.reasonerState, "Setting log level is not allowed after closing.");
		}
		Validate.notNull(logLevel, "Log level cannot be null!");
		this.internalLogLevel = logLevel;
		this.vLog.setLogLevel(ModelToVLogConverter.toVLogLogLevel(this.internalLogLevel));
	}

	@Override
	public LogLevel getLogLevel() {
		return this.internalLogLevel;
	}

	@Override
	public void setLogFile(String filePath) throws ReasonerStateException {
		if (this.reasonerState.equals(ReasonerState.AFTER_CLOSING)) {
			throw new ReasonerStateException(this.reasonerState, "Setting log file is not allowed after closing.");
		}
		this.vLog.setLogFile(filePath);
	}

	@Override
	public boolean isJA() throws ReasonerStateException, NotStartedException {
		return checkAcyclicity(AcyclicityNotion.JA);
	}

	@Override
	public boolean isRJA() throws ReasonerStateException, NotStartedException {
		return checkAcyclicity(AcyclicityNotion.RJA);
	}

	@Override
	public boolean isMFA() throws ReasonerStateException, NotStartedException {
		return checkAcyclicity(AcyclicityNotion.MFA);
	}

	@Override
	public boolean isRMFA() throws ReasonerStateException, NotStartedException {
		return checkAcyclicity(AcyclicityNotion.RMFA);
	}

	@Override
	public boolean isMFC() throws ReasonerStateException, NotStartedException {
		if (this.reasonerState == ReasonerState.BEFORE_LOADING) {
			throw new ReasonerStateException(this.reasonerState,
					"checking rules acyclicity is not allowed before loading!");
		}

		final CyclicCheckResult checkCyclic = this.vLog.checkCyclic("MFC");
		if (checkCyclic.equals(CyclicCheckResult.CYCLIC)) {
			return true;
		}
		return false;
	}

	private boolean checkAcyclicity(final AcyclicityNotion acyclNotion)
			throws ReasonerStateException, NotStartedException {
		if (this.reasonerState == ReasonerState.BEFORE_LOADING) {
			throw new ReasonerStateException(this.reasonerState,
					"checking rules acyclicity is not allowed before loading!");
		}

		final CyclicCheckResult checkCyclic = this.vLog.checkCyclic(acyclNotion.name());
		if (checkCyclic.equals(CyclicCheckResult.NON_CYCLIC)) {
			return true;
		}
		return false;
	}

	@Override
	public CyclicityResult checkForCycles() throws ReasonerStateException, NotStartedException {
		final boolean acyclic = isJA() || isRJA() || isMFA() || isRMFA();
		if (acyclic) {
			return CyclicityResult.ACYCLIC;
		} else {
			final boolean cyclic = isMFC();
			if (cyclic) {
				return CyclicityResult.CYCLIC;
			}
			return CyclicityResult.UNDETERMINED;
		}
	}

	@Override
	public void onStatementsAdded(Set<Statement> statementsAdded) {
		// TODO more elaborate materialisation state handling
		// updateReasonerStateToKnowledgeBaseChanged();
		// updateMaterialisationStateOnStatementsAdded(statementsAddedInvalidateMaterialisation(statementsAdded));

		updateReasonerToKnowledgeBaseChanged();
	}

	@Override
	public void onStatementAdded(Statement statementAdded) {
		// TODO more elaborate materialisation state handling
		// updateReasonerStateToKnowledgeBaseChanged();
		// updateMaterialisationStateOnStatementsAdded(statementAddedInvalidatesMaterialisation(statementAdded));

		updateReasonerToKnowledgeBaseChanged();
	}

	private void updateReasonerToKnowledgeBaseChanged() {
		if (this.reasonerState.equals(ReasonerState.AFTER_LOADING)
				|| this.reasonerState.equals(ReasonerState.AFTER_REASONING)) {

			this.reasonerState = ReasonerState.KNOWLEDGE_BASE_CHANGED;
			this.materialisationState = MaterialisationState.WRONG;
		}
	}

//	private void updateReasonerStateToKnowledgeBaseChanged() {
//		if (this.reasonerState.equals(ReasonerState.AFTER_LOADING)
//				|| this.reasonerState.equals(ReasonerState.AFTER_REASONING)) {
//			this.reasonerState = ReasonerState.KNOWLEDGE_BASE_CHANGED;
//		}
//	}

//	private boolean statementsAddedInvalidateMaterialisation(Set<Statement> statementsAdded) {
//		// TODO implement and use to decide materialisation state
//		return true;
//
//	}
//
//	private boolean statementAddedInvalidatesMaterialisation(Statement statementAdded) {
//		// TODO implement and use to decide materialisation state
//		return true;
//	}

//	private void updateMaterialisationStateOnStatementsAdded(boolean materialisationInvalidated) {
//		if (this.reasonerState.equals(ReasonerState.KNOWLEDGE_BASE_CHANGED) && materialisationInvalidated) {
//			this.materialisationState = MaterialisationState.WRONG;
//		}
//	}

}
