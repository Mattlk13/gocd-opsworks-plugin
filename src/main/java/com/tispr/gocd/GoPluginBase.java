/*
 * Copyright 2015 BuddyHopp, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tispr.gocd;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.AbstractGoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 * Common base for GoCD plugins. Inspired by reference plugins:
 * - https://github.com/gocd/go-plugins/tree/master/yum-plugin
 * - https://github.com/srinivasupadhya/script-executor-task
 */
public abstract class GoPluginBase extends AbstractGoPlugin {
    public final static String EXTENSION_NAME = "task";
    public static final int SUCCESS_RESPONSE_CODE = 200;
    public final static List<String> SUPPORTED_API_VERSIONS = asList("1.0");

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) throws UnhandledRequestTypeException {
        if ("configuration".equals(request.requestName())) {
            return handleGetConfigRequest();
        } else if ("validate".equals(request.requestName())) {
            return handleValidation(request);
        } else if ("execute".equals(request.requestName())) {
            return handleTaskExecution(request);
        } else if ("view".equals(request.requestName())) {
            try {
                return handleView();
            } catch (IOException e) {
                String message = "Failed to find template: " + e.getMessage();
                return renderJSON(500, message);
            }
        }
        throw new UnhandledRequestTypeException(request.requestName());
    }

    protected abstract GoPluginApiResponse handleGetConfigRequest();

    protected abstract GoPluginApiResponse handleValidation(GoPluginApiRequest request);

    protected abstract GoPluginApiResponse handleTaskExecution(GoPluginApiRequest request);

    protected Map<String, Object> createField(String displayName, String defaultValue, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<String, Object>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    protected abstract String getDisplayName();

    protected GoPluginApiResponse handleView() throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("displayValue", getDisplayName());
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/views/task.template.html"), "UTF-8"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    protected String getValue(Map config, String property) {
        return (String) ((Map) config.get(property)).get("value");
    }

    protected GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }

    protected void log(String msg) {
        JobConsoleLogger.getConsoleLogger().printLine(msg);
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, SUPPORTED_API_VERSIONS);
    }

}
