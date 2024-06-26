import {App} from 'aws-cdk-lib';
import {FetchMigrationStack} from "../lib/fetch-migration-stack";
import {Template} from "aws-cdk-lib/assertions";
import {NetworkStack} from "../lib/network-stack";
import {CpuArchitecture} from "aws-cdk-lib/aws-ecs";

test('Test default fetch migration stack creates required resources', () => {
    const app = new App();

    const networkStack = new NetworkStack(app, "NetworkStack", {
        defaultDeployId: "default",
        sourceClusterEndpoint: "https://test-cluster",
        stage: "unit-test"
    })

    const fetchStack = new FetchMigrationStack(app, "FetchMigrationStack", {
        vpc: networkStack.vpc,
        dpPipelineTemplatePath: "./dp_pipeline_template.yaml",
        defaultDeployId: "default",
        stage: "unit-test",
        fargateCpuArch: CpuArchitecture.X86_64,

    })

    const template = Template.fromStack(fetchStack);
    template.resourceCountIs("AWS::ECS::TaskDefinition", 1)
    template.resourceCountIs("AWS::SecretsManager::Secret", 1)
    template.hasResourceProperties("AWS::SecretsManager::Secret", {
        Name: "unit-test-default-fetch-migration-pipelineConfig"
    })
})