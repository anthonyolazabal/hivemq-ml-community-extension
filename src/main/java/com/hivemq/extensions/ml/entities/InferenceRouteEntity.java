package com.hivemq.extensions.ml.entities;

public class InferenceRouteEntity {
    private String topic;
    private String inferenceUri;
    private Boolean endpointAuth;
    private String destinationTopic;

    public String getTopic(){
        return topic;
    }

    public String getInferenceUri(){
        return inferenceUri;
    }

    public Boolean getEndpointAuth(){
        return endpointAuth;
    }

    public String getDestinationTopic(){
        return destinationTopic;
    }
}
