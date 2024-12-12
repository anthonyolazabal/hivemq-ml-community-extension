package com.hivemq.extensions.ml.entities;

import java.util.List;

public class ConfigurationEntity {
    private List<InferenceRouteEntity> routes;

    public List<InferenceRouteEntity> getRoutes(){
        return routes;
    }
}
