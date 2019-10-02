package org.semanticweb.vlog4j.core.model.implementation;

/*-
 * #%L
 * VLog4j Core Components
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

import org.apache.commons.lang3.Validate;
import org.semanticweb.vlog4j.core.model.api.LanguageStringConstant;
import org.semanticweb.vlog4j.core.model.api.TermVisitor;

/**
 * Simple implementation of {@link LanguageStringConstant}.
 * 
 * @author Markus Kroetzsch
 *
 */
public class LanguageStringConstantImpl implements LanguageStringConstant {

	final String string;
	final String lang;

	public LanguageStringConstantImpl(String string, String languageTag) {
		Validate.notNull(string);
		Validate.notBlank(languageTag, "Language tags cannot be blank strings.");
		this.string = string;
		this.lang = languageTag;
	}

	@Override
	public String getName() {
		return "\"" + string.replace("\\", "\\\\").replace("\"", "\\\"") + "\"@" + lang;
	}

	@Override
	public <T> T accept(TermVisitor<T> termVisitor) {
		return termVisitor.visit(this);
	}

	@Override
	public String getString() {
		return this.string;
	}

	@Override
	public String getLanguageTag() {
		return this.lang;
	}

}
