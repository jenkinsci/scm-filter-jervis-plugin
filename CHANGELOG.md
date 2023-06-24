# SCM Filter Jervis YAML 1.7-continuous Jun 22, 2023

- Convert to maven from gradle.
- Still using Jervis 1.7 for the time being.
- Integrate [automated plugin release][auto-release]

[auto-release]: https://www.jenkins.io/doc/developer/publishing/releasing-cd/

# SCM Filter Jervis YAML 0.3 - Apr 21, 2020

- Fix for SECURITY-1826, upgrade snakeyaml and jervis dependences. Update Yaml
  code to parse YAML using `SafeConstructor`.

# SCM Filter Jervis YAML 0.2.1 - Jan 27, 2020

- No code changes.
- Minor release which only changes how the HPI is packaged.  See [PR #4][#4] for
  details.

[#4]: https://github.com/jenkinsci/scm-filter-jervis-plugin/pull/4

# SCM Filter Jervis YAML 0.2 - Nov 10, 2019

- Support falling back to different paths.  Now a user can provide alternate
  YAML paths to search for YAML.  It will try the first path but fall back to
  searching YAML in alternate paths.  The user simply sets comma separated file
  paths for locations of the Jervis YAML.

# SCM Filter Jervis YAML 0.1 - Nov 8, 2019

- SCM filter for GitHub branch sources in multibranch pipelines.
- User can customize the path of the Jervis YAML file.
