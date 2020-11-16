package com.myorg;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.SecretValue;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codecommit.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import software.amazon.awscdk.services.secretsmanager.*;
import java.util.*;
import static software.amazon.awscdk.services.codebuild.LinuxBuildImage.AMAZON_LINUX_2;

public class PipelineStack extends Stack {
    public PipelineStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public PipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here
        Bucket artifactsBucket = new Bucket(this, "ArtifactsBucket");

        Pipeline pipeline = new Pipeline(this, "Pipeline", PipelineProps.builder()
                .artifactBucket(artifactsBucket).build());

        Artifact sourceOutput = new Artifact("sourceOutput");

        SecretValue oauthToken = SecretValue.secretsManager("sam-cicd-app-key");
        GitHubSourceAction gitHubSource = new GitHubSourceAction(GitHubSourceActionProps.builder()
                .actionName("GitHub_Source")
                .repo("sam-cicd-app")
                .owner("folksgl")
                .branch("main")
                .oauthToken(oauthToken)
                .output(sourceOutput)
                .build());

        pipeline.addStage(StageOptions.builder()
                .stageName("Source")
                .actions(Collections.singletonList(gitHubSource))
                .build());

        // Declare build output as artifacts
        Artifact buildOutput = new Artifact("buildOutput");

        // Declare a new CodeBuild project
        PipelineProject buildProject = new PipelineProject(this, "Build", PipelineProjectProps.builder()
                .environment(BuildEnvironment.builder()
                        .buildImage(AMAZON_LINUX_2).build())
                .environmentVariables(Collections.singletonMap("PACKAGE_BUCKET", BuildEnvironmentVariable.builder()
                        .value(artifactsBucket.getBucketName())
                        .build()))
                .build());

        // Add the build stage to our pipeline
        CodeBuildAction buildAction = new CodeBuildAction(CodeBuildActionProps.builder()
                .actionName("Build")
                .project(buildProject)
                .input(sourceOutput)
                .outputs(Collections.singletonList(buildOutput))
                .build());

        pipeline.addStage(StageOptions.builder()
                .stageName("Build")
                .actions(Collections.singletonList(buildAction))
                .build());
    }

}