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
package com.jaeksoft.opensearchserver.front;

import com.jaeksoft.opensearchserver.model.UserRecord;
import com.jaeksoft.opensearchserver.services.ConfigService;
import com.qwazr.library.freemarker.FreeMarkerTool;
import com.qwazr.utils.ExceptionUtils;
import com.qwazr.utils.LinkUtils;
import com.qwazr.utils.LoggerUtils;
import com.qwazr.utils.StringUtils;
import freemarker.template.TemplateException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ServletTransaction {

    private final static Logger LOGGER = LoggerUtils.getLogger(ServletTransaction.class);

    private final FreeMarkerTool freemarker;
    private final ConfigService configService;
    protected final HttpServletRequest request;
    protected final HttpServletResponse response;
    protected final HttpSession session;
    protected final UserRecord userRecord;

    protected ServletTransaction(final FreeMarkerTool freemarker,
                                 final ConfigService configService,
                                 final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 boolean requireLoggedUser) {
        this.freemarker = freemarker;
        this.configService = configService;
        this.request = request;
        this.response = response;
        this.session = request.getSession();
        this.userRecord = (UserRecord) request.getUserPrincipal();
        if (requireLoggedUser)
            requireLoggedUser();
    }

    private void requireLoggedUser() {
        if (userRecord != null)
            return;
        final StringBuffer requestUrlBuilder = request.getRequestURL();
        final String queryString = request.getQueryString();
        if (!StringUtils.isBlank(queryString)) {
            requestUrlBuilder.append('?');
            requestUrlBuilder.append(queryString);
        }
        final String requestUrl = requestUrlBuilder.toString();
        throw new RedirectionException(requestUrl, Response.Status.TEMPORARY_REDIRECT,
            URI.create("/signin?url=" + LinkUtils.urlEncode(requestUrl)));
    }

    /**
     * @return the message list
     */
    synchronized List<Message> getMessages() {
        List<Message> messages = (List<Message>) session.getAttribute("messages");
        if (messages == null) {
            messages = new ArrayList<>();
            session.setAttribute("messages", messages);
        }
        return messages;
    }

    /**
     * Returns the parameter value from the HTTP request or the default value.
     *
     * @param requestParameterName the request name of the parameter
     * @param sessionParameterName the session name of the parameter
     * @param defaultValue         a default value (can be null)
     * @param minValue             the minimum value (can be null)
     * @param maxValue             the maximum value (can be null)
     * @return the value of the given parameter
     */
    protected Integer getRequestParameter(final String requestParameterName, String sessionParameterName,
                                          final Integer defaultValue, final Integer minValue, final Integer maxValue) {
        final String stringValue = getParameter(requestParameterName, sessionParameterName);
        if (StringUtils.isBlank(stringValue))
            return defaultValue;
        final Integer value = Integer.parseInt(stringValue);
        if (minValue != null && value < minValue)
            return minValue;
        if (maxValue != null && value > maxValue)
            return maxValue;
        return value;
    }

    protected Integer getRequestParameter(final String requestParameterName, final Integer defaultValue,
                                          final Integer minValue, final Integer maxValue) {
        return getRequestParameter(requestParameterName, null, defaultValue, minValue, maxValue);
    }

    /**
     * Return the parameter value from the HTTP request or the default value
     *
     * @param parameterName
     * @param defaultValue
     * @return
     */
    protected Boolean getRequestParameter(final String parameterName, final Boolean defaultValue) {
        final String stringValue = request.getParameter(parameterName);
        if (StringUtils.isBlank(stringValue))
            return defaultValue;
        return Boolean.parseBoolean(stringValue);
    }

    /**
     * Returns the parameter value from the HTTP request or the value provided by the supplier
     *
     * @param requestParameterName the request name of the parameter
     * @param sessionParameterName the session name of the parameter
     * @return the value of the given parameter
     */
    protected String getParameter(final String requestParameterName, String sessionParameterName) {
        if (requestParameterName != null) {
            final String value = request.getParameter(requestParameterName);
            if (value != null) {
                if (sessionParameterName != null)
                    session.setAttribute(sessionParameterName, value);
                return value;
            }
        }
        return sessionParameterName != null ? (String) session.getAttribute(sessionParameterName) : null;
    }

    /**
     * @return the freemarker template
     */
    protected String getTemplate() {
        return null;
    }

    /**
     * This default implementation for HTTP GET method display the template provided by getTemplate().
     * If there is not template, it returns a 405 error code.
     *
     * @throws IOException      if any I/O error occured
     * @throws ServletException if any Servlet error occured
     */
    protected void doGet() throws IOException, ServletException {
        final String template = getTemplate();
        if (template != null)
            doTemplate(template);
        else
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /**
     * This default implementation for HTTP POST method returns a 405 error code.
     *
     * @throws IOException
     * @throws ServletException
     */
    protected void doPost() throws IOException, ServletException {
        final String action = request.getParameter("action");
        if (StringUtils.isBlank(action)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        try {
            final Method method = getClass().getDeclaredMethod(action);
            final Object redirect = method.invoke(this);
            if (!response.isCommitted())
                sendRedirect(redirect == null ? null : redirect.toString());
        }
        catch (ReflectiveOperationException | WebApplicationException | URISyntaxException e) {
            final String msg = "Action failed: " + action;
            addMessage(msg, e);
            LOGGER.log(Level.WARNING, msg, e);
            try {
                sendRedirect(null);
            }
            catch (URISyntaxException e1) {
                LOGGER.log(Level.SEVERE, "Self Redirect failed", e1);
            }
        }
    }

    /**
     * Redirect to the given URL. If redirect is null, it redirects to the current URL
     *
     * @throws IOException        if any I/O error occurs
     * @throws URISyntaxException if the redirection is not a valid URI
     */
    private void sendRedirect(String redirect) throws IOException, URISyntaxException {
        if (redirect == null) {
            final String queryString = request.getQueryString();
            final StringBuffer urlBuilder = request.getRequestURL();
            if (queryString != null) {
                urlBuilder.append('?');
                urlBuilder.append(queryString);
            }
            redirect = urlBuilder.toString();
        }
        response.sendRedirect(new URI(redirect).toString());
    }

    /**
     * Display a Freemarker template
     *
     * @param templatePath the path to the freemarker template
     * @throws IOException      if any I/O exception occurs
     * @throws ServletException if any template exception occurs
     */
    protected void doTemplate(String templatePath) throws IOException, ServletException {
        try {
            final List<Message> messages = getMessages();
            request.setAttribute("messages", messages);
            request.setAttribute("config", configService);
            freemarker.template(templatePath, request, response);
            messages.clear();
        }
        catch (TemplateException e) {
            throw new ServletException(e);
        }
    }

    /**
     * Add a message. The message may be displayed to the user.
     *
     * @param css     the CSS type to use
     * @param title   the title of the message
     * @param message the content of the message
     */
    protected void addMessage(final Message.Css css, final String title, final String message) {
        getMessages().add(new Message(css, title, message));
    }

    protected void addMessage(final String message, final Exception e) {
        LOGGER.log(Level.SEVERE, e, () -> message == null ? e.getMessage() : message);
        addMessage(Message.Css.danger, "Internal error", ExceptionUtils.getRootCauseMessage(e));
    }

}
