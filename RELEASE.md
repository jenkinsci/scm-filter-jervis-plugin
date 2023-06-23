# Release to Jenkins

This document is for plugin maintainers instructing how to release to the
Jenkins plugin registry.

### Prerequisites

Install Maven 3.8.8 or higher.

If using noexec filesystem, you may need to customize tmp dir path for maven
dependencies.

    export MAVEN_OPTS='-Djansi.tmpdir=path/to/exec/capable/tmp'

### Prepare for release checklist

- [ ] Update wiki documentation for release.
- [ ] Update [CHANGELOG.md](CHANGELOG.md) with changes.
- [ ] Update [CHANGELOG.md](CHANGELOG.md) with release date.

### Testing Locally

To build the plugin `.hpi` file run the following command.

    mvn clean package

Upload `target/scm-filter-jervis.hpi` to your test Jenkins instance.

### Perform Release

Releases are now [automatically handled][auto-release] by the Jenkins org.

[auto-release]: https://www.jenkins.io/doc/developer/publishing/releasing-cd/
