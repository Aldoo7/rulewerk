package org.semanticweb.vlog4j.parser.javacc;

/*-
 * #%L
 * vlog4j-parser
 * %%
 * Copyright (C) 2018 - 2019 VLog4j Developers
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

import java.io.InputStream;
import java.io.ByteArrayInputStream;

import org.semanticweb.vlog4j.core.model.api.PrefixDeclarations;
import org.semanticweb.vlog4j.core.reasoner.KnowledgeBase;
import org.semanticweb.vlog4j.parser.ParserConfiguration;
import org.semanticweb.vlog4j.parser.RuleParser;

/**
 * Factory for creating a SubParser sharing configuration, state, and
 * prefixes, but with an independent input stream, to be used, e.g.,
 * for parsing arguments in data source declarations.
 *
 * @author Maximilian Marx
 */
public class SubParserFactory {
	private KnowledgeBase knowledgeBase;
	private ParserConfiguration parserConfiguration;
	private PrefixDeclarations prefixDeclarations;

	/**
	 * Construct a SubParserFactory.
	 *
	 * @argument parser the parser instance to get the state from.
	 */
	SubParserFactory(JavaCCParser parser) {
		this.knowledgeBase = parser.getKnowledgeBase();
		this.prefixDeclarations = parser.getPrefixDeclarations();
		this.parserConfiguration = parser.getParserConfiguration();
	}

	/**
	 * Create a new parser with the specified state and given input.
	 *
	 * @argument inputStream the input stream to parse.
	 * @argument encoding encoding of the input stream.
	 *
	 * @return A new {@link JavaCCParser} bound to inputStream and
	 * with the specified parser state.
	 */
	public JavaCCParser makeSubParser(final InputStream inputStream, final String encoding) {
		JavaCCParser subParser = new JavaCCParser(inputStream, encoding);
		subParser.setKnowledgeBase(this.knowledgeBase);
		subParser.setPrefixDeclarations(this.prefixDeclarations);
		subParser.setParserConfiguration(this.parserConfiguration);

		return subParser;
	}

	public JavaCCParser makeSubParser(final InputStream inputStream) {
		return makeSubParser(inputStream, RuleParser.DEFAULT_STRING_ENCODING);
	}

	public JavaCCParser makeSubParser(final String string) {
		return makeSubParser(new ByteArrayInputStream(string.getBytes()));
	}
}
