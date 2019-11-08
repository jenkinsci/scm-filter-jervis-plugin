# Release to Jenkins

This document is for plugin maintainers instructing how to release to the
Jenkins plugin registry.

### Prerequisites

Add credentials to [`~/.jenkins-ci.org`][dot-jenkins].

    userName=user
    password=mypassword

### Prepare for release checklist

- [ ] Update wiki documentation for release.
- [ ] Update [CHANGELOG.md](CHANGELOG.md) with changes.
- [ ] Update [CHANGELOG.md](CHANGELOG.md) with release date.

### Perform Rlease

1. Increment `gradle.properties` version to a stable release: e.g. if version is
   `0.2-SNAPSHOT`, then you should make the release `0.2` since it's the next
   release.  Commit.
2. Run `./gradlew clean publish`

[dot-jenkins]: https://wiki.jenkins-ci.org/display/JENKINS/Dot+Jenkins+Ci+Dot+Org
