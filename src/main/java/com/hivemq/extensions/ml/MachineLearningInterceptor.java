/*
 * Copyright 2024-present HiveMQ GmbH
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
package com.hivemq.extensions.ml;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.interceptor.publish.PublishInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.packets.general.Qos;
import com.hivemq.extension.sdk.api.packets.publish.ModifiablePublishPacket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extension.sdk.api.services.builder.Builders;
import com.hivemq.extension.sdk.api.services.builder.PublishBuilder;
import com.hivemq.extension.sdk.api.services.publish.PublishService;
import com.hivemq.extensions.ml.entities.InferenceRouteEntity;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an interceptor to handle inference on the fly.
 *
 * @author Anthony Olazabal
 * @since Edge 2024.7 and Enterprise Broker 4.34
 */
public class MachineLearningInterceptor implements PublishInboundInterceptor {
    private final List<InferenceRouteEntity> routes;
    private static final @NotNull Logger log = LoggerFactory.getLogger(MachineLearningInterceptor.class);
    final PublishBuilder publishBuilder = Builders.publish();
    final PublishService publishService = Services.publishService();
    public static final MediaType mediaType = MediaType.get("application/json");
    final OkHttpClient client = new OkHttpClient();
    private static final @NotNull String DEFAULT_VALUE = "";

    public MachineLearningInterceptor(List<InferenceRouteEntity> routes) {
        this.routes = routes;
    }

    @Override
    public void onInboundPublish(
            final @NotNull PublishInboundInput publishInboundInput,
            final @NotNull PublishInboundOutput publishInboundOutput) {

        final ModifiablePublishPacket publishPacket = publishInboundOutput.getPublishPacket();

        for(InferenceRouteEntity route : routes) {
            if (route.getTopic().equals(publishPacket.getTopic())) {
                log.info("Triggering inference for : {}", publishPacket.getTopic());
                String inferenceResult;
                try {
                    inferenceResult = inferencePost(route.getInferenceUri(),byteBufferAsUtf8String(publishPacket.getPayload()));
                    log.debug("Result : {}", inferenceResult);
                } catch (Exception e) {
                    log.debug("Error handling inference : {}", e.getLocalizedMessage());
                    throw new RuntimeException(e);
                }
                publishBuilder.topic(route.getDestinationTopic())
                        .qos(Qos.EXACTLY_ONCE)
                        .payload(StandardCharsets.UTF_8.encode(inferenceResult))
                        .build();
                final CompletableFuture<Void> future = publishService.publish(publishBuilder.build());
                future.whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.debug("Inference result published !");
                    } else {
                        log.debug("Error publishing inference result : {}", throwable.getMessage());
                    }
                });
            }
        }



    }

    private String inferencePost(String url, String requestBody) throws Exception {
        RequestBody body = RequestBody.create(requestBody, mediaType);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            return response.body().string();
        }
    }

    private @NotNull String byteBufferAsUtf8String(final @NotNull Optional<ByteBuffer> bufferOptional) {
        return bufferOptional.map(byteBuffer -> new String(fromReadOnlyBuffer(byteBuffer), StandardCharsets.UTF_8))
                .orElse(DEFAULT_VALUE);
    }

    public static byte @NotNull [] fromReadOnlyBuffer(final @NotNull ByteBuffer byteBuffer) {
        final ByteBuffer rewind = byteBuffer.asReadOnlyBuffer().rewind();
        final byte[] array = new byte[rewind.remaining()];
        rewind.get(array);
        return array;
    }
}