/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.datastore;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.jboss.pnc.model.*;
import org.jboss.pnc.spi.datastore.Datastore;
import org.jboss.pnc.spi.datastore.predicates.RepositoryConfigurationPredicates;
import org.jboss.pnc.spi.datastore.repositories.ArtifactRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationAuditedRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildEnvironmentRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildRecordRepository;
import org.jboss.pnc.spi.datastore.repositories.LicenseRepository;
import org.jboss.pnc.spi.datastore.repositories.ProductRepository;
import org.jboss.pnc.spi.datastore.repositories.ProjectRepository;
import org.jboss.pnc.spi.datastore.repositories.RepositoryConfigurationRepository;
import org.jboss.pnc.spi.datastore.repositories.UserRepository;
import org.jboss.pnc.spi.datastore.repositories.api.Predicate;
import org.jboss.pnc.test.category.ContainerTest;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@RunWith(Arquillian.class)
@Category(ContainerTest.class)
public class DatastoreTest {

    private static String ARTIFACT_1_IDENTIFIER = "org.jboss.test:artifact1";

    private static String ARTIFACT_2_IDENTIFIER = "org.jboss.test:artifact2";

    private static String ARTIFACT_3_IDENTIFIER = "org.jboss.test:artifact3";

    private static String ARTIFACT_1_CHECKSUM = "1";

    private static String ARTIFACT_2_CHECKSUM = "2";

    private static String ARTIFACT_3_CHECKSUM = "3";

    private static Long ARTIFACT_1_SIZE = 111L;

    private static Long ARTIFACT_2_SIZE = 222L;

    private static Long ARTIFACT_3_SIZE = 333L;


    @Inject
    ArtifactRepository artifactRepository;

    @Inject
    BuildConfigurationRepository buildConfigurationRepository;

    @Inject
    RepositoryConfigurationRepository repositoryConfigurationRepository;

    @Inject
    BuildConfigurationAuditedRepository buildConfigurationAuditedRepository;

    @Inject
    BuildEnvironmentRepository buildEnvironmentRepository;

    @Inject
    BuildRecordRepository buildRecordRepository;

    @Inject
    ProductRepository productRepository;

    @Inject
    ProjectRepository projectRepository;

    @Inject
    LicenseRepository licenseRepository;
    
    @Inject
    UserRepository userRepository;

    @Inject
    Datastore datastore;

    @Deployment
    public static Archive<?> getDeployment() {
        return DeploymentFactory.createDatastoreDeployment();
    }

    /**
     * The data initialization of the build configurations needs to be done in a separate test method so that 
     * the transaction can complete, and the hibernate envers BuildConfigurationAudit is created.
     */
    @Test
    @InSequence(1)
    @Transactional
    public void initBuildConfigData() {
        License license = License.Builder.newBuilder().fullName("test license").fullContent("test license").build();
        Project project = Project.Builder.newBuilder().name("Test Project 1").description("Test").build();
        BuildEnvironment buildEnv = BuildEnvironment.Builder.newBuilder().name("test build env").systemImageId("12345").systemImageType(SystemImageType.DOCKER_IMAGE).build();
        RepositoryConfiguration repositoryConfiguration = RepositoryConfiguration.Builder.newBuilder()
                .internalUrl("github.com/project-ncl/pnc")
                .build();
        BuildConfiguration buildConfig = BuildConfiguration.Builder.newBuilder().name("test build config").buildScript("mvn deploy").build();

        license = licenseRepository.save(license);
        project.setLicense(license);
        project = projectRepository.save(project);
        buildEnv = buildEnvironmentRepository.save(buildEnv);
        repositoryConfiguration = repositoryConfigurationRepository.save(repositoryConfiguration);

        buildConfig.setRepositoryConfiguration(repositoryConfiguration);
        buildConfig.setProject(project);
        buildConfig.setBuildEnvironment(buildEnv);
        buildConfig = buildConfigurationRepository.save(buildConfig);
        Assert.assertNotNull(buildConfig.getId());
    }

