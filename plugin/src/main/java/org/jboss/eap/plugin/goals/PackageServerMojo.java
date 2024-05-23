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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePack;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanResults;
import org.wildfly.plugin.common.GlowMavenMessageWriter;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.provision.ChannelMavenArtifactRepositoryManager;
import org.wildfly.plugin.tools.GalleonUtils;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;

/**
 * Package an EAP server.
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends org.wildfly.plugin.provision.AbstractPackageServerMojo {

    public static final String JAR = "jar";
    public static final String BOOTABLE_JAR_NAME_RADICAL = "server-";

    @Parameter(alias = "discover-provisioning-info")
    private GlowConfig discoverProvisioningInfo;
    /**
     * Package the provisioned server into a WildFly Bootable JAR.
     * <p>
     * Note that the produced fat JAR is ignored when running the
     * {@code dev},{@code image},{@code start} or {@code run} goals.
     * </p>
     */
    @Parameter(alias = "bootable-jar", required = false, property = PropertyNames.BOOTABLE_JAR)
    private boolean bootableJar;

    /**
     * When {@code bootable-jar} is set to true, use this parameter to name the
     * generated jar file. The jar file is named by default
     * {@code server-bootable.jar}.
     */
    @Parameter(alias = "bootable-jar-name", required = false, property = PropertyNames.BOOTABLE_JAR_NAME)
    private String bootableJarName;

    /**
     * When {@code bootable-jar} is set to true, the bootable JAR artifact is
     * attached to the project with the classifier 'bootable'. Use this
     * parameter to configure the classifier.
     */
    @Parameter(alias = "bootable-jar-install-artifact-classifier", property = PropertyNames.BOOTABLE_JAR_INSTALL_CLASSIFIER, defaultValue = BootableJarSupport.BOOTABLE_SUFFIX)
    private String bootableJarInstallArtifactClassifier;

    private GalleonProvisioningConfig config;

    @Override
    protected void enrichRepositories() throws MojoExecutionException {
        // NO-OP
    }

    @Override
    protected GalleonProvisioningConfig buildGalleonConfig(GalleonBuilder pm)
            throws MojoExecutionException, ProvisioningException {
        if (discoverProvisioningInfo == null) {
            config = super.buildGalleonConfig(pm);
            return config;
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
                config = results.getProvisioningConfig();
                return config;
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    @Override
    protected void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException {
        super.serverProvisioned(jbossHome);
        try {
            if (bootableJar) {
                packageBootableJar(jbossHome, config);
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    private void packageBootableJar(Path jbossHome, GalleonProvisioningConfig activeConfig) throws Exception {
        getLog().info("Building Bootable JAR...");
        String jarName = bootableJarName == null ? BOOTABLE_JAR_NAME_RADICAL + BootableJarSupport.BOOTABLE_SUFFIX + "." + JAR
                : bootableJarName;
        Path targetPath = Paths.get(project.getBuild().getDirectory());
        Path targetJarFile = targetPath.toAbsolutePath()
                .resolve(jarName);
        Files.deleteIfExists(targetJarFile);
        BootableJarSupport.packageBootableJar(targetJarFile, targetPath,
                activeConfig, jbossHome,
                artifactResolver,
                new MvnMessageWriter(getLog()));
        attachJar(targetJarFile);
        getLog().info("Bootable JAR packaging DONE. To run the server: java -jar " + targetJarFile);
    }

    private void attachJar(Path jarFile) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Attaching bootable jar " + jarFile + " as a project artifact with classifier "
                    + bootableJarInstallArtifactClassifier);
        }
        projectHelper.attachArtifact(project, JAR, bootableJarInstallArtifactClassifier, jarFile.toFile());
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
