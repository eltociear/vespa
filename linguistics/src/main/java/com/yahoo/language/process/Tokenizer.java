// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

import java.util.List;

/**
 * Language-sensitive tokenization of a text string.
 *
 * @author Mathias Mølster Lidal
 */
public interface Tokenizer {

    /**
     * Returns the tokens produced from an input string under the rules of the given Language and additional options.
     * This default implementation does nothing and returns no tokens.
     *
     * @param input the string to tokenize. May be arbitrarily large.
     * @param language the language of the input string.
     * @param stemMode the stem mode applied on the returned tokens
     * @param removeAccents if true accents and similar are removed from the returned tokens
     * @return the tokens of the input String.
     * @throws ProcessingException If the underlying library throws an Exception.
     * @deprecated use tokenize with a context instead
     */
    @Deprecated // TODO:  Remove on Vespa 8
    default Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
        return List.of();
    }

    /**
     * Returns the tokens produced from an input string under the rules of the given Language and additional options.
     * This dsefault implementation delegates to the tokenize method without context.
     *
     * @param input the string to tokenize. May be arbitrarily large.
     * @param language the language of the input string.
     * @param stemMode the stem mode applied on the returned tokens
     * @param removeAccents if true accents and similar are removed from the returned tokens
     * @param context the context of this processing
     * @return the tokens of the input String.
     * @throws ProcessingException If the underlying library throws an Exception.
     */
    default Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents,
                                     LinguisticsContext context) {
        return tokenize(input, language,  stemMode, removeAccents);
    }

}
