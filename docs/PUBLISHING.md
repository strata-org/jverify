# Publishing to Maven Central

JVerify publishes four modules to Maven Central under the `io.github.strata-org`
group. (Java package names remain `org.strata.jverify.*` — only the published
Maven coordinate uses the GitHub-based group id.)

| Artifact | Purpose |
|----------|---------|
| `library` | Specification annotations/methods consumers compile against |
| `common` | Shared code; a transitive dependency of `javac-plugin` and `verifier` |
| `javac-plugin` | Annotation processor that erases specification code at build time |
| `verifier` | The verifier CLI / build tool |

`common` is published because `javac-plugin` and `verifier` depend on it. Without
it, their POMs would reference a coordinate that isn't on Central and consumer
builds would fail to resolve.

## One-time setup

### 1. Verify the namespace on the Central Portal

Maven Central is served by the [Sonatype Central Portal](https://central.sonatype.com).
You must own the `io.github.strata-org` namespace before you can publish.

1. Sign in to the portal with your GitHub account.
2. **Add Namespace → `io.github.strata-org`**.
3. The portal shows a verification key and asks you to create a temporary
   **public** repository with that exact name under the `strata-org` GitHub org
   (e.g. `https://github.com/strata-org/<verification-key>`).
4. Click **Verify**. Once verified, delete the temporary repo.

This proves ownership of the `strata-org` GitHub org — no domain required. The
group id is `io.github.strata-org`, which is already set in `build.gradle.kts`.

### 2. Generate a Central Portal token

In the portal: **Account → Generate User Token**. You'll get a username and a
password (token). Keep them secret.

### 3. Create and publish a GPG signing key

```bash
gpg --gen-key                       # RSA 3072+, no expiry is fine
gpg --list-secret-keys --keyid-format LONG
# Publish the PUBLIC key so Central can verify signatures:
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# Export the PRIVATE key in ASCII-armored form for the Gradle build:
gpg --armor --export-secret-keys <KEY_ID> > signing-key.asc
```

## Building the release bundle

The build stages all four modules into `build/staging-deploy/` and zips them
into the bundle format the Central Portal upload API expects.

```bash
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat signing-key.asc)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="<key passphrase>"   # omit if none

./gradlew centralBundle -Pversion=0.1.0
# → build/central-bundle.zip
```

Notes:
- `-Pversion` **must not** end in `-SNAPSHOT`; Central rejects snapshots. The
  default version is `0.1.0-SNAPSHOT` for local development, and signing is skipped
  for snapshot builds.
- `verifier` requires the `Strata` git submodule (a Lean project) plus `z3` and
  `lake` on the build machine, and the Laurel AST codegen (`make generate-laurel-ast`),
  exactly as the PR-build CI workflow sets up. Build in that environment.

## Uploading

Upload the bundle to the Central Portal. `AUTH` is the base64 of
`username:token` from step 2.

```bash
AUTH=$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_TOKEN" | base64 -w0)

curl --fail-with-body -X POST \
  -H "Authorization: Bearer $AUTH" \
  -F "bundle=@build/central-bundle.zip" \
  "https://central.sonatype.com/api/v1/publisher/upload?name=jverify-0.1.0&publishingType=USER_MANAGED"
```

- `publishingType=USER_MANAGED` uploads to a validated deployment you review and
  release manually from the portal UI. Use `AUTOMATIC` to release immediately
  once validation passes.
- The response is a deployment id. Track it at
  `https://central.sonatype.com/api/v1/publisher/status?id=<deploymentId>`
  or in the portal's **Deployments** view.

Alternatively, upload `build/central-bundle.zip` through the portal web UI
(**Publish → Upload a bundle**).

## After first release

- Once artifacts are live (usually minutes to a few hours to sync to
  `repo1.maven.org`), update `sample-project/app/build.gradle.kts` to drop
  `mavenLocal()` and use the released version.
- Bump `-Pversion` for each subsequent release. Central rejects re-publishing an
  existing version.