    /**
     * Create some existing artifacts and build records in the database to test for conflicts.
     */
    @Test
    @InSequence(2)
    @Transactional
    public void initBuildRecordData() throws Exception {

        List<BuildConfiguration> buildConfigs = buildConfigurationRepository.queryAll();
        Assert.assertTrue("No build configurations were found", buildConfigs.size() > 0);
        BuildConfiguration buildConfig = buildConfigs.get(0);
        List<BuildConfigurationAudited> buildConfigAudList = buildConfigurationAuditedRepository
                .findAllByIdOrderByRevDesc(buildConfig.getId());
        Assert.assertTrue("No build config audit record was created", buildConfigAudList.size() > 0);

        BuildConfigurationAudited buildConfigAud = buildConfigAudList.get(0);
        Assert.assertNotNull(buildConfigAud);

        Artifact builtArtifact1 = Artifact.Builder.newBuilder().identifier(ARTIFACT_1_IDENTIFIER)
                .md5("md-fake-" + ARTIFACT_1_CHECKSUM)
                .sha1("sha1-fake-" + ARTIFACT_1_CHECKSUM)
                .sha256("sha256-fake-" + ARTIFACT_1_CHECKSUM)
                .size(ARTIFACT_1_SIZE)
                .repoType(ArtifactRepo.Type.MAVEN).build();
        Artifact importedArtifact2 = Artifact.Builder.newBuilder().identifier(ARTIFACT_2_IDENTIFIER)
                .md5("md-fake-" + ARTIFACT_2_CHECKSUM)
                .sha1("sha1-fake-" + ARTIFACT_2_CHECKSUM)
                .sha256("sha256-fake-" + ARTIFACT_2_CHECKSUM)
                .size(ARTIFACT_2_SIZE)
                .originUrl("http://test/artifact2.jar")
                .importDate(Date.from(Instant.now()))
                .repoType(ArtifactRepo.Type.MAVEN).build();

        User user = User.Builder.newBuilder()
                .username("pnc").email("pnc@redhat.com").build();
        user = userRepository.save(user);
        Assert.assertNotNull(user.getId());
        
        BuildRecord buildRecord = BuildRecord.Builder.newBuilder().id(datastore.getNextBuildRecordId())
                .buildConfigurationAudited(buildConfigAud)
                .submitTime(Date.from(Instant.now())).startTime(Date.from(Instant.now())).endTime(Date.from(Instant.now()))
                .builtArtifact(builtArtifact1).dependency(importedArtifact2)
                .user(user).build();

        builtArtifact1 = artifactRepository.save(builtArtifact1);
        importedArtifact2 = artifactRepository.save(importedArtifact2);

        Assert.assertNotNull(builtArtifact1.getId());
        Assert.assertNotNull(importedArtifact2.getId());

        buildRecord = buildRecordRepository.save(buildRecord);

    }

    /**
     * Call the datastore to save a build record
     */
    @Test
    @InSequence(3)
    @Transactional
    public void testDatastore() throws Exception {

        List<BuildConfiguration> buildConfigs = buildConfigurationRepository.queryAll();
        Assert.assertTrue("No build configurations were found", buildConfigs.size() > 0);
        BuildConfiguration buildConfig = buildConfigs.get(0);
        List<BuildConfigurationAudited> buildConfigAudList = buildConfigurationAuditedRepository
                .findAllByIdOrderByRevDesc(buildConfig.getId());
        Assert.assertTrue("No build config audit record was created", buildConfigAudList.size() > 0);

        BuildConfigurationAudited buildConfigAud = buildConfigAudList.get(0);
        Assert.assertNotNull(buildConfigAud);

        Artifact builtArtifact1 = Artifact.Builder.newBuilder()
                .identifier(ARTIFACT_1_IDENTIFIER)
                .size(ARTIFACT_1_SIZE)
                .md5("md-fake-" + ARTIFACT_1_CHECKSUM)
                .sha1("sha1-fake-" + ARTIFACT_1_CHECKSUM)
                .sha256("sha256-fake-" + ARTIFACT_1_CHECKSUM)
                .repoType(ArtifactRepo.Type.MAVEN).build();
        Artifact importedArtifact2 = Artifact.Builder.newBuilder()
                .identifier(ARTIFACT_2_IDENTIFIER)
                .size(ARTIFACT_2_SIZE)
                .md5("md-fake-" + ARTIFACT_2_CHECKSUM)
                .sha1("sha1-fake-" + ARTIFACT_2_CHECKSUM)
                .sha256("sha256-fake-" + ARTIFACT_2_CHECKSUM)
                .originUrl("http://test/importArtifact2.jar").importDate(Date.from(Instant.now())).repoType(ArtifactRepo.Type.MAVEN).build();
        Artifact builtArtifact3 = Artifact.Builder.newBuilder()
                .identifier(ARTIFACT_3_IDENTIFIER)
                .md5("md-fake-" + ARTIFACT_3_CHECKSUM)
                .sha1("sha1-fake-" + ARTIFACT_3_CHECKSUM)
                .sha256("sha256-fake-" + ARTIFACT_3_CHECKSUM)
                .size(ARTIFACT_3_SIZE).originUrl("http://test/importArtifact2.jar")
                .importDate(Date.from(Instant.now())).repoType(ArtifactRepo.Type.MAVEN).build();


        User user = User.Builder.newBuilder()
                .username("pnc2").email("pnc2@redhat.com").build();
        user = userRepository.save(user);
        Assert.assertNotNull(user.getId());
        
        BuildRecord.Builder buildRecordBuilder = BuildRecord.Builder.newBuilder().id(datastore.getNextBuildRecordId())
                .buildConfigurationAudited(buildConfigAud)
                .submitTime(Date.from(Instant.now())).startTime(Date.from(Instant.now())).endTime(Date.from(Instant.now()))
                .builtArtifact(builtArtifact1).dependency(importedArtifact2).builtArtifact(builtArtifact3)
                .user(user);

        BuildRecord buildRecord = datastore.storeCompletedBuild(buildRecordBuilder);

        Assert.assertEquals(2, buildRecord.getBuiltArtifacts().size());
        Assert.assertEquals(1, buildRecord.getDependencies().size());
        Assert.assertEquals(3, artifactRepository.queryAll().size());

    }

