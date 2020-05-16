/*
 * Copyright 2017-2018 Emmanuel Keller / Jaeksoft
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jaeksoft.opensearchserver.front.search;

import com.jaeksoft.opensearchserver.Components;
import com.jaeksoft.opensearchserver.front.ServletTransaction;
import com.jaeksoft.opensearchserver.model.AccountRecord;
import com.jaeksoft.opensearchserver.model.Language;
import com.jaeksoft.opensearchserver.model.SearchResults;
import com.jaeksoft.opensearchserver.services.IndexService;
import com.jaeksoft.opensearchserver.services.SearchService;
import com.qwazr.utils.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;

public class SearchTransaction extends ServletTransaction {

    private final static String RESULT_TEMPLATE = "result.ftl";
    private final static String FORM_TEMPLATE = "form.ftl";

    private final IndexService indexService;
    private final SearchService searchService;

    public SearchTransaction(final Components components, final AccountRecord accountRecord, final String indexName,
                             final HttpServletRequest request, final HttpServletResponse response) {
        super(components.getTemplatesService().getFreeMarkerTool(accountRecord.getId(), indexName), components.getConfigService(), request, response,
            false);
        this.indexService = components.getIndexesService().getIndex(accountRecord.id, indexName);
        if (indexService == null)
            throw new NotFoundException("Index not found: " + indexName);
        this.searchService = components.getSearchService();
        request.setAttribute("account", accountRecord);
        request.setAttribute("indexName", indexName);
    }

    class Parameters {

        final String keywords;
        final String lang;
        final Language language;
        final int start;
        final int rows;

        Parameters() {
            start = getRequestParameter("start", 0, 0, null);
            rows = getRequestParameter("rows", 10, 10, 100);
            keywords = request.getParameter("keywords");
            lang = request.getParameter("lang");
            language = Language.findByName(lang, Language.en);
            request.setAttribute("lang", language.name());
            request.setAttribute("keywords", keywords);
            request.setAttribute("start", start);
            request.setAttribute("rows", rows);
        }
    }

    @Override
    public void doGet() throws IOException, ServletException {
        new Parameters();
        doTemplate(FORM_TEMPLATE);
    }

    @Override
    public void doPost() throws IOException, ServletException {
        try {
            final Parameters parameters = new Parameters();
            if (!StringUtils.isBlank(parameters.keywords)) {
                final SearchResults results =
                    searchService.webSearch(indexService, parameters.language, parameters.keywords,
                        parameters.start, parameters.rows);
                request.setAttribute("numDocs", results.getNumDocs());
                request.setAttribute("totalTime", results.getTotalTime());
                request.setAttribute("results", results.getResults());
                request.setAttribute("paging", results.getPaging());
            }
        }
        catch (WebApplicationException e) {
            addMessage(null, e);
        }
        response.addHeader("Access-Control-Allow-Origin", "*");
        doTemplate(RESULT_TEMPLATE);
    }

}
