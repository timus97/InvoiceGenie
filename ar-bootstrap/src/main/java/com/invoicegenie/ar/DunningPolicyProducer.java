package com.invoicegenie.ar;

import com.invoicegenie.ar.application.service.DunningPolicy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI producer for dunning policy from application.yml (STORY-015).
 */
@ApplicationScoped
public class DunningPolicyProducer {

    @ConfigProperty(name = "invoicegenie.dunning.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "invoicegenie.dunning.levels", defaultValue = "30,60,90")
    String levels;

    @Produces
    @ApplicationScoped
    public DunningPolicy dunningPolicy() {
        return DunningPolicy.parse(enabled, levels);
    }
}