    @Test
    @InSequence(4)
    public void testRepositoryCreationSearchPredicates() {
        //given
        String externalUrl = "https://github.com/external/repo.git";
        String internalUrl = "git+ssh://internal.repo.com/repo.git";

        RepositoryConfiguration repositoryConfiguration = RepositoryConfiguration.Builder.newBuilder()
                .externalUrl(externalUrl)
                .internalUrl(internalUrl)
                .build();

        RepositoryConfiguration saved = repositoryConfigurationRepository.save(repositoryConfiguration);

        List<RepositoryConfiguration> repositoryConfigurations;

        //when
        String scmUrl = "repo";
        repositoryConfigurations = searchForRepositoryConfigurations(scmUrl);
        //expect
        Assert.assertTrue("Repository configuration was not found.", !repositoryConfigurations.isEmpty());

        //when
        scmUrl = "repoX";
        repositoryConfigurations = searchForRepositoryConfigurations(scmUrl);
        //expect
        Assert.assertTrue("Repository configuration should not be found.", repositoryConfigurations.isEmpty());

        //when
        scmUrl = "ssh://internal.repo.com/repo.git";
        repositoryConfigurations = searchForRepositoryConfigurations(scmUrl);
        //expect
        Assert.assertTrue("Repository configuration was not found.", !repositoryConfigurations.isEmpty());

        //when
        scmUrl = "http://internal.repo.com/repo.git";
        repositoryConfigurations = searchForRepositoryConfigurations(scmUrl);
        //expect
        Assert.assertTrue("Repository configuration was not found.", !repositoryConfigurations.isEmpty());

        //when
        repositoryConfigurations = repositoryConfigurationRepository
                .queryWithPredicates(RepositoryConfigurationPredicates.withExactInternalScmRepoUrl(internalUrl));
        //expect
        Assert.assertTrue("Repository configuration was not found.", !repositoryConfigurations.isEmpty());

        //when
        repositoryConfigurations = repositoryConfigurationRepository
                .queryWithPredicates(RepositoryConfigurationPredicates.withInternalScmRepoUrl("ssh://internal.repo.com/repo"));
        //expect
        Assert.assertTrue("Repository configuration was not found.", !repositoryConfigurations.isEmpty());

        //when
        repositoryConfigurations = repositoryConfigurationRepository
                .queryWithPredicates(RepositoryConfigurationPredicates.withExternalScmRepoUrl("http://github.com/external/repo.git"));
        //expect
        Assert.assertTrue("Repository configuration was not found.", !repositoryConfigurations.isEmpty());

        //when
        repositoryConfigurations = repositoryConfigurationRepository
                .queryWithPredicates(RepositoryConfigurationPredicates.withInternalScmRepoUrl("http://github.com/external/repo.git"));
        //expect
        Assert.assertTrue("Repository configuration should not be found. Found " + repositoryConfigurations.size() + " repositoryConfigurations.", repositoryConfigurations.isEmpty());

        //when
        repositoryConfigurations = repositoryConfigurationRepository
                .queryWithPredicates(RepositoryConfigurationPredicates.withExternalScmRepoUrl("ssh://internal.repo.com/"));
        //expect
        Assert.assertTrue("Repository configuration should not be found.", repositoryConfigurations.isEmpty());


    }

    public List<RepositoryConfiguration> searchForRepositoryConfigurations(String scmUrl) {
        Predicate<RepositoryConfiguration> predicate = RepositoryConfigurationPredicates.searchByScmUrl(scmUrl);
        return repositoryConfigurationRepository.queryWithPredicates(predicate);
    }
}
