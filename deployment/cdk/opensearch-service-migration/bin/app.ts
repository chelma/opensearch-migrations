#!/usr/bin/env node
import 'source-map-support/register';
import {readFileSync} from 'fs';
import {App, Tags} from 'aws-cdk-lib';
import {StackComposer} from "../lib/stack-composer";

const app = new App();
const versionFile = readFileSync('../../../VERSION', 'utf-8')
// Remove any blank newlines because this would be an invalid tag value
const version = versionFile.replace(/\n/g, '');
Tags.of(app).add("migration_deployment", version)
const account = process.env.CDK_DEFAULT_ACCOUNT
const region = process.env.CDK_DEFAULT_REGION
// Environment setting to allow providing an existing AWS AppRegistry application ARN which each created CDK stack
// from this CDK app will be added to.
const migrationsAppRegistryARN = process.env.MIGRATIONS_APP_REGISTRY_ARN
if (migrationsAppRegistryARN) {
    console.info(`App Registry mode is enabled for CFN stack tracking. Will attempt to import the App Registry application from the MIGRATIONS_APP_REGISTRY_ARN env variable of ${migrationsAppRegistryARN} and looking in the configured region of ${region}`)
}
const customReplayerUserAgent = process.env.CUSTOM_REPLAYER_USER_AGENT

new StackComposer(app, {
    migrationsAppRegistryARN: migrationsAppRegistryARN,
    customReplayerUserAgent: customReplayerUserAgent,
    migrationsSolutionVersion: version,
    env: { account: account, region: region }
});