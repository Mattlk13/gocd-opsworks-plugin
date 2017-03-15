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
package com.tispr.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.opsworks.AWSOpsWorks;
import com.amazonaws.services.opsworks.AWSOpsWorksClientBuilder;
import com.amazonaws.services.opsworks.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OpsWorksClient {

    private static final int DEPLOYMENT_TIMEOUT = 900;
    private static final int DEPLOYMENT_CHECK_INTERVAL = 10;

    private AWSOpsWorks opsWorksClient;

    public OpsWorksClient(String accessKey, String secretKey, String region) {
        AWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        opsWorksClient = AWSOpsWorksClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(creds))
            .withRegion(region)
            .build();
    }

    public Deployment deploy(String appId, String layerId, String comment, String revision, boolean noWait)
            throws ExecutionException, InterruptedException {
        return deploy(getApp(appId), getInstancesIds(layerId), comment, revision, noWait);
    }

    protected List<String> getInstancesIds(String layerId) {
        List<String> instanceIds = new ArrayList<String>();

        if (layerId == null || layerId.isEmpty()) {
            return instanceIds;
        }

        DescribeInstancesRequest req = new DescribeInstancesRequest()
                .withLayerId(layerId);
        DescribeInstancesResult res = opsWorksClient.describeInstances(req);

        for (Instance i: res.getInstances()) {
            instanceIds.add(i.getInstanceId());
        }

        return instanceIds;
    }

    protected App getApp(String appId) {
        DescribeAppsRequest req = new DescribeAppsRequest().withAppIds(appId);
        DescribeAppsResult res = opsWorksClient.describeApps(req);

        for (App app: res.getApps()) {
            if (app.getAppId().equals(appId)) {
                return app;
            }
        }
        throw new IllegalArgumentException(String.format("Application [%s] not found.", appId));
    }

    protected Deployment deploy(App app, List<String> instanceIds, String comment, String revision, boolean noWait)
            throws ExecutionException, InterruptedException {
        DeploymentCommand command = new DeploymentCommand()
                .withName(DeploymentCommandName.Deploy);

        CreateDeploymentRequest deployReq = new CreateDeploymentRequest()
                .withAppId(app.getAppId())
                .withStackId(app.getStackId())
                .withCommand(command);

        if (instanceIds != null && !instanceIds.isEmpty()) {
            deployReq.withInstanceIds(instanceIds);
        }

        if (comment != null && !comment.isEmpty()) {
            deployReq.withComment(comment);
        }

        if (revision != null && !revision.isEmpty()) {
            String customJson = String.format(
                    "{\"deploy\":{\"%s\":{\"migrate\":false,\"scm\":{\"revision\":\"%s\"}}}}",
                    app.getShortname(),
                    revision
            );
            deployReq.withCustomJson(customJson);
        }

        CreateDeploymentResult deployRes = opsWorksClient.createDeployment(deployReq);

        String deploymentId = deployRes.getDeploymentId();
        Deployment deployment;
        int timeSpent = 0;

        while (timeSpent < DEPLOYMENT_TIMEOUT) {
            deployment = getDeployment(app.getAppId(), app.getStackId(), deploymentId);
            if (!deployment.getStatus().equals("running") || noWait) {
                return deployment;
            }

            Thread.sleep(DEPLOYMENT_CHECK_INTERVAL * 1000);
            timeSpent += DEPLOYMENT_CHECK_INTERVAL;
        }

        throw new IllegalStateException(String.format(
                "Deployment [appId=%s], [stackId=%s], [deploymentId=%s] timed out.",
                app.getAppId(), app.getStackId(), deploymentId
        ));
    }

    private Deployment getDeployment(String appId, String stackId, String deploymentId) {
        DescribeDeploymentsRequest describeReq = new DescribeDeploymentsRequest();
        describeReq.setDeploymentIds(Arrays.asList(deploymentId));

        DescribeDeploymentsResult describeRes = opsWorksClient.describeDeployments(describeReq);

        for (Deployment d: describeRes.getDeployments()) {
            if (d.getAppId().equals(appId) && d.getStackId().equals(stackId)) {
                return d;
            }
        }

        throw new IllegalStateException(String.format(
                "Deployment [appId=%s], [stackId=%s], [deploymentId=%s] not found.",
                appId, stackId, deploymentId
        ));
    }
}
