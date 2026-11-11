# CI pipeline migration

## Problem

The previous `.github/workflows/deploy.yml` combined compilation, container publishing, and remote deployment. It ran only on pushes to `main` and built with `./gradlew build -x test`, bypassing the application context integrity test and all other automated tests.

The workflow published an image to GHCR, then used `appleboy/ssh-action@v1.0.3` to connect to a server labelled “Droplet”. It generated a Compose configuration and executed Podman deployment commands. This required package write permission and the `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`, and `SSH_PASSPHRASE` secrets.

The SSH step was the repository's explicit connection to an external deployment host. Its destination was supplied by a secret, so the hostname cannot be determined from the workflow. There is no explicit DeepOcean/VCS URL, mirror, or integration in the repository's Gradle configuration or workflows. A similarly named external status check could also originate from a GitHub App, webhook, or external CI project.

## Decision

Build verification is independent of deployment. The single `ci.yml` workflow checks out the GitHub repository, configures Temurin 25 and the Gradle wrapper, then runs:

```bash
./gradlew clean build --no-daemon --console=plain
```

This executes compilation, the default integrity test, regression tests, and packaging. Compilation or test failures fail the job. It requires no Spotify or Groq production credentials.

The workflow runs on pushes and pull requests targeting `main`, plus manual dispatch. Repository permissions are limited to `contents: read`; checkout does not persist credentials, and pull requests use read-only caches. Superseded runs are cancelled, and jobs have a 15-minute timeout.

Gradle remains at 9.4.1 with Maven Central dependencies. Official [checkout](https://github.com/actions/checkout), [Java setup](https://github.com/actions/setup-java), and [Gradle setup](https://github.com/gradle/actions) actions provide repository checkout, JDK installation, wrapper validation, and caching. CI no longer publishes images or performs SSH deployment.

## External integration retirement

Repository configuration cannot remove installed GitHub integrations or required status checks. If an obsolete DeepOcean/VCS check continues to appear, the repository administrator should identify its source and remove the corresponding configuration:

1. In [Webhooks](https://github.com/Erickkamii/SpotifyRoaster/settings/hooks), disable or delete the hook targeting the obsolete service's payload URL.
2. In **Settings → Integrations → GitHub Apps**, remove this repository from the obsolete app's installation. Uninstall the app only if no other repository uses it. OAuth app access is managed through account **Settings → Applications → Authorized OAuth Apps**.
3. In [Rulesets](https://github.com/Erickkamii/SpotifyRoaster/settings/rules) and [Branches](https://github.com/Erickkamii/SpotifyRoaster/settings/branches), replace the obsolete required status check with `Build and test (Java 25)` after that check has appeared in an Actions run.
4. Disconnect the repository from the obsolete external CI project, if one exists.
5. Review the unused `SSH_*` secrets under repository Actions settings. Remove them only when no other deployment depends on them.

Remove integrations only after matching them to the obsolete service. Workflow changes do not modify GitHub settings or secrets. GitHub-hosted execution must be confirmed in Actions after pushing the updated workflow.
