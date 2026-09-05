package com.partvision.ai.search;

import java.util.Optional;

public interface SearchInterpreter {

    Optional<SearchInterpretation> interpret(String query);
}
