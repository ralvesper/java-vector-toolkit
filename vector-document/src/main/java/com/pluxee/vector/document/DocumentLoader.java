package com.pluxee.vector.document;

import java.util.List;

public interface DocumentLoader {

    List<SourceDocument> load(DocumentSource source);
}

