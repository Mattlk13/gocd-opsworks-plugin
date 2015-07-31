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

import com.amazonaws.services.opsworks.model.Deployment;
import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.tispr.aws.OpsWorksClient;

import java.util.HashMap;
import java.util.Map;

@Extension
public class OpsWorksGoPlugin extends GoPluginBase {

    public static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    public static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";

    @Override
    protected GoPluginApiResponse handleGetConfigRequest() {
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("appId", createField("appId", null, true, false, "0"));
        config.put("layerId", createField("layerId", null, false, false, "1"));
        config.put("noWaitTrue", createField("noWaitTrue", null, false, false, "2"));
        return renderJSON(SUCCESS_RESPONSE_CODE, config);
    }

    @Override
    protected GoPluginApiResponse handleValidation(GoPluginApiRequest request) {
        Map<String, Object> response = new HashMap<String, Object>();
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    @Override
    protected GoPluginApiResponse handleTaskExecution(GoPluginApiRequest request) {
        Map<String, Object> response = new HashMap<String, Object>();
        try {
            Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(request.requestBody(), Object.class);

            Map<String, Object> configVars = (Map<String, Object>) map.get("config");
            Map<String, Object> context = (Map<String, Object>) map.get("context");
            Map<String, String> envVars = (Map<String, String>) context.get("environmentVariables");

            String appId = getValue(configVars, "appId");
            String layerId = getValue(configVars, "layerId");
            String noWait = getValue(configVars, "noWaitTrue");

            boolean noWaitValue = (noWait != null && noWait.equals("true"));

            String comment = String.format("Deploy build %s via go.cd", envVars.get("GO_PIPELINE_COUNTER"));
            String revision = envVars.get("GO_REVISION");

            log(String.format("[opsworks] Deployment of [appId=%s] started.", appId));

            OpsWorksClient opsWorksClient = new OpsWorksClient(envVars.get(AWS_ACCESS_KEY_ID), envVars.get(AWS_SECRET_ACCESS_KEY));
            Deployment d = opsWorksClient.deploy(appId, layerId, comment, revision, noWaitValue);

            if (d.getStatus().equals("successful") || noWaitValue) {
                response.put("success", true);
                response.put("message", String.format("[opsworks] Deployment of [appId=%s] completed successfully.", appId));
            } else {
                response.put("success", false);
                response.put("message", String.format("[opsworks] Deployment of [appId=%s] failed.", appId));
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "[opsworks] Deployment interrupted. Reason: " + e.getMessage());
        }
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    @Override
    protected String getDisplayName() {
        return "AWS OpsWorks Deploy";
    }
}
