/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.eap.plugin.goals;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.wildfly.channel.ChannelSession;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanArguments.Builder;

/**
 *
 * @author jdenise
 */
@SuppressWarnings("unused")
public class GlowConfig {

    private String context = "bare-metal";
    private String profile;
    private Set<String> addOns = Set.of();
    private boolean suggest;
    private Set<String> layersForJndi = Set.of();
    private Set<String> excludedArchives = Set.of();
    private boolean failsOnError = true;
    private boolean verbose;
    private String product = "eap8.0";

    public GlowConfig() {
    }

    public Arguments toArguments(Path deployment, Path inProvisioning, String layersConfigurationFileName,
            ChannelSession session) {
        final Set<String> profiles = profile != null ? Set.of(profile) : Set.of();
        List<Path> lst = List.of(deployment);
        Builder builder = Arguments.scanBuilder().setExecutionContext(context).setExecutionProfiles(profiles)
                .setUserEnabledAddOns(addOns).setBinaries(lst).setSuggest(suggest).setJndiLayers(getLayersForJndi())
                .setExcludeArchivesFromScan(excludedArchives)
                .setVerbose(verbose)
                .setOutput(OutputFormat.PROVISIONING_XML);
        if (inProvisioning != null) {
            builder.setProvisoningXML(inProvisioning);
        }
        if (layersConfigurationFileName != null) {
            builder.setConfigName(layersConfigurationFileName);
        }
        if (session != null) {
            builder.setChannelSession(session);
        }
        return builder.build();
    }

    /**
     * @return the execution context
     */
    public String getContext() {
        return context;
    }

    /**
     * @param context the execution context to set
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * @return the profile
     */
    public String getProfile() {
        return profile;
    }

    /**
     * @param profile the profile to set
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /**
     * @return the userEnabledAddOns
     */
    public Set<String> getAddOns() {
        return addOns;
    }

    /**
     * @param addOns the userEnabledAddOns to set
     */
    public void setAddOns(Set<String> addOns) {
        this.addOns = Set.copyOf(addOns);
    }

    /**
     * @return the suggest
     */
    public boolean isSuggest() {
        return suggest;
    }

    /**
     * @param suggest the suggest to set
     */
    public void setSuggest(boolean suggest) {
        this.suggest = suggest;
    }

    /**
     * @return the layersForJndi
     */
    public Set<String> getLayersForJndi() {
        Set<String> extraLayers = new HashSet<>();
        // Missing in EAP 8.0
        extraLayers.add("deployment-scanner");
        if (!layersForJndi.isEmpty()) {
            extraLayers.addAll(layersForJndi);
        }
        return extraLayers;
    }

    /**
     * @param layersForJndi the layersForJndi to set
     */
    public void setLayersForJndi(Set<String> layersForJndi) {
        this.layersForJndi = Set.copyOf(layersForJndi);
    }

    /**
     * @return the failsOnError
     */
    public boolean isFailsOnError() {
        return failsOnError;
    }

    /**
     * @param failsOnError the failsOnError to set
     */
    public void setFailsOnError(boolean failsOnError) {
        this.failsOnError = failsOnError;
    }

    /**
     * @return the excludedArchives
     */
    public Set<String> getExcludedArchives() {
        return excludedArchives;
    }

    /**
     * @param excludedArchives the excludedArchives to set
     */
    public void setExcludedArchives(Set<String> excludedArchives) {
        this.excludedArchives = Set.copyOf(excludedArchives);
    }

    /**
     * @param verbose the verbose to set
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @return the verbose
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * @return the product
     */
    public String getProduct() {
        return product;
    }

    /**
     * @param product the product to set
     */
    public void setProduct(String product) {
        this.product = product;
    }
}
