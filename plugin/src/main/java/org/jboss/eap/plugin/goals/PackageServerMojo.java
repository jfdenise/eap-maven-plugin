/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.eap.plugin.goals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePack;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanResults;
import org.wildfly.plugin.common.GlowMavenMessageWriter;
import org.wildfly.plugin.provision.ChannelMavenArtifactRepositoryManager;
import org.wildfly.plugin.tools.GalleonUtils;

/**
 * Package an EAP server.
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends org.wildfly.plugin.provision.AbstractPackageServerMojo {

    @Parameter(alias = "discover-provisioning-info")
    private GlowConfig discoverProvisioningInfo;

    @Override
    protected void enrichRepositories() throws MojoExecutionException {
        // NO-OP
    }

    @Override
    protected GalleonProvisioningConfig buildGalleonConfig(GalleonBuilder pm)
            throws MojoExecutionException, ProvisioningException {
        if (discoverProvisioningInfo == null) {
            return super.buildGalleonConfig(pm);
        }
        try {
            try (ScanResults results = scanDeployment(discoverProvisioningInfo,
                    layers,
                    excludedLayers,
                    featurePacks,
                    dryRun,
                    getLog(),
                    getDeploymentContent(),
                    artifactResolver,
                    Paths.get(project.getBuild().getDirectory()),
                    pm,
                    galleonOptions,
                    layersConfigurationFileName)) {
                return results.getProvisioningConfig();
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    public static ScanResults scanDeployment(GlowConfig discoverProvisioningInfo,
            List<String> layers,
            List<String> excludedLayers,
            List<GalleonFeaturePack> featurePacks,
            boolean dryRun,
            Log log,
            Path deploymentContent,
            MavenRepoManager artifactResolver,
            Path outputFolder,
            GalleonBuilder pm,
            Map<String, String> galleonOptions,
            String layersConfigurationFileName) throws Exception {
        if (!layers.isEmpty()) {
            throw new MojoExecutionException("layers must be empty when enabling glow");
        }
        if (!excludedLayers.isEmpty()) {
            throw new MojoExecutionException("excluded layers must be empty when enabling glow");
        }
        if (!Files.exists(deploymentContent)) {
            throw new MojoExecutionException("A deployment is expected when enabling glow layer discovery");
        }
        Path inProvisioningFile = null;
        Path glowOutputFolder = outputFolder.resolve("glow-scan");
        Files.createDirectories(glowOutputFolder);
        if (!featurePacks.isEmpty()) {
            GalleonProvisioningConfig in = GalleonUtils.buildConfig(pm, featurePacks, layers, excludedLayers, galleonOptions,
                    layersConfigurationFileName);
            inProvisioningFile = glowOutputFolder.resolve("eap-maven-plugin-in-provisioning.xml");
            try (Provisioning p = pm.newProvisioningBuilder(in).build()) {
                p.storeProvisioningConfig(in, inProvisioningFile);
            }
        } else {
            inProvisioningFile = getProvisioningXML(discoverProvisioningInfo.getProduct(), discoverProvisioningInfo.getContext());
        }
        Arguments arguments = discoverProvisioningInfo.toArguments(deploymentContent, inProvisioningFile,
                layersConfigurationFileName,
                (artifactResolver instanceof ChannelMavenArtifactRepositoryManager
                        ? ((ChannelMavenArtifactRepositoryManager) artifactResolver).getChannelSession()
                        : null));
        log.info("JBoss EAP Maven Plugin is scanning the deployment... ");
        ScanResults results;
        GlowMavenMessageWriter writer = new GlowMavenMessageWriter(log);
        try {
            results = GlowSession.scan(artifactResolver, arguments, writer);
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }

        log.info("JBoss EAP Maven Plugin scanning DONE.");
        try {
            results.outputInformation(writer);
        } catch (Exception ex) {
            results.close();
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
        if (!dryRun) {
            results.outputConfig(glowOutputFolder, null);
        }
        if (results.getErrorSession().hasErrors()) {
            if (discoverProvisioningInfo.isFailsOnError()) {
                results.close();
                throw new MojoExecutionException("Error detected by WildFly Glow. Aborting.");
            } else {
                log.warn("Some erros have been identified, check logs.");
            }
        }

        return results;
    }

    public static Path getProvisioningXML(String product, String execution) throws Exception {
        String path = "/" + product + "/provisioning-" + execution + ".xml";
        Path provisioningXML = Files.createTempFile("jboss-eap-trimmer", "-provisioning.xml");
        provisioningXML.toFile().deleteOnExit();
        try (InputStream stream = PackageServerMojo.class.getResourceAsStream(path)) {
            Files.write(provisioningXML, stream.readAllBytes());
        }
        return provisioningXML;
    }
}
