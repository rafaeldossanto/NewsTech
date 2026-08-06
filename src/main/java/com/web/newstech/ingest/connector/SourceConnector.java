package com.web.newstech.ingest.connector;

import com.web.newstech.ingest.enums.ConnectorType;
import com.web.newstech.ingest.Source;

public interface SourceConnector {

	ConnectorType type();

	FetchResult fetch(Source source);

}
