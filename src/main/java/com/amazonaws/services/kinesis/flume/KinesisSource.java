package com.amazonaws.services.kinesis.flume;

/*
 * Copyright 2012-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Modifications: Copyright 2015 Sharethrough, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import com.google.common.base.Preconditions;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flume.Context;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.PollableSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.source.AbstractSource;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.kinesis.MyAwsCredential;
import com.amazonaws.services.kinesis.RecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
 
public class KinesisSource extends AbstractSource implements Configurable, PollableSource {

  private static final Log LOG = LogFactory.getLog(KinesisSource.class);
  private Worker worker;

  // Initial position in the stream when the application starts up for the first time.
  // Position can be one of LATEST (most recent data) or TRIM_HORIZON (oldest available data)
  private InitialPositionInStream DEFAULT_INITIAL_POSITION = InitialPositionInStream.TRIM_HORIZON;

  private KinesisClientLibConfiguration kinesisClientLibConfiguration;

  @Override
  public void configure(Context context) {
    String endpoint = context.getString("endpoint", ConfigurationConstants.DEFAULT_KINESIS_ENDPOINT);

    String accessKeyId = Preconditions.checkNotNull(
        context.getString("accessKeyId"), "accessKeyId is required");

    String secretAccessKey = Preconditions.checkNotNull(
        context.getString("secretAccessKey"), "secretAccessKey is required");

    String streamName = Preconditions.checkNotNull(
        context.getString("streamName"), "streamName is required");

    String applicationName = Preconditions.checkNotNull(
        context.getString("applicationName"), "applicationName is required");

    String initialPosition = context.getString("initialPosition", "TRIM_HORIZON");
    String workerId=null;

    if (initialPosition.equals("LATEST")){
      DEFAULT_INITIAL_POSITION=InitialPositionInStream.LATEST;
    }

    AWSCredentialsProvider credentialsProvider;
    try {

      credentialsProvider = new InstanceProfileCredentialsProvider();
      // Verify we can fetch credentials from the provider
      credentialsProvider.getCredentials();
      LOG.info("Obtained credentials from the IMDS.");

    } catch (AmazonClientException e) {
      LOG.info("Unable to obtain credentials from the IMDS, trying classpath properties", e);

      credentialsProvider = new MyAwsCredential(accessKeyId, secretAccessKey);
      credentialsProvider.getCredentials();

      LOG.info("Obtained credentials from the properties file.");
    }

    try {
      workerId = InetAddress.getLocalHost().getCanonicalHostName() + ":" + UUID.randomUUID();
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }

    LOG.info("Using workerId: " + workerId);

    kinesisClientLibConfiguration = new KinesisClientLibConfiguration(applicationName, streamName,
        credentialsProvider, workerId).
        withKinesisEndpoint(endpoint).
        withInitialPositionInStream(DEFAULT_INITIAL_POSITION);

  }

  @Override
  public void start() {
    IRecordProcessorFactory recordProcessorFactory = new RecordProcessorFactory(getChannelProcessor());
    worker = new Worker(recordProcessorFactory, kinesisClientLibConfiguration);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        System.out.println("Shutting down Kinesis client thread...");
        worker.shutdown();
      }
    });

    worker.run();
  }

  @Override
  public void stop () {
  }

  @Override
  public Status process() throws EventDeliveryException {
    return null;
  }
}
