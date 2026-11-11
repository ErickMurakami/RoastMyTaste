# GitHub-native CI cleanup

## Findings

The only original workflow, `.github/workflows/deploy.yml`, ran on pushes to `main`. It:

1. Built with `./gradlew build -x test`, bypassing the default integrity test.
2. Logged in to GHCR and published a Docker image with package write permission.
3. Used `appleboy/ssh-action@v1.0.3` and `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`, and `SSH_PASSPHRASE` secrets to connect to a server labelled “Droplet”.
4. Wrote a Compose file and executed Podman deployment commands there.

That SSH deployment was the only tracked action that contacted an external deployment server. The hostname came from a GitHub secret, so its destination cannot be determined from repository code. There was no explicit DeepOcean/VCS URL, mirror, credential, or build hook in the tracked workflows, Gradle build/settings/wrapper, environment example, Dockerfile, or documentation. No repository `gradle.properties`, init scripts, or additional CI scripts were present. No local Gradle init configuration was found either.

The evidence establishes the obsolete SSH deployment path; it does **not** establish that this was a service specifically called DeepOcean VCS. An externally installed check or webhook cannot be removed by editing a workflow.

## Changes

Replaced the deployment workflow with a single `.github/workflows/ci.yml`:

- Push and pull request triggers targeting `main`, plus `workflow_dispatch`.
- Current repository checkout with persisted Git credentials disabled.
- Temurin Java 25 and official Gradle setup with wrapper validation/cache.
- `./gradlew clean build --no-daemon --console=plain`: standard compilation, integrity/regression tests, and packaging. No test exclusions or custom deployment command.
- Only `contents: read`; PR cache access is read-only.
- A 15-minute job timeout and cancellation of superseded runs.
- No Docker login, package publishing, SSH steps, production credentials, or deployment.

The wrapper remains on Gradle 9.4.1 and Maven Central remains the dependency repository. GitHub repository secrets have not been read, deleted, or modified.

Official action references: [checkout](https://github.com/actions/checkout), [setup-java](https://github.com/actions/setup-java), and [Gradle actions](https://github.com/gradle/actions).

## Manual action if the old check still appears

These settings were not accessible through the local checkout, so their state is unverified. Only remove a setting when it identifies the obsolete service.

1. Open [repository Webhooks](https://github.com/Erickkamii/SpotifyRoaster/settings/hooks). Disable/delete the hook whose payload URL targets the obsolete DeepOcean/VCS endpoint.
2. Open repository **Settings → Integrations → GitHub Apps**. Configure the obsolete app's installation and remove this repository from its access, or uninstall it if it is no longer used elsewhere. For OAuth app integrations, use account **Settings → Applications → Authorized OAuth Apps** to revoke the obsolete application's access.
3. Open [Rulesets](https://github.com/Erickkamii/SpotifyRoaster/settings/rules) and [Branches](https://github.com/Erickkamii/SpotifyRoaster/settings/branches). Remove any required status check belonging to that obsolete service. After the new workflow has run, select `Build and test (Java 25)` as the GitHub-native required check if desired.
4. If there is a corresponding external CI project/repository integration, disconnect this repository in that service as well.
5. The `SSH_*` secrets are no longer referenced by CI. Review/remove them manually only if no other workflow or deployment uses them. No secret changes were attempted through code.

A GitHub-hosted Actions pass must be confirmed from an actual run after pushing; local Gradle success alone is not such confirmation.
